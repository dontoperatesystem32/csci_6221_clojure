(ns ray-marcher.shadows
  "Hard directional-light visibility, independent of materials, cameras, and UI."
  (:require [ray-marcher.march :as march]
            [ray-marcher.vector :as v]))

(def default-settings
  {:mode :off :bias 0.01 :epsilon 0.001 :max-distance 100.0 :max-steps 128})

(defn settings [overrides]
  (when-not (and (map? overrides)
                 (every? (set (keys default-settings)) (keys overrides)))
    (throw (ex-info "Shadows accept :mode, :bias, :epsilon, :max-distance, and :max-steps." {})))
  (let [{:keys [mode bias epsilon max-distance max-steps] :as result}
        (merge default-settings overrides)
        finite-positive? #(and (number? %) (Double/isFinite (double %)) (pos? %))]
    (when-not (and (#{:off :hard} mode)
                   (finite-positive? epsilon) (finite-positive? bias) (> bias epsilon)
                   (finite-positive? max-distance) (integer? max-steps) (pos? max-steps))
      (throw (ex-info "Invalid shadows: choose off/hard, positive finite distances, bias greater than shadow epsilon, and a positive integer step limit." {})))
    result))

(defn make-visibility
  "Compile a visibility query: :visible, :blocked, or :unresolved.
  Inputs are hit position, unit outward normal, and unit direction toward light.
  Start outside the hit surface to avoid immediately hitting that same surface.
  A travel-limit miss means clear within the configured range, not to infinity."
  [scene-distance overrides]
  (let [{:keys [mode bias] :as options} (settings overrides)
        limits (select-keys options [:epsilon :max-distance :max-steps])]
    (if (= mode :off)
      (constantly :visible)
      (fn [point normal toward-light]
        (let [origin (v/add point (v/scale normal bias))]
          (case (:status (march/raymarch scene-distance origin toward-light limits))
            :hit :blocked
            :miss :visible
            :step-limit :unresolved))))))
