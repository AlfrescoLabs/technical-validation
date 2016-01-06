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

(ns alfresco-technical-validation.impl.validations.com
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.cypher        :as cy]
            [alfresco-technical-validation.impl.util :refer :all]))

(defn- com01
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         WHERE EXISTS(n.name)
                           AND EXISTS(n.package)
                           AND NOT(n.package =~ 'java.*')
                           AND NOT(n.package =~ 'sun.*')
                           AND NOT(n.package =~ 'com.sun.*')
                           AND NOT(n.package =~ 'org.w3c.*')
                           AND NOT(n.package =~ 'org.apache.*')
                           AND NOT(n.package =~ 'org.alfresco.*')
                           AND NOT(n.package =~ 'org.json.*')
                           AND NOT(n.package =~ 'org.xml.*')
                           AND NOT(n.package =~ 'org.springframework.*')
                           AND NOT(n.package =~ 'org.hibernate.*')
                           AND NOT(n.package =~ 'org.mybatis.*')
                           AND NOT(n.package =~ 'net.sf.ehcache.*')
                           AND NOT(n.package =~ 'org.quartz.*')
                           AND NOT(n.package =~ 'org.mozilla.*')
                           AND NOT(n.package =~ 'com.google.*')
                           AND NOT(n.package =~ 'com.sap.*')
                           AND NOT(n.package =~ 'com.license4j.*')
                           AND NOT(n.package =~ 'com.aspose.*')
                           AND NOT(n.package =~ 'asposewobfuscated.*')
                           AND NOT(n.package =~ 'groovy.*')
                           AND NOT(n.package =~ 'org.gradle.*')
                           AND NOT(n.package =~ 'proguard.*')
                        RETURN DISTINCT n.package AS PackageName
                         ORDER BY PackageName
                       ")
        res-as-string (str "The following Java packages are used:\n"
                        (s/join "\n" (map #(str (get % "PackageName")) res)))
        message       (if (empty? res)
                        "The code does not have any Java packages.\n#### Manual followup required - validate whether there's any Java in the solution. ####"
                        (str res-as-string "\n#### Manual followup required - ensure reasonable uniqueness of these package names. ####"))]
    (declare-result "COM01"
                    message)))

(defn- com03
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)
        matches       (filter #(= :com03 (:regex-id %)) content-index)
        message       (str "Module identifier(s):\n"
                           (s/join "\n"
                                   (distinct (map #(second (first (:re-seq %)))
                                                  matches))))]
      (declare-result "COM03"
                      (if (empty? matches)
                        "No module identifier provided."
                        (str message "\n#### Manual followup required - check that module identifier is sufficiently unique. ####")))))

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- com04
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(m.name)
                           AND m.name IN [
                                           'org.alfresco.repo.action.executer.ActionExecuter',
                                           'org.alfresco.repo.action.executer.ActionExecuterAbstractBase'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-binary-validation "COM04" res false "The technology does not provide any repository actions." #(not (empty? %)))))

(defn- com06
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         WHERE EXISTS(n.name)
                           AND EXISTS(n.package)
                           AND NOT(n.package =~ 'java.*')
                           AND NOT(n.package =~ 'sun.*')
                           AND NOT(n.package =~ 'com.sun.*')
                           AND NOT(n.package =~ 'org.w3c.*')
                           AND NOT(n.package =~ 'org.apache.*')
                           AND NOT(n.package =~ 'org.alfresco.*')
                           AND NOT(n.package =~ 'org.json.*')
                           AND NOT(n.package =~ 'org.xml.*')
                           AND NOT(n.package =~ 'org.springframework.*')
                           AND NOT(n.package =~ 'org.hibernate.*')
                           AND NOT(n.package =~ 'org.mybatis.*')
                           AND NOT(n.package =~ 'net.sf.ehcache.*')
                           AND NOT(n.package =~ 'org.quartz.*')
                           AND NOT(n.package =~ 'org.mozilla.*')
                           AND NOT(n.package =~ 'com.google.*')
                           AND NOT(n.package =~ 'com.sap.*')
                           AND NOT(n.package =~ 'com.license4j.*')
                           AND NOT(n.package =~ 'com.aspose.*')
                           AND NOT(n.package =~ 'asposewobfuscated.*')
                           AND NOT(n.package =~ 'groovy.*')
                           AND NOT(n.package =~ 'org.gradle.*')
                           AND NOT(n.package =~ 'proguard.*')
                           AND EXISTS(n.`class-version`)
                           AND n.`class-version` < 50
                        RETURN n.name AS ClassName, n.`class-version-str` AS ClassVersion
                         ORDER BY n.name
                       ")
        message (s/join "\n"
                        (map #(str (get % "ClassName")
                                   " is compiled for JVM version "
                                   (get % "ClassVersion"))
                             res))]
    (declare-result "COM06"
                    (empty? res)
                    (if (empty? res) "The code has been compiled for JVM 1.6 or greater." message))))

(defn- com08
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)
        matches       (filter #(= :com08 (:regex-id %)) content-index)
        message       (str "Content model prefix(es):\n"
                           (s/join "\n"
                                   (distinct (map #(second (first (:re-seq %)))
                                              matches))))]
      (if (empty? matches)
        (declare-result "COM08" true "The technology does not define any content model namespaces.")
        (declare-result "COM08" (str message "\n#### Manual followup required - check that these content model prefixes are sufficiently unique. ####")))))

(defn- com09
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(m.name)
                           AND m.name = 'org.alfresco.service.cmr.search.SearchService'
                        RETURN DISTINCT n.name AS ClassName
                         ORDER BY n.name
                       ")
        message (str "The following class(es) use SearchService:\n"
                     (s/join "\n" (map #(get % "ClassName") res))
                     "\n#### Manual followup required - check search language. ####")]
    (if (empty? res)
      (declare-result "COM09" true "The technology does not use the Search APIs.")
      (declare-result "COM09" message))))

(defn- com10
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)
        matches       (filter #(= :com10 (:regex-id %)) content-index)
        message       (str "Bean id(s):\n"
                           (s/join "\n"
                                   (distinct (map #(second (first (:re-seq %)))
                                              matches))))]
      (if (empty? matches)
        (declare-result "COM10" true "The technology does not define any Spring beans.")
        (declare-result "COM10" (str message "\n#### Manual followup required - check that these bean ids are sufficiently unique. ####")))))

(defn- com11
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(m.name)
                           AND m.name = 'org.alfresco.service.cmr.attributes.AttributeService'
                        RETURN DISTINCT n.name AS ClassName
                         ORDER BY n.name
                       ")
        message (str "The following class(es) use the AttributeService:\n"
                     (s/join "\n" (map #(get % "ClassName") res))
                     "\n#### Manual followup required - check keys for uniqueness. ####")]
    (if (empty? res)
      (declare-result "COM11" true "The technology does not use the AttributeService.")
      (declare-result "COM11" message))))

(def tests
  "List of COM validation functions."
  [com01 com03 com04 com06 com08 com09 com10 com11])

(def missing-tests
  "List of COM tests that aren't yet implemented."
  ["COM02" "COM05" "COM07"])
