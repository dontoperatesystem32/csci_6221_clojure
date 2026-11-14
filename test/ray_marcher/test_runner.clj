(ns ray-marcher.test-runner
  (:require [clojure.test :as test]
            [ray-marcher.render-test]
            [ray-marcher.controls-test]
            [ray-marcher.lighting-test]
            [ray-marcher.scene-test]
            [ray-marcher.fractals-test]
            [ray-marcher.progress-test]
            [ray-marcher.blend-test]
            [ray-marcher.ao-test]
            [ray-marcher.launcher-test]
            [ray-marcher.shadows-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'ray-marcher.render-test 'ray-marcher.controls-test
                                          'ray-marcher.lighting-test 'ray-marcher.scene-test
                                          'ray-marcher.fractals-test 'ray-marcher.progress-test
                                          'ray-marcher.blend-test 'ray-marcher.ao-test 'ray-marcher.launcher-test 'ray-marcher.shadows-test)]
    (shutdown-agents)
    (when (pos? (+ fail error)) (System/exit 1))))
