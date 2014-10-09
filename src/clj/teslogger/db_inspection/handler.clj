(ns teslogger.db-inspection.handler
  (:use [compojure.core :only [defroutes GET POST DELETE]]
        [liberator.core :only [defresource request-method-in]]
        [liberator.representation :only [Representation]]
        (hiccup core page element
                (util :only (with-base-url)))
        [environ.core :only [env]])
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.data.codec.base64 :as b64]
            [clj-time [core :as tm]
                      [format :as tm-fmt]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [liberator.dev :as dev]
            [teslogger.db-inspection.auto :as auto])
  (:import [net.unit8.teslogger.comparator TableSnapshot]
           [java.util Properties]
           [org.h2 Driver]
           [org.apache.commons.dbcp2 BasicDataSourceFactory]))


(def db-url (atom nil))

(defn init []
  (let [props (Properties.)]
    (reset! db-url
            (or (env :teslogger-db-url)
                "jdbc:mysql://localhost/teslogger?user=root&password=root"))
    (.setProperty props "url" @db-url)
    (def snapshoter (TableSnapshot.
                      (BasicDataSourceFactory/createDataSource props)
                      "jdbc:h2:file:./target/compare"))))

(defn body-as-string [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn parse-edn [context key]
  (when (#{:put :post} (get-in context [:request :request-method]))
    (try
      (if-let [body (body-as-string context)]
        (let [data (read-string body)]
          [false {key data}])
        {:message "No body"})
      (catch Exception e
        (.printStackTrace e)
        {:message (format "IOException: %s" (.getMessage e))}))))

(defresource tables
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [context] (seq (.listCandidate snapshoter))))

(defresource snapshot [table-name]
  :available-media-types ["application/json"]
  :allowed-methods [:post]
  :malformed? #(parse-edn % ::data)
  :post! (fn [ctx] (.take snapshoter (into-array (::data ctx))))
  :post-redirect? false
  :handle-created (fn [ctx]
                    (->> (::data ctx)
                         (map (fn [tname]
                                [tname (dissoc (bean (.diffFromPrevious snapshoter tname)) :class)]))
                         (reduce #(assoc %1 (first %2) (last %2)) {})))
  :handle-exception #(.printStackTrace (:exception %)))

(defresource diff [table-name]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok #(.diffFromPrevious snapshoter table-name))

(defresource watch-tables
  :available-media-types ["application/edn"]
  :allowed-methods [:post]
  :malformed? #(parse-edn % ::data)
  :post! (fn [req]
           (reset! auto/auto-watching-tables (::data req))
           (auto/create-triggers))
  :hanlhdle-exception #(.printStackTrace (:exception %)))

(defresource index
  :available-media-types ["text/html"]
  :handle-ok (fn [context]
               (with-base-url (get-in context [:request :context] "")
                 (html5
                  [:head
                   (include-css "/css/bootstrap.min.css"
                                "/css/inspection.css")]
                  [:body
                   [:div.container
                    [:h3 "Teslogger Database Inspection"]
                    [:div#app]]
                   (include-js "http://fb.me/react-0.11.2.js"
                               "/js/html2canvas.js"
                               "/js/main.min.js")
                   #_(javascript-tag "goog.require('teslogger.db-inspection.core');")]))))

(defn case-id []
  (try
    (let [res (slurp "http://localhost:5621/current-case")]
      (or (not-empty (get (json/read-str res) "id")) "other"))
    (catch Exception ex
      "other")))
               
(defroutes app-routes
  (GET "/" [] index)
  (GET "/tables" [] tables)
  (POST "/snapshot" [] snapshot)
  (GET "/diff/:table_name" [table-name]
    (diff table-name))
  (POST "/watch-tables" [] watch-tables)
  (POST "/save-screenshot" request
    (let [img (slurp (:body request))
          case-id (case-id)
          ss-path (io/file "screenshots"
                           case-id
                           (str (tm-fmt/unparse (tm-fmt/formatter "yyyyMMddhhmmss")
                                                (tm/now))
                                ".png"))]
      (io/make-parents ss-path)
      (io/copy (b64/decode (.getBytes img)) ss-path)
      "ok"))
  (POST "/auto" []
    (try
      (auto/start @db-url snapshoter)
      "ok"
      (catch Exception ex
        (.printStackTrace ex)
        {:status 400 :body "ng"})))
  (DELETE "/auto" []
    (auto/stop)
    "ok")
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (dev/wrap-trace :header :ui)
      handler/api))
