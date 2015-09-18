(ns teslogger.db-inspection.main
  (:gen-class)
  (:require [org.httpkit.server :refer :all]
            [teslogger.db-inspection.handler :as handler]
            [environ.core :refer [env]]))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)
    (handler/destroy)))

(defn -main [& args]
  (let [port (Integer/parseInt (or (:port env) "8868"))]
    (handler/init)
    (reset! server (run-server #'handler/app {:port port}))))
