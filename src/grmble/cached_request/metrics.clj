(ns grmble.cached-request.metrics
  (:import
   [com.codahale.metrics MetricRegistry Timer ConsoleReporter]
   [com.codahale.metrics.jmx JmxReporter]
   [java.util.concurrent TimeUnit]))

(defonce ^MetricRegistry registry (MetricRegistry.))

(defmacro metric-name
  [& names]
  `(apply str ~*ns* "."
          (interpose "." [~@names])))

(defmacro with-timer
  "Times the execution of `body` using `timer`.
   
   ```clojure
   (with-timer example-timer (println :foo))
   ```
   
   Note that calling `.stop` on the context is not compulsory,
   so it is possible to start multiple contexts without stopping them.
   "
  [timer & body]
  `(let [^Timer timer# ~timer
         ctx# (.time timer#)]
     (try
       ~@body
       (finally
         (.stop ctx#)))))

(defn console-reporter []
  (.. (ConsoleReporter/forRegistry registry)
      (convertRatesTo TimeUnit/SECONDS)
      (convertDurationsTo TimeUnit/MILLISECONDS)
      (build)
      (start 1 TimeUnit/MINUTES)))

(defn jmx-reporter []
  (.. (JmxReporter/forRegistry registry)
      (build)
      (start)))
