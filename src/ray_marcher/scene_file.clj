(ns ray-marcher.scene-file
  "Load local Clojure scene definitions and prepare consistent export/viewer settings."
  (:require [ray-marcher.camera :as camera]
            [ray-marcher.controls :as controls]
            [ray-marcher.lighting :as lighting]
            [ray-marcher.march :as march]
            [ray-marcher.scene :as scene]))

(defn- check-keys! [value allowed label]
  (when-not (map? value)
    (throw (ex-info (str label " must be a map.") {})))
  (let [unknown (remove (set allowed) (keys value))]
    (when (seq unknown)
      (throw (ex-info (str "Unknown " label " keys: " (pr-str (vec unknown))) {})))))

(defn- point? [value]
  (and (vector? value) (= 3 (count value))
       (every? #(and (number? %) (Double/isFinite (double %))) value)))

(defn- distance-function [definition overrides]
  (let [objects? (contains? definition :objects)
        distance? (contains? definition :scene-distance)]
    (when (= objects? distance?)
      (throw (ex-info "Scene requires exactly one of :objects or :scene-distance." {})))
    (if objects?
      (do
        (when-not (and (vector? (:objects definition)) (every? ifn? (:objects definition)))
          (throw (ex-info ":objects must be an ordered vector of distance functions." {})))
        (when-not (map? (get definition :union {}))
          (throw (ex-info ":union must be a map." {})))
        (apply scene/blended-union (merge (:union definition) overrides) (:objects definition)))
      (do
        (when (or (contains? definition :union) (seq overrides))
          (throw (ex-info "Union settings require :objects; an opaque :scene-distance cannot be reconfigured." {})))
        (when-not (ifn? (:scene-distance definition))
          (throw (ex-info "Scene requires a callable :scene-distance function." {})))
        (:scene-distance definition)))))

(defn prepare
  "Prepare either an opaque distance function or a configurable ordered object group.
  CLI union overrides affect only this group, never nested distance functions."
  ([definition] (prepare definition {}))
  ([definition union-overrides]
   (check-keys! definition [:scene-distance :objects :union :camera :lighting :settings] "scene")
   (let [scene-distance (distance-function definition union-overrides)
         camera-options (get definition :camera {})
         lighting-options (get definition :lighting {})
         settings (merge march/default-settings (:settings definition))]
     (check-keys! camera-options [:position :look-at :fov] "camera")
     (check-keys! lighting-options (keys lighting/default-settings) "lighting")
     (check-keys! (get definition :settings {}) (keys march/default-settings) "marching settings")
     (when-not (and (number? (:epsilon settings)) (pos? (:epsilon settings))
                    (Double/isFinite (double (:epsilon settings)))
                    (number? (:max-distance settings)) (pos? (:max-distance settings))
                    (Double/isFinite (double (:max-distance settings)))
                    (integer? (:max-steps settings)) (pos? (:max-steps settings)))
       (throw (ex-info "Marching settings require positive finite limits and an integer :max-steps." {})))
     (let [{:keys [position look-at fov] :or {position [0.0 0.0 0.0]
                                            look-at [10.0 0.0 0.0] fov 60.0}} camera-options]
       (when-not (and (point? position) (point? look-at) (number? fov) (< 0 fov 180))
         (throw (ex-info "Camera needs finite [X Y Z] points and a field of view between 0 and 180." {})))
       (let [view (camera/camera position look-at [0.0 1.0 0.0] fov)]
         ;; Fail invalid lighting before opening a window or allocating an image.
         (lighting/make-shader scene-distance lighting-options)
         {:camera-state (controls/from-camera view)
          :render-options {:scene-distance scene-distance
                           :camera view :settings settings :lighting lighting-options}})))))

(defn load-scene
  "Evaluate a local .clj file whose final expression returns a scene map.
  Scene files are ordinary executable Clojure, not a restricted data format."
  ([path] (load-scene path {}))
  ([path union-overrides]
   (prepare (load-file path) union-overrides)))

(defn default-scene
  ([] (default-scene {}))
  ([union-overrides]
   (prepare {:objects scene/default-objects} union-overrides)))
