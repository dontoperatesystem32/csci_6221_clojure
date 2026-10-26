(ns ray-marcher.core
  "Application entry point: interactive window or a single PNG export."
  (:require [ray-marcher.image :as image]
            [ray-marcher.render :as render]
            [ray-marcher.ui :as ui]))

(def usage
  "Usage: clojure -M:run [--width N] [--height N] [--lighting lambert|half-lambert|valve-half-lambert|flat] [--output FILE] [--help]\nDefaults: 800 × 450 pixels, Lambert lighting. --output saves a PNG instead of opening a window.")

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
          "--output" (if (or (nil? value) (.startsWith ^String value "--"))
                       (throw (ex-info "--output requires a file path." {}))
                       (recur remaining (assoc options :output value)))
          (throw (ex-info (str "Unknown option: " flag) {})))))))

(defn -main [& args]
  (try
    (let [{:keys [width height output help? lighting]} (parse-options args)
          rendering-options (if (= lighting :flat)
                              {:color-fn render/silhouette-color}
                              {:lighting {:diffuse-model (or lighting :lambert)}})
          render-view (fn [w h options]
                        (render/render-image w h (merge rendering-options options)))]
      (if help?
        (println usage)
        (if output
          (println "Saved" (.getAbsolutePath (image/save-png! (render-view width height {}) output)))
          (ui/show-viewer! width height render-view))))
    (catch Exception e
      (binding [*out* *err*]
        (println "Could not start:" (.getMessage e))
        (println usage))
      (System/exit 1))))
