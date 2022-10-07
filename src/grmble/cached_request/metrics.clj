(ns grmble.cached-request.metrics
  (:import
   [com.codahale.metrics MetricRegistry Timer ConsoleReporter]
   [com.codahale.metrics.jmx JmxReporter]
   [java.util.concurrent TimeUnit])
  (:require
   [promesa.core :as p]))

(defonce ^MetricRegistry registry (MetricRegistry.))

(defmacro metric-name
  [& names]
  `(apply str ~*ns* "."
          (interpose "." [~@names])))


(defmacro timed-promise
  "Times the execution of `body` using `timer`.

   Body is executed using promessa/do, so it can be a promise
   or plain code.   
   
   If you don't want a promise as result, just use `with-open` -
   `.close` is an alias to `.stop`.
   "
  [timer & body]
  `(let [^Timer timer# ~timer
         ctx# (.time timer#)]
     (p/finally (p/do! ~@body)
                (constantly (.stop ctx#)))))


(defn console-reporter
  "Create (and start) a console reporter.
   
   `(.stop *1) to stop it again."
  ^ConsoleReporter []
  (doto (.. (ConsoleReporter/forRegistry registry)
            (convertRatesTo TimeUnit/SECONDS)
            (convertDurationsTo TimeUnit/MILLISECONDS)
            (build))
    (.start 1 TimeUnit/MINUTES)))

(defn jmx-reporter
  "Create (and start) a JmxReporter.
  
  `(.stop reporter)` to stop it."
  ^JmxReporter []
  (doto (.. (JmxReporter/forRegistry registry)
            (build))
    (.start)))
