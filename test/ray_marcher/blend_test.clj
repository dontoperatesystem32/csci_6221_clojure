(ns ray-marcher.blend-test
  (:require [clojure.test :refer [deftest is]]
            [ray-marcher.blend :as blend]
            [ray-marcher.core :as core]
            [ray-marcher.lighting :as lighting]
            [ray-marcher.march :as march]
            [ray-marcher.render :as render]
            [ray-marcher.scene :as scene]
            [ray-marcher.scene-file :as scene-file]))

(defn close? [a b] (< (Math/abs (double (- a b))) 1.0e-9))

(deftest known-minima-and-numerical-stability
  (doseq [mode [:hard :polynomial :exponential]]
    (let [combine (blend/combiner {:mode mode :width 2})]
      (is (= 3 (combine Double/POSITIVE_INFINITY 3)))
      (is (= 3 (combine 3 Double/POSITIVE_INFINITY)))
      (is (= Double/POSITIVE_INFINITY (combine Double/POSITIVE_INFINITY Double/POSITIVE_INFINITY)))
      (is (= Double/NEGATIVE_INFINITY (combine Double/NEGATIVE_INFINITY 3)))
      (is (close? (case mode :hard 3 :polynomial 2.5 :exponential (- 3 (* 2 (Math/log 2))))
                  (combine 3 3)))
      (is (close? -10000 (combine -10000 10000)))
      (doseq [a [-10 -1 0 0.2 3 10000] b [-10 -1 0 0.2 3 10000]]
        (is (close? (combine a b) (combine b a)))
        (is (<= (combine a b) (min a b))))))
  (let [poly (blend/combiner {:mode :polynomial :width 2})]
    (is (= 1.0 (poly 1 3)))
    (is (= 1.0 (poly 1 4))))
  (doseq [mode [:polynomial :exponential]]
    (is (= -3 ((blend/combiner {:mode mode :width 0}) 2 -3))))
  (let [exp-min (blend/combiner {:mode :exponential :width 0.7})
        values [1.0 1.1 -0.4 2.0]
        expected (* -0.7 (Math/log (reduce + (map #(Math/exp (/ (- %) 0.7)) values))))]
    (is (close? expected (reduce exp-min values)))
    (is (close? expected (reduce exp-min (reverse values)))))
  (let [poly (blend/combiner {:mode :polynomial :width 1})]
    (is (not (close? (poly (poly 0 0) 0.2) (poly 0 (poly 0 0.2)))))))

(deftest configurable-groups-and-cli
  (doseq [args [["--union" "unknown"] ["--union"] ["--blend"]
                ["--blend" "NaN"] ["--blend" "Infinity"] ["--blend" "-1"]]]
    (is (thrown? clojure.lang.ExceptionInfo (core/parse-options args))))
  (is (= {:mode :exponential :width 0.5}
         (:union (core/parse-options ["--blend" "0.5" "--union" "exponential"]))))
  (doseq [settings [{:mode :wrong} {:width -1} {:width Double/NaN}
                    {:width Double/POSITIVE_INFINITY} {:typo 1} nil]]
    (is (thrown? clojure.lang.ExceptionInfo (blend/combiner settings))))
  (doseq [definition [{:objects [] :scene-distance scene/default-scene}
                      {:objects (list scene/default-scene)} {:objects [1]}
                      {:objects [] :union nil}
                      {:scene-distance scene/default-scene :union {:mode :hard}}]]
    (is (thrown? clojure.lang.ExceptionInfo (scene-file/prepare definition))))
  (is (thrown? clojure.lang.ExceptionInfo
               (scene-file/prepare {:scene-distance scene/default-scene} {:mode :polynomial})))
  (doseq [mode [:hard :polynomial :exponential]]
    (let [empty-distance (apply scene/blended-union {:mode mode} [])
          single (scene/blended-union {:mode mode} (constantly 3))]
      (is (= Double/POSITIVE_INFINITY (empty-distance [0 0 0])))
      (is (= 3 (single [0 0 0])))))
  (let [definition {:objects [(constantly 2) (constantly 2)]
                    :union {:mode :exponential :width 2}}
        distance #(get-in (scene-file/prepare definition %) [:render-options :scene-distance])]
    (is (close? (- 2 (* 2 (Math/log 2))) ((distance {}) [0 0 0])))
    (is (close? 1.5 ((distance {:mode :polynomial}) [0 0 0])))
    (is (= 2 ((distance {:mode :hard}) [0 0 0])))))

(deftest blended-geometry-rays-normals-and-images
  ;; At z=0 the separated spheres leave a gap of 0.1. Both blends can bridge it.
  (let [objects [(scene/sphere [10 0 -2.1] 2) (scene/sphere [10 0 2.1] 2)]
        distance #(apply scene/blended-union {:mode % :width 1} objects)]
    (is (= :miss (:status (march/raymarch (distance :hard) [0 0 0] [1 0 0]))))
    (doseq [mode [:polynomial :exponential]]
      (let [field (distance mode)
            hit (march/raymarch field [0 0 0] [1 0 0] {:max-steps 256})]
        (is (neg? (field [10 0 0])))
        (is (= :hit (:status hit)))
        (is (every? true? (map close? [-1 0 0] (lighting/surface-normal field (:position hit) 0.001)))))))
  ;; A one-pixel export through a configurable scene must honor both union
  ;; settings and color-fn (regression for dropped single-thread render options).
  (let [definition {:objects [(scene/sphere [10 0 -2.1] 2) (scene/sphere [10 0 2.1] 2)]}
        pixel (fn [mode]
                (let [opts (:render-options (scene-file/prepare definition {:mode mode}))
                      picture (render/render-image 1 1 (assoc opts :color-fn render/silhouette-color))]
                  (bit-and 0xFFFFFF (.getRGB picture 0 0))))]
    (is (= 0x101A2B (pixel :hard)))
    (is (= 0xF5AC48 (pixel :polynomial)))
    (is (= 0xF5AC48 (pixel :exponential)))))
