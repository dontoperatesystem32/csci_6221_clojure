(ns scenes.menger
  (:require [ray-marcher.fractals :as fractals]))

;; A turquoise cube carved into repeating tunnels. Y is height.
;; Change depth to 2 for a lighter render, or 4 for finer detail.
{:scene-distance (fractals/menger-sponge [10 0 0] 6 5)

 :camera {:position [-1 6 -9]
          :look-at [10 -0.5 0]
          :fov 40}

 :lighting {:base-color 0x68D8C5
            :toward-light [-0.8 1.0 0.5]
            :ambient 0.055
            :diffuse-strength 0.945
            :normal-step 0.0005
            :diffuse-model :lambert}

 :settings {:epsilon 0.0005
            :max-distance 80.0
            :max-steps 512}}
