(ns teslogger.db-inspection.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-bootstrap.random :as r]
            [om-tools.dom :as d :include-macros true]))

(defn widget [data]
  (om/component (r/jumbotron
   {}
   (d/h1 "Teslogger DB Inspection")
   (d/p "Difference of data."))))

(om/root widget {}
         {:target (.getElementById js/document "app")})
