(ns ray-marcher.launcher-form
  "Swing fields and layout only. Configuration and rendering live elsewhere."
  (:require [clojure.string :as str])
  (:import [java.awt GridLayout]
           [java.awt.event ActionListener]
           [javax.swing BorderFactory BoxLayout JButton JComboBox JColorChooser JLabel
            JPanel JSpinner JTextField JToggleButton SpinnerNumberModel]))

(def shading-choices [["Lambert" :lambert] ["Half-Lambert" :half-lambert]
                      ["Valve Half-Lambert" :valve-half-lambert] ["Flat silhouette" :flat]])
(def ao-choices [["Off" :off] ["Normal-sampled AO" :normal] ["AO only (grayscale)" :only]])
(def union-choices [["Hard minimum" :hard] ["Polynomial smooth minimum" :polynomial]
                    ["Exponential softmin" :exponential]])

(def field-groups
  [{:title "Rendering" :fields [[[:width] "Width (pixels)" :integer]
                                [[:height] "Height (pixels)" :integer]
                                [[:threads] "Rendering threads" :integer]]}
   {:title "Effects" :fields [[[:shading] "Shading" shading-choices]
                              [[:lighting :ao :mode] "Ambient occlusion" ao-choices]
                              [[:union :mode] "Object blending" union-choices]
                              [[:union :width] "Blend width (world units)" :number]]}
   {:title "Camera" :advanced? true
    :fields [[[:camera :position] "Position (X, Y, Z)" :point]
             [[:camera :look-at] "Look at (X, Y, Z)" :point]
             [[:camera :fov] "Vertical field of view (degrees)" :number]]}
   {:title "Lighting" :advanced? true
    :fields [[[:lighting :base-color] "Base color" :color]
             [[:lighting :toward-light] "Direction toward light (X, Y, Z)" :point]
             [[:lighting :ambient] "Ambient brightness (0–1)" :number]
             [[:lighting :diffuse-strength] "Diffuse strength" :number]
             [[:lighting :normal-step] "Normal sampling distance" :number]]}
   {:title "AO tuning" :advanced? true
    :fields [[[:lighting :ao :samples] "AO samples (1–64)" :integer]
             [[:lighting :ao :radius] "AO sampling radius" :number]
             [[:lighting :ao :bias] "AO sampling bias" :number]
             [[:lighting :ao :strength] "AO strength" :number]]}
   {:title "Marching" :advanced? true
    :fields [[[:settings :max-steps] "Maximum steps per ray" :integer]
             [[:settings :max-distance] "Maximum travel distance" :number]
             [[:settings :epsilon] "Hit tolerance" :number]]}])

(defn- listen! [button f]
  (.addActionListener button (reify ActionListener (actionPerformed [_ _] (f)))))
(defn- vertical-panel []
  (let [p (JPanel.)] (.setLayout p (BoxLayout. p BoxLayout/Y_AXIS)) p))
(defn- titled [title]
  (doto (JPanel. (GridLayout. 0 2 12 7))
    (.setBorder (BorderFactory/createTitledBorder title))))

(defn- widget [kind]
  (cond
    (vector? kind) (JComboBox. (into-array String (map first kind)))
    (= kind :integer) (JSpinner. (SpinnerNumberModel. (int 1) (int 1) (int Integer/MAX_VALUE) (int 1)))
    (= kind :color)
    (let [b (JButton. "Choose color")]
      (listen! b #(when-let [color (JColorChooser/showDialog b "Base color" (.getClientProperty b "color"))]
                    (.putClientProperty b "color" color)
                    (.setText b (format "#%06X" (bit-and 0xFFFFFF (.getRGB color)))))) b)
    :else (JTextField. 22)))

(defn- set-value! [component kind value]
  (cond
    (vector? kind) (.setSelectedIndex component (or (first (keep-indexed #(when (= value (second %2)) %1) kind)) 0))
    (= kind :integer) (.setValue component (int value))
    (= kind :color) (do (.putClientProperty component "color" (java.awt.Color. (int value)))
                       (.setText component (format "#%06X" value)))
    :else (.setText component (if (= kind :point) (str/join ", " value) (str value)))))

(defn- read-value [component kind label]
  (try
    (cond
      (vector? kind) (second (nth kind (.getSelectedIndex component)))
      (= kind :integer) (do (.commitEdit component) (.getValue component))
      (= kind :color) (bit-and 0xFFFFFF (.getRGB (.getClientProperty component "color")))
      (= kind :point) (let [values (mapv #(Double/parseDouble %) (str/split (str/trim (.getText component)) #"[,\s]+"))]
                       (when-not (= 3 (count values)) (throw (Exception.))) values)
      :else (Double/parseDouble (str/trim (.getText component))))
    (catch Exception _ (throw (ex-info (str "Check " label ". Enter " (if (= kind :point) "three coordinates: X, Y, Z." "a valid number.")) {})))))

(defn create-form!
  "Build controls on the EDT. Return explicit read/fill actions and components."
  []
  (let [panel (vertical-panel)
        advanced (vertical-panel)
        toggle (JToggleButton. "Advanced settings")
        fields (into {} (for [{:keys [fields]} field-groups [path label kind] fields]
                          [path {:component (widget kind) :kind kind :label label}]))
        blendable? (atom true)
        component #(get-in fields [% :component])
        choice #(read-value (component %) (:kind (fields %)) (:label (fields %)))
        update-enabled! (fn []
                          (let [flat? (= :flat (choice [:shading]))
                                ao? (and (not flat?) (not= :off (choice [:lighting :ao :mode])))]
                            (.setEnabled (component [:union :mode]) @blendable?)
                            (.setEnabled (component [:union :width]) (and @blendable? (not= :hard (choice [:union :mode]))))
                            (.setEnabled (component [:lighting :ao :mode]) (not flat?))
                            (doseq [k [:samples :radius :bias :strength]]
                              (.setEnabled (component [:lighting :ao k]) ao?))))]
    (doseq [{:keys [title fields advanced?]} field-groups]
      (let [group (titled title)]
        (doseq [[path label _] fields]
          (.add group (JLabel. label))
          (.setName (component path) (pr-str path))
          (.add group (component path)))
        (.add (if advanced? advanced panel) group)))
    (.setMaximum (.getModel (component [:lighting :ao :samples])) (int 64))
    (.setVisible advanced false)
    (.add panel toggle)
    (.add panel advanced)
    (listen! toggle #(do (.setVisible advanced (.isSelected toggle)) (.revalidate panel)))
    (doseq [path [[:shading] [:lighting :ao :mode] [:union :mode]]]
      (listen! (component path) update-enabled!))
    {:panel panel :fields fields :advanced-button toggle
     :read-values (fn []
                    (reduce-kv (fn [values path {:keys [component kind label]}]
                                 (assoc-in values path (read-value component kind label))) {} fields))
     :fill! (fn [values can-blend?]
              (reset! blendable? can-blend?)
              (doseq [[path {:keys [component kind]}] fields]
                (set-value! component kind (get-in values path)))
              (update-enabled!))}))
