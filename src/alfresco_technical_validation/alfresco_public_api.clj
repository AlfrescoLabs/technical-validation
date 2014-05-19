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

(ns alfresco-technical-validation.alfresco-public-api
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]))


(def ^:private ^String api-page-url       "http://docs.alfresco.com/4.2/topic/com.alfresco.enterprise.doc/concepts/java-public-api-list.html")
(def ^:private ^String api-list-open-tag  "<pre class=\"pre codeblock\">")
(def ^:private ^String api-list-close-tag "</pre>")

(defn public-java-api
  ([] (public-java-api api-page-url))
  ([url]
   (log/debug "Retrieving Alfresco Public Java API list from" url "...")
   (let [api-page-html ^String (slurp url)
         api-list-text ^String (.substring api-page-html (+ (.indexOf api-page-html api-list-open-tag) (.length api-list-open-tag))
                                                         (.indexOf api-page-html api-list-close-tag))
         api-list              (map #(s/replace % #"\s" "") (s/split-lines api-list-text))   ; Strip whitespace chars *within* API names, due to https://issues.alfresco.com/jira/browse/MNT-10346
         sorted-api-list       (sort (set api-list))]
     sorted-api-list)))
