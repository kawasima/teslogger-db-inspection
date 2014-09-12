(ns teslogger.db-inspection.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [om-bootstrap.random :as r]
            [om-bootstrap.button :as b]
            [om-bootstrap.panel :as p]
            [om-bootstrap.table :refer [table]]
            [cljs.core.async :refer [put! <! chan]]
            [clojure.browser.net :as net]
            [goog.events :as events])
  (:import [goog.net.EventType]
           [goog.events EventType]))

(enable-console-print!)

(def app-state
  (atom {:candidates #{}
         :watches #{}
         :snapshot-ch #{}}))

(defcomponent tables-widget [data owner]
  (will-mount [_]
    (let [xhrio (net/xhr-connection)]
      (events/listen xhrio goog.net.EventType.SUCCESS
        (fn [e]
          (om/update! data :candidates (set (js->clj (.getResponseJson xhrio))) )))
      (.send xhrio "http://localhost:3000/tables")))
  (render-state [_ {:keys [comm]}]
    (dom/div {:class "form-group"}
      (dom/select {:on-change #(om/set-state! owner :selected-candidate (.. % -target -value))
                   :class "form-control"}
        (dom/option nil "")
        (map #(dom/option nil %) (:candidates data)))
      (b/button {:bs-style "primary"
                  :on-click #(put! comm [:add (om/get-state owner :selected-candidate)])} "Watch"))))

(defn- take-snapshot [table-name owner]
  (let [xhrio (net/xhr-connection)]
    (events/listen xhrio goog.net.EventType.SUCCESS
        (fn [e]
          (om/set-state! owner :records (js->clj (.getResponseJson xhrio)) )))
      (.send xhrio (str "http://localhost:3000/snapshot/" table-name) "post")))

(defcomponent watch-table [table-name owner]
  (init-state [_]
    {:ch (chan)})
  (will-mount [_]
    (go (while true
          (let [_ (<! (om/get-state owner :ch))]
            (take-snapshot table-name owner))))
    (put! (om/get-state owner :comm) [:add-ch (om/get-state owner :ch)]))
  (render-state [_ {:keys [records]}]
    (table {:striped? false :bordered? true :condensed? true :hover? true}
      (dom/thead
        (dom/tr
          (for [col (get records "headers")]
            (dom/th col))))
      (dom/tbody
        (for [category ["add" "modify" "delete"]]
          (for [row (get records category)]
            (dom/tr (case category "add" {:class "inserted"} "delete" {:class "deleted"} {})
              (for [val row]
                (dom/td (when (vector? val) {:class "updated"})
                  (if (vector? val)
                    (str (first val) " => " (last val))
                    val)))))))))
  (will-umount [_]
    (put! (om/get-state owner :comm) [:remove-ch (om/get-state owner :ch)])))

(defcomponent watch-panel [table-name owner]
  (render-state [_ {:keys [comm]}]
    (p/panel {:header (dom/span table-name
                        (dom/button {:type "button"
                                     :class "close"
                                     :on-click #(put! comm [:delete table-name])}
                          (dom/span {:aria-hidden "true"} "×"))) }
      (om/build watch-table table-name
        {:init-state {:comm comm}}))))

(defn add-watch-panel [app table]
  (om/transact! app :watches #(conj % table))
  (om/transact! app :candidates #(disj % table)))

(defn delete-watch-panel [app table]
  (om/transact! app :watches #(disj % table))
  (om/transact! app :candidates #(conj % table)))

(defn handle-event [type app val]
  (case type
    :add (add-watch-panel app val)
    :delete (delete-watch-panel app val)
    :add-ch    (om/transact! app :snapshot-ch #(conj % val))
    :remove-ch (om/transact! app :snapshot-ch #(disj % val))))

(defcomponent main-app [{:keys [watches] :as data} owner]
  (will-mount [_]
    (let [comm (chan)]
      (om/set-state! owner :comm comm)
      (go (while true
            (let [[type value] (<! comm)]
              (handle-event type data value))))))
  (render-state [_ {:keys [comm] :as state}]
    (dom/div {:class "row"}
      (dom/div {:class "col-md-6"}
        (om/build tables-widget data
          {:init-state {:comm comm}}))
      (dom/div {:class "col-md-6"}
        (dom/div {:class "pull-right"}
          (dom/button {:type "button"
                       :class "btn btn-success btn-lg"
                       :on-click #(doseq [ch (:snapshot-ch @data)] (put! ch :refresh))} "Snapshot!")))
      (dom/div {:class "col-md-12"}
        (om/build-all watch-panel watches
          {:init-state {:comm comm}})))))
    
 
(om/root main-app app-state
  {:target (.getElementById js/document "app")})
