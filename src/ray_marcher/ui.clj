(ns ray-marcher.ui
  "Swing displays completed images; image generation stays outside this namespace."
  (:import [java.awt BorderLayout GraphicsEnvironment]
           [java.awt.image BufferedImage]
           [javax.swing BorderFactory ImageIcon JFrame JLabel JScrollPane
            SwingConstants SwingUtilities WindowConstants]))

(defn show-image!
  "Schedule a window on Swing's UI thread. Large images can be scrolled.
  Call with a completed image that will no longer be modified."
  [^BufferedImage image]
  (when (GraphicsEnvironment/isHeadless)
    (throw (ex-info "A graphical desktop is required. Use --output preview.png to save an image instead."
                    {})))
  (SwingUtilities/invokeLater
   (fn []
     (let [frame (JFrame. "Clojure Ray Marcher — Sphere MVP")
           picture (JLabel. (ImageIcon. image))
           status (JLabel. (format "%d × %d pixels | Ray-marched sphere | Camera (0,0,0) → sphere (10,0,0), radius 2"
                                   (.getWidth image) (.getHeight image)))
           scroll (JScrollPane. picture)]
       (.setHorizontalAlignment picture SwingConstants/CENTER)
       (.setBorder status (BorderFactory/createEmptyBorder 10 12 10 12))
       (.add (.getContentPane frame) scroll BorderLayout/CENTER)
       (.add (.getContentPane frame) status BorderLayout/SOUTH)
       (.setDefaultCloseOperation frame WindowConstants/DISPOSE_ON_CLOSE)
       (.setSize frame 900 600)
       (.setLocationRelativeTo frame nil)
       (.setVisible frame true)))))
