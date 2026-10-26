(ns ray-marcher.controls
  "Pure fly-camera controls. Horizontal movement follows heading, not tilt."
  (:require [ray-marcher.camera :as camera]
            [ray-marcher.vector :as v]))

(def initial-state
  {:position [0.0 0.0 0.0] :yaw 0.0 :pitch 0.0 :fov 60.0})

(defn look
  "Apply mouse displacement in pixels. Positive dx turns right; negative dy looks up."
  [state dx dy]
  (let [limit (Math/toRadians 89.0)]
    (-> state
        (update :yaw #(Math/IEEEremainder (+ % (* dx 0.003)) (* 2 Math/PI)))
        (update :pitch #(max (- limit) (min limit (- % (* dy 0.003))))))))

(defn move
  "Move at 3 world units/second. Normalize combined axes to avoid faster diagonals."
  [{:keys [yaw] :as state} held seconds]
  (let [axis (fn [positive negative]
               (- (if (held positive) 1.0 0.0) (if (held negative) 1.0 0.0)))
        forward [(Math/cos yaw) 0.0 (Math/sin yaw)]
        right [(- (Math/sin yaw)) 0.0 (Math/cos yaw)]
        direction (v/add (v/scale forward (axis :forward :backward))
                         (v/add (v/scale right (axis :right :left))
                                [0.0 (axis :up :down) 0.0]))]
    (if (zero? (v/length direction))
      state
      (update state :position v/add (v/scale (v/normalize direction) (* 3.0 seconds))))))

(defn as-camera [{:keys [position yaw pitch fov]}]
  (let [forward [(* (Math/cos pitch) (Math/cos yaw))
                 (Math/sin pitch)
                 (* (Math/cos pitch) (Math/sin yaw))]]
    (camera/camera position (v/add position forward) [0.0 1.0 0.0] fov)))
