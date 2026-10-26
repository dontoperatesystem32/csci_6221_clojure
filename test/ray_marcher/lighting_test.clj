(ns ray-marcher.lighting-test
  (:require [clojure.test :refer [deftest is]]
            [ray-marcher.core :as core]
            [ray-marcher.lighting :as lighting]
            [ray-marcher.render :as render]
            [ray-marcher.scene :as scene]
            [ray-marcher.vector :as v]))

(def single-sphere (scene/sphere [10 0 0] 2))

(defn near? [a b] (< (Math/abs (double (- a b))) 1.0e-6))

(deftest distance-field-normals-match-known-geometry
  (doseq [offset [[-2 0 0] [0 2 0] [0 0 -2] [-1.2 1.6 0] [1 1 1]]]
    (let [normal (lighting/surface-normal single-sphere (v/add [10 0 0] offset) 0.001)]
      (is (every? true? (map near? (v/normalize offset) normal)))
      (is (near? 1 (v/length normal)))))
  (is (= [0.0 1.0 0.0]
         (lighting/surface-normal (fn [[_ y _]] (- y 2)) [4 2 7] 0.001)))
  (is (= [0.0 0.0 0.0] (lighting/surface-normal single-sphere [10 0 0] 0.001))))

(deftest angular-response
  (doseq [[normal lambert half valve] [[[1 0 0] 1 1 1]
                                      [[0 1 0] 0 0.5 0.25]
                                      [[-1 0 0] 0 0 0]
                                      [[0.5 (Math/sqrt 0.75) 0] 0.5 0.75 0.5625]
                                      [[-0.5 (Math/sqrt 0.75) 0] 0 0.25 0.0625]]]
    (is (near? lambert (lighting/diffuse-factor normal [1 0 0] :lambert)))
    (is (near? half (lighting/diffuse-factor normal [1 0 0] :half-lambert)))
    (is (near? valve (lighting/diffuse-factor normal [1 0 0] :valve-half-lambert)))))

(deftest shader-light-direction-and-ambient
  (let [distance (scene/sphere [0 0 0] 1)
        settings {:base-color 0xFFFFFF :toward-light [10 0 0] :ambient 0 :diffuse-strength 1}
        shade (lighting/make-shader distance settings)]
    (is (= 0xFFFFFF (shade {:status :hit :position [1 0 0]})))
    (is (= 0 (shade {:status :hit :position [-1 0 0]})))
    (is (= 0 (shade {:status :hit :position [0 1 0]})))
    (is (= 0x101A2B (shade {:status :miss})))
    (is (= 0xFF00FF (shade {:status :step-limit})))
    (let [fill (lighting/make-shader distance (assoc settings :ambient 0.25 :diffuse-strength 0))]
      (is (= 0x898989 (fill {:status :hit :position [-1 0 0]})))
      (is (= 0x898989 (fill {:status :hit :position [0 0 0]})))))
  (is (thrown? clojure.lang.ExceptionInfo
               (lighting/make-shader single-sphere {:toward-light [0 0 0]})))
  (is (thrown? clojure.lang.ExceptionInfo
               (lighting/make-shader single-sphere {:normal-step 0}))))

(deftest color-space-conversion
  (is (= 188 (lighting/linear-to-srgb 0.5)))
  (is (= 0 (lighting/linear-to-srgb -1)))
  (is (= 255 (lighting/linear-to-srgb 2)))
  (doseq [channel (range 256)]
    (is (= channel (lighting/linear-to-srgb (lighting/srgb-to-linear channel))))))

(deftest integrated-shaded-render
  (let [options {:scene-distance single-sphere :lighting {:base-color 0xFFFFFF :ambient 0 :diffuse-strength 1 :toward-light [-1 0 0]}}
        lit (render/render-image 1 1 options)
        dark (render/render-image 1 1 (assoc-in options [:lighting :toward-light] [1 0 0]))]
    (is (= 0xFFFFFF (bit-and 0xFFFFFF (.getRGB lit 0 0))))
    (is (= 0 (bit-and 0xFFFFFF (.getRGB dark 0 0)))))
  (doseq [mode ["lambert" "half-lambert" "valve-half-lambert" "flat"]]
    (is (= (keyword mode) (:lighting (core/parse-options ["--lighting" mode])))))
  (is (thrown? clojure.lang.ExceptionInfo (core/parse-options ["--lighting" "unknown"])))
  (is (thrown? clojure.lang.ExceptionInfo (core/parse-options ["--lighting"]))))
