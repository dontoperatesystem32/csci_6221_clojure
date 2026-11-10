(ns scenes.ao-demo
  (:require [ray-marcher.scene :as scene]))

;; A sphere resting on a floor, plus a cube: inspect contact and open surfaces.
{:objects [(scene/sphere [10 0 -1.8] 2)
           (scene/cube [11 -0.5 2] 3)
           (fn [[_ y _]] (+ y 2))]
 :camera {:position [0 5 -7] :look-at [10 -0.5 0] :fov 45}
 :lighting {:base-color 0xCCDDEE :ambient 0.65 :diffuse-strength 0.35
            :toward-light [-0.6 0.8 -0.5]
            :ao {:mode :normal :samples 5 :radius 1.5 :bias 0.01 :strength 1}}
 :settings {:max-steps 256}}
