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


(def ^:private ^String api-page-url       "http://dev.alfresco.com/resource/AlfrescoOne/5.0/PublicAPI/all-classes-frame.html")
(def ^:private ^String api-list-open-tag  "All Classes</A> (281)</SPAN></DIV>")
(def ^:private ^String api-list-close-tag "</BODY>")

(defn public-java-api
  ([] (public-java-api api-page-url))
  ([url]
   (log/debug "Retrieving Alfresco Public Java API list from" url "...")
   (let [api-page-html ^String (slurp url)
         api-list-text ^String (.substring api-page-html (+ (.indexOf api-page-html api-list-open-tag) (.length api-list-open-tag))
                                                         (.indexOf api-page-html api-list-close-tag))
         api-list-text-trimed ^String (s/replace api-list-text #"\"\s*TARGET=\"detail\">" ".")
		 api-list              (map #(s/replace % #"\s" "") (s/split api-list-text-trimed #"</A></SPAN></DIV><DIV\s*CLASS=\"p5\"><SPAN\s*CLASS=\"(f10|f15)\"><A\s*HREF=\"org/alfresco/[A-Za-z0-9\./]*\.html\"\s*title=\"(class\s*in\s*|interface\s*in\s*|enum\s*in\s*|annotation\s*in\s*)"))
         sorted-api-list       (sort (set api-list))]
     sorted-api-list)))
