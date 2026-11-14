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

(defn- lerp-rgb24
  "Linearly blends two 24-bit sRGB colors (0xRRGGBB) by weight w in [0.0, 1.0].
  w = 0.0 returns c1; w = 1.0 returns c2."
  [c1 c2 ^double w]
  (cond
    (nil? c1) c2
    (nil? c2) c1
    (<= w 0.0) c1
    (>= w 1.0) c2
    :else
    (let [inv (- 1.0 w)
          r (int (+ (* inv (bit-and 255 (bit-shift-right c1 16)))
                    (* w   (bit-and 255 (bit-shift-right c2 16)))))
          g (int (+ (* inv (bit-and 255 (bit-shift-right c1 8)))
                    (* w   (bit-and 255 (bit-shift-right c2 8)))))
          b (int (+ (* inv (bit-and 255 c1))
                    (* w   (bit-and 255 c2))))]
      (bit-or (bit-shift-left r 16) (bit-shift-left g 8) b))))

(defn combiner
  "Construct a binary minimum operation over numbers or {:distance d :color c} maps.
  Positive infinity is the empty identity. Exponential uses shifted log-sum-exp
  and logistic sigmoid color weights; polynomial uses quadratic smooth-min and
  linear seam weights."
  [settings]
  (let [{:keys [mode width]} (options settings)
        k (double width)
        hard? (or (= mode :hard) (zero? k))]
    (fn [res-a res-b]
      (let [map-input? (or (map? res-a) (map? res-b))
            a (double (if (map? res-a) (:distance res-a) res-a))
            b (double (if (map? res-b) (:distance res-b) res-b))
            c1 (when (map? res-a) (:color res-a))
            c2 (when (map? res-b) (:color res-b))
            [dist weight]
            (cond
              (= a Double/POSITIVE_INFINITY) [b 1.0]
              (= b Double/POSITIVE_INFINITY) [a 0.0]
              (= a Double/NEGATIVE_INFINITY) [a 0.0]
              (= b Double/NEGATIVE_INFINITY) [b 1.0]
              hard?                          [(min a b) (if (< b a) 1.0 0.0)]
              :else
              (let [m (min a b)
                    delta (Math/abs (- a b))]
                (case mode
                  :polynomial
                  (let [h (max (- 1.0 (/ delta k)) 0.0)
                        w (max 0.0 (min 1.0 (+ 0.5 (* 0.5 (/ (- a b) k)))))]
                    [(- m (* 0.25 k h h)) w])

                  :exponential
                  (let [diff (max -40.0 (min 40.0 (/ (- a b) k)))
                        w (/ 1.0 (+ 1.0 (Math/exp (- diff))))]
                    [(- m (* k (Math/log1p (Math/exp (- (/ delta k)))))) w]))))]
        (if map-input?
          {:distance dist :color (lerp-rgb24 c1 c2 weight)}
          dist)))))

