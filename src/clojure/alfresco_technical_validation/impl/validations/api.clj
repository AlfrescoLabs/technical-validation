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

(ns alfresco-technical-validation.impl.validations.api
  (:require [clojure.string                                    :as s]
            [clojure.tools.logging                             :as log]
            [clojurewerkz.neocons.rest                         :as nr]
            [clojurewerkz.neocons.rest.cypher                  :as cy]
            [alfresco-technical-validation.alfresco-public-api :as alf-api]
            [alfresco-technical-validation.impl.util           :refer :all]
            ))

; List of special characters is from http://lucene.apache.org/core/3_6_2/queryparsersyntax.html#Escaping Special Characters
; (neo4j v2.0.0 uses Lucene 3.6.2)
(def ^:private cypher-value-escapes {\+ "\\+"
                                     \- "\\-"
                                     \& "\\&"
                                     \| "\\|"
                                     \! "\\!"
                                     \( "\\("
                                     \) "\\)"
                                     \{ "\\{"
                                     \} "\\}"
                                     \[ "\\["
                                     \] "\\]"
                                     \^ "\\^"
                                     \" "\\\""
                                     \~ "\\~"
                                     \* "\\*"
                                     \? "\\?"
                                     \: "\\:"
                                     \\ "\\\\" })   ; ####TODO: does \$ need to be added too??

(defn- cypher-escaped-alfresco-api
  []
  (map #(s/escape % cypher-value-escapes) (alf-api/public-java-api)))

; To workaround the limitation described in the comments of this page: http://docs.neo4j.org/chunked/stable/cypher-parameters.html
(defn- populate-in-clause
  [query in-values]
  (let [comma-delimited-string-quoted-in-values (s/join "," (map #(str "'" % "'") in-values))]
    (s/replace query "{in-clause-values}" comma-delimited-string-quoted-in-values)))

(defn- api01
  [indexes]
  (let [con                      (:binary-index indexes)
        alfresco-public-java-api (cypher-escaped-alfresco-api)
        cypher-query             (populate-in-clause "
                                                       START n=NODE(*)
                                                       MATCH (n)-->(m)
                                                       WHERE EXISTS(n.name)
                                                         AND EXISTS(m.name)
                                                         AND EXISTS(m.package)
                                                         AND m.package =~ 'org.alfresco..*'
                                                         AND NOT(m.package =~ 'org.alfresco.extension..*')
                                                         AND NOT(m.name IN [
                                                                             {in-clause-values}
                                                                           ])
                                                      RETURN DISTINCT m.name AS PrivateAPIs
                                                       ORDER BY m.name
                                                     ", alfresco-public-java-api)
        res                      (cy/tquery con cypher-query)
        res-as-string            (str "The following private Java APIs are used:\n"
                                   (s/join "\n" (map #(str (get % "PrivateAPIs")) res)))
        message                  (if (empty? res)
                                   "The technology only uses Alfresco's Public Java APIs."
                                   res-as-string)]
    (declare-result "API01"
                    (empty? res)
                    message)))

(defn- api05
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :api05
                                "API05"
                                "Bean injections other than ServiceRegistry"
                                true
                                "The technology does not inject any beans other than the ServiceRegistry."
                                #(when (empty? %) true))))

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- api06
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
                                           'org.springframework.context.ApplicationContextAware',
                                           'org.springframework.context.ApplicationContext'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-binary-validation "API06" res false "The technology does not use the service locator pattern.")))

(def tests
  "List of API validation functions."
  [api01 api05 api06])

(def missing-tests
  "List of API tests that aren't yet implemented."
  ["API02" "API03" "API04"])

