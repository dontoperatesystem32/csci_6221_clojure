(ns scenes.blend-demo
  (:require [ray-marcher.scene :as scene]))

;; Two overlapping spheres show a sharp seam with hard min and a rounded neck
;; with either smooth mode. Keep geometry, order and camera fixed for comparisons.
{:objects [(scene/sphere [10 0 -1.5] 2 0xFF0000)
           (scene/sphere [10 0 1.5] 2 0x0000FF)]
 :union {:mode :hard :width 1.0}
 :camera {:position [0 3 -0.5] :look-at [10 0 0] :fov 40}
 :lighting {:base-color 0x70BFE8 :toward-light [-0.6 0.8 -0.5]}
 :settings {:max-steps 256}}
