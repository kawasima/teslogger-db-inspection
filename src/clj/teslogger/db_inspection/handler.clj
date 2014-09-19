(ns teslogger.db-inspection.handler
  (:use [compojure.core]
        [liberator.core :only [defresource request-method-in]]
        [liberator.representation :only [Representation]]
        [hiccup core page element])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [liberator.dev :as dev])
  (:import [net.unit8.teslogger.comparator TableSnapshot]
           [java.util Properties]
           [org.h2 Driver]
           [org.apache.commons.dbcp2 BasicDataSourceFactory]))


(let [props (Properties.)]
  (.setProperty props "url" "jdbc:mysql://localhost/teslogger?user=root&password=root")
  (def snapshoter (TableSnapshot.
                    (BasicDataSourceFactory/createDataSource props)
                    "jdbc:h2:file:./target/compare")))

(defresource tables
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [context] (seq (.listCandidate snapshoter))))

(defresource snapshot [table-name]
  :available-media-types ["application/json"]
  :allowed-methods [:post]
  :post! (fn [_] (.take snapshoter table-name))
  :post-redirect? false
  :handle-created (fn [_] (dissoc (bean (.diffFromPrevious snapshoter table-name)) :class))
  :handle-exception #(.printStackTrace (:exception %)))

(defresource diff [table-name]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok #(.diffFromPrevious snapshoter table-name))

(defresource index
  :available-media-types ["text/html"]
  :handle-ok (fn [context]
               (html5
                 [:head
                   (include-css "/css/bootstrap.min.css"
                     "/css/inspection.css")]
                 [:body
                   [:div.container
                     [:h3 "Teslogger Database Inspection"]
                     [:div#app]]
                 (include-js "http://fb.me/react-0.11.1.js"
                             "/js/main.js")
                 (javascript-tag "goog.require('teslogger.db-inspection.core');")])))

(defroutes app-routes
  (GET "/" [] index)
  (GET "/tables" [] tables)
  (POST "/snapshot/:tname" [tname]
    (snapshot tname))
  (GET "/diff/:table_name" [table-name]
    (diff table-name))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (dev/wrap-trace :header :ui)
      handler/api))
