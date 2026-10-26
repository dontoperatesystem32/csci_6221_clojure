(ns ray-marcher.fractals-test
  (:require [clojure.test :refer [deftest is]]
            [ray-marcher.fractals :as fractals]
            [ray-marcher.march :as march]
            [ray-marcher.scene :as scene]
            [ray-marcher.scene-file :as scene-file]
            [ray-marcher.vector :as v]))

(defn- leaf-cubes
  "Independent geometric oracle: explicitly subdivide and keep 20/27 cubes."
  [center half depth]
  (if (zero? depth)
    [(scene/cube center (* 2 half))]
    (mapcat (fn [[x y z]]
              (leaf-cubes (v/add center (v/scale [x y z] (* 2 (/ half 3.0))))
                          (/ half 3.0) (dec depth)))
            (for [x [-1 0 1], y [-1 0 1], z [-1 0 1]
                  :when (<= (count (filter zero? [x y z])) 1)]
              [x y z]))))

(deftest sponge-agrees-with-recursive-solid
  (doseq [depth [0 1 2]]
    (let [sponge (fractals/menger-sponge [0 0 0] 2 depth)
          oracle (apply scene/union (leaf-cubes [0 0 0] 1 depth))]
      (doseq [x [-1.2 -0.81 -0.43 -0.07 0.29 0.63 1.17]
              y [-0.86 -0.14 0.52]
              z [-0.74 0.04 0.87]]
        (let [p [x y z], expected (oracle p), actual (sponge p)]
          (when (> (Math/abs expected) 1.0e-8)
            (is (= (neg? expected) (neg? actual)) (str "Solid membership at " p " depth " depth)))
          (when (pos? expected)
            (is (<= actual (+ expected 1.0e-8)) (str "Safe exterior distance at " p " depth " depth))))))))

(deftest sponge-rays-and-world-scale
  (let [sponge (fractals/menger-sponge [0 0 0] 2 3)]
    (is (pos? (sponge [0 0 0])))
    (is (= :miss (:status (march/raymarch sponge [-3 0 0] [1 0 0] {:max-distance 10 :max-steps 512}))))
    (let [hit (march/raymarch sponge [-3 0.8 0.8] [1 0 0] {:epsilon 1.0e-7 :max-steps 512})]
      (is (= :hit (:status hit)))
      (is (< (Math/abs (- 2 (:distance hit))) 1.0e-6))))
  (let [unit (fractals/menger-sponge [0 0 0] 2 2)
        larger (fractals/menger-sponge [10 2 -1] 6 2)
        p [0.2 0.6 -0.8]]
    (is (< (Math/abs (- (* 3 (unit p)) (larger (v/add [10 2 -1] (v/scale p 3))))) 1.0e-8))))

(deftest sponge-validation-and-scene
  (doseq [args [[[0 0 0] 0 3] [[0 0 0] 2 -1] [[0 0 0] 2 7]
                [[0 0 0] 2 2.5] [[0 0] 2 3]]]
    (is (thrown? clojure.lang.ExceptionInfo (apply fractals/menger-sponge args))))
  (let [{:keys [render-options camera-state]} (scene-file/load-scene "scenes/menger.clj")]
    (is (= [-1 6 -9] (:position camera-state)))
    (is (= 512 (get-in render-options [:settings :max-steps])))
    (is (pos? ((:scene-distance render-options) [10 0 0])))))
