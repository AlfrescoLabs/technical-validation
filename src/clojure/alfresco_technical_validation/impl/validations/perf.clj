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

(ns alfresco-technical-validation.impl.validations.perf
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.cypher        :as cy]
            [alfresco-technical-validation.impl.util :refer :all]))

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- perf01
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND EXISTS(m.name)
                           AND m.name IN [
                                           'org.alfresco.repo.policy.Behaviour'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-binary-validation "PERF01" res true "The technology does not contain any behaviours." #(when (empty? %) true))))

(defn- perf02
  [indexes]
  (let [source                 (:source               indexes)
        source-index           (:source-index         indexes)
        content-index          (:source-content-index source-index)
        property-count         (count (filter #(= :properties (:regex-id %)) content-index))
        indexed-property-count (count (filter #(= :perf02     (:regex-id %)) content-index))
        indexed-property-ratio (if (zero? property-count)
                                 0.0
                                 (float (* 100 (/ indexed-property-count property-count))))]
    (declare-result "PERF02"
                    (< indexed-property-ratio 50)
                    (if (zero? property-count)
                      "The technology does not define any content model properties."
                      (str indexed-property-count " of " property-count " content model properties are indexed.")))))

(defn- perf03
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :perf03
                                "PERF03"
                                "Stored content model properties"
                                false
                                "The technology does not store any content model properties in the search engine indexes.")))

(def tests
  "List of PERF validation functions."
  [perf01 perf02 perf03])

(def missing-tests
  "List of PERF tests that aren't yet implemented."
  [])
