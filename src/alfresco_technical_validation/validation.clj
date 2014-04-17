;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;     http://www.apache.org/licenses/LICENSE-2.0
; 
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
; 
; This file is part of an unsupported extension to Alfresco.
;

(ns alfresco-technical-validation.validation
  (:require [clojure.string                                  :as s]
            [clojure.tools.logging                           :as log]
            [clojure.java.io                                 :as io]
            [alfresco-technical-validation.indexer           :as idx]
            [alfresco-technical-validation.binary-validation :as bin]
            [alfresco-technical-validation.source-validation :as src]
            [alfresco-technical-validation.loc-counter       :as loc]
            [bookmark-writer.core                            :as bw]
            [multigrep.core                                  :as mg]))

(def ^:private report-template (io/resource "alfresco-technical-validation-template.docx"))

(defn- result-to-bookmark
  [result]
  (let [criteria-id (:criteria-id result)
        passes      (:passes      result)
        message     (:message     result)]
    (if (nil? passes)
      { (str criteria-id "_Evidence") message }
      (if passes
        { (str criteria-id "_Evidence")    message
          (str criteria-id "_DoesNotMeet") ""
          (str criteria-id "_Remedy")      "" }
        { (str criteria-id "_Evidence")    message
          (str criteria-id "_Meets")       ""
          (str criteria-id "_NoRemedy")    "" }))))

(defn- build-loc-bookmarks
  [locs type]
  (let [[f l] (get locs type)
        files (if (s/blank? f) "0" f)
        loc   (if (s/blank? l) "0" l)]
    { (str type "Files") files
      (str type "LOC")   loc }))

(defn- count-locs
  [source source-index]
  (let [locs (loc/count-locs source source-index)]
    (merge
      (build-loc-bookmarks locs "java")
      (build-loc-bookmarks locs "javascript")
      (build-loc-bookmarks locs "freemarker"))))

(defn- detect-build-tools
  [files-by-type]
  (let [build-tools (s/join ","
                            (filter #(not (nil? %))
                                    (vector (if (not-empty (:ant       files-by-type)) (if (empty? (mg/grep-files #"antlib:org\.apache\.ivy\.ant" (:ant files-by-type)))
                                                                                         "Ant"
                                                                                         "Ivy"))
                                            (if (not-empty (:maven     files-by-type)) "Maven")
                                            (if (not-empty (:gradle    files-by-type)) "Gradle")
                                            (if (not-empty (:leiningen files-by-type)) "Leiningen")
                                            (if (not-empty (:sbt       files-by-type)) "SBT")
                                            (if (not-empty (:make      files-by-type)) "Make")
                                            (if (not-empty (:pants     files-by-type)) "Pants"))))]
    { "BuildTools" (if (empty? build-tools) "Unknown" build-tools) }))

(defn- module-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :module-version (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "ModuleVersion" (if (empty? message) "Not specified" message) }))

(defn- alfresco-min-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :up03-min (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "AlfrescoVersionMin" (if (empty? message) "Not specified" message) }))

(defn- alfresco-max-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :up03-max (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "AlfrescoVersionMax" (if (empty? message) "Not specified" message) }))

(defn- alfresco-editions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :up04 (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "AlfrescoEditions" (if (empty? message) "Not specified" message) }))

(defn- global-bookmarks
  [source-index]
  (let [files-by-type (:source-files-by-type source-index)
        content-index (:source-content-index source-index)]
    (merge { "Date" (java.lang.String/format "%1$tF" (into-array Object [(java.util.Date.)])) }
           (detect-build-tools    files-by-type)
           (module-versions       content-index)
           (alfresco-min-versions content-index)
           (alfresco-max-versions content-index)
           (alfresco-editions     content-index))))

(defn validate
  "Validates the given source and binaries, using the Neo4J server available at the given URL,
  writing the report to the specified Word document."
  [source binaries neo4j-url report-filename]
  (let [source-index              (idx/index neo4j-url binaries source)
        loc-bookmarks             (count-locs source source-index)
        global-bookmarks          (global-bookmarks source-index)
        source-validation-results (src/validate source source-index)
        binary-validation-results (bin/validate)
        validation-results        (concat source-validation-results
                                          binary-validation-results)
        results-as-bookmarks      (into {} (map result-to-bookmark validation-results))
        all-bookmarks             (merge loc-bookmarks global-bookmarks results-as-bookmarks)]
    (bw/populate-bookmarks! (io/input-stream report-template) report-filename all-bookmarks)))
