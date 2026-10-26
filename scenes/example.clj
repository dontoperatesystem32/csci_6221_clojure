(ns scenes.example
  (:require [ray-marcher.scene :as scene]))

;; All coordinates use [X Y Z], with Y as height. Copy this file to start a scene.
{:scene-distance
 (scene/union
   (scene/sphere [10 0 -2] 2)
   (scene/cube [12 0 2] 3))

 :camera {:position [0 1 -4]
          :look-at [11 0 0]
          :fov 50}

 :lighting {:toward-light [-0.6 0.8 -0.5]
            :base-color 0x70BFE8
            :ambient 0.08
            :diffuse-model :lambert}

 :settings {:max-steps 256}}
