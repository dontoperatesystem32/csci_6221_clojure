(ns ray-marcher.fractals
  "Finite fractal distance functions, usable as ordinary scene functions."
  (:require [ray-marcher.scene :as scene]))

(defn- folded-coordinate
  "Absolute coordinate inside a repeating cell, normalized to [-1,1]."
  [coordinate scale]
  (Math/abs (double (- (mod (* (+ coordinate 1.0) scale) 2.0) 1.0))))

(defn menger-sponge
  "A finite Menger sponge centered at center, with full side length and depth.
  Depth 0 is a cube. Each level removes the central cross of every remaining cell.
  Returns a conservative signed distance bound, not an exact Euclidean distance.
  Evaluates depth levels per point; does not enumerate 20^depth solid cubes."
  [center side-length depth]
  (when-not (and (vector? center) (= 3 (count center))
                 (every? #(and (number? %) (Double/isFinite (double %))) center)
                 (number? side-length) (pos? side-length)
                 (Double/isFinite (double side-length))
                 (integer? depth) (<= 0 depth 6))
    (throw (ex-info "Menger sponge needs a finite center, positive side length, and depth 0–6."
                    {:center center :side-length side-length :depth depth})))
  (let [[cx cy cz] center
        half-size (/ side-length 2.0)
        cube (scene/cube [0 0 0] 2)]
    (fn [[px py pz]]
      (let [x (/ (- px cx) half-size)
            y (/ (- py cy) half-size)
            z (/ (- pz cz) half-size)]
        (* half-size
           (loop [level 0, scale 1.0, distance (double (cube [x y z]))]
             (if (= level depth)
               distance
               (let [a (folded-coordinate x scale)
                     b (folded-coordinate y scale)
                     c (folded-coordinate z scale)
                     ;; At least two coordinates in the central third means
                     ;; this point belongs to one of the three square tunnels.
                     middle (max (min a b) (min b c) (min c a))
                     hole (/ (- (/ 1.0 3.0) middle) scale)]
                 (recur (inc level) (* scale 3.0) (max distance hole))))))))))

(defn mandelbulb
  "Exterior distance estimate for a spherical-power Mandelbulb, with world +Y
  as its polar axis. Scale controls world size, not an exact bounding radius.
  Non-escaping points return zero, so this is NOT an interior signed distance.
  The step factor reduces estimated steps; it is not a proof of a safe bound."
  ([center scale] (mandelbulb center scale {}))
  ([center scale settings]
   (let [{:keys [power iterations bailout step-factor]
          :or {power 8 iterations 14 bailout 4.0 step-factor 0.7}} settings]
     (when-not (and (vector? center) (= 3 (count center))
                    (every? #(and (number? %) (Double/isFinite (double %))) center)
                    (number? scale) (pos? scale) (Double/isFinite (double scale))
                    (integer? power) (<= 2 power 16)
                    (integer? iterations) (<= 1 iterations 32)
                    (number? bailout) (<= 2 bailout 8)
                    (number? step-factor) (< 0 step-factor) (<= step-factor 1.0))
       (throw (ex-info "Mandelbulb needs a finite center, positive scale, power 2–16, iterations 1–32, bailout 2–8, and step-factor in (0,1]."
                       {:center center :scale scale :settings settings})))
     (let [[cx cy cz] center
           exponent (double power)
           world-scale (double scale)
           safe-scale (* world-scale step-factor)]
       (fn [[px py pz]]
         (let [a (/ (- px cx) world-scale)
               b (/ (- py cy) world-scale)
               c (/ (- pz cz) world-scale)
               radius (Math/sqrt (+ (* a a) (* b b) (* c c)))]
           (if (> radius 4.0)
             ;; For power >= 2 the set lies inside radius 2. Use that bound
             ;; far away, where the logarithmic estimate can overstep badly.
             (* safe-scale (- radius 2.0))
             (* safe-scale
                (loop [i 0, x a, y b, z c, derivative 1.0]
                  (let [r (Math/sqrt (+ (* x x) (* y y) (* z z)))]
                    (cond
                      (> r bailout) (/ (* 0.5 (Math/log r) r) derivative)
                      (>= i iterations) 0.0
                      (< r 1.0e-12) (if (zero? radius) 0.0
                                       (recur (inc i) a b c 1.0))
                      :else
                      (let [theta (* exponent (Math/acos (max -1.0 (min 1.0 (/ y r)))))
                            phi (* exponent (Math/atan2 z x))
                            raised (Math/pow r exponent)
                            sin-theta (Math/sin theta)
                            next-derivative (+ (* exponent (Math/pow r (dec exponent)) derivative) 1.0)]
                        (recur (inc i)
                               (+ (* raised sin-theta (Math/cos phi)) a)
                               (+ (* raised (Math/cos theta)) b)
                               (+ (* raised sin-theta (Math/sin phi)) c)
                               next-derivative)))))))))))))
