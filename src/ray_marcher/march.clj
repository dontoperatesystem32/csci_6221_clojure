(ns ray-marcher.march
  "March one ray independently of the camera, image, and UI."
  (:require [ray-marcher.vector :as v]))

(def default-settings
  {:epsilon 0.001 :max-distance 100.0 :max-steps 128})

(defn raymarch
  "Sphere-trace a scene distance function from ro along unit direction rd.
  Settings require positive epsilon/max-distance and a positive integer max-steps.
  Returns :hit with position, or :miss / :step-limit, plus distance and steps.
  Negative distances count as hits: starting inside a solid is an immediate hit.
  Distance functions must supply exact distances or safe distance bounds."
  ([scene-distance ro rd]
   (raymarch scene-distance ro rd default-settings))
  ([scene-distance ro rd settings]
   (let [{:keys [epsilon max-distance max-steps]} (merge default-settings settings)]
     (loop [t 0.0, steps 0]
       (if (>= steps max-steps)
         {:status :step-limit :distance t :steps steps}
         (let [point (v/add ro (v/scale rd t))
               d (scene-distance point)
               evaluated (inc steps)]
           (cond
             (<= d epsilon)
             {:status :hit :position point :distance t :steps evaluated}

             (> (+ t d) max-distance)
             {:status :miss :distance max-distance :steps evaluated}

             :else (recur (+ t d) evaluated))))))))
