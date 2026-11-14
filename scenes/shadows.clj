(ns scenes.shadows
  (:require [ray-marcher.scene :as scene]))

;; Keep the floor separate from the object group. Changing the group's union
;; in code can blend objects without melting them into the ground.
{:scene-distance
 (scene/union
   (scene/union (scene/sphere [10 0 -2] 2)
                (scene/cube [12 -0.5 2] 3))
   (scene/floor-plane -2))
 :camera {:position [0 6 -9] :look-at [11 -0.5 0] :fov 45}
 :lighting {:base-color 0xD6C4A1
            :toward-light [0.6 0.8 -0.5]
            :ambient 0.18 :diffuse-strength 0.82
            :shadows {:mode :hard :bias 0.01 :epsilon 0.001
                      :max-distance 100 :max-steps 256}}
 :settings {:max-steps 256}}
