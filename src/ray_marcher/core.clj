(ns ray-marcher.core
  "Application entry point: interactive window or a single PNG export."
  (:require [ray-marcher.image :as image]
            [ray-marcher.render :as render]
            [ray-marcher.scene-file :as scene-file]
            [ray-marcher.ui :as ui]))

(def usage
  "Usage: clojure -M:run [--width N] [--height N] [--scene FILE.clj] [--lighting lambert|half-lambert|valve-half-lambert|flat] [--output FILE] [--help]\nDefaults: 800 × 450 pixels, Lambert lighting. --output saves a PNG instead of opening a window.")

(defn- positive-dimension [flag value]
  (let [n (parse-long (or value ""))]
    (when-not (and n (<= 1 n Integer/MAX_VALUE))
      (throw (ex-info (str flag " requires a positive integer.") {:value value})))
    (int n)))

(defn parse-options [args]
  (loop [args (seq args), options {:width 800 :height 450}]
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
          "--scene" (if (or (nil? value) (.startsWith ^String value "--"))
                      (throw (ex-info "--scene requires a Clojure scene-file path." {}))
                      (recur remaining (assoc options :scene value)))
          "--output" (if (or (nil? value) (.startsWith ^String value "--"))
                       (throw (ex-info "--output requires a file path." {}))
                       (recur remaining (assoc options :output value)))
          (throw (ex-info (str "Unknown option: " flag) {})))))))

(defn configure-lighting
  "A CLI lighting choice overrides only the model, preserving scene light settings."
  [render-options mode]
  (case mode
    nil render-options
    :flat (assoc render-options :color-fn render/silhouette-color)
    (assoc-in render-options [:lighting :diffuse-model] mode)))

(defn -main [& args]
  (try
    (let [{:keys [width height output help? lighting scene]} (parse-options args)]
      (if help?
        (println usage)
        (let [{:keys [render-options camera-state]}
              (if scene (scene-file/load-scene scene) (scene-file/default-scene))
              configured (configure-lighting render-options lighting)
              render-view (fn [w h options]
                            (render/render-image w h (merge configured options)))]
          (if output
            (println "Saved" (.getAbsolutePath (image/save-png! (render-view width height {}) output)))
            (ui/show-viewer! width height render-view camera-state)))))
    (catch Exception e
      (binding [*out* *err*]
        (println "Could not start:" (.getMessage e))
        (println usage))
      (System/exit 1))))
