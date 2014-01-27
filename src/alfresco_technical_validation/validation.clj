;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; This work is licensed under the Creative Commons Attribution-ShareAlike 3.0
; Unported License. To view a copy of this license, visit
; http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to Creative
; Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
;

(ns alfresco-technical-validation.validation
  (:require [clojure.string                                    :as s]
            [clojure.tools.logging                             :as log]
            [clojurewerkz.neocons.rest                         :as nr]
            [clojurewerkz.neocons.rest.cypher                  :as cy]
            [depends.reader                                    :as dr]
            [depends.neo4jwriter                               :as dn]
            [bookmark-writer.core                              :as bw]
            [alfresco-technical-validation.alfresco-public-api :as alf-api]
            ))

(def ^:private report-template (clojure.java.io/resource "alfresco-technical-validation-template.docx"))

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

(defn- index-source
  [source]
  (comment "####TODO!!!!"))

(defn- cypher-escaped-alfresco-api
  []
  (map #(s/escape % cypher-value-escapes) (alf-api/public-java-api)))

; To workaround the limitation described in the comments of this page: http://docs.neo4j.org/chunked/stable/cypher-parameters.html
(defn- populate-in-clause
  [query in-values]
  (let [values-1 (s/join (map #(str "'" % "',") in-values))
        values-2 (.substring values-1 0 (- (.length values-1) 1))]
    (s/replace query "{in-clause-values}" values-2)))

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
                                                      RETURN m.name AS BlacklistedAlfrescoAPI, COLLECT(DISTINCT n.name) AS UsedBy
                                                       ORDER BY m.name
                                                     ", alfresco-public-java-api)
        res                      (cy/tquery cypher-query)]
    (println "res =" res)))  ;####TEST

(defn- com06-compiled-jvm-version
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         WHERE HAS(n.name)
                           AND HAS(n.`class-version`)
                           AND n.`class-version` < 50
                        RETURN n.name AS ClassName, n.`class-version-str` AS ClassVersion
                         ORDER BY n.name
                       ")]
    (println "res =" res)))    ;####TEST

(defn- sec04-stb03-stb04-stb05-stb06-stb10-stb12-java-apis
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND (   m.name IN [
                                              'java.lang.Throwable',
                                              'java.lang.Error',
                                              'java.lang.System',
                                              'java.lang.Thread',
                                              'java.lang.ThreadGroup',
                                              'java.lang.ThreadLocal',
                                              'java.lang.Runnable',
                                              'java.lang.Process',
                                              'java.lang.ProcessBuilder',
                                              'java.lang.ClassLoader',
                                              'java.security.SecureClassLoader'
                                             ]
                            OR (    HAS(m.package)
                           AND m.package IN [
                                              'java.sql',
                                              'javax.sql',
                                              'org.springframework.jdbc',
                                              'com.ibatis',
                                              'org.hibernate',
                                              'java.util.concurrent',
                                              'javax.servlet',
                                              'javax.servlet.http',
                                              'javax.transaction',
                                              'javax.transaction.xa'
                                            ]))
                        RETURN m.name AS BlacklistedJavaAPI, COLLECT(DISTINCT n.name) AS UsedBy
                         ORDER BY m.name
                       ")]
    (println "res =" res)))    ;####TEST

(defn- stb07-stb14-search-apis
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
                        RETURN m.name AS SearchAPI, COLLECT(DISTINCT n.name) AS UsedBy
                         ORDER BY m.name
                       ")]
    (println "res =" res)))    ;####TEST

(defn- sec02-minimise-manual-authentication
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name = 'org.alfresco.repo.security.authentication.AuthenticationUtil'
                        RETURN n.name AS Class, m.name AS AuthenticationUtil
                         ORDER BY n.name
                       ")]
    (println "res =" res)))    ;####TEST

(defn- stb06-stb18-manual-transactions
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
RETURN n.name AS Class, COLLECT(DISTINCT m.name) AS TransactionClass
 ORDER BY n.name
                       ")]
    (println "res =" res)))    ;####TEST


(defn- validate-criteria
  [neo4j-url
   source-index]
  (nr/connect! neo4j-url)
  (api01-public-alfresco-java-api)
  (com06-compiled-jvm-version)
  (sec04-stb03-stb04-stb05-stb06-stb10-stb12-java-apis)
  (stb07-stb14-search-apis)
  (sec02-minimise-manual-authentication)
  (stb06-stb18-manual-transactions)
  )

(defn validate
  "Validates the given source and binaries, using the Neo4J server available at the given URL."
  [source binaries neo4j-url report-filename]
  (let [dependencies             (dr/classes-info binaries)
        source-index             (index-source source)]
    (dn/write-dependencies! neo4j-url dependencies)
    (let [bookmarks (validate-criteria neo4j-url source-index)]
      (comment
      (bw/populate-bookmarks! report-template report-filename bookmarks))))
      )
