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

(ns alfresco-technical-validation.impl.binary-validation
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
     (declare-result criteria-id
                     (comparison-fn query-result)
                     (if manual-followup-required (str message "\n#### Manual followup required. ####") message)))))

(defn- api01-public-alfresco-java-api
  [binary-index]
  (let [con                      binary-index
        alfresco-public-java-api (cypher-escaped-alfresco-api)
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

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- api06-service-locator
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
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

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- dev02-prefer-javascript-web-scripts
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
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
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
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

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- com04-prefer-repository-actions
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
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
    (standard-validation "COM04" res false "The technology does not provide any repository actions." #(not (empty? %)))))

(defn- com06-compiled-jvm-version
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
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
                           AND NOT(n.package =~ 'groovy.*')
                           AND NOT(n.package =~ 'org.gradle.*')
                           AND NOT(n.package =~ 'proguard.*')
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
    (declare-result "COM06"
                    (empty? res)
                    (if (empty? res) "The code has been compiled for JVM 1.6 or greater." message))))

(defn- com09-prefer-cmisql
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name = 'org.alfresco.service.cmr.search.SearchService'
                        RETURN n.name AS ClassName
                         ORDER BY n.name
                       ")
        message (str "The following class(es) use SearchService:\n"
                     (s/join "\n" (map #(get % "ClassName") res))
                     "\n#### Manual followup required - check search language. ####")]
    (if (empty? res)
      (declare-result "COM09" true "The technology does not use the Search APIs.")
      (declare-result "COM09" message))))

(defn- sec02-minimise-manual-authentication
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name = 'org.alfresco.repo.security.authentication.AuthenticationUtil$RunAsWork'
                        RETURN n.name AS ClassName
                         ORDER BY n.name
                       ")
        message (str "The following manually authenticate:\n"
                     (s/join "\n" (map #(get % "ClassName") res))
                     "\n#### Manual followup required - check use of RunAsWork. ####")]
    (declare-result "SEC02" (empty? res) (if (empty? res) "The technology does not manually authenticate." message))))

(defn- sec04-process-exec-builder
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND HAS(n.package)
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
    (standard-validation "SEC04" res false "The technology does not use Process.exec() or ProcessBuilder.")))

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- stb03-servlets-servlet-filters
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND NOT(n.package =~ 'com.sun..*')
                           AND HAS(m.package)
                           AND m.name IN [
                                           'javax.servlet.Servlet',
                                           'javax.servlet.GenericServlet',
                                           'javax.servlet.http.HttpServlet',
                                           'javax.servlet.Filter'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB03" res false "The technology does not use servlets or servlet filters.")))

(defn- stb04-database-access
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND NOT(n.package =~ 'com.sun..*')
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
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name = 'org.alfresco.service.transaction.TransactionService'
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB06" res false "The technology does not use the TransactionService.")))

(defn- stb07-close-all-resources
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
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
                           AND NOT(n.package =~ 'groovy.*')
                           AND NOT(n.package =~ 'org.gradle.*')
                           AND NOT(n.package =~ 'proguard.*')
                           AND HAS(m.name)
                           AND m.name IN [
                                           'org.alfresco.service.cmr.search.ResultSet',
                                           'java.io.Closeable',
                                           'java.io.InputStream',
                                           'java.io.FileInputStream',
                                           'java.io.BufferedInputStream',
                                           'java.io.OutputStream',
                                           'java.io.FileOutputStream',
                                           'java.io.BufferedOuputStream',
                                           'java.io.Reader',
                                           'java.io.FileReader',
                                           'java.io.BufferedReader',
                                           'java.io.Writer',
                                           'java.io.FileWriter',
                                           'java.io.BufferedWriter',
                                           'java.net.Socket',
                                           'java.net.ServerSocket',
                                           'javax.net.ssl.SSLSocket',
                                           'javax.net.ssl.SSLServerSocket'
                                           // Add to this list as other closeable resources are identified - http://docs.oracle.com/javase/7/docs/api/java/io/Closeable.html is a good starting point for further research
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB07" res true "The technology does not use resources that need to be closed." #(if (empty? %) true nil))))

(defn- stb10-threading
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
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
                                                    'java.lang.Runnable'
                                                  ])
                                OR (    HAS(m.package)
                                    AND m.package  = 'java.util.concurrent'))
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB10" res false "The technology does not use threading APIs.")))

(defn- stb12-logging
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
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
                           AND NOT(n.package =~ 'groovy.*')
                           AND NOT(n.package =~ 'org.gradle.*')
                           AND NOT(n.package =~ 'proguard.*')
                           AND HAS(m.name)
                           AND m.name IN [
//                                           'java.lang.Throwable',   // Every class has this dependency
                                           'java.lang.Error',
                                           'java.lang.System'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB12" res true "The technology does not use improper logging techniques.")))

(defn- stb13-use-timeouts-for-rpcs
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND NOT(n.package =~ 'org.apache..*')
                           AND NOT(n.package =~ 'com.google..*')
                           AND NOT(n.package =~ 'com.sap..*')
                           AND NOT(n.package =~ 'com.sun..*')
                           AND HAS(m.name)
                           AND m.name IN [
                                           'java.net.HttpURLConnection',
                                           'org.apache.commons.httpclient.HttpClient',
                                           'org.apache.http.impl.client.AutoRetryHttpClient',
                                           'org.apache.http.impl.client.CloseableHttpClient',
                                           'org.apache.http.impl.client.ContentEncodingHttpClient',
                                           'org.apache.http.impl.client.DecompressingHttpClient',
                                           'org.apache.http.impl.client.DefaultHttpClient',
                                           'org.apache.http.impl.client.SystemDefaultHttpClient'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB13" res true "The technology does not use common HTTP RPC client libraries.")))

(defn- stb14-search-during-bootstrap
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(m.name)
                           AND m.name = 'org.alfresco.service.cmr.search.SearchService'
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
  (standard-validation "STB14" res true "The technology does not use the Search APIs." #(if (empty? %) true nil))))

(defn- stb18-prefer-automatic-transactions
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
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
                       ")
        message (str "The following classes manually demarcate transactions:\n"
                     (s/join "\n" (map #(get % "ClassName") res)))]
    (declare-result "STB18"
                    (empty? res)
                    (if (empty? res) "The technology does not manually demarcate transactions." message))))

(defn- stb22-minimise-threadlocals
  [binary-index]
  (let [con binary-index
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE HAS(n.name)
                           AND HAS(n.package)
                           AND HAS(m.name)
                           AND m.name = 'java.lang.ThreadLocal'
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-validation "STB22" res false "The technology does not use ThreadLocals.")))

(defn validate
  "Runs all binary-based validations."
  [binary-index]
  (concat
    (vector
      (api01-public-alfresco-java-api       binary-index)
      (api06-service-locator                binary-index)
      (dev02-prefer-javascript-web-scripts  binary-index)
      (com01-unique-java-package            binary-index)
      (com04-prefer-repository-actions      binary-index)
      (com06-compiled-jvm-version           binary-index)
      (com09-prefer-cmisql                  binary-index)
      (sec02-minimise-manual-authentication binary-index)
      (sec04-process-exec-builder           binary-index)
      (stb03-servlets-servlet-filters       binary-index)
      (stb04-database-access                binary-index)
      (stb06-dont-use-transaction-service   binary-index)
      (stb07-close-all-resources            binary-index)
      (stb10-threading                      binary-index)
      (stb12-logging                        binary-index)
      (stb13-use-timeouts-for-rpcs          binary-index)
      (stb14-search-during-bootstrap        binary-index)
      (stb18-prefer-automatic-transactions  binary-index)
      (stb22-minimise-threadlocals          binary-index)
    )
    []   ; Multi-result validations, if any, go here
   ))
