(ns ray-marcher.scene
  "Scenes are functions from a world-space point to a signed distance."
  (:require [ray-marcher.vector :as v]
            [ray-marcher.blend :as blend]))

(defn sphere
  "Return the exact signed distance and color for a sphere.
  Returns {:distance d :color c}, where color may be a static 0xRRGGBB int
  or a function (fn [point] -> 0xRRGGBB)."
  ([center radius]
   (sphere center radius 0xFFFFFF))
  ([center radius color]
   (when-not (pos? radius)
     (throw (ex-info "Sphere radius must be positive." {:radius radius})))
   (fn [point]
     {:distance (- (v/length (v/subtract point center)) radius)
      :color    (if (fn? color) (color point) color)})))

(defn blended-union
  "Combine an ordered group with {:mode ..., :width ...} settings.
  Polynomial folds left, so preserve order/grouping for reproducibility."
  [settings & shapes]
  (let [combine (blend/combiner settings)]
    (fn [point]
      (reduce (fn [nearest shape] (combine nearest (shape point)))
              {:distance Double/POSITIVE_INFINITY :color nil}
              shapes))))

(defn union
  "Hard minimum union. An empty scene has no surfaces."
  [& shapes]
  (apply blended-union {:mode :hard} shapes))


(defn floor-plane
  "Exact signed distance for an infinite horizontal floor at height Y.
  Space below the floor is solid; positive Y points upward."
  [height]
  (when-not (and (number? height) (Double/isFinite (double height)))
    (throw (ex-info "Floor height must be a finite number." {})))
  (fn [[_ y _]] (- y height)))

(defn box
  "Exact signed distance and color for an axis-aligned box. Half-sizes are [hx hy hz]."
  ([center half-sizes]
   (box center half-sizes 0xFFFFFF))
  ([center half-sizes color]
   (when-not (and (= 3 (count half-sizes)) (every? pos? half-sizes))
     (throw (ex-info "Box half-sizes must be three positive numbers." {:half-sizes half-sizes})))
   (fn [point]
     (let [q (mapv - (mapv #(Math/abs (double %)) (v/subtract point center)) half-sizes)
           d (+ (v/length (mapv #(max % 0.0) q))
                (min (apply max q) 0.0))]
       {:distance d
        :color    (if (fn? color) (color point) color)}))))

(defn cube
  "Axis-aligned cube with a full side length, not a half-size."
  ([center side-length]
   (cube center side-length 0xFFFFFF))
  ([center side-length color]
   (box center (vec (repeat 3 (/ side-length 2.0))) color)))

(defn- wrap-coord
  "Folds coordinate v into [-half-period, +half-period] symmetrically."
  [^double v ^double period]
  (- (mod (+ v (* 0.5 period)) period) (* 0.5 period)))

(defn tile-space
  "Infinitely tiles a scene function across 3D space with cell dimensions [sx sy sz].
  Turns a localized fractal into an endless surrounding interior world."
  [[sx sy sz] shape]
  (let [px-period (double sx)
        py-period (double sy)
        pz-period (double sz)]
    (fn [[x y z]]
      (shape [(wrap-coord x px-period)
              (wrap-coord y py-period)
              (wrap-coord z pz-period)]))))


(def default-objects
  ;; Coordinates are [X Y Z]; Y is height, so both centers have Y = 0.
  [(sphere [10.0 0.0 -2.0] 2.0 0xFF7B3A)  ;; Warm coral sphere
   (cube   [12.0 0.0  2.0] 3.0 0x2A9D8F)]) ;; Teal cube

(def default-scene (apply union default-objects))
