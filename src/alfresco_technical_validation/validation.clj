;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; This work is licensed under the Creative Commons Attribution-ShareAlike 3.0
; Unported License. To view a copy of this license, visit
; http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to Creative
; Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
;

(ns alfresco-technical-validation.validation
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [depends.reader        :as dr]
            [depends.neo4jwriter   :as dn]
            [bookmark-writer.core                              :as bw]
            [alfresco-technical-validation.alfresco-public-api :as alf-api]
            ))

(def ^:private report-template (clojure.java.io/resource "alfresco-technical-validation-template.docx"))

(defn- index-source
  [source]
  (comment "####TODO!!!!"))

(defn- validate-criteria
  [neo4j-url
   source-index]
  (comment "####TODO!!!!"))

(defn validate
  "Validates the given source and binaries, using the Neo4J server available at the given URL."
  [source binaries neo4j-url report-filename]
  (let [dependencies             (dr/classes-info binaries)
        alfresco-public-java-api (alf-api/public-java-api)
        source-index             (index-source source)]
    (dn/write-dependencies! neo4j-url dependencies)
    (let [bookmarks (validate-criteria neo4j-url source-index)]
      (comment
      (bw/populate-bookmarks! report-template report-filename bookmarks))))
      )
