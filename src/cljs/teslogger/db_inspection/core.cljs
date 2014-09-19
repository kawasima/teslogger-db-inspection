(ns teslogger.db-inspection.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
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
      (.send xhrio "/tables")))
  (render-state [_ {:keys [comm]}]
    (html 
      [:form.form-inline
        [:div.form-group
          [:select.form-control
            {:on-change #(om/set-state! owner :selected-candidate (.. % -target -value))}
            [:option ""]
            (for [tname (:candidates data)]
              [:option tname])]
          [:button.btn.btn-primary
            {:type "button"
             :on-click (fn [e]
                         (when-let [tname (om/get-state owner :selected-candidate)]
                           (put! comm [:add tname])
                           (om/set-state! owner :selected-canidate "")))} "Watch"]]])))

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
    (html
      [:table.table
        [:thead
          [:tr
            (for [col (get records "headers")]
              [:th col])]]
        [:tbody
          (for [category ["add" "modify" "delete"]]
            (for [row (get records category)]
              [:tr (case category "add" {:class "inserted"} "delete" {:class "deleted"} {})
                (for [val row]
                  [:td (when (vector? val) {:class "updated"})
                    (if (vector? val)
                      (str (first val) " => " (last val))
                      val)])]))]]))
  (will-umount [_]
    (put! (om/get-state owner :comm) [:remove-ch (om/get-state owner :ch)])))

(defcomponent watch-panel [table-name owner]
  (render-state [_ {:keys [comm]}]
    (html
      [:div.panel.panel-info
        [:div.panel-heading
          [:span table-name
            [:button.close {:type "button"
                            :on-click #(put! comm [:delete table-name])}
              [:span {:aria-hidden "true"} "×"]]]]
        [:div.panel-body
          (om/build watch-table table-name
            {:init-state {:comm comm}})]])))

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
    (html
      [:div.row
        [:div.col-md-6
          (om/build tables-widget data
            {:init-state {:comm comm}})]
        [:div.col-md-6
          [:div.pull-right
            [:button.btn.btn-success.btn-lg {:type "button"
                                             :on-click #(doseq [ch (:snapshot-ch @data)] (put! ch :refresh))} "Snapshot!"]]]
        [:div.col-md-12
          (om/build-all watch-panel watches
          {:init-state {:comm comm}})]])))
    
 
(om/root main-app app-state
  {:target (.getElementById js/document "app")})
