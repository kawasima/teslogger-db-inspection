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
        (let [{:keys [records update-ch]} (<! cx)]
          (om/set-state! owner :records records)
          (om/set-state! owner :update-ch update-ch)
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

  (did-update [_ _ _]
    (when-let [ch (om/get-state owner :update-ch)]
      (put! ch "did update.")))

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

(defn- send-watching-tables [owner tables]
  (let [xhrio (net/xhr-connection)]
    (events/listen xhrio goog.net.EventType.ERROR
                   (fn [e]
                     (js/console.error "Can't `create trigger` for auto mode.")
                     (om/set-state! owner :auto-mode false)))
    (.send xhrio (str "watch-tables" tables) "post"
           (pr-str tables)
           (clj->js {:content-type "application/edn"}))))

(defn add-watch-panel [app owner table]
  (om/transact! app :watches #(conj % table))
  (om/transact! app :candidates #(disj % table))
  (when (om/get-state owner :auto-mode)
    (send-watching-tables owner (:watches @app))))

(defn delete-watch-panel [app owner table]
  (om/transact! app :watches #(disj % table))
  (om/transact! app :candidates #(conj % table))
  (when (om/get-state owner :auto-mode)
    (send-watching-tables owner (:watches @app))))

(defn handle-event [type app owner val]
  (case type
    :add (add-watch-panel app owner val)
    :delete (delete-watch-panel app owner val)))

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
            (handle-event type data owner value))))
    (let [xhrio (net/xhr-connection)]
      (events/listen xhrio goog.net.EventType.SUCCESS
        (fn [e]
          (om/set-state! owner :auto-mode true)
          (start-autosnapshot (om/get-state owner :pub-ch))))
      (.send xhrio "auto" "post")))

  (render-state [_ {:keys [comm sub-ch pub-ch] :as state}]
    (html
     [:div
      [:div.row
       [:div.col-md-6.col-sm-6
        (om/build tables-widget data
                  {:init-state {:comm comm}})]
       [:div.col-md-6.col-sm-6pull-right
        [:span.pull-right
         [:button.btn.btn-success.btn-lg {:type "button"
                                          :on-click (fn [e]
                                                      (if (om/get-state owner :auto-mode)
                                                        (om/set-state! owner :auto-mode false)
                                                        (apply take-snapshot pub-ch (seq watches))))}
          (if (om/get-state owner :auto-mode)
            [:span [:i.fa.fa-camera] "auto"]
            [:span [:i.fa.fa-camera-retro] "snapshot"])]]]]
      [:div.row
       [:div#panel-container.col-md-12
        (om/build-all watch-panel watches
                      {:init-state {:comm comm :sub-ch sub-ch :pub-ch pub-ch}})]]])))

(defn- clear-history []
  (let [xhrio (net/xhr-connection)]
    (.send xhrio "snapshot" "delete")))

(defcomponent menu-app [app owner]
  (render [_]
    (html
     [:ul.nav.navbar-nav.navbar-right
      [:li
       [:form.navbar-form
        [:button#clear-history.btn.btn-danger {:on-click (fn [e] (clear-history) false)}
         [:span.fa.fa-trash-o]]]]])))

(om/root menu-app {}
         {:target (.getElementById js/document "menu")})
    
(om/root main-app app-state
  {:target (.getElementById js/document "app")})


