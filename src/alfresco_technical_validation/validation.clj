;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; This work is licensed under the Creative Commons Attribution-ShareAlike 3.0
; Unported License. To view a copy of this license, visit
; http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to Creative
; Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
;

(ns alfresco-technical-validation.validation
  (:require [clojure.string                                  :as s]
            [clojure.tools.logging                           :as log]
            [clojure.java.io                                 :as io]
            [alfresco-technical-validation.binary-validation :as bin]
            [alfresco-technical-validation.source-validation :as src]
            [bookmark-writer.core                            :as bw]
            ))

(def ^:private report-template (io/resource "alfresco-technical-validation-template.docx"))

(defn validate
  "Validates the given source and binaries, using the Neo4J server available at the given URL,
  writing the report to the specified Word document."
  [source binaries neo4j-url report-filename]
  (let [global-bookmarks { "Date" (java.lang.String/format "%1$tF" (into-array Object [(java.util.Date.)])) }
        source-bookmarks (src/validate source)
        binary-bookmarks (bin/validate neo4j-url binaries)
        all-bookmarks    (merge global-bookmarks source-bookmarks binary-bookmarks)]
    (clojure.pprint/pprint source-bookmarks)
    (comment
    (bw/populate-bookmarks! (io/input-stream report-template) report-filename all-bookmarks)))
    )