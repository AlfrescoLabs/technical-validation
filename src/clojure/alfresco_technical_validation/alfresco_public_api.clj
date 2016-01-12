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
            [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]))


(def ^:private ^String api-page-url       "http://dev.alfresco.com/resource/AlfrescoOne/5.0/PublicAPI/all-classes-frame.html")
(defn fetch-url [url]
  (html/html-resource (java.net.URL. url)))

(defn public-java-api
  ([] (public-java-api api-page-url))
  ([url]
   (log/debug "Retrieving Alfresco Public Java API list from" url "...")
    (let [api-page-href (mapcat #(html/attr-values % :href) (html/select (fetch-url url) [[:span #{:.f10 :.f15}] [:a  (html/attr? :href)]]))
          api-list    (map #(s/replace % #"\/|\.html|\s" {"/" "." ".html" "" "s" ""}) api-page-href)
         sorted-api-list       (sort (set api-list))]
     sorted-api-list)))
