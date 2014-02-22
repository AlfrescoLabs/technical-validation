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
    (bw/populate-bookmarks! (io/input-stream report-template) report-filename all-bookmarks)))
