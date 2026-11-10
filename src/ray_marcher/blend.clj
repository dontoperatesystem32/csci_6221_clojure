(ns ray-marcher.blend
  "Pure distance combiners. Width is in world units; zero selects exact min.")

(def default-options {:mode :hard :width 1.0})

(defn options
  "Validate and fill blending settings once, when constructing a scene."
  [settings]
  (when-not (and (map? settings)
                 (every? #{:mode :width} (keys settings)))
    (throw (ex-info "Union settings accept only :mode and :width." {})))
  (let [{:keys [mode width] :as result} (merge default-options settings)]
    (when-not (#{:hard :polynomial :exponential} mode)
      (throw (ex-info "Union mode must be :hard, :polynomial, or :exponential." {})))
    (when-not (and (number? width) (Double/isFinite (double width)) (<= 0 width))
      (throw (ex-info "Blend width must be a finite nonnegative number." {})))
    result))

(defn combiner
  "Construct a binary minimum operation. Positive infinity is the empty identity.
  Exponential uses a shifted log-sum-exp to avoid exponential overflow."
  [settings]
  (let [{:keys [mode width]} (options settings)
        k (double width)]
    (if (or (= mode :hard) (zero? k))
      min
      (fn [a b]
        (cond
          (= a Double/POSITIVE_INFINITY) b
          (= b Double/POSITIVE_INFINITY) a
          (= a Double/NEGATIVE_INFINITY) a
          (= b Double/NEGATIVE_INFINITY) b
          :else
          (let [m (min a b)
                delta (Math/abs (- (double a) (double b)))]
            (case mode
              :polynomial (let [h (max (- 1.0 (/ delta k)) 0.0)]
                            (- m (* 0.25 k h h)))
              :exponential (- m (* k (Math/log1p (Math/exp (- (/ delta k)))))))))))))
