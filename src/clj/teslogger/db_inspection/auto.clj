(ns ^{:name "Take shapshots automatically"}
  teslogger.db-inspection.auto
  (:require [clojure.java.jdbc :as j]
            [clojure.core.async :as async :refer [chan]]
            [clojure.string :as string]
            [clojure.data.json :as json])
  (:use [ulon-colon.producer]
        [environ.core]
        [clojure.core.async :only [go-loop chan]])
  (:import [java.sql Types]))

(def oracle-db (atom nil))

(def auto-watching-tables (atom nil))
(def producer (atom nil))

(defn create-triggers []
  (j/with-db-metadata [md @oracle-db]
    (doseq [table-name @auto-watching-tables]
      (j/db-do-commands @oracle-db
        (str "CREATE OR REPLACE TRIGGER " table-name
          "_dml_event AFTER INSERT OR UPDATE OR DELETE ON " table-name " FOR EACH ROW\n"
          "BEGIN\n"
          "DBMS_ALERT.SIGNAL('EXEC_DML', systimestamp);\n"
          "END;")))))

(defn start [db-url snapshoter]
  (let [[_ subprotocol subname] (string/split db-url #":" 3)]
    (when-not (= subprotocol "oracle")
      (throw (IllegalArgumentException. "Not oracle db")))
    (reset! oracle-db {:classname "oracle.jdbc.driver.OracleDriver"
                       :subprotocol "oracle"
                       :subname subname}))
  (let [conn (j/get-connection @oracle-db)
        ch (chan)]
    (when-not @producer
      (reset! producer (start-producer :port (or (env :auto-snapshot-port) 56297))))

    (let [stmt (.prepareCall conn "{call DBMS_ALERT.REGISTER(?)}")]
      (doto stmt
        (.setString 1 "EXEC_DML")
        (.executeUpdate)
        (.close)))
    (let [stmt (.prepareCall conn "{call DBMS_ALERT.WAITANY(?,?,?,?)}")]
      (doto stmt
        (.registerOutParameter 1 Types/VARCHAR)
        (.registerOutParameter 2 Types/VARCHAR)
        (.registerOutParameter 3 Types/INTEGER)
        (.setInt 4 300))

      (go-loop []
        (.commit conn)
        (.executeUpdate stmt)
        (when (= 0 (.getInt stmt 3))
          (let [msg (.getString stmt 2)]
            (loop [diff {} tables @auto-watching-tables]
              (if (empty? tables)
                (produce {:json (json/write-str diff)})
                (let [table-name (first tables)]
                  (.take snapshoter table-name)
                  (recur
                   (assoc diff table-name
                          (dissoc (bean (.diffFromPrevious snapshoter table-name)) :class))
                   (rest tables)))))))
        (recur)))))

(defn stop []
  )

