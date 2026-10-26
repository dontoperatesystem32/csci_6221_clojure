(ns ray-marcher.progress
  "Console-only PNG export progress. No renderer or Swing dependencies."
  (:import [java.io Writer]))

(defn reporter
  "A live ASCII bar in a terminal; occasional lines when output is redirected.
  Capture the writer once. finish! clears the terminal line but never closes it."
  ([] (reporter *err* (some? (System/console))))
  ([^Writer writer interactive?]
   (let [last-step (atom -1)
         line-width (atom 0)
         finished? (atom false)
         write! (fn [text] (.write writer ^String text) (.flush writer))
         replace-line! (fn [text]
                         (write! (str "\r" text
                                      (apply str (repeat (max 0 (- @line-width (count text))) \space))
                                      (when (empty? text) "\r")))
                         (reset! line-width (count text)))]
     {:report! (fn [completed total]
                 (when-not @finished?
                   (let [percent (quot (* 100 completed) total)
                         step (if interactive? percent (quot percent 10))]
                     (when (> step @last-step)
                       (reset! last-step step)
                       (let [filled (quot (* 24 percent) 100)
                             text (format "Rendering [%s%s] %3d%% (%d/%d rows)"
                                          (apply str (repeat filled \#))
                                          (apply str (repeat (- 24 filled) \-))
                                          percent completed total)]
                         (if interactive? (replace-line! text) (write! (str text "\n"))))))))
      :writing! (fn []
                  (when-not @finished?
                    (if interactive?
                      (replace-line! "Writing PNG...")
                      (write! "Writing PNG...\n"))))
      :finish! (fn []
                 (when (compare-and-set! finished? false true)
                   (when interactive? (replace-line! ""))))})))
