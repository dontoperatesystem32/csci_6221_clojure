(ns ray-marcher.core
  "Application entry point: interactive window or a single PNG export."
  (:require [ray-marcher.application :as application]
            [ray-marcher.configuration :as configuration]
            [ray-marcher.launcher :as launcher]
            [ray-marcher.progress :as progress]))

(def usage
  "Usage: clojure -M:run [--launcher | --viewer] [--width N] [--height N] [--threads N] [--scene FILE.clj] [--lighting lambert|half-lambert|valve-half-lambert|flat] [--ao off|normal|only] [--shadows off|hard] [--union hard|polynomial|exponential] [--blend WIDTH] [--output FILE] [--help]\nNo arguments opens the graphical launcher. Rendering flags run directly; --viewer opens the default viewer.\nDefaults: 800 × 450 pixels, Lambert lighting, AO and shadows off, hard union, blend width 1.0 (world units; 0 disables blending). --output saves a PNG instead of opening a window.")

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
          "--launcher" (recur (next args) (assoc options :launcher? true))
          "--viewer" (recur (next args) (assoc options :viewer? true))
          "--width" (recur remaining (assoc options :width (positive-dimension flag value)))
          "--height" (recur remaining (assoc options :height (positive-dimension flag value)))
          "--lighting" (if (#{"lambert" "half-lambert" "valve-half-lambert" "flat"} value)
                         (recur remaining (assoc options :lighting (keyword value)))
                         (throw (ex-info "--lighting requires lambert, half-lambert, valve-half-lambert, or flat." {})))
          "--ao" (if (#{"off" "normal" "only"} value)
                   (recur remaining (assoc options :ao (keyword value)))
                   (throw (ex-info "--ao requires off, normal, or only." {})))
          "--shadows" (if (#{"off" "hard"} value)
                        (recur remaining (assoc options :shadows (keyword value)))
                        (throw (ex-info "--shadows requires off or hard." {})))
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

(def configure-lighting configuration/configure-lighting)
(def configure-ao configuration/configure-ao)

(defn -main [& args]
  (try
    (let [{:keys [output help? launcher? viewer?] :as options} (parse-options args)]
      (cond
        help? (println usage)
        (or (empty? args) launcher?)
        (do
          (when (and launcher? (not= (vec args) ["--launcher"]))
            (throw (ex-info "Use --launcher on its own; choose settings in the menu." {})))
          (launcher/show-launcher!))
        :else
        (let [prepared (configuration/prepare-cli options)]
          (when (and viewer? output)
            (throw (ex-info "Choose either --viewer or --output." {})))
          (if output
            (let [{:keys [report! writing! finish!]} (progress/reporter)
                  file (try (application/export! options prepared output
                                                 {:on-progress report! :on-writing writing!})
                            (finally (finish!)))]
              (println "Saved" (.getAbsolutePath file))
              (System/exit 0))
            (application/open-viewer! options prepared)))))
    (catch Exception e
      (binding [*out* *err*]
        (println "Could not start:" (.getMessage e))
        (println usage))
      (System/exit 1))))
