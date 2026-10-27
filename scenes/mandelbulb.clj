(ns scenes.mandelbulb
  (:require [ray-marcher.fractals :as fractals]))

;; Power-8 Mandelbulb: folded, cauliflower-like detail. Y is the polar/up axis.
;; This matches the fractal family in the reference, not its exact render settings.
{:scene-distance
 (let [bulb (fractals/mandelbulb [0 0 0] 2.5
               {:power 8 :iterations 14 :bailout 4.0 :step-factor 0.7})]
   ;; Translate the local model to [10 0 0] and reflect Y to put its spire on top.
   (fn [[x y z]] (bulb [(- x 10.0) (- y) z])))

 :camera {:position [0 1.6 -0.8]
          :look-at [10 0 0]
          :fov 40}

 :lighting {:base-color 0xDDDDDD
            :toward-light [-0.7 0.9 -0.5]
            :ambient 0.08
            :diffuse-strength 0.92
            :normal-step 0.008
            :diffuse-model :lambert}

 :settings {:epsilon 0.0015
            :max-distance 60.0
            :max-steps 768}}
