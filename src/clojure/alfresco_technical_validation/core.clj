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

(ns alfresco-technical-validation.core
  (:require [clojure.string                                      :as s]
            [clojure.tools.logging                               :as log]
            [clojure.java.io                                     :as io]
            [clojurewerkz.neocons.rest.cypher                    :as cy]
            [alfresco-technical-validation.impl.indexer          :as idx]
            [alfresco-technical-validation.impl.loc-counter      :as loc]
            [alfresco-technical-validation.impl.validations.api  :as val-api]
            [alfresco-technical-validation.impl.validations.cm   :as val-cm]
            [alfresco-technical-validation.impl.validations.dev  :as val-dev]
            [alfresco-technical-validation.impl.validations.com  :as val-com]
            [alfresco-technical-validation.impl.validations.perf :as val-perf]
            [alfresco-technical-validation.impl.validations.sec  :as val-sec]
            [alfresco-technical-validation.impl.validations.stb  :as val-stb]
            [alfresco-technical-validation.impl.validations.ux   :as val-ux]
            [alfresco-technical-validation.impl.validations.up   :as val-up]
            [alfresco-technical-validation.impl.validations.lgl  :as val-lgl]
            [bookmark-writer.core                                :as bw]
            [multigrep.core                                      :as mg]))

(def ^:private report-template (io/resource "alfresco-technical-validation-template.docx"))

(def ^:private validation-fns (concat val-api/tests
                                      val-cm/tests
                                      val-dev/tests
                                      val-com/tests
                                      val-perf/tests
                                      val-sec/tests
                                      val-stb/tests
                                      val-ux/tests
                                      val-up/tests
                                      val-lgl/tests))

(def ^:private missing-tests (concat val-api/missing-tests
                                     val-cm/missing-tests
                                     val-dev/missing-tests
                                     val-com/missing-tests
                                     val-perf/missing-tests
                                     val-sec/missing-tests
                                     val-stb/missing-tests
                                     val-ux/missing-tests
                                     val-up/missing-tests
                                     val-lgl/missing-tests))

(defn- result-to-bookmark
  [result]
  (let [criteria-id (:criteria-id result)
        passes      (:passes      result)
        message     (:message     result)]
    (if (nil? passes)
      { (str criteria-id "_Evidence")    message
        (str criteria-id "_DoesNotMeet") "#### UNKNOWN: Manual followup required. ####"
        (str criteria-id "_Meets")       "" }
      (if passes
        { (str criteria-id "_Evidence")    message
          (str criteria-id "_DoesNotMeet") ""
          (str criteria-id "_Remedy")      "" }
        { (str criteria-id "_Evidence")    message
          (str criteria-id "_Meets")       ""
          (str criteria-id "_NoRemedy")    "" } ))))

(defn- missing-test-to-bookmark
  [criteria-id]
  { (str criteria-id "_DoesNotMeet") "#### UNKNOWN: This criterion isn't checked by the tool yet. ####"
    (str criteria-id "_Meets")       "" } )

(defn- build-loc-bookmarks
  [locs type]
  (let [[f l] (get locs type)]
    (if (and (or (nil? f) (s/blank? f))
             (or (nil? l) (s/blank? l)))
      nil
      (let [files (if (or (nil? f) (s/blank? f)) "0" f)
            loc   (if (or (nil? l) (s/blank? l)) "0" l)]
        { (str type "Files") files
          (str type "LOC")   loc }))))

(defn- count-file-type-from-source
  [bookmark-name file-type source-index]
  { bookmark-name (str (count (file-type (:source-files-by-type source-index)))) })

(defn- count-content-models
  [source-index]
  (count-file-type-from-source "ContentModels" :content-model source-index))

(defn- count-spring-app-contexts
  [source-index]
  (count-file-type-from-source "SpringAppContexts" :spring-app-context source-index))

(defn- count-web-scripts
  [source-index]
  (count-file-type-from-source "WebScripts" :web-script-descriptor source-index))

(defn- count-file-type-from-binary
  [binary-index bookmark-name type]
  (let [con   binary-index
        query (str "
                     START n=NODE(*),
                           m=NODE:node_auto_index('name:*')
                     WHERE m.name = '" type "'
                       AND m.name <> n.name
                     MATCH SHORTESTPATH((n)-[*]->(m))
                    RETURN COUNT(n.name) AS TypeCount;
                   ")  ; ####TODO: UPDATE THIS TO ONLY CONSIDER ":implements" AND ":extends" DEPENDENCIES!!
        res   (cy/tquery con query)]
    { bookmark-name (str (get (first res) "TypeCount")) } ))

(defn- count-actions
  [binary-index]
  (count-file-type-from-binary binary-index "Actions" "org.alfresco.service.cmr.action.Action"))

(defn- count-behaviours
  [binary-index]
  (count-file-type-from-binary binary-index "Behaviours" "org.alfresco.repo.policy.Behaviour"))  ;####TODO: this doesn't provide an accurate count, due to the way behaviours are implemented

(defn- count-quartz-jobs
  [binary-index]
  (let [con   binary-index
        query (str "
                     START n=NODE(*),
                           m=NODE:node_auto_index('name:*')
                     WHERE m.name IN ['org.quartz.Job', 'org.alfresco.util.CronTriggerBean']
                       AND m.name <> n.name
                     MATCH SHORTESTPATH((n)-[*]->(m))
                    RETURN COUNT(n.name) AS QuartzJobCount;
                   ")
        res   (cy/tquery con query)]
    { "QuartzJobs" (str (get (first res) "QuartzJobCount")) } ))

(defn- count-locs
  [indexes]
  (let [source       (:source       indexes)
        source-index (:source-index indexes)
        binary-index (:binary-index indexes)
        locs         (loc/count-locs source source-index)]
    (merge
      (count-content-models      source-index)
      (count-spring-app-contexts source-index)
      (count-web-scripts         source-index)
      (count-actions             binary-index)
      (count-behaviours          binary-index)
      (count-quartz-jobs         binary-index)
      (build-loc-bookmarks locs "java")
      (build-loc-bookmarks locs "javascript")
      (build-loc-bookmarks locs "freemarker"))))

(defn- detect-build-tools
  [files-by-type]
  (let [build-tools (s/join ","
                            (filter #(not (nil? %))
                                    (vector (if (not-empty (:ant       files-by-type)) (if (empty? (mg/grep #"antlib:org\.apache\.ivy\.ant" (:ant files-by-type)))
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
  [indexes]
  (let [source-index  (:source-index indexes)
        files-by-type (:source-files-by-type source-index)
        content-index (:source-content-index source-index)]
    (merge { "Date" (java.lang.String/format "%1$tF" (into-array Object [(java.util.Date.)])) }
           (detect-build-tools    files-by-type)
           (module-versions       content-index)
           (alfresco-min-versions content-index)
           (alfresco-max-versions content-index)
           (alfresco-editions     content-index))))

(defn index-extension
  "Indexes an extension, given its source, binaries, and with a Neo4J server running at neo4j-url."
  ([source binaries neo4j-url] (index-extension source binaries neo4j-url nil))
  ([source binaries neo4j-url status-fn]
   (assoc (idx/indexes neo4j-url binaries source status-fn) :binaries binaries :source source)))

(defn validate
  "Validates the given source and binaries."
  ([source binaries neo4j-url]           (validate source binaries neo4j-url nil))
  ([source binaries neo4j-url status-fn] (validate (index-extension source binaries neo4j-url status-fn) status-fn))
  ([indexes status-fn]
    (if status-fn (status-fn "\nValidating criteria... "))
    (map #(% indexes) validation-fns)))

(defn validate-and-write-report
  "Validates the given source and binaries, using the Neo4J server available at the given URL,
  writing the report to the specified Word document."
  ([source binaries neo4j-url report-filename]           (validate-and-write-report source binaries neo4j-url report-filename nil))
  ([source binaries neo4j-url report-filename status-fn] (validate-and-write-report (index-extension source binaries neo4j-url status-fn) report-filename status-fn))
  ([indexes report-filename status-fn]
   (let [loc-bookmarks              (count-locs       indexes)
         global-bookmarks           (global-bookmarks indexes)
         validation-results         (validate         indexes status-fn)
         results-as-bookmarks       (into {} (map result-to-bookmark validation-results))
         missing-tests-as-bookmarks (into {} (map missing-test-to-bookmark missing-tests))
         all-bookmarks              (merge loc-bookmarks global-bookmarks results-as-bookmarks missing-tests-as-bookmarks)]
     (if status-fn (status-fn "\nGenerating report... "))
     (bw/populate-bookmarks! (io/input-stream report-template) report-filename all-bookmarks)
     nil)))

(defn- java-ify-result
  "Converts a single validation result into something Java can digest.  Specifically it replaces keyword keys with strings."
  [result]
  (into {} (remove #(nil? (val %)))
    {
      "criteriaId" (:criteria-id result)
      "passes"     (:passes      result)
      "message"    (:message     result)
    }))

(defn validate-java
  "Validates the given source and binaries, returning a Java-friendly result."
  ([source binaries neo4j-url]           (validate-java source binaries neo4j-url nil))
  ([source binaries neo4j-url status-fn] (validate-java (index-extension source binaries neo4j-url status-fn) status-fn))
  ([indexes status-fn]
    (doall (map java-ify-result (validate indexes status-fn)))))

