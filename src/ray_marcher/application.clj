(ns ray-marcher.application
  "Reusable viewer/export actions. Never exits the process."
  (:require [ray-marcher.image :as image]
            [ray-marcher.render :as render]
            [ray-marcher.ui :as ui]))

(defn open-viewer! [{:keys [width height threads]} {:keys [render-options camera-state]}]
  (ui/show-viewer! width height
                   (fn [w h options] (render/render-image w h (merge render-options options)))
                   camera-state threads))

(defn export!
  "Return the saved file, or nil on cancellation. Call outside the Swing EDT.
  Callbacks run on rendering threads and must marshal UI updates to the EDT."
  [{:keys [width height threads]} {:keys [render-options]} path callbacks]
  (let [options (merge render-options {:threads threads} callbacks)]
    (when-let [picture (render/render-image width height options)]
      (when-not (and (:cancelled? callbacks) ((:cancelled? callbacks)))
        (when-let [writing! (:on-writing callbacks)] (writing!))
        (image/save-png! picture path)))))
