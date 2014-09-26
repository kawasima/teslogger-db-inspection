(ns ^{:name "Take shapshots automatically"}
  teslogger.db-inspection.auto
  (:require [clojure.java.jdbc :as j]
            [clojure.core.async :as async :refer [chan]]))

(def oracle-db {:classname "oracle.jdbc.driver.OracleDriver"
                :subprotocol "oracle"
                :subname "thin:scott/tiger@localhost:1521/XE"})

(defn create-trigger []
  (with-db-metadata [md oracle-db]
    (doseq [{table-name :table-name} (resutset-seq (.getTables md nil nil "%" nil))]
      (db-do-commands oracle-db
        (str "CREATE OR REPLACE TRIGGER " table-name
          "_dml_event AFTER INSERT OR UPDATE OR DELETE ON " table-name " FOR EACH ROW\n"
          "BEGIN\n"
          "DBMS_ALERT.SIGNAL('DML_EVENT', systimestamp);\n"
          "END;")))))

(defn start []
  (let [conn (j/get-connection oracle-db)
        ch (chan)]
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
            ;; TODO take snapshot & push notification
            ))
        (recur)))))
