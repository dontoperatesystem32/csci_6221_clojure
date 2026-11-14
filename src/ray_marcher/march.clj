(ns ray-marcher.march
  "March one ray independently of the camera, image, and UI."
  (:require [ray-marcher.vector :as v]))

(def default-settings
  {:epsilon 0.001 :max-distance 100.0 :max-steps 128})

(defn raymarch
  "Sphere-trace a scene function from ro along unit direction rd.
  Scene functions may return {:distance d :color c} or a plain numeric distance.
  Returns :hit with :position and :color, or :miss / :step-limit."
  ([scene-fn ro rd]
   (raymarch scene-fn ro rd default-settings))
  ([scene-fn ro rd settings]
   (let [{:keys [epsilon max-distance max-steps]} (merge default-settings settings)]
     (loop [t 0.0, steps 0]
       (if (>= steps max-steps)
         {:status :step-limit :distance t :steps steps}
         (let [point (v/add ro (v/scale rd t))
               res (scene-fn point)
               d (if (map? res) (:distance res) res)
               c (when (map? res) (:color res))
               evaluated (inc steps)]
           (cond
             (<= d epsilon)
             {:status :hit :position point :distance t :steps evaluated :color c}

             (> (+ t d) max-distance)
             {:status :miss :distance max-distance :steps evaluated}

             :else (recur (+ t d) evaluated))))))))

