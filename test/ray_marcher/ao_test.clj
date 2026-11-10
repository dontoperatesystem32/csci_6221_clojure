(ns ray-marcher.ao-test
  (:require [clojure.test :refer [deftest is]]
            [ray-marcher.ao :as ao]
            [ray-marcher.core :as core]
            [ray-marcher.lighting :as lighting]
            [ray-marcher.march :as march]
            [ray-marcher.render :as render]
            [ray-marcher.scene :as scene]
            [ray-marcher.scene-file :as scene-file]))

(def floor-distance (fn [[_ y _]] y))
(defn corridor [height]
  (scene/union floor-distance (fn [[_ y _]] (- height y))))
(defn near? [a b] (< (Math/abs (double (- a b))) 1.0e-8))

(deftest open-surfaces-and-controlled-blocker
  (let [opts {:mode :normal :samples 5 :radius 1 :bias 0 :strength 1}
        open (ao/make-accessibility floor-distance opts)
        sphere (ao/make-accessibility (scene/sphere [0 0 0] 1) opts)
        near (ao/make-accessibility (corridor 0.4) opts)
        far (ao/make-accessibility (corridor 3) opts)]
    (is (near? 1 (open [0 0 0] [0 1 0])))
    (is (near? 1 (sphere [0 1 0] [0 1 0])))
    (is (< (near [0 0 0] [0 1 0]) (far [0 0 0] [0 1 0])))
    (is (near? 1 (far [0 0 0] [0 1 0])))
    ;; One probe at h=1: ceiling at 1.5 gives d=0.5 and accessibility=0.5.
    (is (near? 0.5 ((ao/make-accessibility (corridor 1.5)
                                          {:mode :normal :samples 1 :radius 1 :bias 0})
                    [0 0 0] [0 1 0])))
    (is (near? 0 ((ao/make-accessibility (corridor 0.1) opts) [0 0 0] [0 1 0])))
    (doseq [height [0.1 0.3 1 3] strength [0 0.5 1 4]]
      (is (<= 0 ((ao/make-accessibility (corridor height) (assoc opts :strength strength))
                 [0 0 0] [0 1 0]) 1)))))

(deftest probe-cost-and-disable-behavior
  (let [calls (atom 0)
        distance (fn [p] (swap! calls inc) (floor-distance p))]
    (doseq [opts [{:mode :off} {:mode :normal :strength 0}]]
      (is (= 1.0 ((ao/make-accessibility distance opts) [0 0 0] [0 1 0]))))
    (is (zero? @calls))
    (let [access (ao/make-accessibility distance {:mode :normal :samples 5})]
      (is (= 1.0 (access [0 0 0] [0 0 0])))
      (is (zero? @calls))
      (access [0 0 0] [0 1 0])
      (is (= 5 @calls)))))

(deftest step-count-does-not-measure-occlusion
  (let [settings {:epsilon 1.0e-5 :max-distance 1000 :max-steps 2000}
        straight (march/raymarch floor-distance [0 1 0] [0 -1 0] settings)
        grazing (march/raymarch floor-distance [0 1 0] [(Math/sqrt 0.9999) -0.01 0] settings)
        access (ao/make-accessibility floor-distance {:mode :normal})]
    (is (= :hit (:status straight) (:status grazing)))
    (is (= 2 (:steps straight)))
    (is (> (:steps grazing) 1000))
    (is (near? 1 (access (:position straight) [0 1 0])))
    (is (near? 1 (access (:position grazing) [0 1 0])))))

(deftest shading-and-cli-integration
  (let [distance (corridor 0.1)
        opts {:base-color 0xFFFFFF :ambient 0.4 :diffuse-strength 0.6 :toward-light [0 1 0]}
        hit {:status :hit :position [0 0 0]}
        off (lighting/make-shader distance opts)
        on (lighting/make-shader distance (assoc opts :ao {:mode :normal}))
        direct (lighting/make-shader distance (assoc opts :ambient 0))
        only (lighting/make-shader distance (assoc opts :ao {:mode :only}))]
    (is (= 0xFFFFFF (off hit)))
    (is (= (direct hit) (on hit)))
    (is (= 0 (only hit)))
    (is (= 0x101A2B (only {:status :miss})))
    (is (= 0xFF00FF (only {:status :step-limit})))
    (is (= (on hit) (on (assoc hit :steps 9999)))))
  (let [opts {:lighting {:ao {:radius 3 :samples 8}}}]
    (is (= opts (core/configure-ao opts nil)))
    (is (= {:mode :normal :radius 3 :samples 8}
           (get-in (core/configure-ao opts :normal) [:lighting :ao]))))
  (doseq [mode ["off" "normal" "only"]]
    (is (= (keyword mode) (:ao (core/parse-options ["--ao" mode])))))
  (doseq [args [["--ao"] ["--ao" "wrong"]]]
    (is (thrown? clojure.lang.ExceptionInfo (core/parse-options args))))
  ;; Exercise a complete image path with AO-only and a known black surface.
  (let [opts (:render-options (scene-file/prepare
                               {:scene-distance (corridor 0.1)
                                :camera {:position [0 0.05 -1] :look-at [0 0 0]}
                                :lighting {:ao {:mode :only}}}))
        picture (render/render-image 1 1 opts)]
    (is (= 0 (bit-and 0xFFFFFF (.getRGB picture 0 0)))))
  (is (map? (scene-file/load-scene "scenes/ao-demo.clj"))))

(deftest ao-validation
  (doseq [opts [nil 1 {:typo 1} {:mode :wrong} {:samples 0} {:samples 65}
                {:samples 1.5} {:radius 0} {:radius Double/NaN}
                {:radius Double/POSITIVE_INFINITY} {:bias -1}
                {:bias Double/NaN} {:strength -1} {:strength Double/POSITIVE_INFINITY}]]
    (is (thrown? clojure.lang.ExceptionInfo (ao/make-accessibility floor-distance opts)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (scene-file/prepare {:scene-distance floor-distance :lighting {:ao opts}})))))
