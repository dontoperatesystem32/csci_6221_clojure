(ns ray-marcher.render
  "Primary-ray image rendering with optional parallel row ranges. No Swing dependencies."
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
  "Render integer row ranges concurrently, then collect them in image order.
  Progress callbacks are serialized and report completed rows across all tasks."
  [render-fn width height info]
  (let [threads (min height (get info :threads 1))
        progress (:on-progress info)
        completed (atom 0)
        progress-lock (Object.)
        report! (when progress
                  (fn [_ _]
                    (locking progress-lock (progress (swap! completed inc) height))))
        _ (when progress (progress 0 height))
        tasks (mapv (fn [index]
                      (future (render-fn width height
                                         (assoc info :threads threads :thread-idx index :on-progress report!))))
                    (range 1 (inc threads)))]
    (try
      (loop [remaining tasks, pixels []]
        (if-let [task (first remaining)]
          (when-let [part @task]
            (recur (next remaining) (into pixels part)))
          pixels))
      (finally (doseq [task tasks] (when-not (future-done? task) (future-cancel task)))))))

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
         min-height (quot (* height (dec thread-idx)) threads)
         max-height (quot (* height thread-idx) threads)
         cancelled-now? #(or (.isInterrupted (Thread/currentThread)) (cancelled?))]
     (loop [y min-height, pixels []]
       (cond
         (cancelled-now?) nil
         (>= y max-height) pixels
         :else
         (let [row (mapv (fn [x]
                           (int (shade (march/raymarch scene-distance (:origin camera)
                                                      (camera/ray-direction camera x y width height) settings))))
                         (range width))]
           (when on-progress (on-progress (inc y) height))
           (recur (inc y) (into pixels row))))))))

(defn render-image
  "Generate an image using one or more rendering tasks. Cancellation returns nil."
  ([width height] (render-image width height {}))
  ([width height info]
   (if (= 1 (get info :threads 1))
     (render-image-sync width height info)
     (when-let [pixels (multi-render render-pixels width height info)]
       (pixels-to-image width height pixels)))))
