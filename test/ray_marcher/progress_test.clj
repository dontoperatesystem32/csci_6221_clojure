(ns ray-marcher.progress-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [ray-marcher.progress :as progress]
            [ray-marcher.render :as render])
  (:import [java.io StringWriter]))

(deftest row-progress-preserves-image
  (let [events (atom [])
        expected (render/render-image 8 4)
        actual (render/render-image 8 4 {:on-progress #(swap! events conj [%1 %2])})]
    (is (= [[0 4] [1 4] [2 4] [3 4] [4 4]] @events))
    (is (= (vec (.getRGB expected 0 0 8 4 nil 0 8))
           (vec (.getRGB actual 0 0 8 4 nil 0 8))))))

(deftest cancellation-does-not-report-completion
  (let [events (atom [])
        stopped? (atom false)
        result (render/render-image 3 4
                 {:cancelled? #(deref stopped?)
                  :on-progress (fn [done total]
                                 (swap! events conj [done total])
                                 (when (= done 2) (reset! stopped? true)))})]
    (is (nil? result))
    (is (= [[0 4] [1 4] [2 4]] @events))))

(deftest redirected-output-is-readable-and-bounded
  (let [writer (StringWriter.)
        {:keys [report! writing! finish!]} (progress/reporter writer false)]
    (doseq [row (range 1001)] (report! row 1000))
    (writing!)
    (finish!)
    (let [output (str writer)
          lines (str/split-lines output)]
      (is (= 12 (count lines)))
      (is (str/includes? (first lines) "0% (0/1000 rows)"))
      (is (str/includes? (nth lines 10) "100% (1000/1000 rows)"))
      (is (= "Writing PNG..." (last lines)))
      (is (not (str/includes? output "\r"))))))

(deftest terminal-bar-clears-and-finishes-once
  (let [writer (StringWriter.)
        {:keys [report! writing! finish!]} (progress/reporter writer true)]
    (report! 0 4)
    (report! 2 4)
    (report! 4 4)
    (writing!)
    (finish!)
    (let [output (str writer)]
      (is (str/includes? output "50% (2/4 rows)"))
      (is (str/includes? output "100% (4/4 rows)"))
      (is (str/includes? output "Writing PNG..."))
      (is (str/ends-with? output "\r"))
      (is (not (str/includes? output "\n")))
      (finish!)
      (report! 4 4)
      (is (= output (str writer))))))
