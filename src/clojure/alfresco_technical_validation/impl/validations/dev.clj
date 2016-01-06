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

(ns alfresco-technical-validation.impl.validations.dev
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.cypher        :as cy]
            [alfresco-technical-validation.impl.util :refer :all]))

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- dev02
  [indexes]
  (let [source          (:source       indexes)
        source-index    (:source-index indexes)
        content-index   (:source-content-index source-index)
        webscript-count (count (:web-script-descriptor (:source-files-by-type source-index)))
        con             (:binary-index indexes)
        res             (cy/tquery con
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
                                                       'org.springframework.extensions.webscripts.WebScript',
                                                       'org.springframework.extensions.webscripts.AbstractWebScript',
                                                       'org.springframework.extensions.webscripts.DeclarativeWebScript',
                                                       'org.springframework.extensions.webscripts.atom.AtomWebScript'
                                                     ]
                                    RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                                     ORDER BY n.name
                                   ")
        java-webscript-count (count res)
        java-webscript-ratio (if (zero? webscript-count)
                               0.0
                               (float (* 100 (/ java-webscript-count webscript-count))))]
    (declare-result "DEV02"
                    (< java-webscript-ratio 50)
                    (if (zero? webscript-count)
                      "The technology does not include any Web Scripts."
                      (str java-webscript-count " of " webscript-count " Web Scripts are Java-backed.")))))

(def tests
  "List of DEV validation functions."
  [dev02])

(def missing-tests
  "List of DEV tests that aren't yet implemented."
  ["DEV01"])
