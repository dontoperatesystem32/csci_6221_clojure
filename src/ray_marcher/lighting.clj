(ns ray-marcher.lighting
  "Distance-field normals and simple matte directional lighting, independent of UI."
  (:require [ray-marcher.vector :as v]))

(def default-settings
  {:toward-light [-0.6 0.8 -0.5]
   :ambient 0.08
   :diffuse-strength 0.92
   :normal-step 0.001
   :base-color 0xF5AC48
   :diffuse-model :lambert})

(defn surface-normal
  "Estimate the outward gradient with six central-difference distance samples.
  A zero gradient has no defined normal; return zero so it receives ambient only."
  [distance [x y z] step]
  (let [gradient [(- (distance [(+ x step) y z]) (distance [(- x step) y z]))
                  (- (distance [x (+ y step) z]) (distance [x (- y step) z]))
                  (- (distance [x y (+ z step)]) (distance [x y (- z step)]))]
        magnitude (v/length gradient)]
    ;; Division by 2*step is unnecessary because we normalize the gradient.
    (if (> magnitude 1.0e-12)
      (v/scale gradient (/ 1.0 magnitude))
      [0.0 0.0 0.0])))

(defn- clamp01 [x] (max 0.0 (min 1.0 x)))

(defn diffuse-factor
  "Both arguments must be unit vectors. Light vector points FROM the surface
  TOWARD the light, opposite to the direction in which light rays travel.
  :half-lambert uses the unsquared remap; :valve-half-lambert squares it."
  [normal toward-light model]
  (let [cosine (v/dot normal toward-light)]
    (case model
      :lambert (clamp01 cosine)
      :half-lambert (clamp01 (+ 0.5 (* 0.5 cosine)))
      :valve-half-lambert (let [wrapped (clamp01 (+ 0.5 (* 0.5 cosine)))]
                           (* wrapped wrapped))
      (throw (ex-info "Unknown diffuse lighting model." {:model model})))))

(defn srgb-to-linear [channel]
  (let [c (/ channel 255.0)]
    (if (<= c 0.04045) (/ c 12.92) (Math/pow (/ (+ c 0.055) 1.055) 2.4))))

(defn linear-to-srgb [channel]
  (let [c (clamp01 channel)]
    (int (Math/round (* 255.0 (if (<= c 0.0031308)
                              (* 12.92 c)
                              (- (* 1.055 (Math/pow c (/ 1.0 2.4))) 0.055)))))))

(defn make-shader
  "Create a hit/miss color function. Normalize light and decode base color once
  per image, then compute lighting in linear RGB and encode to sRGB for display.
  The ambient term is an artistic fill, not simulated indirect illumination."
  ([scene-distance] (make-shader scene-distance {}))
  ([scene-distance settings]
   (let [{:keys [toward-light ambient diffuse-strength normal-step base-color diffuse-model]}
         (merge default-settings settings)
         light (v/normalize toward-light)
         base (mapv #(srgb-to-linear (bit-and 255 (bit-shift-right base-color %))) [16 8 0])]
     (when-not (and (pos? normal-step) (<= 0 ambient 1) (not (neg? diffuse-strength))
                    (#{:lambert :half-lambert :valve-half-lambert} diffuse-model))
       (throw (ex-info "Invalid lighting settings." {:settings settings})))
     (fn [{:keys [status position]}]
       (case status
         :miss 0x101A2B
         :step-limit 0xFF00FF
         :hit
         (let [normal (surface-normal scene-distance position normal-step)
               ;; At undefined normals (e.g. the sphere center), neither model
               ;; should invent a direction-dependent contribution.
               diffuse (if (zero? (v/length normal)) 0.0
                           (diffuse-factor normal light diffuse-model))
               brightness (+ ambient (* diffuse-strength diffuse))
               [r g b] (mapv #(linear-to-srgb (* % brightness)) base)]
           (bit-or (bit-shift-left r 16) (bit-shift-left g 8) b)))))))
