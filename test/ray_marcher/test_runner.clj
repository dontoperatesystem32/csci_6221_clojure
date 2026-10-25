(ns ray-marcher.test-runner
  (:require [clojure.test :as test]
            [ray-marcher.render-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'ray-marcher.render-test)]
    (when (pos? (+ fail error)) (System/exit 1))))
