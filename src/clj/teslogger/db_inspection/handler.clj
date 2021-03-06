(ns teslogger.db-inspection.handler
  (:use [compojure.core :only [defroutes GET POST DELETE ANY]]
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


(defonce db-url (atom nil))

(defn init []
  (let [props (Properties.)]
    (reset! db-url
            (or (env :teslogger-db-url)
                "jdbc:mysql://localhost/teslogger?user=root&password=root&autocommit=false"))
    (.setProperty props "url" @db-url)
    (def snapshoter (TableSnapshot.
                      (BasicDataSourceFactory/createDataSource props)
                      "jdbc:h2:./target/compare;AUTO_SERVER=TRUE"))))

(defn destroy []
  (.dispose snapshoter))

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
  :handle-ok (fn [context]
               (vec (.listCandidate snapshoter))))

(defresource snapshot
  :available-media-types ["application/json"]
  :allowed-methods [:post :delete]
  :malformed? #(parse-edn % ::data)
  :post! (fn [ctx] (.take snapshoter (into-array (::data ctx))))
  :delete (fn [_] (.clear snapshoter))
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
  :handle-exception #(.printStackTrace (:exception %)))

(defresource index
  :available-media-types ["text/html"]
  :handle-ok (fn [context]
               (with-base-url (get-in context [:request :context] "")
                 (html5
                  [:head
                   (include-css "//cdn.jsdelivr.net/bootstrap/3.3.5/css/bootstrap.min.css"
                                "//cdn.jsdelivr.net/fontawesome/4.4.0/css/font-awesome.min.css"
                                "/css/inspection.css")]
                  [:body
                   [:nav.navbar.navbar-default.navbar-fixed-top
                    [:div.container-fluid
                     [:div.navbar-header
                      [:a.navbar-brand "Teslogger Database Inspection"]]
                     [:div#menu.collapse.navbar-collapse]]]
                   [:div.container
                    [:div#app]]
                   (include-js "/js/html2canvas.js"
                               "/js/main.min.js")]))))

(defn case-id []
  (try
    (let [res (slurp "http://localhost:5621/current-case")]
      (or (not-empty (get (json/read-str res) "id")) "other"))
    (catch Exception ex
      "other")))
               
(defroutes app-routes
  (GET "/" [] index)
  (GET "/tables" [] tables)
  (ANY "/snapshot" [] snapshot)
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
      (catch IllegalArgumentException ex
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
