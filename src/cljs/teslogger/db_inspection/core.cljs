(ns teslogger.db-inspection.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! chan pub sub unsub-all]]
            [clojure.browser.net :as net]
            [goog.events :as events])
  (:use [teslogger.db-inspection.auto :only [start-autosnapshot]])
  (:import [goog.net.EventType]
           [goog.events EventType]))

(enable-console-print!)

(def app-state
  (atom {:candidates #{}
         :watches #{}}))

(defcomponent tables-widget [data owner]
  (init-state [_]
    {:selected-candidate ""})
  (will-mount [_]
    (let [xhrio (net/xhr-connection)]
      (events/listen xhrio goog.net.EventType.SUCCESS
        (fn [e]
          (om/update! data :candidates (set (js->clj (.getResponseJson xhrio))) )))
      (.send xhrio "tables")))
  (render-state [_ {:keys [comm selected-candidate]}]
    (html 
      [:form.form-inline
        [:div.form-group
          [:select.form-control
            {:on-change #(om/set-state! owner :selected-candidate (.. % -target -value))}
            [:option ""]
            (for [tname (sort (:candidates data))]
              [:option (merge {} (when (= selected-candidate tname) {:selected "selected"})) tname])]
          [:button.btn.btn-primary
            {:type "button"
             :on-click (fn [e]
                         (when-let [tname (not-empty (om/get-state owner :selected-candidate))]
                           (put! comm [:add tname])
                           (om/set-state! owner :selected-candidate "")))} "Watch"]]])))

(defn- take-snapshot [ch & table-names]
  (let [xhrio (net/xhr-connection)]
    (events/listen xhrio goog.net.EventType.SUCCESS
        (fn [e]
          (doseq [[table-name diff] (js->clj (.getResponseJson xhrio))]
            (put! ch {:table table-name :records diff}))))
      (.send xhrio (str "snapshot") "post"
             (pr-str table-names)
             (clj->js {:content-type "application/edn"}))))

(defcomponent watch-table [table-name owner]
  (will-mount [_]
    (let [cx (chan)
          sub-ch (om/get-state owner :sub-ch)]
      (sub sub-ch table-name cx)
      (go-loop []
        (let [{:keys [records]} (<! cx)]
          (om/set-state! owner :records records)
          (recur))))
    (take-snapshot (om/get-state owner :pub-ch) table-name))

  (render-state [_ {:keys [records]}]
    (html
      [:table.table.table-condensed.table-bordered
        [:thead
          [:tr
            (for [col (get records "headers")]
              [:th {:title col} col])]]
        [:tbody
          (for [category ["add" "modify" "delete"]]
            (for [row (get records category)]
              [:tr (case category "add" {:class "inserted"} "delete" {:class "deleted"} {})
                (for [val row]
                  [:td (when (vector? val) {:class "updated"})
                    (if (vector? val)
                      (str (first val) " => " (last val))
                      val)])]))]]))
  (will-unmount [_]
    (unsub-all (om/get-state owner :sub-ch) table-name)))

(defcomponent watch-panel [table-name owner]
  (render-state [_ {:keys [comm sub-ch pub-ch]}]
    (html
      [:div.panel.panel-info
        [:div.panel-heading
          [:span table-name
            [:button.close {:type "button"
                            :on-click #(put! comm [:delete table-name])}
              [:span {:aria-hidden "true"} "×"]]]]
        [:div.panel-body
          (om/build watch-table table-name
            {:init-state {:sub-ch sub-ch :pub-ch pub-ch}})]])))

(defn- send-watching-tables [tables]
  (let [xhrio (net/xhr-connection)]
    (.send xhrio (str "watch-tables" tables) "post"
           (pr-str tables)
           (clj->js {:content-type "application/edn"}))))

(defn add-watch-panel [app table]
  (om/transact! app :watches #(conj % table))
  (om/transact! app :candidates #(disj % table))
  (send-watching-tables (:watches @app)))

(defn delete-watch-panel [app table]
  (om/transact! app :watches #(disj % table))
  (om/transact! app :candidates #(conj % table))
  (send-watching-tables (:watches @app)))

(defn handle-event [type app val]
  (case type
    :add (add-watch-panel app val)
    :delete (delete-watch-panel app val)))

(defcomponent main-app [{:keys [watches] :as data} owner]
  (init-state [_]
    (let [pub-ch (chan)]
      
      {:comm (chan)
       :pub-ch pub-ch
       :sub-ch (pub pub-ch :table)
       :auto-mode false}))
  (will-mount [_]
    (go (while true
          (let [[type value] (<! (om/get-state owner :comm))]
            (handle-event type data value))))
    (let [xhrio (net/xhr-connection)]
      (events/listen xhrio goog.net.EventType.SUCCESS
        (fn [e]
          (om/set-state! owner :auto-mode true)
          (start-autosnapshot (om/get-state owner :pub-ch))))
      (.send xhrio "auto" "post")))
  (render-state [_ {:keys [comm sub-ch pub-ch] :as state}]
    (html
      [:div.row
        [:div.col-md-6
          (om/build tables-widget data
            {:init-state {:comm comm}})]
        [:div.col-md-6
          [:div.pull-right
            [:button.btn.btn-success.btn-lg {:type "button"
                                             :on-click #(apply take-snapshot pub-ch (seq watches))}
             (if (om/get-state owner :auto-mode)
               [:span.glyphicon.glyphicon-screenshot "auto"]
               [:span.glyphicon.glyphicon-camera "snapshot"])]]]
        [:div#panel-container.col-md-12
          (om/build-all watch-panel watches
          {:init-state {:comm comm :sub-ch sub-ch :pub-ch pub-ch}})]])))
    
(om/root main-app app-state
  {:target (.getElementById js/document "app")})


