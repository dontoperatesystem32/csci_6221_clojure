(ns ray-marcher.image
  "Image creation and file output, independent of the window."
  (:import [java.awt.image BufferedImage]
           [java.io File]
           [javax.imageio ImageIO]))

(defn save-png!
  "Save an image to an existing directory. Return the output file."
  [^BufferedImage image path]
  (let [file (File. ^String path)]
    (when-not (ImageIO/write image "png" file)
      (throw (ex-info "No PNG image writer is available." {:path path})))
    file))
