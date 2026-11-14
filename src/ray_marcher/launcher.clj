(ns ray-marcher.launcher
  "Startup menu controller. Form layout is separate from render configuration.
  All scene-file evaluation and image generation run outside Swing's EDT."
  (:require [clojure.string :as str]
            [ray-marcher.application :as application]
            [ray-marcher.configuration :as configuration]
            [ray-marcher.launcher-form :as form]
            [ray-marcher.scene-file :as scene-file])
  (:import [java.awt BorderLayout Dimension GraphicsEnvironment]
           [java.awt.event ActionListener WindowAdapter]
           [java.io File]
           [java.util.concurrent Executors ExecutorService ThreadFactory]
           [javax.swing BorderFactory BoxLayout JButton JComboBox JFileChooser JFrame
            JLabel JOptionPane JPanel JProgressBar JScrollPane SwingUtilities WindowConstants]
           [javax.swing.filechooser FileNameExtensionFilter]))

(defn- listen! [button f]
  (.addActionListener button (reify ActionListener (actionPerformed [_ _] (f)))))
(defn- on-ui! [{:keys [closed?]} f]
  (SwingUtilities/invokeLater ^Runnable #(when-not @closed? (f))))
(defn- message! [{:keys [status]} text] (.setText status text))
(defn- busy! [{:keys [busy? controls]} value]
  (reset! busy? value)
  (doseq [control controls] (.setEnabled control (not value))))
(defn- show-error! [menu error]
  (let [cause (or (.getCause ^Exception error) error)]
    (message! menu (str "Error: " (.getMessage ^Exception cause)))
    (.setToolTipText (:status menu) (.getMessage ^Exception cause))))

(defn- submit!
  "Run work on the menu's executor and marshal completion/errors onto the EDT."
  [{:keys [executor] :as menu} work complete]
  (.execute ^ExecutorService executor
            ^Runnable (fn []
                        (try
                          (let [result (work)] (on-ui! menu #(complete result)))
                          (catch Exception e
                            (on-ui! menu #(do (busy! menu false)
                                              (.setEnabled (:cancel menu) false)
                                              (show-error! menu e))))))))

(defn scene-catalog []
  (into [["Built-in sphere and cube" nil]]
        (filter (fn [[_ path]] (.isFile (File. path)))
                [["Sphere and cube example" "scenes/example.clj"]
                 ["Menger sponge" "scenes/menger.clj"]
                 ["Mandelbulb" "scenes/mandelbulb.clj"]
                 ["Blending demo" "scenes/blend-demo.clj"]
                 ["Ambient occlusion demo" "scenes/ao-demo.clj"]
                 ["Floor and shadows" "scenes/shadows.clj"]])))

(defn- fill-scene! [{:keys [form definition note] :as menu} scene values]
  (reset! definition scene)
  (.setValue (:progress menu) 0)
  (.setString (:progress menu) "Ready")
  ((:fill! form) values (contains? scene :objects))
  (.setText note (if (contains? scene :objects)
                   "Settings apply to new viewers and PNG exports."
                   "This scene defines its own geometry; object blending is unavailable.")))

(defn load-scene!
  "Load a scene once in the background. On failure keep the previous scene."
  [{:keys [scene-picker loaded-index] :as menu} path]
  (let [index (.getSelectedIndex scene-picker)]
    (busy! menu true)
    (message! menu "Loading scene…")
    (submit! menu
             #(try
                (let [definition (if path (scene-file/read-definition path) (configuration/default-definition))]
                  {:definition definition :values (configuration/form-values definition)})
                (catch Exception e {:error e}))
             (fn [{:keys [definition values error]}]
               (if error
                 (do (.setSelectedIndex scene-picker @loaded-index) (show-error! menu error))
                 (do (reset! loaded-index index)
                     (fill-scene! menu definition values)
                     (message! menu "Scene loaded. Settings match the scene defaults.")))
               (busy! menu false)))))

(defn- prepared-form [{:keys [form definition]}]
  (let [values ((:read-values form))]
    [values (configuration/prepare-form @definition values)]))

(defn- open-viewer! [menu]
  (try
    (let [[values prepared] (prepared-form menu)]
      (application/open-viewer! values prepared)
      (message! menu "Viewer opened. Menu edits apply to the next viewer/export."))
    (catch Exception e (show-error! menu e))))

(defn export!
  "Snapshot the form and run one cancellable PNG export in the background."
  [{:keys [cancelled? closed? progress cancel] :as menu} path]
  (try
    (let [[values prepared] (prepared-form menu)]
      (reset! cancelled? false)
      (busy! menu true)
      (.setEnabled cancel true)
      (.setValue progress 0)
      (.setString progress "Rendering…")
      (message! menu "Rendering PNG in the background…")
      (submit! menu
               (fn [] (application/export!
                  values prepared path
                  {:cancelled? (fn [] (or @closed? @cancelled?))
                   :on-progress (fn [done total]
                                  (on-ui! menu (fn []
                                                 (.setValue progress (int (* 100 (/ done total))))
                                                 (.setString progress (str done " / " total " rows")))))
                   :on-writing (fn [] (on-ui! menu #(do (message! menu "Writing PNG…")
                                                       (.setEnabled cancel false))))}))
               (fn [file]
                 (busy! menu false)
                 (.setEnabled cancel false)
                 (.setString progress (if file "Saved" "Cancelled"))
                 (message! menu (if file (str "Saved " (.getAbsolutePath file)) "Export cancelled.")))))
    (catch Exception e (show-error! menu e))))

(defn- choose-scene! [{:keys [frame catalog scene-picker]}]
  (let [chooser (doto (JFileChooser. (File. "scenes"))
                  (.setDialogTitle "Choose a trusted Clojure scene")
                  (.setFileFilter (FileNameExtensionFilter. "Clojure scene (.clj)" (into-array String ["clj"]))))]
    (when (= JFileChooser/APPROVE_OPTION (.showOpenDialog chooser frame))
      (let [path (.getAbsolutePath (.getSelectedFile chooser))]
        (swap! catalog conj [path path])
        (.addItem scene-picker path)
        (.setSelectedIndex scene-picker (dec (count @catalog)))))))

(defn- choose-output! [{:keys [frame] :as menu}]
  (let [chooser (doto (JFileChooser.) (.setDialogTitle "Render PNG") (.setSelectedFile (File. "render.png")))]
    (when (= JFileChooser/APPROVE_OPTION (.showSaveDialog chooser frame))
      (let [selected (.getAbsolutePath (.getSelectedFile chooser))
            path (if (str/ends-with? (str/lower-case selected) ".png") selected (str selected ".png"))]
        (when (or (not (.exists (File. path)))
                  (= JOptionPane/YES_OPTION (JOptionPane/showConfirmDialog frame "Replace this PNG?" "Confirm overwrite" JOptionPane/YES_NO_OPTION)))
          (export! menu path))))))

(defn create-launcher!
  "Build/show the menu on the EDT. Return an explicit handle to its actions/state."
  []
  (assert (SwingUtilities/isEventDispatchThread))
  (let [frame (JFrame. "Ray Marcher — Launcher")
        root (JPanel. (BorderLayout. 10 10))
        header (JPanel.)
        footer (JPanel.)
        buttons (JPanel.)
        catalog (atom (scene-catalog))
        picker (JComboBox. (into-array String (map first @catalog)))
        browse (JButton. "Browse .clj…")
        reset-button (JButton. "Reset to scene defaults")
        open (JButton. "Open viewer")
        export (JButton. "Render PNG…")
        cancel (JButton. "Cancel export")
        menu {:frame frame :form (form/create-form!) :definition (atom (configuration/default-definition))
              :catalog catalog :scene-picker picker :loaded-index (atom 0)
              :note (JLabel.) :status (JLabel. "Ready") :progress (JProgressBar. 0 100)
              :cancel cancel :reset reset-button :open open :export export
              :busy? (atom false) :closed? (atom false) :cancelled? (atom false)
              :controls [picker browse reset-button open export]
              :executor (Executors/newSingleThreadExecutor
                          (reify ThreadFactory
                            (newThread [_ runnable]
                              (doto (Thread. runnable "ray-marcher-launcher") (.setDaemon true)))))}]
    (.setBorder root (BorderFactory/createEmptyBorder 12 16 12 16))
    (.add header (JLabel. "Scene")) (.add header picker) (.add header browse)
    (.setLayout footer (BoxLayout. footer BoxLayout/Y_AXIS))
    (doseq [button [reset-button open export cancel]] (.add buttons button))
    (doseq [c [(:note menu) (JLabel. "Scene files execute Clojure code. Open files you trust.")
               (:progress menu) (:status menu) buttons]] (.add footer c))
    (.setMinimumSize (:progress menu) (Dimension. 100 24))
    (.setPreferredSize (:progress menu) (Dimension. 700 24))
    (.setMaximumSize (:progress menu) (Dimension. Integer/MAX_VALUE 24))
    (.setStringPainted (:progress menu) true)
    (.setString (:progress menu) "Ready")
    (.setEnabled cancel false)
    (listen! picker #(when-not @(:busy? menu)
                      (load-scene! menu (second (nth @catalog (.getSelectedIndex picker))))))
    (listen! browse #(choose-scene! menu))
    (listen! reset-button #(try
                            (fill-scene! menu @(:definition menu) (configuration/form-values @(:definition menu)))
                            (message! menu "Scene defaults restored (800 × 450, one thread).")
                            (catch Exception e (show-error! menu e))))
    (listen! open #(open-viewer! menu))
    (listen! export #(choose-output! menu))
    (listen! cancel #(do (reset! (:cancelled? menu) true) (.setEnabled cancel false) (message! menu "Cancelling…")))
    (.add root header BorderLayout/NORTH)
    (.add root (JScrollPane. (:panel (:form menu))) BorderLayout/CENTER)
    (.add root footer BorderLayout/SOUTH)
    (.setContentPane frame root)
    (.setMinimumSize frame (Dimension. 740 500))
    (.setSize frame 850 740)
    (.setDefaultCloseOperation frame WindowConstants/DISPOSE_ON_CLOSE)
    (.addWindowListener frame
                        (proxy [WindowAdapter] []
                          (windowClosed [_]
                            (reset! (:closed? menu) true)
                            (reset! (:cancelled? menu) true)
                            (.shutdownNow ^ExecutorService (:executor menu)))))
    (fill-scene! menu @(:definition menu) (configuration/form-values @(:definition menu)))
    (.setLocationRelativeTo frame nil)
    (.setVisible frame true)
    menu))

(defn show-launcher! []
  (when (GraphicsEnvironment/isHeadless)
    (throw (ex-info "A graphical desktop is required for the launcher. Use --output FILE.png for headless rendering." {})))
  (SwingUtilities/invokeLater ^Runnable create-launcher!))
