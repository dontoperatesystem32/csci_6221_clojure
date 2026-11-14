(ns ray-marcher.lighting
  "Distance-field normals and simple matte directional lighting, independent of UI."
  (:require [ray-marcher.vector :as v]
            [ray-marcher.ao :as ao]))

(def default-settings
  {:toward-light [-0.6 0.8 -0.5]
   :ambient 0.08
   :diffuse-strength 0.92
   :normal-step 0.001
   :base-color 0xF5AC48
   :diffuse-model :lambert
   :ao ao/default-settings})

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

(defn- decode-srgb24 [rgb24]
  (mapv #(srgb-to-linear (bit-and 255 (bit-shift-right rgb24 %))) [16 8 0]))

(defn make-shader
  "Create a hit/miss color function. Uses :color from the raymarch hit result
  when present, falling back to :base-color from settings."
  ([scene-fn] (make-shader scene-fn {}))
  ([scene-fn settings]
   (let [;; Adapter so surface-normal and accessibility always receive a plain number
         distance-fn (fn [p]
                       (let [res (scene-fn p)]
                         (if (map? res) (:distance res) res)))
         ao-settings (ao/settings (get settings :ao {}))
         accessibility (ao/make-accessibility distance-fn ao-settings)
         {:keys [toward-light ambient diffuse-strength normal-step base-color diffuse-model]}
         (merge default-settings settings)
         light (v/normalize toward-light)
         fallback-base (decode-srgb24 base-color)]
     (when-not (and (pos? normal-step) (<= 0 ambient 1) (not (neg? diffuse-strength))
                    (#{:lambert :half-lambert :valve-half-lambert} diffuse-model))
       (throw (ex-info "Invalid lighting settings." {:settings settings})))
     (fn [{:keys [status position color]}]
       (case status
         :miss 0x101A2B
         :step-limit 0xFF00FF
         :hit
         (let [normal (surface-normal distance-fn position normal-step)
               diffuse (if (zero? (v/length normal)) 0.0
                           (diffuse-factor normal light diffuse-model))
               access (accessibility position normal)
               brightness (+ (* ambient access) (* diffuse-strength diffuse))
               ;; Use the color returned by the shape on hit, or fallback to base-color
               base (if color
                      (decode-srgb24 color)
                      fallback-base)
               [r g b] (if (= :only (:mode ao-settings))
                         (vec (repeat 3 (linear-to-srgb access)))
                         (mapv #(linear-to-srgb (* % brightness)) base))]
           (bit-or (bit-shift-left r 16) (bit-shift-left g 8) b)))))))
