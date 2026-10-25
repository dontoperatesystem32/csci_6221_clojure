(ns ray-marcher.camera
  "Perspective camera: +Y is world up; the default camera looks along +X."
  (:require [ray-marcher.vector :as v]))

(defn camera
  "Prepare a camera basis. Up must not be parallel to the viewing direction.
  Vertical field of view is in degrees, strictly between 0 and 180."
  [origin target up vertical-fov]
  (when-not (< 0 vertical-fov 180)
    (throw (ex-info "Field of view must be between 0 and 180 degrees." {})))
  (let [forward (v/normalize (v/subtract target origin))
        right (v/normalize (v/cross forward up))]
    {:origin origin
     :forward forward
     :right right
     :up (v/cross right forward)
     :half-height (Math/tan (/ (Math/toRadians (double vertical-fov)) 2.0))}))

(def default-camera
  (camera [0.0 0.0 0.0] [10.0 0.0 0.0] [0.0 1.0 0.0] 60.0))

(defn ray-direction
  "Unit direction through pixel (x,y)'s center. Image y increases downward.
  Aspect correction keeps a sphere circular in rectangular images."
  [{:keys [forward right up half-height]} x y width height]
  (let [screen-x (* (- (* 2.0 (/ (+ x 0.5) width)) 1.0)
                    (/ (double width) height) half-height)
        screen-y (* (- 1.0 (* 2.0 (/ (+ y 0.5) height))) half-height)]
    (v/normalize (v/add forward
                        (v/add (v/scale right screen-x)
                               (v/scale up screen-y))))))
