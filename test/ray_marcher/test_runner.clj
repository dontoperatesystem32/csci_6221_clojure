(ns ray-marcher.test-runner
  (:require [clojure.test :as test]
            [ray-marcher.render-test]
            [ray-marcher.controls-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'ray-marcher.render-test 'ray-marcher.controls-test)]
    (when (pos? (+ fail error)) (System/exit 1))))
