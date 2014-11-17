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

(def missing-tests (concat val-api/missing-tests
                           val-cm/missing-tests
                           val-dev/missing-tests
                           val-com/missing-tests
                           val-perf/missing-tests
                           val-sec/missing-tests
                           val-stb/missing-tests
                           val-ux/missing-tests
                           val-up/missing-tests
                           val-lgl/missing-tests))

(defn index-extension
  "Indexes an extension, given its source, binaries, and with a Neo4J server running at neo4j-url."
  ([source binaries neo4j-url] (index-extension source binaries neo4j-url nil))
  ([source binaries neo4j-url status-fn]
   (doall (assoc (idx/indexes neo4j-url binaries source status-fn) :binaries binaries :source source))))

(defn validate
  "Validates the given source and binaries."
  ([source binaries neo4j-url]           (validate source binaries neo4j-url nil))
  ([source binaries neo4j-url status-fn] (validate (index-extension source binaries neo4j-url status-fn) status-fn))
  ([indexes status-fn]
    (if status-fn (status-fn "\nValidating criteria... "))
    (doall (map #(% indexes) validation-fns))))

(defn- java-ify-result
  "Converts a single validation result into something Java can digest.  Specifically it replaces keyword keys with string keys."
  [result]
  (into {}
        (remove #(nil? (val %))
                {
                  "criteriaId" (:criteria-id result)
                  "checked"    true
                  "passes"     (:passes      result)
                  "message"    (:message     result)
                })))

(defn- java-ify-missing-results
  []
  (map #(hash-map "criteriaId" %, "checked" false) missing-tests))

(defn validate-java
  "Validates the given source and binaries, returning a Java-friendly result."
  ([source binaries neo4j-url]           (validate-java source binaries neo4j-url nil))
  ([source binaries neo4j-url status-fn] (validate-java (index-extension source binaries neo4j-url status-fn) status-fn))
  ([indexes status-fn]
    (doall (sort-by #(get % "criteriaId") (concat (map java-ify-result (validate indexes status-fn)) (java-ify-missing-results))))))
