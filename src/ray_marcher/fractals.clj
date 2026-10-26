(ns ray-marcher.fractals
  "Finite fractal distance bounds, usable as ordinary scene functions."
  (:require [ray-marcher.scene :as scene]))

(defn- folded-coordinate
  "Absolute coordinate inside a repeating cell, normalized to [-1,1]."
  [coordinate scale]
  (Math/abs (double (- (mod (* (+ coordinate 1.0) scale) 2.0) 1.0))))

(defn menger-sponge
  "A finite Menger sponge centered at center, with full side length and depth.
  Depth 0 is a cube. Each level removes the central cross of every remaining cell.
  Returns a conservative signed distance bound, not an exact Euclidean distance.
  Evaluates depth levels per point; does not enumerate 20^depth solid cubes."
  [center side-length depth]
  (when-not (and (vector? center) (= 3 (count center))
                 (every? #(and (number? %) (Double/isFinite (double %))) center)
                 (number? side-length) (pos? side-length)
                 (Double/isFinite (double side-length))
                 (integer? depth) (<= 0 depth 6))
    (throw (ex-info "Menger sponge needs a finite center, positive side length, and depth 0–6."
                    {:center center :side-length side-length :depth depth})))
  (let [[cx cy cz] center
        half-size (/ side-length 2.0)
        cube (scene/cube [0 0 0] 2)]
    (fn [[px py pz]]
      (let [x (/ (- px cx) half-size)
            y (/ (- py cy) half-size)
            z (/ (- pz cz) half-size)]
        (* half-size
           (loop [level 0, scale 1.0, distance (double (cube [x y z]))]
             (if (= level depth)
               distance
               (let [a (folded-coordinate x scale)
                     b (folded-coordinate y scale)
                     c (folded-coordinate z scale)
                     ;; At least two coordinates in the central third means
                     ;; this point belongs to one of the three square tunnels.
                     middle (max (min a b) (min b c) (min c a))
                     hole (/ (- (/ 1.0 3.0) middle) scale)]
                 (recur (inc level) (* scale 3.0) (max distance hole))))))))))
