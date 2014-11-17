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

(ns alfresco-technical-validation.impl.util
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [clojure.java.io       :as io]
            ))

(defn declare-result
  "Builds a map representing the result of testing a single validation criteria."
  ([criteria-id message] (declare-result criteria-id nil message))
  ([criteria-id passes message]
   (if (nil? passes)
     { :criteria-id criteria-id
       :message     message }
     { :criteria-id criteria-id
       :passes      passes
       :message     message } )))

(defn build-binary-message
  [query-result]
  (s/join "\n"
          (map #(str (get % "ClassName")
                     " uses "
                     (s/join ", " (get % "APIs")))
               query-result)))

(defn standard-binary-validation
  ([criteria-id query-result manual-followup-required success-message]
   (standard-binary-validation criteria-id query-result manual-followup-required success-message empty?))
  ([criteria-id query-result manual-followup-required success-message comparison-fn]
   (let [query-result-as-string (build-binary-message query-result)
         message                (if (empty? query-result-as-string) success-message query-result-as-string)]
     (declare-result criteria-id
                     (comparison-fn query-result)
                     (if manual-followup-required (str message "\n#### Manual followup required. ####") message)))))

(defn build-source-message
  [source header matches]
  (str header ":\n"
       (s/join "\n"
               (map #(str (subs (str (:file %)) (.length ^String source)) " line " (:line-number %) ": " (s/trim (:line %)))
                    matches))))

(defn standard-source-validation
  ([source content-index regex-id criteria-id message-header manual-followup-required success-message]
   (standard-source-validation source content-index regex-id criteria-id message-header manual-followup-required success-message empty?))
  ([source content-index regex-id criteria-id message-header manual-followup-required success-message comparison-fn]
   (let [matches (filter #(= regex-id (:regex-id %)) content-index)
         message (if (empty? matches)
                   success-message
                   (build-source-message source message-header matches))]
       (declare-result criteria-id
                       (comparison-fn matches)
                       (if manual-followup-required (str message "\n#### Manual followup required. ####") message)))))

