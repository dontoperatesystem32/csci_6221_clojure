(ns ray-marcher.core
  "Application entry point: interactive window or a single PNG export."
  (:require [ray-marcher.image :as image]
            [ray-marcher.progress :as progress]
            [ray-marcher.render :as render]
            [ray-marcher.scene-file :as scene-file]
            [ray-marcher.ui :as ui]))

(def usage
  "Usage: clojure -M:run [--width N] [--height N] [--threads N] [--scene FILE.clj] [--lighting lambert|half-lambert|valve-half-lambert|flat] [--ao off|normal|only] [--union hard|polynomial|exponential] [--blend WIDTH] [--output FILE] [--help]\nDefaults: 800 × 450 pixels, Lambert lighting, AO off, hard union, blend width 1.0 (world units; 0 disables blending). --output saves a PNG instead of opening a window.")

(defn- positive-dimension [flag value]
  (let [n (parse-long (or value ""))]
    (when-not (and n (<= 1 n Integer/MAX_VALUE))
      (throw (ex-info (str flag " requires a positive integer.") {:value value})))
    (int n)))

(defn- blend-width [value]
  (let [width (try (Double/parseDouble (or value ""))
                   (catch NumberFormatException _ Double/NaN))]
    (when-not (and (Double/isFinite width) (<= 0 width))
      (throw (ex-info "--blend requires a finite nonnegative width in world units." {})))
    width))

(defn parse-options [args]
  (loop [args (seq args), options {:width 800 :height 450 :threads 1}]
    (if-not args
      options
      (let [[flag value & remaining] args]
        (case flag
          "--help" (assoc options :help? true)
          "--width" (recur remaining (assoc options :width (positive-dimension flag value)))
          "--height" (recur remaining (assoc options :height (positive-dimension flag value)))
          "--lighting" (if (#{"lambert" "half-lambert" "valve-half-lambert" "flat"} value)
                         (recur remaining (assoc options :lighting (keyword value)))
                         (throw (ex-info "--lighting requires lambert, half-lambert, valve-half-lambert, or flat." {})))
          "--ao" (if (#{"off" "normal" "only"} value)
                   (recur remaining (assoc options :ao (keyword value)))
                   (throw (ex-info "--ao requires off, normal, or only." {})))
          "--union" (if (#{"hard" "polynomial" "exponential"} value)
                      (recur remaining (assoc-in options [:union :mode] (keyword value)))
                      (throw (ex-info "--union requires hard, polynomial, or exponential." {})))
          "--blend" (recur remaining (assoc-in options [:union :width] (blend-width value)))
          "--scene" (if (or (nil? value) (.startsWith ^String value "--"))
                      (throw (ex-info "--scene requires a Clojure scene-file path." {}))
                      (recur remaining (assoc options :scene value)))
          "--output" (if (or (nil? value) (.startsWith ^String value "--"))
                       (throw (ex-info "--output requires a file path." {}))
                       (recur remaining (assoc options :output value)))
          "--threads" (recur remaining (assoc options :threads (positive-dimension flag value)))
          (throw (ex-info (str "Unknown option: " flag) {})))))))

(defn configure-lighting
  "A CLI lighting choice overrides only the model, preserving scene light settings."
  [render-options mode]
  (case mode
    nil render-options
    :flat (assoc render-options :color-fn render/silhouette-color)
    (assoc-in render-options [:lighting :diffuse-model] mode)))

(defn configure-ao
  "Override only the AO mode, preserving scene probe settings."
  [render-options mode]
  (if mode (assoc-in render-options [:lighting :ao :mode] mode) render-options))

(defn -main [& args]
  (try
    (let [{:keys [width height output help? lighting scene threads union ao]} (parse-options args)]
      (if help?
        (println usage)
        (let [{:keys [render-options camera-state]}
              (if scene (scene-file/load-scene scene (or union {})) (scene-file/default-scene (or union {})))
              configured (configure-ao (configure-lighting render-options lighting) ao)
              render-view (fn [w h options]
                            (render/render-image w h (merge configured options)))]
          (if output
            (let [{:keys [report! writing! finish!]} (progress/reporter)
                  file (try
                         (let [picture (render-view width height {:on-progress report! :threads threads})]
                           (writing!)
                           (image/save-png! picture output))
                         (finally (finish!)))]
              (println "Saved" (.getAbsolutePath file))
              (System/exit 0))
            (ui/show-viewer! width height render-view camera-state threads)))))
    (catch Exception e
      (binding [*out* *err*]
        (println "Could not start:" (.getMessage e))
        (println usage))
      (System/exit 1))))
