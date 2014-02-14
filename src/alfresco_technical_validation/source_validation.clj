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
            [clojure.set                        :as set]
            [alfresco-technical-validation.util :refer :all]
            ))

(def ^:private file-types
  "The file types we're interested in, with symbolic names and filename regex to identify them."
  {
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
  (into {} (map #(build-file-type-index files (key %) (val %)) file-types)))

(def ^:private content-regexes-by-file-type
  "Regexes we want to run over the files, by file-type."
  {
    :module-properties {
                         :module-id      #"module\.id=(.*)\z"
                         :module-version #"module\.version=(.*)\z"
                         :repo-min       #"module\.repo\.version\.min=(.*)\z"
                         :repo-max       #"module\.repo\.version\.max=(.*)\z"
                         :alf-edition    #"module\.edition=(.*)\z"
                       }
    :ant               { :ivy #"antlib:org\.apache\.ivy\.ant" }
    :java              {
                         :synchronized #"\ssynchronized\s"
                       }
  })

(defn- build-content-index-for-file-type
  [file-type file-index]
  (let [relevant-files   (file-type file-index)
        relevant-regexes (vals (file-type content-regexes-by-file-type))
        regex-id-lookup  (set/map-invert (file-type content-regexes-by-file-type))
        raw-grep-result  (multi-grep-files relevant-regexes relevant-files)]
    (flatten (map #(assoc % :regex-id (get regex-id-lookup (:regex %))) raw-grep-result))))

(defn- build-content-index
  [file-index]
  (flatten (map #(build-content-index-for-file-type % file-index) (keys content-regexes-by-file-type))))

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

(defn- module-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :module-version (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "ModuleVersion" (if (empty? message) "Not specified" message) }))

(defn- alfresco-min-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :repo-min (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "AlfrescoVersionMin" (if (empty? message) "Not specified" message) }))

(defn- alfresco-max-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :repo-max (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "AlfrescoVersionMax" (if (empty? message) "Not specified" message) }))

(defn- stb08-stb09-use-of-synchronized
  [content-index]
  (let [uses-of-synchronized (filter #(= :synchronized (:regex-id %)) content-index)
        message              (str "Uses of synchronized:\n" (s/join "\n" (map #(str (:file %) " line " (:line-number %)) uses-of-synchronized)))]
    (merge
      (build-bookmark-map "STB08"
                          (empty? uses-of-synchronized)
                          (str message "\n#### Manual followup required to check these uses of synchronized. ####")
                          "The technology does not synchronize.")
      (build-bookmark-map "STB09"
                          (empty? uses-of-synchronized)
                          (str message "\n#### Manual followup required to check these uses of synchronized. ####")
                          "The technology does not synchronize."))))

(defn validate
  "Runs all source-based validations."
  [source]
  (let [files         (file-seq (io/file source))
        file-index    (build-file-types-index files)
        content-index (build-content-index file-index)]
    (merge
      (detect-build-tools              file-index)
      (module-versions                 content-index)
      (alfresco-min-versions           content-index)
      (alfresco-max-versions           content-index)
      (stb08-stb09-use-of-synchronized content-index)
    )))
  