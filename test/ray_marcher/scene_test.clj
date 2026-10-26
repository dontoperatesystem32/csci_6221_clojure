(ns ray-marcher.scene-test
  (:require [clojure.test :refer [deftest is]]
            [ray-marcher.controls :as controls]
            [ray-marcher.core :as core]
            [ray-marcher.march :as march]
            [ray-marcher.render :as render]
            [ray-marcher.scene :as scene]
            [ray-marcher.scene-file :as scene-file]
            [ray-marcher.vector :as v])
  (:import [java.nio.file Files] [java.nio.file.attribute FileAttribute]))

(defn near? [a b] (< (Math/abs (double (- a b))) 1.0e-8))

(deftest box-distances
  (let [distance (scene/cube [12 0 2] 3)]
    (is (near? -1.5 (distance [12 0 2])))
    (is (near? 0 (distance [10.5 0 2])))
    (is (near? 0 (distance [13.5 1.5 3.5])))
    (is (near? 1 (distance [14.5 0 2])))
    (is (near? (Math/sqrt 3) (distance [14.5 2.5 4.5]))))
  (is (near? -1 ((scene/box [0 0 0] [1 2 3]) [0 0 0])))
  (is (thrown? clojure.lang.ExceptionInfo (scene/cube [0 0 0] 0))))

(deftest requested-default-layout
  (is (near? -2 (scene/default-scene [10 0 -2])))
  (is (near? -1.5 (scene/default-scene [12 0 2])))
  (is (pos? (scene/default-scene [10 0 0.25])))
  (doseq [target [[10 0 -2] [12 0 2]]]
    (let [result (march/raymarch scene/default-scene [0 0 0] (v/normalize target)
                               {:epsilon 1.0e-8 :max-steps 256})
          expected (if (= target [10 0 -2])
                     (- (v/length target) 2)
                     (/ 10.5 (first (v/normalize target))))]
      (is (= :hit (:status result)))
      (is (< (Math/abs (- expected (:distance result))) 1.0e-6)))))

(deftest load-custom-scene-and-preserve-overrides
  (let [file (.toFile (Files/createTempFile "ray-marcher-scene-" ".clj" (make-array FileAttribute 0)))]
    (try
      (spit file "{:scene-distance (ray-marcher.scene/cube [8 0 0] 2)
                   :camera {:position [1 0 0] :look-at [8 0 0] :fov 45}
                   :lighting {:toward-light [-1 0 0] :base-color 0xFFFFFF
                              :ambient 0 :diffuse-strength 1 :diffuse-model :half-lambert}
                   :settings {:max-steps 200}}")
      (let [{:keys [camera-state render-options]} (scene-file/load-scene (.getPath file))
            picture (render/render-image 1 1 render-options)
            cli-lighting (core/configure-lighting render-options :valve-half-lambert)]
        (is (= [1 0 0] (:position camera-state)))
        (is (near? 45 (:fov camera-state)))
        (is (= 200 (get-in render-options [:settings :max-steps])))
        (is (= 0.001 (get-in render-options [:settings :epsilon])))
        (is (= :half-lambert (get-in render-options [:lighting :diffuse-model])))
        (is (= render-options (core/configure-lighting render-options nil)))
        (is (= :valve-half-lambert (get-in cli-lighting [:lighting :diffuse-model])))
        (is (= 0 (get-in cli-lighting [:lighting :ambient])))
        (is (= [-1 0 0] (get-in cli-lighting [:lighting :toward-light])))
        (is (= 0xFFFFFF (bit-and 0xFFFFFF (.getRGB picture 0 0))))
        (is (= 0xF5AC48 ((:color-fn (core/configure-lighting render-options :flat)) {:status :hit}))))
      (finally (.delete file)))))

(deftest custom-camera-pose-roundtrip
  (let [{:keys [camera-state render-options]}
        (scene-file/prepare {:scene-distance (scene/sphere [10 0 0] 2)
                             :camera {:position [1 2 3] :look-at [11 3 -2] :fov 50}})
        rebuilt (controls/as-camera camera-state)]
    (is (= (:origin (:camera render-options)) (:origin rebuilt)))
    (is (every? true? (map near? (:forward (:camera render-options)) (:forward rebuilt))))
    (is (near? (:half-height (:camera render-options)) (:half-height rebuilt)))))

(deftest scene-validation-and-example
  (doseq [definition [nil {} {:scene-distance 1}
                      {:scene-distance scene/default-scene :typo true}
                      {:scene-distance scene/default-scene :camera {:position [0 0]}}
                      {:scene-distance scene/default-scene :camera {:position [0 0 0] :look-at [0 0 0]}}
                      {:scene-distance scene/default-scene :lighting {:ambent 0.1}}
                      {:scene-distance scene/default-scene :settings {:max-steps 0}}]]
    (is (thrown? clojure.lang.ExceptionInfo (scene-file/prepare definition))))
  (let [options (:render-options (scene-file/load-scene "scenes/example.clj"))]
    (is (near? -2 ((:scene-distance options) [10 0 -2])))
    (is (= 0x70BFE8 (get-in options [:lighting :base-color]))))
  (is (= "scenes/example.clj" (:scene (core/parse-options ["--scene" "scenes/example.clj"]))))
  (is (thrown? clojure.lang.ExceptionInfo (core/parse-options ["--scene"]))))
