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

(ns alfresco-technical-validation.impl.validations.sec
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.cypher        :as cy]
            [alfresco-technical-validation.impl.util :refer :all]))

(defn- sec01
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :sec01
                                "SEC01"
                                "Bean injections"
                                true
                                "The technology does not perform bean injection."
                                #(when (empty? %) true))))

(defn- sec02
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(m.name)
                           AND m.name = 'org.alfresco.repo.security.authentication.AuthenticationUtil$RunAsWork'
                        RETURN n.name AS ClassName
                         ORDER BY n.name
                       ")
        message (str "The following manually authenticate:\n"
                     (s/join "\n" (map #(get % "ClassName") res))
                     "\n#### Manual followup required - check use of RunAsWork. ####")]
    (declare-result "SEC02" (empty? res) (if (empty? res) "The technology does not manually authenticate." message))))

(defn- sec03
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :sec03
                                "SEC03"
                                "'None' authentication in Web Scripts"
                                false
                                "The technology does not use 'none' authentication in Web Scripts.")))

(defn- sec04
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(m.name)
                           AND EXISTS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND NOT(n.package =~ 'com.sun..*')
                           AND m.name IN [
                                           'java.lang.Process',
                                           'java.lang.ProcessBuilder'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-binary-validation "SEC04" res false "The technology does not use Process.exec() or ProcessBuilder.")))

(defn- sec05
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :sec05
                                "SEC05"
                                "Uses of eval() in Javascript"
                                false
                                "The technology does not use eval() in Javascript.")))

(def tests
  "List of SEC validation functions."
  [sec01 sec02 sec03 sec04 sec05])

(def missing-tests
  "List of SEC tests that aren't yet implemented."
  [])
