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
  Returns (fn [point] -> {:distance d :color c}) where :color is a single 0xRRGGBB value."
  ([center side-length depth]
   (menger-sponge center side-length depth 0xFFFFFF))
  ([center side-length depth color]
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
             z (/ (- pz cz) half-size)
             init-res (cube [x y z])
             init-dist (double (if (map? init-res) (:distance init-res) init-res))]
         (loop [level 0, scale 1.0, distance init-dist]
           (if (= level depth)
             {:distance (* half-size distance)
              :color    color}
             (let [a (folded-coordinate x scale)
                   b (folded-coordinate y scale)
                   c (folded-coordinate z scale)
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

(defn- eval-cosine-palette
  "Evaluates a 4-vector cosine palette {:bias [r g b] :amp [r g b] :freq [r g b] :phase [r g b]}
  for scalar t, returning a packed 24-bit sRGB integer (0xRRGGBB)."
  [{:keys [bias amp freq phase]} ^double t]
  (let [tau (* 2.0 Math/PI)
        [br bg bb] bias
        [ar ag ab] amp
        [fr fg fb] freq
        [pr pg pb] phase
        r (int (* 255.0 (max 0.0 (min 1.0 (+ br (* ar (Math/cos (* tau (+ (* fr t) pr)))))))))
        g (int (* 255.0 (max 0.0 (min 1.0 (+ bg (* ag (Math/cos (* tau (+ (* fg t) pg)))))))))
        b (int (* 255.0 (max 0.0 (min 1.0 (+ bb (* ab (Math/cos (* tau (+ (* fb t) pb)))))))))]
    (bit-or (bit-shift-left r 16) (bit-shift-left g 8) b)))

(def default-fractal-settings
  {:iterations    20
   :bailout       32.0
   :step-factor   0.42
   :fold-limit    1.08
   :box-scale     -1.88
   :min-radius    0.36
   :fold-period   3
   :rot-angles    [0.36 -0.28 0.16]
   :trap-scale    2.2
   :palette-params {:bias  [0.50 0.48 0.55]
                    :amp   [0.48 0.45 0.42]
                    :freq  [1.00 1.00 1.00]
                    :phase [0.00 0.28 0.58]}})

(defn fractal-world
  "Continuous 3D fractal world. Returns (fn [point] -> {:distance d :color c})
  where :color is a 24-bit sRGB integer (0xRRGGBB). All geometry and color
  parameters are configurable via the settings map."
  ([scale] (fractal-world scale {}))
  ([scale settings]
   (let [{:keys [iterations bailout step-factor
                 fold-limit box-scale min-radius fold-period rot-angles
                 trap-scale palette-params palette-fn]}
         (merge default-fractal-settings settings)
         world-scale (double scale)
         safe-scale  (* world-scale step-factor)
         fl          (double fold-limit)
         bs          (double box-scale)
         mr2         (* (double min-radius) (double min-radius))
         fr2         1.0
         t-scale     (double trap-scale)
         color-lookup (or palette-fn #(eval-cosine-palette palette-params %))
         [rx ry rz]  (mapv double rot-angles)
         cx (Math/cos rx) sx (Math/sin rx)
         cy (Math/cos ry) sy (Math/sin ry)
         cz (Math/cos rz) sz (Math/sin rz)]
     (fn [[px py pz]]
       (let [a (/ (double px) world-scale)
             b (/ (double py) world-scale)
             c (/ (double pz) world-scale)]
         (loop [i 0, x a, y b, z c, derivative 1.0, trap 1.0e10]
           (let [r2 (+ (* x x) (* y y) (* z z))
                 r (Math/sqrt r2)
                 shell-trap (Math/abs (- r 0.72))
                 plane-trap (Math/abs (- (Math/abs x) 0.42))
                 current-trap (min trap shell-trap plane-trap)]
             (cond
               (> r bailout)
               {:distance (* safe-scale (/ r (Math/abs derivative)))
                :color    (color-lookup (* t-scale current-trap))}

               (>= i iterations)
               {:distance 0.0
                :color    (color-lookup (* t-scale current-trap))}

               :else
               (let [y1 (- (* y cx) (* z sx))
                     z1 (+ (* y sx) (* z cx))
                     x2 (+ (* x cy) (* z1 sy))
                     z2 (- (* z1 cy) (* x sy))
                     rx-pos (- (* x2 cz) (* y1 sz))
                     ry-pos (+ (* x2 sz) (* y1 cz))
                     rz-pos z2]
                 (if (zero? (mod i fold-period))
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
                            (+ (* derivative (Math/abs scale-mult)) 1.0)
                            current-trap))
                   (let [ax (Math/abs rx-pos)
                         ay (Math/abs ry-pos)
                         az (Math/abs rz-pos)
                         [sx1 sy1] (if (< ax ay) [ay ax] [ax ay])
                         [sx2 sz1] (if (< sx1 az) [az sx1] [sx1 az])
                         [sy2 sz2] (if (< sy1 sz1) [sz1 sy1] [sy1 sz1])
                         k-scale 1.85
                         nx (- (* sx2 k-scale) 1.15)
                         ny (- (* sy2 k-scale) 0.65)
                         nz (let [z-shifted (- (* sz2 k-scale) 0.35)]
                              (if (< z-shifted -0.25) (+ z-shifted 0.5) z-shifted))]
                     (recur (inc i)
                            (+ nx a)
                            (+ ny b)
                            (+ nz c)
                            (+ (* derivative k-scale) 1.0)
                            current-trap))))))))))))
