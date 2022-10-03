(defproject grmble/cached-request "0.1.0-SNAPSHOT"
  :description "cache expensive computations like web requests"
  :url "https://github.com/grmble/cached-request"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [metosin/malli "0.8.9"]
                 [metosin/jsonista "0.3.6"]
                 [org.ehcache/ehcache "3.10.0"
                  ;; leiningen does not want dependencies from http only repos
                  :exclusions [org.glassfish.jaxb/jaxb-runtime]]]
  :repl-options {:init-ns grmble.cached-request})
