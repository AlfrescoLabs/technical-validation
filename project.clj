;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; This work is licensed under the Creative Commons Attribution-ShareAlike 3.0
; Unported License. To view a copy of this license, visit
; http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to Creative
; Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
;

(defproject alfresco-technical-validation "0.1.0-SNAPSHOT"
  :min-lein-version "2.0.0"
  :description "Performs technical validation of an Alfresco extension."
  :url "https://github.com/pmonks/depends"
  :license {:name "Creative Commons Attribution-ShareAlike 3.0 Unported License."
            :url "http://creativecommons.org/licenses/by-sa/3.0/"}
  :javac-target "1.7"
  :dependencies [
                  [org.clojure/clojure             "1.5.1"]
                  [org.clojure/tools.cli           "0.3.1"]
                  [org.clojure/tools.trace         "0.7.6"]
                  [org.clojure/tools.logging       "0.2.6"]
                  [clojurewerkz/neocons            "2.0.1"]
                  [ch.qos.logback/logback-classic  "1.1.1"]
                  [io.aviso/pretty                 "0.1.8"]
                  [depends                         "0.1.0-SNAPSHOT"]
                  [bookmark-writer                 "0.1.0-SNAPSHOT"]
                ]
  :profiles {:dev {:dependencies [
                                   [midje "1.6.2"]
                                   [clj-ns-browser "1.3.1"]
                                 ]}
             :uberjar {:aot :all}}
  :resource-paths ["config"]
  :jvm-opts ^:replace []  ; Stop Leiningen from turning off JVM optimisations - makes it slower to start but ensures code runs as fast as possible
  :main alfresco-technical-validation.core)
