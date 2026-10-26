(ns ray-marcher.worker
  "One background renderer, one replaceable pending view. No Swing dependencies."
  (:import [java.util.concurrent ArrayBlockingQueue Executors ExecutorService ThreadFactory]))

(defn start!
  "render-fn receives width, height, and options with :camera and :cancelled?.
  Callbacks run on the worker thread; callers must schedule any UI updates.
  Requests contain :width, :height, :camera, :preview?, and a :valid? predicate.
  Previews may finish during movement. Obsolete full renders are cancelled."
  [{:keys [render-fn on-image on-error]}]
  (let [queue (ArrayBlockingQueue. 1)
        closed? (atom false)
        executor (Executors/newSingleThreadExecutor
                  (reify ThreadFactory
                    (newThread [_ runnable]
                      (doto (Thread. runnable "ray-marcher-render") (.setDaemon true)))))]
    (.execute executor
      ^Runnable
      (fn []
        (try
          (while (not @closed?)
            (let [{:keys [width height camera preview? valid?] :as request} (.take queue)
                  cancelled? #(or @closed? (.isInterrupted (Thread/currentThread))
                                  (and (not preview?) (not (valid?))))]
              (try
                (when-not (cancelled?)
                  (when-let [image (render-fn width height {:camera camera :cancelled? cancelled?})]
                    (when-not (cancelled?) (on-image request image))))
                (catch Exception e
                  (when-not (cancelled?) (on-error request e))))))
          (catch InterruptedException _ nil))))
    {:request! (fn [request]
                 (when-not @closed?
                   ;; One UI producer, one worker consumer. Replacing the pending
                   ;; request bounds memory even when input outpaces rendering.
                   (when-not (.offer queue request)
                     (.poll queue)
                     (.offer queue request))))
     :close! (fn []
               (reset! closed? true)
               (.clear queue)
               (.shutdownNow ^ExecutorService executor))}))
