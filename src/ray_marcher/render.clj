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

(defn render-image-sync
  "One ray per pixel. Optional on-progress receives completed rows and total rows.
  Returns nil if cancelled? becomes true between rows.
  Scene, camera, marching settings, and coloring are replaceable.
  Magenta marks a ray that exhausted its step budget, rather than a proven miss."
  ([width height]
   (render-image-sync width height {}))
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

(defn multi-render
  [render-fn width height info]
  (loop [threads (get info :threads 1)
         thread-idx 1
         pixels []
         queued-futures (loop [thread-idx 1
                               futures []]
                          (if (> thread-idx threads)
                            futures
                            (recur (inc thread-idx) (conj futures (future (render-fn width height (assoc info :thread-idx thread-idx)))))))]
    (if (> thread-idx threads)
      pixels
      (recur threads (inc thread-idx) (into pixels (deref (let [[head & _] queued-futures] head))) (let [[_ & tail] queued-futures] tail)))))

(defn pixels-to-image [width height pixels]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        java-array (int-array pixels)]
    (.setRGB image 0 0 width height java-array 0 width)
    image))

(defn render-pixels
  "One ray per pixel. Optional on-progress receives completed rows and total rows.
  Returns nil if cancelled? becomes true between rows.
  Scene, camera, marching settings, and coloring are replaceable.
  Magenta marks a ray that exhausted its step budget, rather than a proven miss."
  ([width height]
   (render-pixels width height {}))
  ([width height {:keys [scene-distance camera settings color-fn cancelled? lighting on-progress threads thread-idx]
                  :or {scene-distance scene/default-scene
                       camera camera/default-camera
                       settings march/default-settings
                       cancelled? (constantly false)
                       threads 1
                       thread-idx 1}}]
   (let [shade (or color-fn (lighting/make-shader scene-distance lighting))
         height-div (max 1 (/ height threads))
         max-height (if (= thread-idx threads) height (* thread-idx height-div))
         min-height (* height-div (- thread-idx 1))]
     (loop [y min-height
            pixels []] 
       (if (or (cancelled?) (>= y max-height))
         (if (>= y max-height) pixels
             nil)
         (recur
          (inc y)
          (loop [x 0
                 x_pixels pixels]
            (if (>= x width)
              x_pixels
              (recur
               (inc x)
               (conj x_pixels (int (shade (march/raymarch scene-distance (:origin camera) (camera/ray-direction camera x y width height) settings)))))))))))))

(defn render-image
  ([width height]
   (render-image width height {}))
  ([width height info]
   (if (= 1 (get info :threads 1))
     (render-image-sync width height)
     (pixels-to-image width height (multi-render render-pixels width height info)))
   ))