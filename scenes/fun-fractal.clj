(ns scenes.fun-fractal
  (:require [ray-marcher.fractals :as fractals]))

;; Preset A: Continuous Organic Caverns & Ribbed Tunnels
(def dream-world
  (fractals/fractal-world 2.5 {:iterations 18
                      :box-scale -1.85
                      :min-radius 0.45
                      :fold-period 2
                      :rot-angles [0.22 -0.18 0.12]}))

;; Preset B: Grand Connected Temple Arches & Canyons
(def temple-world
  (fractals/fractal-world 3.0 {:iterations 16
                      :box-scale -2.05
                      :min-radius 0.52
                      :fold-period 2
                      :rot-angles [0.08 -0.11 0.06]}))

;; Preset C: Dense Interconnected Coral Caves
(def coral-world
  (fractals/fractal-world 2.0 {:iterations 20
                      :box-scale -1.72
                      :min-radius 0.40
                      :fold-period 3
                      :rot-angles [0.31 0.24 -0.19]}))
;; Preset D: Porous Organic Ribcages & Layered Membranes ("Like in a Dream" Title Screen)
(def living-cathedral
  (fractals/fractal-world 2.8 {:iterations 20
                      :bailout 32.0
                      :step-factor 0.45
                      :fold-limit 1.05
                      :box-scale -1.92
                      :min-radius 0.38
                      :fold-period 3
                      :rot-angles [0.34 -0.26 0.14]}))


{:scene-distance living-cathedral
 
 :camera {:position [0 0 0]
          :look-at [-2 0 0]
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
