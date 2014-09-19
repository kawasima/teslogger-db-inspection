(defproject net.unit8/teslogger-db-inspection "0.1.0-SNAPSHOT"
  :description "The tool for viewing the difference of data."
  :url "http://github.com/kawasima/teslogger-db-inspection"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.6"]
                 [liberator "0.12.1"]
                 [hiccup "1.0.5"]
                 [net.unit8.teslogger/comparator-ds "0.1.0-SNAPSHOT"]

                 [org.clojure/clojurescript "0.0-2311"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [om "0.7.3"]
                 [sablono "0.2.22"]
                 [prismatic/om-tools "0.3.2"]]
  :plugins [[lein-ring "0.8.10"]
            [lein-cljsbuild "1.0.3"]]
  :source-paths ["src/clj"]
  :ring {:handler teslogger.db-inspection.handler/app}

  :cljsbuild {
              :builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :compiler {:output-to "resources/public/js/main.js"
                                   :optimizations :simple}}]
              }
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [mysql/mysql-connector-java "5.1.32"]
                        [ring-mock "0.1.5"]]}})
