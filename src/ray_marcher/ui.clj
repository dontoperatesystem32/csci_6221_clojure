(ns ray-marcher.ui
  "Interactive Swing viewer. Input and display use the EDT; rendering uses a worker."
  (:require [ray-marcher.controls :as controls]
            [ray-marcher.worker :as worker])
  (:import [java.awt BorderLayout Color Dimension Graphics GraphicsEnvironment
            KeyboardFocusManager KeyEventDispatcher]
           [java.awt.event ActionListener KeyEvent MouseAdapter MouseMotionAdapter WindowAdapter]
           [java.awt.image BufferedImage]
           [javax.swing BorderFactory JFrame JLabel JPanel SwingUtilities Timer WindowConstants]))

(def key-actions
  {KeyEvent/VK_W :forward KeyEvent/VK_S :backward
   KeyEvent/VK_A :left KeyEvent/VK_D :right
   KeyEvent/VK_SHIFT :up KeyEvent/VK_CONTROL :down})

(defn show-viewer!
  "Open an interactive viewer. render-fn is independent of Swing and accepts
  width, height, and rendering options. Drag the left mouse button to look."
  ([width height render-fn]
   (show-viewer! width height render-fn controls/initial-state))
  ([width height render-fn initial-state]
  (when (GraphicsEnvironment/isHeadless)
    (throw (ex-info "A graphical desktop is required. Use --output preview.png instead." {})))
  (SwingUtilities/invokeLater
   (fn []
     (let [frame (JFrame. "Clojure Ray Marcher")
           state (atom initial-state)
           revision (atom 0)
           held (atom #{})
           drag (atom nil)
           closed? (atom false)
           picture (atom nil)
           changed-at (atom (System/nanoTime))
           last-tick (atom (System/nanoTime))
           last-request (atom nil)
           status (JLabel. "Rendering…")
           instructions (JLabel. "Drag left mouse: look | WASD: move | Shift / Ctrl: up / down | R: reset | Esc: stop")
           canvas (proxy [JPanel] []
                    (paintComponent [graphics]
                      (proxy-super paintComponent graphics)
                      (when-let [^BufferedImage image @picture]
                        (let [panel-width (.getWidth this)
                              panel-height (.getHeight this)
                              scale (min (/ (double panel-width) (.getWidth image))
                                         (/ (double panel-height) (.getHeight image)))
                              w (int (* scale (.getWidth image)))
                              h (int (* scale (.getHeight image)))]
                          (.drawImage ^Graphics graphics image
                                      (int (/ (- panel-width w) 2))
                                      (int (/ (- panel-height h) 2)) w h nil)))))
           change! (fn [next-state]
                     (when (not= next-state @state)
                       (reset! state next-state)
                       (swap! revision inc)
                       (reset! changed-at (System/nanoTime))))
           clear-input! #(do (reset! held #{}) (reset! drag nil))
           renderer (worker/start!
                     {:render-fn render-fn
                      :on-image
                      (fn [{:keys [preview? valid? camera]} image]
                        (SwingUtilities/invokeLater
                         (fn []
                           (when (and (not @closed?) (or preview? (valid?)))
                             (reset! picture image)
                             (.repaint canvas)
                             (.setText status
                               (let [[x y z] (mapv double (:origin camera))]
                                 (format "%s %d × %d | Camera (%.2f, %.2f, %.2f)"
                                         (if preview? "Preview" "Full resolution")
                                         (.getWidth ^BufferedImage image)
                                         (.getHeight ^BufferedImage image) x y z)))))))
                      :on-error
                      (fn [_ error]
                        (SwingUtilities/invokeLater
                         #(when-not @closed?
                            (.setText status (str "Rendering failed: " (.getMessage ^Exception error))))))})
           dispatcher
           (reify KeyEventDispatcher
             (dispatchKeyEvent [_ event]
               (if (and (.isFocused frame) (not @closed?))
                 (let [code (.getKeyCode event)
                       pressed? (= KeyEvent/KEY_PRESSED (.getID event))
                       released? (= KeyEvent/KEY_RELEASED (.getID event))]
                   (cond
                     (and (key-actions code) (or pressed? released?))
                     (do (swap! held (if pressed? conj disj) (key-actions code)) true)

                     (and pressed? (= code KeyEvent/VK_ESCAPE))
                     (do (clear-input!) true)

                     (and pressed? (= code KeyEvent/VK_R))
                     (do (clear-input!) (change! initial-state) true)

                     :else false))
                 false)))
           focus-manager (KeyboardFocusManager/getCurrentKeyboardFocusManager)
           timer (Timer.
                  16
                  (reify ActionListener
                    (actionPerformed [_ _]
                      (let [now (System/nanoTime)
                            dt (min 0.1 (/ (- now @last-tick) 1.0e9))]
                        (reset! last-tick now)
                        (change! (controls/move @state @held dt))
                        (let [preview? (< (- now @changed-at) 250000000)
                              signature [@revision preview?]]
                          (when (not= signature @last-request)
                            (reset! last-request signature)
                            (let [version @revision
                                  ;; Cap moving previews at 240 pixels on their
                                  ;; longest side, preserving the output aspect.
                                  scale (if preview? (min 1.0 (/ 240.0 (max width height))) 1.0)]
                              ((:request! renderer)
                               {:width (max 1 (int (Math/round (* width scale))))
                                :height (max 1 (int (Math/round (* height scale))))
                                :camera (controls/as-camera @state)
                                :preview? preview?
                                :valid? #(= version @revision)}))))))))]
       (.setBackground canvas (Color. 16 26 43))
       (.setPreferredSize canvas (Dimension. 800 450))
       (.setBorder instructions (BorderFactory/createEmptyBorder 10 12 10 12))
       (.setBorder status (BorderFactory/createEmptyBorder 10 12 10 12))
       (.addMouseListener canvas
         (proxy [MouseAdapter] []
           (mousePressed [event]
             (when (SwingUtilities/isLeftMouseButton event)
               (reset! drag [(.getX event) (.getY event)])))
           (mouseReleased [_] (reset! drag nil))))
       (.addMouseMotionListener canvas
         (proxy [MouseMotionAdapter] []
           (mouseDragged [event]
             (when-let [[x y] @drag]
               (let [next-point [(.getX event) (.getY event)]]
                 (reset! drag next-point)
                 (change! (controls/look @state (- (first next-point) x) (- (second next-point) y))))))))
       (.addKeyEventDispatcher focus-manager dispatcher)
       (.addWindowListener frame
         (proxy [WindowAdapter] []
           (windowDeactivated [_] (clear-input!))
           (windowClosed [_]
             (reset! closed? true)
             (clear-input!)
             (.stop timer)
             (.removeKeyEventDispatcher focus-manager dispatcher)
             ((:close! renderer)))))
       (.add (.getContentPane frame) instructions BorderLayout/NORTH)
       (.add (.getContentPane frame) canvas BorderLayout/CENTER)
       (.add (.getContentPane frame) status BorderLayout/SOUTH)
       (.setDefaultCloseOperation frame WindowConstants/DISPOSE_ON_CLOSE)
       (.setSize frame 900 600)
       (.setLocationRelativeTo frame nil)
       (.setVisible frame true)
       (.start timer))))))
