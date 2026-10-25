(ns ray-marcher.core
  "Application entry point. Generates an image before asking Swing to display it."
  (:require [ray-marcher.image :as image]
            [ray-marcher.render :as render]
            [ray-marcher.ui :as ui]))

(def usage
  "Usage: clojure -M:run [--width N] [--height N] [--output FILE] [--help]\nDefaults: 800 × 450 pixels. --output saves a PNG instead of opening a window.")

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
          "--output" (if (or (nil? value) (.startsWith ^String value "--"))
                       (throw (ex-info "--output requires a file path." {}))
                       (recur remaining (assoc options :output value)))
          (throw (ex-info (str "Unknown option: " flag) {})))))))

(defn -main [& args]
  (try
    (let [{:keys [width height output help?]} (parse-options args)]
      (if help?
        (println usage)
        (let [picture (render/render-image width height)]
          (if output
            (println "Saved" (.getAbsolutePath (image/save-png! picture output)))
            (ui/show-image! picture)))))
    (catch Exception e
      (binding [*out* *err*]
        (println "Could not start:" (.getMessage e))
        (println usage))
      (System/exit 1))))
