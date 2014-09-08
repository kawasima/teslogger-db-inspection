(ns teslogger.db-inspection.handler
  (:use [compojure.core]
        [liberator.core :only [defresource request-method-in]]
        [liberator.representation :only [Representation]]
        [hiccup core page element])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [liberator.dev :as dev]))

(defresource tables)
(defresource index
  :available-media-types ["text/html"]
  :handle-ok (fn [context]
               (html5
                [:head
                 (include-css "/css/bootstrap.min.css")
                 ]
                [:body
                 [:div#app]
                 (include-js "http://fb.me/react-0.11.1.js"
                             "/js/main.js")
                 (javascript-tag "goog.require('teslogger.db-inspection.core');")])))

(defroutes app-routes
  (GET "/" [] index)
  (GET "/tables" []
       )
  (POST "/snapshot/:table_name" []
        )
  (GET "/diff/:table_name" [])
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (dev/wrap-trace :ui :trace)
      handler/site))
