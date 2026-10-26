(ns ray-marcher.controls-test
  (:require [clojure.test :refer [deftest is]]
            [ray-marcher.camera :as camera]
            [ray-marcher.controls :as controls]
            [ray-marcher.render :as render]
            [ray-marcher.vector :as v]
            [ray-marcher.worker :as worker]))

(defn near? [a b] (< (Math/abs (double (- a b))) 1.0e-8))

(deftest movement-is-independent-of-frame-rate-and-tilt
  (let [start (controls/look controls/initial-state 0 -200)
        once (controls/move start #{:forward} 1.0)
        ticks (nth (iterate #(controls/move % #{:forward} 0.01) start) 100)]
    (is (near? 3 (first (:position once))))
    (is (zero? (second (:position once))))
    (is (every? true? (map near? (:position once) (:position ticks)))))
  (is (= controls/initial-state (controls/move controls/initial-state #{:forward :backward} 1)))
  (is (near? 3 (v/length (:position (controls/move controls/initial-state #{:forward :right :up} 1)))))
  (is (= [0.0 -3.0 0.0] (:position (controls/move controls/initial-state #{:down} 1)))))

(deftest heading-and-look
  (let [right-facing (assoc controls/initial-state :yaw (/ Math/PI 2))
        moved (controls/move right-facing #{:forward} 1)
        extreme (controls/look controls/initial-state 100000 -100000)
        prepared (controls/as-camera extreme)]
    (is (near? 0 (first (:position moved))))
    (is (near? 3 (last (:position moved))))
    (is (near? (Math/toRadians 89) (:pitch extreme)))
    (is (near? 1 (v/length (:forward prepared))))
    (is (near? 0 (v/dot (:forward prepared) (:up prepared)))))
  (is (= camera/default-camera (controls/as-camera controls/initial-state)))
  (is (pos? (last (:forward (controls/as-camera (controls/look controls/initial-state 100 0))))))
  (is (neg? (second (:forward (controls/as-camera (controls/look controls/initial-state 0 100)))))))

(deftest cancelled-render-does-not-evaluate-scene
  (let [calls (atom 0)]
    (is (nil? (render/render-image 100 100
                {:scene-distance (fn [_] (swap! calls inc) 1)
                 :cancelled? (constantly true)})))
    (is (zero? @calls)))
  (let [rows (atom 0)]
    (is (nil? (render/render-image 5 5 {:cancelled? #(> (swap! rows inc) 2)})))
    (is (= 3 @rows))))

(deftest worker-keeps-only-latest-pending-view
  (let [entered (promise)
        release (promise)
        completed (promise)
        seen (atom [])
        renderer (worker/start!
                   {:render-fn (fn [_ _ {:keys [camera]}]
                                 (swap! seen conj camera)
                                 (when (= camera 1)
                                   (deliver entered true)
                                   (deref release 5000 nil))
                                 camera)
                    :on-image (fn [_ image] (when (= image 3) (deliver completed true)))
                    :on-error (fn [_ e] (deliver completed e))})
        request (fn [id] {:width 1 :height 1 :camera id :preview? true :valid? (constantly true)})]
    (try
      ((:request! renderer) (request 1))
      (is (= true (deref entered 5000 :timeout)))
      ((:request! renderer) (request 2))
      ((:request! renderer) (request 3))
      (deliver release true)
      (is (= true (deref completed 5000 :timeout)))
      (is (= [1 3] @seen))
      (finally (deliver release true) ((:close! renderer))))))

(deftest obsolete-full-frame-is-cancelled
  (let [entered (promise)
        release (promise)
        completed (promise)
        valid? (atom true)
        images (atom [])
        renderer (worker/start!
                   {:render-fn (fn [_ _ {:keys [camera cancelled?]}]
                                 (when (= camera 1)
                                   (deliver entered true)
                                   (deref release 5000 nil))
                                 (when-not (cancelled?) camera))
                    :on-image (fn [_ image] (swap! images conj image) (deliver completed true))
                    :on-error (fn [_ e] (deliver completed e))})]
    (try
      ((:request! renderer) {:width 1 :height 1 :camera 1 :preview? false :valid? #(deref valid?)})
      (is (= true (deref entered 5000 :timeout)))
      (reset! valid? false)
      ((:request! renderer) {:width 1 :height 1 :camera 2 :preview? false :valid? (constantly true)})
      (deliver release true)
      (is (= true (deref completed 5000 :timeout)))
      (is (= [2] @images))
      (finally (deliver release true) ((:close! renderer))))))
