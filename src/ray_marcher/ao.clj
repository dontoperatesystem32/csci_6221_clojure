(ns ray-marcher.ao
  "Deterministic normal-sampled ambient obscurance, independent of camera and UI."
  (:require [ray-marcher.vector :as v]))

(def default-settings
  {:mode :off :samples 5 :radius 1.0 :bias 0.01 :strength 1.0})

(defn settings
  [overrides]
  (when-not (and (map? overrides) (every? (set (keys default-settings)) (keys overrides)))
    (throw (ex-info "AO accepts :mode, :samples, :radius, :bias, and :strength." {})))
  (let [{:keys [mode samples radius bias strength] :as result} (merge default-settings overrides)
        finite? #(and (number? %) (Double/isFinite (double %)))]
    (when-not (and (#{:off :normal :only} mode)
                   (integer? samples) (<= 1 samples 64)
                   (finite? radius) (pos? radius)
                   (finite? bias) (<= 0 bias)
                   (finite? strength) (<= 0 strength))
      (throw (ex-info "Invalid AO settings: mode off/normal/only, samples 1–64, positive radius, nonnegative bias/strength; distances must be finite." {})))
    result))

(defn make-accessibility
  "Compile weighted probes along a unit outward normal. Returns accessibility 0–1.
  The normalized distance deficit is a heuristic, not hemisphere visibility.
  Zero normals and disabled/zero-strength AO require no scene evaluations."
  [distance overrides]
  (let [{:keys [mode samples radius bias strength]} (settings overrides)
        probes (mapv (fn [i] [(+ bias (* radius (/ (inc i) (double samples))))
                              (Math/pow 0.5 i)]) (range samples))
        weight-sum (reduce + (map second probes))]
    (if (or (= mode :off) (zero? strength))
      (constantly 1.0)
      (fn [point normal]
        (if (zero? (v/length normal))
          1.0
          (let [deficit (reduce (fn [total [h weight]]
                                  (let [d (distance (v/add point (v/scale normal h)))]
                                    (+ total (* weight (max 0.0 (min 1.0 (/ (- h d) h)))))))
                                0.0 probes)]
            (max 0.0 (min 1.0 (- 1.0 (* strength (/ deficit weight-sum)))))))))))
