(ns ray-marcher.render
  "Sequential primary-ray rendering. This namespace has no Swing dependencies."
  (:require [ray-marcher.camera :as camera]
            [ray-marcher.lighting :as lighting]
            [ray-marcher.march :as march]
            [ray-marcher.scene :as scene])
  (:import [java.awt.image BufferedImage]))

(defn silhouette-color
  "Flat hit/miss colors, retained for geometry checks and lighting comparisons."
  [result]
  (case (:status result)
    :hit 0xF5AC48
    :miss 0x101A2B
    :step-limit 0xFF00FF))

(defn render-image
  "One ray per pixel. Optional on-progress receives completed rows and total rows.
  Returns nil if cancelled? becomes true between rows.
  Scene, camera, marching settings, and coloring are replaceable.
  Magenta marks a ray that exhausted its step budget, rather than a proven miss."
  ([width height]
   (render-image width height {}))
  ([width height {:keys [scene-distance camera settings color-fn cancelled? lighting on-progress]
                 :or {scene-distance scene/default-scene
                      camera camera/default-camera
                      settings march/default-settings
                      cancelled? (constantly false)}}]
   (let [image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
         shade (or color-fn (lighting/make-shader scene-distance lighting))]
     (when on-progress (on-progress 0 height))
     (loop [y 0]
       (cond
         (cancelled?) nil
         (= y height) image
         :else
         (do
           (dotimes [x width]
             (let [direction (camera/ray-direction camera x y width height)
                   result (march/raymarch scene-distance (:origin camera) direction settings)]
               (.setRGB image x y (int (shade result)))))
           (when on-progress (on-progress (inc y) height))
           (recur (inc y))))))))
