;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; This work is licensed under the Creative Commons Attribution-ShareAlike 3.0
; Unported License. To view a copy of this license, visit
; http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to Creative
; Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
;

(ns alfresco-technical-validation.source-indexer
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [clojure.java.io       :as io]
            ))

(def ^:private file-types-of-interest {
  #"module\.properties"   :module-properties
  #".*\.java"             :java
  #".*\.js"               :javascript
  #".*\.ftl"              :freemarker
  #".*\.xml"              :xml
  #".*\.desc\.xml"        :web-script-descriptors
  #".*-context\.xml"      :spring-app-context
  #"build\.xml"           :ant
  #"pom\.xml"             :maven
  #"build\.gradle"        :gradle                 
  #"project\.clj"         :leiningen
  #"build\.sbt"           :sbt
  #"[mM]akefile"          :make
  })

(defn- build-file-type-index
  [files file-regex k]
  { k (filter #(re-matches file-regex (.getName ^java.io.File %)) files) })

(defn- build-file-types-index
  [files]
  (into {} (map #(build-file-type-index files (key %) (val %)) file-types-of-interest)))

(defn index-source
  "Indexes the source code in the given location."
  [source]
  (let [files      (file-seq (io/file source))
        file-index (build-file-types-index files)]
    (comment "####TODO: implement me!")
    ))
