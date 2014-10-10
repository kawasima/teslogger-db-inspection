(defproject net.unit8.teslogger/teslogger-db-inspection "0.1.0-SNAPSHOT"
  :description "The tool for viewing the difference of data."
  :url "http://github.com/kawasima/teslogger-db-inspection"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [clj-time "0.6.0"]
                 [compojure "1.1.6"]
                 [environ "1.0.0"]
                 [liberator "0.12.1"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [hiccup "1.0.5"]
                 [net.unit8.teslogger/comparator-ds "0.1.1"]
                 [net.unit8/ulon-colon "0.2.0"]

                 [org.clojure/clojurescript "0.0-2311"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [om "0.7.3"]
                 [sablono "0.2.22"]
                 [prismatic/om-tools "0.3.2"]
                 [com.facebook/react "0.11.2"]]
  :plugins [[lein-ring "0.8.10"]
            [lein-cljsbuild "1.0.3"]
            [lein-package "2.1.1"]]
  :source-paths ["src/clj"]
  :ring {:handler teslogger.db-inspection.handler/app
         :init teslogger.db-inspection.handler/init}

  :package {:skipjar true
            :autobuild true
            :reuse false
            :artifacts [{:build "ring war" :extension "war"}]}
  :cljsbuild {
              :builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :compiler {:output-to "resources/public/js/main.js"
                                   :optimizations :simple}}
                       {:id "release"
                        :source-paths ["src/cljs"]
                        :compiler {:output-to "resources/public/js/main.min.js"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :preamble ["react/react.min.js"]
                                   :externs  ["react/externs/react.js"]}}]}
  :hooks [leiningen.package.hooks.deploy
          leiningen.package.hooks.install]

  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [mysql/mysql-connector-java "5.1.32"]
                        [ring-mock "0.1.5"]]}
   :oracle {:dependencies [[com.oracle/ojdbc6 "11.2.0"]]}})
