(ns ray-marcher.configuration
  "Shared configuration for the CLI and launcher; no window or process lifecycle."
  (:require [ray-marcher.ao :as ao]
            [ray-marcher.lighting :as lighting]
            [ray-marcher.march :as march]
            [ray-marcher.render :as render]
            [ray-marcher.scene :as scene]
            [ray-marcher.scene-file :as scene-file]))

(def default-camera {:position [0 0 0] :look-at [10 0 0] :fov 60})
(defn default-definition [] {:objects scene/default-objects})

(defn configure-lighting [render-options mode]
  (case mode
    nil render-options
    :flat (assoc render-options :color-fn render/silhouette-color)
    (assoc-in render-options [:lighting :diffuse-model] mode)))

(defn configure-ao [render-options mode]
  (if mode (assoc-in render-options [:lighting :ao :mode] mode) render-options))

(defn form-values
  "Validate a scene and expose its actual effective settings for editing."
  [definition]
  (scene-file/prepare definition)
  {:width 800 :height 450 :threads 1
   :shading (get-in definition [:lighting :diffuse-model] :lambert)
   :camera (merge default-camera (:camera definition))
   :lighting (assoc (merge lighting/default-settings (:lighting definition))
                    :ao (ao/settings (get-in definition [:lighting :ao] {})))
   :settings (merge march/default-settings (:settings definition))
   :union (merge {:mode :hard :width 1.0} (:union definition))})

(defn validate-render-size! [{:keys [width height threads]}]
  (doseq [[label value] [["Width" width] ["Height" height] ["Thread count" threads]]]
    (when-not (and (integer? value) (<= 1 value Integer/MAX_VALUE))
      (throw (ex-info (str label " must be a positive integer.") {})))))

(defn- validate-lighting! [{:keys [ambient diffuse-strength normal-step base-color toward-light]}]
  (let [finite? #(and (number? %) (Double/isFinite (double %)))]
    (when-not (and (finite? ambient) (<= 0 ambient 1)
                   (finite? diffuse-strength) (<= 0 diffuse-strength)
                   (finite? normal-step) (pos? normal-step)
                   (integer? base-color) (<= 0 base-color 0xFFFFFF)
                   (vector? toward-light) (= 3 (count toward-light))
                   (every? finite? toward-light))
      (throw (ex-info "Lighting requires finite values, ambient 0–1, nonnegative diffuse strength, positive normal spacing, an RGB color, and a finite light direction." {})))))

(defn prepare-form
  "Compile edited settings against the already loaded scene. Opaque geometry is
  preserved; union settings only affect explicitly exposed :objects groups."
  [definition {:keys [camera lighting settings union shading] :as values}]
  (validate-render-size! values)
  (validate-lighting! lighting)
  (when-not (#{:lambert :half-lambert :valve-half-lambert :flat} shading)
    (throw (ex-info "Choose a valid shading mode." {})))
  (let [edited (cond-> (assoc definition :camera camera :lighting lighting :settings settings)
                 (contains? definition :objects) (assoc :union union))
        prepared (scene-file/prepare edited)]
    (update prepared :render-options configure-lighting shading)))

(defn prepare-cli [{:keys [scene union lighting ao] :as options}]
  (validate-render-size! options)
  (let [definition (if scene (scene-file/read-definition scene) (default-definition))
        prepared (scene-file/prepare definition (or union {}))]
    (update prepared :render-options #(configure-ao (configure-lighting % lighting) ao))))
