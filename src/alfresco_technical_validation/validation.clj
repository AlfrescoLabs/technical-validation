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

(defn- sanitised-api-list
  []
  (map #(s/escape % cypher-value-escapes) (alf-api/public-java-api)))

; To workaround the limitation described in the comments of this page: http://docs.neo4j.org/chunked/stable/cypher-parameters.html
(defn- populate-in-clause
  [query in-values]
  (let [values-1 (s/join (map #(str "'" % "',") in-values))
        values-2 (.substring values-1 0 (- (.length values-1) 1))]
    (s/replace query "{in-clause-values}" values-2)))

(defn- validate-alfresco-api-usage
  []
  (let [alfresco-public-java-api (sanitised-api-list)
        cypher-query             (populate-in-clause "
START n=node(*)
MATCH (n)-->(m)
WHERE has(n.name)
  AND has(m.name)
  AND has(m.package)
  AND m.package =~ 'org.alfresco..*'
  AND NOT(m.package =~ 'org.alfresco.extension..*')
  AND NOT(m.name IN [
                      {in-clause-values}
                    ])
RETURN m.name as Blacklisted_Alfresco_API, collect(distinct n.name) as Used_By
 ORDER BY m.name;", alfresco-public-java-api)
        res                      (cy/tquery cypher-query)]
    (println "res =" res)))  ;####TEST

(defn- validate-criteria
  [neo4j-url
   source-index]
  (nr/connect! neo4j-url)
  (validate-alfresco-api-usage))


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
