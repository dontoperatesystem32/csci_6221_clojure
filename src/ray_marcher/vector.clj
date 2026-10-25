(ns ray-marcher.vector
  "Small 3D vector operations. Vectors are [x y z] in world coordinates.")

(defn add [[ax ay az] [bx by bz]]
  [(+ ax bx) (+ ay by) (+ az bz)])

(defn subtract [[ax ay az] [bx by bz]]
  [(- ax bx) (- ay by) (- az bz)])

(defn scale [[x y z] amount]
  [(* x amount) (* y amount) (* z amount)])

(defn dot [[ax ay az] [bx by bz]]
  (+ (* ax bx) (* ay by) (* az bz)))

(defn length [v]
  (Math/sqrt (double (dot v v))))

(defn normalize [v]
  (let [magnitude (length v)]
    (when (zero? magnitude)
      (throw (ex-info "Cannot normalize a zero vector." {:vector v})))
    (scale v (/ 1.0 magnitude))))

(defn cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])
