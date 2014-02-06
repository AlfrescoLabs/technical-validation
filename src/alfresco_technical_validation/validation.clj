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
            [alfresco-technical-validation.source-indexer      :as src-idx]
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

(defn- cypher-escaped-alfresco-api
  []
  (map #(s/escape % cypher-value-escapes) (alf-api/public-java-api)))

; To workaround the limitation described in the comments of this page: http://docs.neo4j.org/chunked/stable/cypher-parameters.html
(defn- populate-in-clause
  [query in-values]
  (let [comma-delimited-string-quoted-in-values (s/join "," (map #(str "'" % "'") in-values))]
    (s/replace query "{in-clause-values}" comma-delimited-string-quoted-in-values)))

(defn- build-bookmark-map
  ([criteria-id meets evidence] (build-bookmark-map criteria-id meets evidence ""))
  ([criteria-id meets evidence non-evidence]
   (if meets
     { (str criteria-id "_Evidence")    non-evidence
       (str criteria-id "_DoesNotMeet") ""
       (str criteria-id "_Remedy")      "" }
     { (str criteria-id "_Evidence")    evidence
       (str criteria-id "_Meets")       ""
       (str criteria-id "_NoRemedy")    "" } )))

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
                                                      RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS BlacklistedAlfrescoAPIs
                                                       ORDER BY n.name
                                                     ", alfresco-public-java-api)
        res                      (cy/tquery cypher-query)
        message                  (if (empty? res)
                                   nil
                                   (s/join "\n"
                                           (map #(str (get % "UsedBy")
                                                      " uses "
                                                      (s/join ", " (get % "BlacklistedAlfrescoAPIs")))
                                                res)))]
    (build-bookmark-map "API01" (empty? res) message "The technology only uses Alfresco's Public Java APIs.")))

(defn- api06-service-locator
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND HAS(m.name)
                           AND m.name IN [
                                           'org.springframework.context.ApplicationContextAware',
                                           'org.springframework.context.ApplicationContext'
                                         ]
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS BlacklistedSpringAPIs
                         ORDER BY n.name
                       ")
        message (if (empty? res)
                  nil
                  (s/join "\n"
                          (map #(str (get % "UsedBy")
                                     " uses "
                                     (s/join ", " (get % "BlacklistedSpringAPIs")))
                               res)))]
    (build-bookmark-map "API06" (empty? res) message "The technology does not use the service locator pattern.")))

(defn- com06-compiled-jvm-version
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND HAS(n.`class-version`)
                           AND n.`class-version` < 50
                        RETURN n.name AS ClassName, n.`class-version-str` AS ClassVersion
                         ORDER BY n.name
                       ")
        message (if (empty? res)
                  nil
                  (s/join "\n"
                          (map #(str (get % "ClassName")
                                     " is compiled for JVM version "
                                     (get % "ClassVersion"))
                               res)))]
    (build-bookmark-map "COM06" (empty? res) message "The code has been compiled for JVM 1.6 or greater.")))

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
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS SearchAPIs
                         ORDER BY n.name
                       ")
        message        (if (empty? res)
                         nil
                         (s/join "\n"
                                 (map #(str (get % "UsedBy")
                                            " uses "
                                            (s/join ", " (get % "SearchAPIs")))
                                      res)))]
    (merge
      (build-bookmark-map "COM09"
                          (empty? res)
                          (str message "\n#### Manual followup required to check which search \"language\" is in use. ####")
                          "The technology does not use the Search APIs.")
      (build-bookmark-map "STB07"
                          (empty? res)
                          (str message "\n#### Manual followup required to confirm ResultSets are closed. ####")
                          "The technology does not use the Search APIs.")
      (build-bookmark-map "STB14"
                          (empty? res)
                          (str message "\n#### Manual followup required to confirm usage isn't done during bootstrap. ####")
                          "The technology does not use the Search APIs."))))

(defn- sec02-minimise-manual-authentication
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name = 'org.alfresco.repo.security.authentication.AuthenticationUtil'
                        RETURN n.name AS UsedBy, m.name AS AuthenticationUtilAPI
                         ORDER BY n.name
                       ")
        message        (if (empty? res)
                         nil
                         (s/join "\n"
                                   (map #(str (get % "UsedBy")
                                              " uses "
                                              (get % "AuthenticationUtilAPI"))
                                        res)))]
    (build-bookmark-map "SEC02" (empty? res) message "The technology does not manually control the authenticated session.")))

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
                           AND m.name IN [
                                           'java.lang.Process',
                                           'java.lang.ProcessBuilder'
                                         ]
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS ProcessAPIs
                         ORDER BY n.name
                       ")
        message (if (empty? res)
                  nil
                  (s/join "\n"
                          (map #(str (get % "UsedBy")
                                     " uses "
                                     (s/join ", " (get % "ProcessAPIs")))
                               res)))]
    (build-bookmark-map "SEC04" (empty? res) message "The technology does not use Process.exec() or ProcessBuilder.")))

(defn- stb03-servlets-servlet-filters
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND HAS(m.package)
                           AND m.package IN [
                                              'javax.servlet',
                                              'javax.servlet.http'
                                            ]
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS ServletAPIs
                         ORDER BY n.name
                       ")
        message (if (empty? res)
                  nil
                  (s/join "\n"
                          (map #(str (get % "UsedBy")
                                     " uses "
                                     (s/join ", " (get % "ServletAPIs")))
                               res)))]
    (build-bookmark-map "STB03" (empty? res) message "The technology does not use servlets or servlet filters.")))

(defn- stb04-database-access
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND HAS(m.name)
                           AND HAS(m.package)
                           AND m.package IN [
                                              'java.sql',
                                              'javax.sql',
                                              'org.springframework.jdbc',
                                              'com.ibatis',
                                              'org.hibernate'
                                            ]
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS DatabaseAPIs
                         ORDER BY n.name
                       ")
        message (if (empty? res)
                  nil
                  (s/join "\n"
                          (map #(str (get % "UsedBy")
                                     " uses "
                                     (s/join ", " (get % "DatabaseAPIs")))
                               res)))]
    (build-bookmark-map "STB04" (empty? res) message "The technology does not access the database directly.")))

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
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS TransactionAPIs
                         ORDER BY n.name
                       ")
        message (if (empty? res)
                  nil
                  (s/join "\n"
                          (map #(str (get % "UsedBy")
                                     " uses "
                                     (s/join ", " (get % "TransactionAPIs")))
                               res)))]
    (merge
      (build-bookmark-map "STB06" (empty? res) message "The technology does not manually demarcate transactions.")
      (build-bookmark-map "STB18" (empty? res) message "The technology does not manually demarcate transactions."))))

(defn- stb10-threading
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND (   (    HAS(m.name)
                                    AND m.name IN [
                                                    'java.lang.Thread',
                                                    'java.lang.ThreadGroup',
                                                    'java.lang.ThreadLocal',
                                                    'java.lang.Runnable'
                                                  ])
                                OR (    HAS(m.package)
                                    AND m.package  = 'java.util.concurrent'))
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS ThreadAPIs
                         ORDER BY n.name
                       ")
        message (if (empty? res)
                  nil
                  (s/join "\n"
                          (map #(str (get % "UsedBy")
                                     " uses "
                                     (s/join ", " (get % "ThreadAPIs")))
                               res)))]
    (build-bookmark-map "STB10" (empty? res) message "The technology does not use threading APIs.")))

(defn- stb12-logging
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND HAS(m.name)
                           AND m.name IN [
                                           'java.lang.Throwable',
                                           'java.lang.Error',
                                           'java.lang.System'
                                         ]
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS LoggingAPIs
                         ORDER BY n.name
                       ")
        message (if (empty? res)
                  nil
                  (s/join "\n"
                          (map #(str (get % "UsedBy")
                                     " uses "
                                     (s/join ", " (get % "LoggingAPIs")))
                               res)))]
    (build-bookmark-map "STB12"
                        (empty? res)
                        (str message "\n#### Manual followup required to check the use of these APIs. ####")
                        "The technology does not use improper logging techniques.")))

(defn- up01-explorer-ui
  []
  (let [res (cy/tquery "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.package)
                           AND m.package =~ 'org.alfresco.web..*'
                        RETURN n.name AS UsedBy, COLLECT(DISTINCT m.name) AS ExplorerUIAPIs
                         ORDER BY n.name
                       ")
        message        (if (empty? res)
                         nil
                         (s/join "\n"
                                 (map #(str (get % "UsedBy")
                                            " uses "
                                            (s/join ", " (get % "ExplorerUIAPIs")))
                                      res)))]
    (build-bookmark-map "UP01" (empty? res) message "The technology does not extend the Explorer UI.")))

(defn- validate-criteria
  [neo4j-url
   source-index]
  (nr/connect! neo4j-url)
  (merge
    { "Date" (java.lang.String/format "%1$tF" (into-array Object [(java.util.Date.)])) }
    (api01-public-alfresco-java-api)
    (api06-service-locator)
    (com06-compiled-jvm-version)
    (com09-stb07-stb14-search-apis)
    (sec02-minimise-manual-authentication)
    (sec04-process-exec-builder)
    (stb03-servlets-servlet-filters)
    (stb04-database-access)
    (stb06-stb18-manual-transactions)
    (stb10-threading)
    (stb12-logging)
    (up01-explorer-ui)
  ))

(defn validate
  "Validates the given source and binaries, using the Neo4J server available at the given URL."
  [source binaries neo4j-url report-filename]
  (let [dependencies             (dr/classes-info binaries)
        source-index             (src-idx/index-source source)]
    (dn/write-dependencies! neo4j-url dependencies)
    (let [bookmarks (validate-criteria neo4j-url source-index)]
      (bw/populate-bookmarks! (clojure.java.io/input-stream report-template) report-filename bookmarks))))
