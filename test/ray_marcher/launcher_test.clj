(ns ray-marcher.launcher-test
  (:require [clojure.test :refer [deftest is]]
            [ray-marcher.application :as application]
            [ray-marcher.configuration :as configuration]
            [ray-marcher.core :as core]
            [ray-marcher.launcher-form :as form]
            [ray-marcher.render :as render]
            [ray-marcher.scene :as scene]
            [ray-marcher.scene-file :as scene-file])
  (:import [javax.swing SwingUtilities]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn on-edt [f]
  (let [result (promise)]
    (SwingUtilities/invokeAndWait (bound-fn [] (deliver result (try {:value (f)} (catch Throwable e {:error e})))))
    (if-let [error (:error @result)] (throw error) (:value @result))))

(defn pixels [image]
  (vec (.getRGB image 0 0 (.getWidth image) (.getHeight image) nil 0 (.getWidth image))))

(deftest scene-defaults-and-edited-configuration
  (let [definition (scene-file/read-definition "scenes/ao-demo.clj")
        values (configuration/form-values definition)
        edited (-> values (assoc :width 21 :height 13 :threads 3)
                   (assoc-in [:lighting :ao :mode] :only)
                   (assoc-in [:union :mode] :polynomial)
                   (assoc-in [:union :width] 0.5))
        prepared (configuration/prepare-form definition edited)]
    (is (= :normal (get-in values [:lighting :ao :mode])))
    (is (= 1.5 (get-in values [:lighting :ao :radius])))
    (is (= [0 5 -7] (get-in values [:camera :position])))
    (is (= :only (get-in prepared [:render-options :lighting :ao :mode])))
    (is (= [0 5 -7] (get-in prepared [:camera-state :position])))
    (is (fn? (get-in prepared [:render-options :scene-distance]))))
  (let [definition (scene-file/read-definition "scenes/mandelbulb.clj")
        opts (configuration/form-values definition)
        prepared (configuration/prepare-form definition (assoc-in opts [:union :mode] :exponential))]
    (is (= (:scene-distance definition) (get-in prepared [:render-options :scene-distance])))
    (is (= 0.008 (get-in opts [:lighting :normal-step]))))
  (let [definition (configuration/default-definition)
        values (assoc (configuration/form-values definition) :shading :flat)]
    (is (= render/silhouette-color (get-in (configuration/prepare-form definition values) [:render-options :color-fn])))))

(deftest configuration-rejects-invalid-edits
  (let [definition (configuration/default-definition)
        defaults (configuration/form-values definition)]
    (doseq [[path value] [[[:threads] 0] [[:width] 0] [[:height] 1.2]
                          [[:lighting :ambient] Double/NaN] [[:lighting :toward-light] [0 0 0]]
                          [[:lighting :base-color] -1] [[:lighting :diffuse-strength] -1]
                          [[:camera :look-at] [0 0 0]] [[:settings :epsilon] 0]
                          [[:lighting :ao :samples] 65] [[:union :width] -1]]]
      (is (thrown? clojure.lang.ExceptionInfo
                   (configuration/prepare-form definition (assoc-in defaults path value)))))))

(deftest swing-form-values-and-dependent-controls
  (on-edt
    (fn []
      (let [definition (scene-file/read-definition "scenes/ao-demo.clj")
            defaults (configuration/form-values definition)
            menu-form (form/create-form!)
            c #(get-in menu-form [:fields % :component])]
        ((:fill! menu-form) defaults true)
        (is (= 800 (.getValue (c [:width]))))
        (is (= 64 (.getMaximum (.getModel (c [:lighting :ao :samples])))))
        (is (= 1.5 (get-in ((:read-values menu-form)) [:lighting :ao :radius])))
        (is (.isEnabled (c [:lighting :ao :samples])))
        (is (not (.isEnabled (c [:union :width]))))
        (.setSelectedIndex (c [:union :mode]) 1)
        (is (.isEnabled (c [:union :width])))
        (.setSelectedIndex (c [:shading]) 3)
        (is (not (.isEnabled (c [:lighting :ao :mode]))))
        (is (not (.isEnabled (c [:lighting :ao :radius]))))
        ((:fill! menu-form) defaults false)
        (is (not (.isEnabled (c [:union :mode]))))
        (is (not (.isEnabled (c [:union :width]))))
        (is (map? (configuration/prepare-form definition ((:read-values menu-form)))))
        (.setSelectedIndex (c [:lighting :shadows :mode]) 1)
        (is (.isEnabled (c [:lighting :shadows :max-steps])))
        (.setSelectedIndex (c [:lighting :ao :mode]) 2)
        (is (not (.isEnabled (c [:lighting :shadows :mode]))))
        (is (not (.isEnabled (c [:lighting :shadows :bias]))))
        (.setText (c [:camera :position]) "1, 2")
        (is (thrown? clojure.lang.ExceptionInfo ((:read-values menu-form))))))))

(deftest arbitrary-thread-counts-preserve-pixels-and-progress
  (doseq [[width height threads] [[13 11 3] [5 3 8] [17 13 2]]]
    (let [reports (atom [])
          info {:scene-distance (scene/sphere [10 0 0] 2) :color-fn render/silhouette-color}
          expected (render/render-image width height info)
          actual (render/render-image width height (assoc info :threads threads
                                                         :on-progress #(swap! reports conj [%1 %2])))]
      (is (= (pixels expected) (pixels actual)))
      (is (= (mapv #(vector % height) (range (inc height))) @reports))))
  (let [cancel? (atom false)
        picture (render/render-image 32 60 {:threads 3 :cancelled? #(deref cancel?)
                                             :on-progress (fn [done _] (when (pos? done) (reset! cancel? true)))})]
    (is (nil? picture))))

(deftest export-without-process-exit-and-cancellation
  (let [file (.toFile (Files/createTempFile "launcher-export-" ".png" (make-array FileAttribute 0)))
        values (assoc (configuration/form-values (configuration/default-definition)) :width 17 :height 11 :threads 3)
        prepared (configuration/prepare-form (configuration/default-definition) values)]
    (try
      (is (= file (application/export! values prepared (.getPath file) {})))
      (let [original (vec (Files/readAllBytes (.toPath file)))]
        (is (nil? (application/export! values prepared (.getPath file) {:cancelled? (constantly true)})))
        (is (= original (vec (Files/readAllBytes (.toPath file))))))
      (finally (.delete file)))))

(deftest cli-entry-options
  (is (:launcher? (core/parse-options ["--launcher"])))
  (is (:viewer? (core/parse-options ["--viewer"])))
  (is (= 3 (:threads (core/parse-options ["--viewer" "--threads" "3"]))))
  (is (map? (configuration/prepare-cli (core/parse-options ["--scene" "scenes/ao-demo.clj" "--ao" "only"])))))
