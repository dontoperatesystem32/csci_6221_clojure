(ns ray-marcher.scene
  "Scenes are functions from a world-space point to a signed distance."
  (:require [ray-marcher.vector :as v]))

(defn sphere
  "Return the exact signed distance function for a sphere."
  [center radius]
  (when-not (pos? radius)
    (throw (ex-info "Sphere radius must be positive." {:radius radius})))
  (fn [point]
    (- (v/length (v/subtract point center)) radius)))

(defn union
  "Combine distance functions into a scene. An empty scene has no surfaces."
  [& distances]
  (fn [point]
    (reduce (fn [nearest distance] (min nearest (distance point)))
            Double/POSITIVE_INFINITY distances)))

(def default-scene (sphere [10.0 0.0 0.0] 2.0))
