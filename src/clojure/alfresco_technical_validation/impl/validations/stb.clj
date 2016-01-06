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

(ns alfresco-technical-validation.impl.validations.stb
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.cypher        :as cy]
            [alfresco-technical-validation.impl.util :refer :all]))

; Would be preferable to do a deeper search here, but Neo4J is super slow at those
(defn- stb03
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
                           AND NOT(n.package =~ 'com.sun..*')
                           AND EXISTS(m.package)
                           AND m.name IN [
                                           'javax.servlet.Servlet',
                                           'javax.servlet.GenericServlet',
                                           'javax.servlet.http.HttpServlet',
                                           'javax.servlet.Filter'
                                         ]
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-binary-validation "STB03" res false "The technology does not use servlets or servlet filters.")))

(defn- stb04
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
                           AND NOT(n.package =~ 'com.sun..*')
                           AND EXISTS(m.name)
                           AND EXISTS(m.package)
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
    (standard-binary-validation "STB04" res false "The technology does not access the database directly.")))

(defn- stb06
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(m.name)
                           AND m.name = 'org.alfresco.service.transaction.TransactionService'
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-binary-validation "STB06" res false "The technology does not use the TransactionService.")))

(defn- stb07
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
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
                           AND EXISTS(m.name)
                           AND (NOT EXISTS(n.type)
                                OR n.type = 'class')
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
    (standard-binary-validation "STB07" res true "The technology does not use resources that need to be closed." #(when (empty? %) true))))

(defn- stb08
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :stb08-stb09
                                "STB08"
                                "Uses of synchronized"
                                false
                                "The technology does not synchronize."
                                #(when (empty? %) true))))

(defn- stb09
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :stb08-stb09
                                "STB09"
                                "Uses of synchronized"
                                false
                                "The technology does not synchronize.")))

(defn- stb10
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
                           AND (   (    EXISTS(m.name)
                                    AND m.name IN [
                                                    'java.lang.Thread',
                                                    'java.lang.ThreadGroup',
                                                    'java.lang.Runnable'
                                                  ])
                                OR (    EXISTS(m.package)
                                    AND m.package  = 'java.util.concurrent'))
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-binary-validation "STB10" res false "The technology does not use threading APIs.")))

(defn- stb11
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :stb11
                                "STB11"
                                "Catch clauses that require manual inspection"
                                true
                                "#### Manual followup required - the technology does not catch exceptions anywhere, which seems suspicious ."
                                (constantly nil))))

(defn- stb12
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)
        system-print  (filter #(= :stb12-1 (:regex-id %)) content-index)
        print-stack   (filter #(= :stb12-2 (:regex-id %)) content-index)
        con           (:binary-index indexes)
        res           (cy/tquery con
                                 "
                                   START n=NODE(*)
                                   MATCH (n)-->(m)
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
                                     AND EXISTS(m.name)
                                     AND m.name IN [
//                                                     'java.lang.Throwable',   // Every class has this dependency
                                                     'java.lang.Error'
                                                   ]
                                  RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                                   ORDER BY n.name
                                 ")
        passes        (= 0 (count system-print) (count print-stack) (count res))
        message       (if passes
                        "The technology does not use improper logging techniques."
                        (s/join "\n"
                                [
                                  (if (pos? (count system-print))
                                    (build-source-message source "Uses of System.out.print* / System.err.print*" system-print))
                                  (if (pos? (count print-stack))
                                    (build-source-message source "Uses of printStackTrace" print-stack))
                                  (if (pos? (count res))
                                    (build-binary-message res))]))]
    (declare-result "STB12" passes message)))

(defn- stb13
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
                           AND NOT(n.package =~ 'com.sun..*')
                           AND EXISTS(m.name)
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
    (standard-binary-validation "STB13" res true "The technology does not use common HTTP RPC client libraries." (constantly nil))))

(defn- stb14
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(m.name)
                           AND m.name = 'org.alfresco.service.cmr.search.SearchService'
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
  (standard-binary-validation "STB14" res true "The technology does not use the Search APIs." #(when (empty? %) true))))

(defn- stb15
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)
        matches       (filter #(= :stb15 (:regex-id %)) content-index)
        message       (str "Dictionary bootstrap(s):\n"
                           (s/join "\n"
                                   (map #(str (subs (str (:file %)) (.length ^String source)) " line " (:line-number %) ": " (s/trim (:line %)))
                                        matches)))]
      (declare-result "STB15"
                      (not (empty? matches))
                      (if (empty? matches)
                        "#### Manual followup required - content models (if any) are not loaded via bootstrap."
                        message))))

(defn- stb18
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(m.name)
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

(defn- stb19
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :stb19
                                "STB19"
                                "Uses of <transaction>none</transaction>"
                                false
                                "The technology does not use <transaction>none</transaction>.")))

(defn- stb20
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :stb20
                                "STB20"
                                "Uses of <transaction> setting"
                                false
                                "The technology does not use the <transaction> setting.")))

(defn- stb22
  [indexes]
  (let [con (:binary-index indexes)
        res (cy/tquery con
                       "
                         START n=NODE(*)
                         MATCH (n)-->(m)
                         WHERE EXISTS(n.name)
                           AND EXISTS(n.package)
                           AND EXISTS(m.name)
                           AND m.name = 'java.lang.ThreadLocal'
                        RETURN n.name AS ClassName, COLLECT(DISTINCT m.name) AS APIs
                         ORDER BY n.name
                       ")]
    (standard-binary-validation "STB22" res false "The technology does not use ThreadLocals.")))


(def tests
  "List of STB validation functions."
  [stb03 stb04 stb06 stb07 stb08 stb09 stb10 stb11 stb12 stb13 stb14 stb15 stb18 stb19 stb20 stb22])

(def missing-tests
  "List of STB tests that aren't yet implemented."
  ["STB01" "STB02" "STB05" "STB16" "STB17" "STB21"])

