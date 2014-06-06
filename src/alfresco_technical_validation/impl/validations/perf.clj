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

(ns alfresco-technical-validation.impl.validations.perf
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.cypher        :as cy]
            [alfresco-technical-validation.impl.util :refer :all]))

(defn- perf02
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :perf02
                                "PERF02"
                                "Indexed content model properties"
                                false
                                "The technology does not index any content model properties."
                                #(if (empty? %) true nil))))

(defn- perf03
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)]
    (standard-source-validation source
                                content-index
                                :perf03
                                "PERF03"
                                "Stored content model properties"
                                false
                                "The technology does not store any content model properties in the search engine indexes.")))

(def tests
  "List of PERF validation functions."
  [perf02 perf03])
