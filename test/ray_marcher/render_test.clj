(ns ray-marcher.render-test
  (:require [clojure.test :refer [deftest is testing]]
            [ray-marcher.camera :as camera]
            [ray-marcher.march :as march]
            [ray-marcher.render :as render]
            [ray-marcher.scene :as scene]
            [ray-marcher.vector :as v]))

(defn close? [a b] (< (Math/abs (double (- a b))) 1.0e-9))

(deftest sphere-distances
  (let [distance (scene/sphere [10 0 0] 2)]
    (is (close? 8 (distance [0 0 0])))
    (is (close? 0 (distance [8 0 0])))
    (is (close? -2 (distance [10 0 0])))
    (is (close? 0 ((scene/union distance (scene/sphere [0 5 0] 1)) [0 4 0])))))

(deftest single-ray-outcomes
  (let [hit (march/raymarch scene/default-scene [0 0 0] [1 0 0])]
    (is (= :hit (:status hit)))
    (is (close? 8 (:distance hit)))
    (is (= [8.0 0.0 0.0] (:position hit))))
  (is (= :miss (:status (march/raymarch scene/default-scene [0 0 0] [-1 0 0]))))
  (is (= :miss (:status (march/raymarch scene/default-scene [0 0 0] [0 1 0]))))
  (is (= :miss (:status (march/raymarch (scene/union) [0 0 0] [1 0 0]))))
  (is (= :miss (:status (march/raymarch scene/default-scene [0 0 0] [1 0 0]
                                      {:max-distance 7}))))
  (is (= :hit (:status (march/raymarch scene/default-scene [0 0 0] [1 0 0]
                                     {:max-distance 8}))))
  (is (= :step-limit (:status (march/raymarch scene/default-scene [0 0 0] [1 0 0]
                                            {:max-steps 1}))))
  (testing "Starting inside counts as an immediate hit, not an exit search"
    (let [hit (march/raymarch scene/default-scene [10 0 0] [1 0 0])]
      (is (= :hit (:status hit)))
      (is (zero? (:distance hit))))))

(deftest camera-rays
  (is (= [1.0 0.0 0.0] (camera/ray-direction camera/default-camera 0 0 1 1)))
  (is (close? 1 (v/length (camera/ray-direction camera/default-camera 10 20 800 450))))
  (is (pos? (second (camera/ray-direction camera/default-camera 0 0 3 3))))
  (is (thrown? clojure.lang.ExceptionInfo (v/normalize [0 0 0])))
  (is (thrown? clojure.lang.ExceptionInfo (camera/camera [0 0 0] [1 0 0] [0 1 0] 180))))

(deftest image-agrees-with-analytic-sphere-intersection
  ;; Independent geometric oracle: a unit ray from the origin hits a sphere
  ;; in front when its quadratic discriminant is positive and projection > 0.
  ;; Skip a narrow silhouette band because marching uses a surface tolerance.
  (doseq [[width height] [[61 41] [41 61]]
          center [[10 0 0] [10 1 1]]]
    (let [picture (render/render-image width height {:scene-distance (scene/sphere center 2)})]
      (is (= [width height] [(.getWidth picture) (.getHeight picture)]))
      (doseq [y (range height), x (range width)]
        (let [rd (camera/ray-direction camera/default-camera x y width height)
              projection (v/dot rd center)
              discriminant (- (* projection projection) (- (v/dot center center) 4))]
          (when (> (Math/abs (double discriminant)) 0.05)
            (is (= (if (and (pos? projection) (pos? discriminant)) 0xF5AC48 0x101A2B)
                   (bit-and 0xFFFFFF (.getRGB picture x y)))
                (str "Pixel " [x y] " for " [width height] " sphere " center))))))))
