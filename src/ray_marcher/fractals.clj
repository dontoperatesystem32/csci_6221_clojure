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

(defn fractal-world
  "Continuous, connected 3D fractal world using an AmazingBox + KIFS fold hybrid
  in Mandelbrot mode. Produces unbroken caverns, arches, and organic structures
  without disconnected floating spheres. Supports (f px py pz) and (f [px py pz])."
  ([scale] (fractal-world scale {}))
  ([scale settings]
   (let [{:keys [iterations bailout step-factor
                 fold-limit box-scale min-radius fold-period rot-angles]
          :or {iterations 18
               bailout 32.0
               step-factor 0.55
               fold-limit 1.0
               box-scale -1.85
               min-radius 0.45
               fold-period 2
               rot-angles [0.22 -0.18 0.12]}} settings]
     (let [world-scale (double scale)
           safe-scale (* world-scale step-factor)
           fl (double fold-limit)
           bs (double box-scale)
           mr2 (* (double min-radius) (double min-radius))
           fr2 1.0
           [rx ry rz] (mapv double rot-angles)
           cx (Math/cos rx) sx (Math/sin rx)
           cy (Math/cos ry) sy (Math/sin ry)
           cz (Math/cos rz) sz (Math/sin rz)
           eval-de
           (fn [^double px ^double py ^double pz]
             (let [a (/ px world-scale)
                   b (/ py world-scale)
                   c (/ pz world-scale)]
               (* safe-scale
                  (loop [i 0, x a, y b, z c, derivative 1.0]
                    (let [r2 (+ (* x x) (* y y) (* z z))
                          r (Math/sqrt r2)]
                      (cond
                        (> r bailout) (/ r (Math/abs derivative))
                        (>= i iterations) 0.0
                        :else
                        ;; 1. Apply 3D rotation to curve straight box corridors into arches
                        (let [y1 (- (* y cx) (* z sx))
                              z1 (+ (* y sx) (* z cx))
                              x2 (+ (* x cy) (* z1 sy))
                              z2 (- (* z1 cy) (* x sy))
                              rx-pos (- (* x2 cz) (* y1 sz))
                              ry-pos (+ (* x2 sz) (* y1 cz))
                              rz-pos z2]
                          (if (zero? (mod i fold-period))
                            ;; 2A. AMAZINGBOX FOLD (Continuous structural grid & vaults)
                            (let [fx (- (max (- fl) (min fl rx-pos)) (* 2.0 (- rx-pos (max (- fl) (min fl rx-pos)))))
                                  fy (- (max (- fl) (min fl ry-pos)) (* 2.0 (- ry-pos (max (- fl) (min fl ry-pos)))))
                                  fz (- (max (- fl) (min fl rz-pos)) (* 2.0 (- rz-pos (max (- fl) (min fl rz-pos)))))
                                  fr-sq (+ (* fx fx) (* fy fy) (* fz fz))
                                  sphere-mult (cond
                                                (< fr-sq mr2) (/ fr2 mr2)
                                                (< fr-sq fr2) (/ fr2 fr-sq)
                                                :else 1.0)
                                  scale-mult (* bs sphere-mult)]
                              (recur (inc i)
                                     (+ (* fx scale-mult) a)
                                     (+ (* fy scale-mult) b)
                                     (+ (* fz scale-mult) c)
                                     (+ (* derivative (Math/abs scale-mult)) 1.0)))

                            ;; 2B. KIFS OCTAHEDRAL / ORGANIC FOLD (Bends flat walls into continuous ribs)
                            (let [ax (Math/abs rx-pos)
                                  ay (Math/abs ry-pos)
                                  az (Math/abs rz-pos)
                                  ;; Sort coordinates so x >= y >= z (creates smooth curved manifolds)
                                  [sx1 sy1] (if (< ax ay) [ay ax] [ax ay])
                                  [sx2 sz1] (if (< sx1 az) [az sx1] [sx1 az])
                                  [sy2 sz2] (if (< sy1 sz1) [sz1 sy1] [sy1 sz1])
                                  k-scale 1.65
                                  nx (- (* sx2 k-scale) 1.0)
                                  ny (- (* sy2 k-scale) 0.5)
                                  nz (* sz2 k-scale)]
                              (recur (inc i)
                                     (+ nx a)
                                     (+ ny b)
                                     (+ nz c)
                                     (+ (* derivative k-scale) 1.0)))))))))))]
       (fn
         ([[px py pz]] (eval-de px py pz))
         ([px py pz] (eval-de px py pz)))))))
