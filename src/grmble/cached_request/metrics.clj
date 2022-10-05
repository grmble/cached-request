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

(defmacro with-timer [timer & body]
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

(defonce ^Timer uncached-timer (.timer registry (metric-name "uncached")))
(defonce ^Timer cached-timer (.timer registry (metric-name "cached")))



(comment
  (set! *warn-on-reflection* true)

  (jmx-reporter)

  (with-timer test-timer (println "asdf")))
