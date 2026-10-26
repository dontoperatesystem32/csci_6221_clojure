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

(defn box
  "Exact signed distance for an axis-aligned box. Half-sizes are [hx hy hz]."
  [center half-sizes]
  (when-not (and (= 3 (count half-sizes)) (every? pos? half-sizes))
    (throw (ex-info "Box half-sizes must be three positive numbers." {:half-sizes half-sizes})))
  (fn [point]
    (let [q (mapv - (mapv #(Math/abs (double %)) (v/subtract point center)) half-sizes)]
      (+ (v/length (mapv #(max % 0.0) q))
         (min (apply max q) 0.0)))))

(defn cube
  "Axis-aligned cube with a full side length, not a half-size."
  [center side-length]
  (box center (vec (repeat 3 (/ side-length 2.0)))))

(def default-scene
  ;; Coordinates are [X Y Z]; Y is height, so both centers have Y = 0.
  (union (sphere [10.0 0.0 -2.0] 2.0)
         (cube [12.0 0.0 2.0] 3.0)))
