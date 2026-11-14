(ns ray-marcher.shadows-test
  (:require [clojure.test :refer [deftest is]]
            [ray-marcher.configuration :as configuration]
            [ray-marcher.core :as core]
            [ray-marcher.lighting :as lighting]
            [ray-marcher.render :as render]
            [ray-marcher.scene :as scene]
            [ray-marcher.scene-file :as scene-file]
            [ray-marcher.shadows :as shadows]))

(def floor-distance (scene/floor-plane 0))
(def blocked-scene (scene/union floor-distance (scene/sphere [0 2 0] 0.5)))

(deftest floor-distances-and-shadow-ray-results
  (let [floor (scene/floor-plane -2)]
    (is (= 0 (floor [7 -2 8])))
    (is (= 3 (floor [7 1 8])))
    (is (= -1 (floor [7 -3 8]))))
  (doseq [value [nil Double/NaN Double/POSITIVE_INFINITY]]
    (is (thrown? clojure.lang.ExceptionInfo (scene/floor-plane value))))
  (let [visibility (shadows/make-visibility blocked-scene {:mode :hard})]
    (is (= :blocked (visibility [0 0 0] [0 1 0] [0 1 0])))
    (is (= :visible (visibility [2 0 0] [0 1 0] [0 1 0])))
    (is (= :visible ((shadows/make-visibility floor-distance {:mode :hard})
                     [0 0 0] [0 1 0] [0 1 0])))
    ;; A finite shadow range deliberately ignores a blocker farther away.
    (is (= :visible ((shadows/make-visibility blocked-scene {:mode :hard :max-distance 1})
                     [0 0 0] [0 1 0] [0 1 0])))
    (is (= :unresolved ((shadows/make-visibility floor-distance {:mode :hard :max-steps 1})
                        [0 0 0] [0 1 0] [0 1 0])))))

(deftest shadow-rays-agree-with-analytic-sphere-intersections
  ;; Independent oracle: rays from the biased floor origins toward a vertical
  ;; light intersect the sphere exactly when their horizontal radius is < 0.5.
  (let [visibility (shadows/make-visibility blocked-scene {:mode :hard :epsilon 1.0e-6 :max-steps 512})]
    (doseq [x [-1 -0.6 -0.3 0 0.3 0.6 1] z [-1 -0.6 -0.3 0 0.3 0.6 1]]
      (is (= (if (< (+ (* x x) (* z z)) 0.25) :blocked :visible)
             (visibility [x 0 z] [0 1 0] [0 1 0]))))))

(deftest ambient-and-ao-are-separate-from-shadowed-direct-light
  (let [settings {:base-color 0xFFFFFF :toward-light [0 1 0] :ambient 0.25 :diffuse-strength 0.75}
        hit {:status :hit :position [0 0 0]}
        off (lighting/make-shader blocked-scene settings)
        hard (lighting/make-shader blocked-scene (assoc settings :shadows {:mode :hard}))
        ambient-only (lighting/make-shader blocked-scene (assoc settings :diffuse-strength 0))]
    (is (= 0xFFFFFF (off hit)))
    (is (= 0x898989 (hard hit)))
    (is (= (ambient-only hit) (hard hit)))
    (is (= (off (assoc hit :position [2 0 0])) (hard (assoc hit :position [2 0 0]))))
    (let [ao {:mode :normal :radius 2}
          both (lighting/make-shader blocked-scene (assoc settings :ao ao :shadows {:mode :hard}))
          ao-ambient (lighting/make-shader blocked-scene (assoc settings :ao ao :diffuse-strength 0))]
      (is (= (ao-ambient hit) (both hit))))
    (doseq [model [:lambert :half-lambert :valve-half-lambert]]
      (is (= 0x898989 ((lighting/make-shader blocked-scene
                         (assoc settings :diffuse-model model :shadows {:mode :hard})) hit)))))
  (is (= 0x00FFFF ((lighting/make-shader floor-distance
                     {:toward-light [0 1 0] :shadows {:mode :hard :max-steps 1}})
                   {:status :hit :position [0 0 0]}))))

(deftest skip-secondary-rays-when-they-cannot-change-shading
  (let [calls (atom 0)
        distance (fn [p] (swap! calls inc) (floor-distance p))
        hit {:status :hit :position [0 0 0]}]
    (is (= :visible ((shadows/make-visibility distance {:mode :off}) [0 0 0] [0 1 0] [0 1 0])))
    (is (zero? @calls))
    (doseq [settings [{:toward-light [0 -1 0]}
                      {:diffuse-strength 0}
                      {:ao {:mode :only :strength 0}}]]
      (reset! calls 0)
      ((lighting/make-shader distance (assoc settings :shadows {:mode :hard})) hit)
      (is (= 6 @calls)))
    (reset! calls 0)
    ((lighting/make-shader distance {:shadows {:mode :hard}}) {:status :miss})
    (is (zero? @calls))))

(deftest configuration-and-cli-validation
  (doseq [settings [nil {:mode :soft} {:typo 1} {:bias 0} {:bias 0.001}
                    {:bias Double/NaN} {:epsilon 0} {:max-distance Double/POSITIVE_INFINITY}
                    {:max-steps 0} {:max-steps 1.5}]]
    (is (thrown? clojure.lang.ExceptionInfo (shadows/make-visibility floor-distance settings)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (scene-file/prepare {:scene-distance floor-distance :lighting {:shadows settings}}))))
  (doseq [mode ["off" "hard"]]
    (is (= (keyword mode) (:shadows (core/parse-options ["--shadows" mode])))))
  (doseq [args [["--shadows"] ["--shadows" "soft"]]]
    (is (thrown? clojure.lang.ExceptionInfo (core/parse-options args))))
  (let [definition (scene-file/read-definition "scenes/shadows.clj")
        values (configuration/form-values definition)
        edited (assoc-in values [:lighting :shadows :mode] :off)
        cli (configuration/prepare-cli (core/parse-options ["--scene" "scenes/shadows.clj" "--shadows" "off"]))]
    (is (= :hard (get-in values [:lighting :shadows :mode])))
    (is (= :off (get-in (configuration/prepare-form definition edited) [:render-options :lighting :shadows :mode])))
    (is (= :off (get-in cli [:render-options :lighting :shadows :mode])))
    (is (= 256 (get-in cli [:render-options :lighting :shadows :max-steps])))))

(deftest integrated-render-has-a-shadowed-pixel
  ;; Camera below a raised occluder looks at the floor beneath it.
  (let [definition {:scene-distance blocked-scene
                    :camera {:position [0 0.5 -2] :look-at [0 0 0]}
                    :lighting {:base-color 0xFFFFFF :toward-light [0 1 0]
                               :ambient 0.25 :diffuse-strength 0.75 :shadows {:mode :hard}}}
        options (:render-options (scene-file/prepare definition))
        picture (render/render-image 1 1 options)]
    (is (= 0x898989 (bit-and 0xFFFFFF (.getRGB picture 0 0))))))
