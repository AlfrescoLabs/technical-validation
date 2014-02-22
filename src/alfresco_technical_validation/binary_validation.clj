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

(ns alfresco-technical-validation.binary-validation
  (:require [clojure.string                                    :as s]
            [clojure.tools.logging                             :as log]
            [clojurewerkz.neocons.rest                         :as nr]
            [clojurewerkz.neocons.rest.cypher                  :as cy]
            [depends.reader                                    :as dr]
            [depends.neo4jwriter                               :as dn]
            [alfresco-technical-validation.alfresco-public-api :as alf-api]
            [alfresco-technical-validation.util                :refer :all]
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

(defn- standard-validation
  ([criteria-id query-result manual-followup-required success-message]
   (standard-validation criteria-id query-result manual-followup-required success-message empty?))
  ([criteria-id query-result manual-followup-required success-message comparison-fn]
   (let [query-result-as-string (s/join "\n"
                                        (map #(str (get % "ClassName")
                                                   " uses "
                                                   (s/join ", " (get % "APIs")))
                                             query-result))
         message                (if (empty? query-result-as-string) success-message query-result-as-string)]
     (build-bookmark-map criteria-id
                         (comparison-fn query-result)
                         (if manual-followup-required (str message "\n#### Manual followup required. ####") message)))))

(defn- api01-public-alfresco-java-api
  []
  (let [alfresco-public-java-api (cypher-escaped-alfresco-api)
        cypher-query             (populate-in-clause "
                                                       START n=NODE(*)
                                                       MATCH (n)-->(m)
                                                       WHERE HAS(n.name)
                                                         AND HAS(m.name)
                                                         AND HAS(m.package)
                                                         AND m.package =~ 'org.alfresco..*'
                                                         AND NOT(m.package =~ 'org.alfresco.extension..*')
                                                         AND NOT(m.name IN [
                                                                             {in-clause-values}
                                                                           ])
                                                      RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                                                       ORDER BY n.name
                                                     ", alfresco-public-java-api)
        res                      (cy/tquery cypher-query)]
    (standard-validation "API01" res false "The technology only uses Alfresco's Public Java APIs.")))

(defn- api06-service-locator
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND HAS(m.name)
                           AND m.name IN [
                                           'org.springframework.context.ApplicationContextAware',
                                           'org.springframework.context.ApplicationContext'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "API06" res false "The technology does not use the service locator pattern.")))

(defn- dev02-prefer-javascript-web-scripts
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND HAS(m.name)
                           AND m.name IN [
                                           'org.springframework.extensions.webscripts.WebScript',
                                           'org.springframework.extensions.webscripts.AbstractWebScript',
                                           'org.springframework.extensions.webscripts.DeclarativeWebScript',
                                           'org.springframework.extensions.webscripts.atom.AtomWebScript'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "DEV02" res false "The technology does not contain any Java-backed Web Scripts.")))

(defn- com01-unique-java-package
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
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
                        RETURN DISTINCT n.package AS PackageName
                         ORDER BY PackageName
                       ")
        res-as-string (str "The following Java packages are used:\n"
                        (s/join "\n" (map #(str (get % "PackageName")) res)))
        message       (if (empty? res)
                        "The code does not have any Java packages.\n#### Manual followup required - validate whether there's any Java in the solution. ####"
                        (str res-as-string "\n#### Manual followup required - ensure reasonable uniqueness of these package names. ####"))]
    (build-bookmark-map "COM01"
                        (not-empty res)
                        message)))

(defn- com04-prefer-repository-actions
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name IN [
                                           'org.alfresco.repo.action.executer.ActionExecuter',
                                           'org.alfresco.repo.action.executer.ActionExecuterAbstractBase'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "COM04" res false "The technology does not provide any repository actions." not-empty)))

(defn- com06-compiled-jvm-version
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND HAS(n.`class-version`)
                           AND n.`class-version` < 50
                        RETURN n.name AS ClassName, n.`class-version-str` AS ClassVersion
                         ORDER BY n.name
                       ")
        message (s/join "\n"
                        (map #(str (get % "ClassName")
                                   " is compiled for JVM version "
                                   (get % "ClassVersion"))
                             res))]
    (build-bookmark-map "COM06"
                        (empty? res)
                        (if (empty? res) "The code has been compiled for JVM 1.6 or greater." message))))

(defn- com09-stb07-stb14-search-apis
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name IN [
                                           'org.alfresco.service.cmr.search.SearchService',
                                           'org.alfresco.service.cmr.search.ResultSet'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (merge
      (standard-validation "COM09" res true "The technology does not use the Search APIs.")
      (standard-validation "STB07" res true "The technology does not use the Search APIs.")
      (standard-validation "STB14" res true "The technology does not use the Search APIs."))))

(defn- sec02-minimise-manual-authentication
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name = 'org.alfresco.repo.security.authentication.AuthenticationUtil'
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "SEC02" res false "The technology does not manually control the authenticated session.")))

(defn- sec04-process-exec-builder
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND m.name IN [
                                           'java.lang.Process',
                                           'java.lang.ProcessBuilder'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "SEC04" res false "The technology does not use Process.exec() or ProcessBuilder.")))

(defn- stb03-servlets-servlet-filters
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND HAS(m.package)
                           AND m.package IN [
                                              'javax.servlet',
                                              'javax.servlet.http'
                                            ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB03" res false "The technology does not use servlets or servlet filters.")))

(defn- stb04-database-access
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND HAS(m.name)
                           AND HAS(m.package)
                           AND m.package IN [
                                              'java.sql',
                                              'javax.sql',
                                              'org.springframework.jdbc',
                                              'com.ibatis',
                                              'org.hibernate'
                                            ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB04" res false "The technology does not access the database directly.")))

(defn- stb06-dont-use-transaction-service
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name = 'org.alfresco.service.transaction.TransactionService'
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB06" res false "The technology does not use the TransactionService.")))


(defn- stb18-prefer-automatic-transactions
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name IN [
                                          'org.alfresco.repo.transaction.RetryingTransactionHelper',
                                          'org.alfresco.service.transaction.TransactionService'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB18" res false "The technology does not manually demarcate transactions.")))

(defn- stb10-threading
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND (   (    HAS(m.name)
                                    AND m.name IN [
                                                    'java.lang.Thread',
                                                    'java.lang.ThreadGroup',
                                                    'java.lang.ThreadLocal',
                                                    'java.lang.Runnable'
                                                  ])
                                OR (    HAS(m.package)
                                    AND m.package  = 'java.util.concurrent'))
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB10" res false "The technology does not use threading APIs.")))

(defn- stb12-logging
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND HAS(m.name)
                           AND m.name IN [
                                           'java.lang.Throwable',
                                           'java.lang.Error',
                                           'java.lang.System'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB12" res true "The technology does not use improper logging techniques.")))

(defn validate
  "Runs all binary-based validations."
  [neo4j-url binaries]
  (nr/connect! neo4j-url)
  (dn/write-dependencies! neo4j-url (dr/classes-info binaries))
  (merge
    (api01-public-alfresco-java-api)
    (api06-service-locator)
    (dev02-prefer-javascript-web-scripts)
    (com01-unique-java-package)
    (com04-prefer-repository-actions)
    (com06-compiled-jvm-version)
    (com09-stb07-stb14-search-apis)
    (sec02-minimise-manual-authentication)
    (sec04-process-exec-builder)
    (stb03-servlets-servlet-filters)
    (stb04-database-access)
    (stb06-dont-use-transaction-service)
    (stb18-prefer-automatic-transactions)
    (stb10-threading)
    (stb12-logging)
  ))
