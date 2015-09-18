(defproject net.unit8.teslogger/teslogger-db-inspection (clojure.string/trim-newline (slurp "VERSION"))
  :description "The tool for viewing the difference of data."
  :url "http://github.com/kawasima/teslogger-db-inspection"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [clj-time "0.11.0"]
                 [compojure "1.4.0"]
                 [environ "1.0.1"]
                 [liberator "0.13"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [hiccup "1.0.5"]
                 [net.unit8.teslogger/comparator-ds "0.1.7"]
                 [net.unit8/ulon-colon "0.2.3"]
                 [http-kit "2.1.19"]

                 [org.clojure/clojurescript "1.7.122" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.9.0" :scope "provided"]
                 [sablono "0.3.6" :scope "provided"]
                 [prismatic/om-tools "0.3.12" :scope "provided"]]
  :plugins [[lein-ring "0.9.3"]
            [lein-cljsbuild "1.1.0"]
            [lein-package "2.1.1"]]
  :pom-plugins [[org.apache.maven.plugins/maven-assembly-plugin "2.5.5"
                 {:configuration [:descriptors [:descriptor "src/assembly/dist.xml"]]}]]

  :aot :all
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
                                   :pretty-print false}}]}
  :hooks [leiningen.package.hooks.deploy
          leiningen.package.hooks.install]

  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "3.0.1"]
                        [mysql/mysql-connector-java "5.1.36"]
                        [ring-mock "0.1.5"]]}
   :oracle {:dependencies [[com.oracle/ojdbc6 "11.2.0"]]}
   :jar ^{:pom-scope "provided"} {:dependencies [[javax.servlet/javax.servlet-api "3.0.1"]]}})
