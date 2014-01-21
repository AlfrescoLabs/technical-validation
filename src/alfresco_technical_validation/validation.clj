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

(defn- index-source
  [source]
  (comment "####TODO!!!!"))

(defn- validate-alfresco-api-usage
  []
  (let [alfresco-public-java-api (alf-api/public-java-api)
        _                        (println "alfresco-public-java-api=" alfresco-public-java-api)
        res                      (cy/tquery "
START n=node(*)
MATCH (n)-->(m)
WHERE has(n.name)
  AND has(m.name)
  AND has(m.package)
  AND m.package =~ 'org.alfresco..*'
  AND NOT(m.package =~ 'org.alfresco.extension..*')
  AND NOT(m.name IN [
                      {public-apis}
                    ])
RETURN m.name as Blacklisted_Alfresco_API, collect(distinct n.name) as Used_By
 ORDER BY m.name;
                                            " {:public-apis alfresco-public-java-api})]
    (println "res=" res)))  ;####TEST

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
