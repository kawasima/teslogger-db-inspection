(ns teslogger.db-inspection.auto
  (:use [ulon-colon.consumer :only [make-consumer consume consume-sync]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! chan]]
            [clojure.browser.net :as net]
            [goog.events :as events]
            [goog.json :as json])
  (:import [goog.net.EventType]
           [goog.events EventType]))

(defn- save-screenshot [img]
  (let [xhrio (net/xhr-connection)]
    (.send xhrio "save-screenshot" "post"
           (.substr img (count "data:image/png;base64,"))
           (clj->js {:content-type "application/octet-stream"}))))

(defn take-screenshot []
  (js/html2canvas (.getElementById js/document "panel-container")
                  (clj->js {:onrendered (fn [canvas]
                                          (save-screenshot
                                           (.toDataURL canvas "image/png")))})))

(defn start-autosnapshot [pub-ch]
  (let [consumer (make-consumer "ws://localhost:56297")]
    (consume consumer
             (fn [msg]
               (doseq [[table-name diff] (js->clj (json/parse (:json msg)))]
                 (put! pub-ch {:table table-name :records diff}))
               (let [interval (atom 0)]
                 (reset! interval
                         (js/setInterval
                          #(when-not (om/rendering?)
                             (take-screenshot)
                             (js/clearInterval @interval))
                          500)))))
    consumer))
