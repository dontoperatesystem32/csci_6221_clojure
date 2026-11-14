(ns scenes.fractal-fun
  (:require [ray-marcher.fractals :as fractals]
            [ray-marcher.scene :as scene]))

;; Reusable external color palettes
(def muted-palettes
  {;; Brighter limestone, pale slate, and warm ivory-ochre
   :muted-bone-slate {:bias  [0.66 0.65 0.67]
                      :amp   [0.24 0.22 0.20]
                      :freq  [0.90 0.90 0.90]
                      :phase [0.02 0.14 0.28]}

   ;; Brighter seafoam-slate, pale verdigris, and warm sand
   :abyssal-moss     {:bias  [0.56 0.63 0.64]
                      :amp   [0.20 0.23 0.21]
                      :freq  [1.05 1.05 1.05]
                      :phase [0.10 0.35 0.52]}

   ;; Brighter weathered ash, warm sandstone, and soft copper
   :charcoal-ember   {:bias  [0.64 0.60 0.58]
                      :amp   [0.25 0.19 0.16]
                      :freq  [0.85 0.85 0.85]
                      :phase [0.00 0.12 0.22]}})



(def scenes
  {;; 1. Dense, rib-lined bone/stone passage wrapping tightly around the lens
   :bone-catacombs
   {:scene-distance    (scene/tile-space [40 40 40]
                          (fractals/fractal-world 33 {:iterations 10
                                                       :box-scale -1.88
                                              :min-radius 0.35
                                              :fold-period 3
                                              :rot-angles [0.38 -0.29 0.18]
                                              :trap-scale 1.8
                                              :palette-params (:muted-bone-slate muted-palettes)}))
    :camera   {:position [0.22 0.14 0.00]
               :look-at  [10.22 0.0 -2.00]
               :fov      90}
    :lighting {:toward-light [-0.45 0.85 0.35]
               :ambient 0.24
               :diffuse-strength 0.85
               :normal-step 0.0015
               :diffuse-model :half-lambert}
    :settings {:epsilon 0.0004 :max-distance 13.0 :max-steps 340}}

   ;; 2. Tangled, root-like underwater cavern with narrow sightlines
   :abyssal-labyrinth
   {:scene-distance    (scene/tile-space [38 38 38]
                          (fractals/fractal-world 32 {:iterations 10
                                                       :box-scale -1.82
                                              :min-radius 0.33
                                              :fold-period 3
                                              :rot-angles [0.44 0.26 -0.22]
                                              :trap-scale 2.0
                                              :palette-params (:abyssal-moss muted-palettes)}))
    :camera   {:position [-0.23 0.12 0.50]
               :look-at  [0.0 0.0 -1.00]
               :fov      92}
    :lighting {:toward-light [-0.35 0.90 0.25]
               :ambient 0.22
               :diffuse-strength 0.88
               :normal-step 0.0015
               :diffuse-model :half-lambert}
    :settings {:epsilon 0.0004 :max-distance 12.0 :max-steps 360}}

   ;; 3. Heavy, encroaching brutalist arches in smoky charcoal and copper
   :basalt-vaults
   {:scene-distance    (scene/tile-space [42 42 42]
                          (fractals/fractal-world 35 {:iterations 11
                                                       :box-scale -1.82
                                              :min-radius 0.35
                                              :fold-period 3
                                              :rot-angles [0.12 -0.15 0.08]
                                              :trap-scale 1.6
                                              :palette-params (:charcoal-ember muted-palettes)}))
    :camera   {:position [1.0 0.0 0.0]
               :look-at  [0.0 1.0 0.0]
               :fov      88}
    :lighting {:toward-light [-0.55 0.80 -0.30]
               :ambient 0.23
               :diffuse-strength 0.86
               :normal-step 0.0016
               :diffuse-model :half-lambert}
    :settings {:epsilon 0.0004 :max-distance 14.0 :max-steps 320}}})

(:bone-catacombs scenes)
