(ns scenes.example
  (:require [ray-marcher.scene :as scene]))

;; All coordinates use [X Y Z], with Y as height. Copy this file to start a scene.
{:objects
 [(scene/sphere [10 0 -2] 2 0x70BFE8)
  (scene/cube [12 0 2] 3 0x70BFE8)]

 :camera {:position [0 1 -4]
          :look-at [11 0 0]
          :fov 50}

 :lighting {:toward-light [-0.6 0.8 -0.5]
            :ambient 0.08
            :diffuse-model :lambert}

 :settings {:max-steps 256}}
