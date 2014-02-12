;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; This work is licensed under the Creative Commons Attribution-ShareAlike 3.0
; Unported License. To view a copy of this license, visit
; http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to Creative
; Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
;

(ns alfresco-technical-validation.source-validation
  (:require [clojure.string                     :as s]
            [clojure.tools.logging              :as log]
            [clojure.java.io                    :as io]
            [alfresco-technical-validation.util :refer :all]
            ))

(def ^:private file-types-of-interest {
  :module-properties      #"module\.properties"
  :java                   #".*\.java"
  :javascript             #".*\.js"
  :freemarker             #".*\.ftl"
  :xml                    #".*\.xml"
  :web-script-descriptors #".*\.desc\.xml"
  :spring-app-context     #".*-context\.xml"
  :ant                    #"build\.xml"
  :maven                  #"pom\.xml"
  :gradle                 #"build\.gradle"
  :leiningen              #"project\.clj"
  :sbt                    #"build\.sbt"
  :make                   #"[mM]akefile"
  :pants                  #"BUILD"
  })

(defn- build-file-type-index
  [files file-type file-regex]
  { file-type (filter #(re-matches file-regex (.getName ^java.io.File %)) files) })

(defn- build-file-types-index
  [files]
  (into {} (map #(build-file-type-index files (key %) (val %)) file-types-of-interest)))

(defn- grep-file
  "Returns a sequence of {:line-number x :re-seq y} maps for all lines in file that match the given regex."
  [regex file]
  (with-open [reader (io/reader file)]
    (let [lines (line-seq reader)]
      (doall
        (map-indexed #(let [matches (re-seq regex %2)]
                        (if (not-empty matches)
                          { :line-number %1 :re-seq matches } ))
                     lines)))))

(defn- grep-files
  "Returns a sequence of [file-name, [{:line-number x :re-seq y}]] pairs that match the regex."
  [regex files]
  (filter #(empty? (second %))
          (map #(vector % (grep-file regex %))
               files)))

(defn- detect-build-tools
  [file-index]
  (let [build-tools (s/join ","
                            (filter #(not (nil? %))
                                    (vector (if (not-empty (:ant       file-index)) (if (empty? (grep-files #"antlib:org\.apache\.ivy\.ant" (:ant file-index)))
                                                                                      "Ant"
                                                                                      "Ivy"))
                                            (if (not-empty (:maven     file-index)) "Maven")
                                            (if (not-empty (:gradle    file-index)) "Gradle")
                                            (if (not-empty (:leiningen file-index)) "Leiningen")
                                            (if (not-empty (:sbt       file-index)) "SBT")
                                            (if (not-empty (:make      file-index)) "Make")
                                            (if (not-empty (:pants     file-index)) "Pants"))))]
    { "BuildTools" (if (empty? build-tools) "Unknown" build-tools) }))

(defn validate
  "Runs all source-based validations."
  [source]
  (let [files      (file-seq (io/file source))
        file-index (build-file-types-index files)]
    (merge
      (detect-build-tools file-index))))
  