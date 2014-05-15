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

(ns alfresco-technical-validation.impl.source-validation
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojure.java.io                         :as io]
            [clojure.set                             :as set]
            [alfresco-technical-validation.impl.util :refer :all]
            ))


(defn- standard-validation
  ([source content-index regex-id criteria-id message-header manual-followup-required success-message]
   (standard-validation source content-index regex-id criteria-id message-header manual-followup-required success-message empty?))
  ([source content-index regex-id criteria-id message-header manual-followup-required success-message comparison-fn]
   (let [matches (filter #(= regex-id (:regex-id %)) content-index)
         message (if (empty? matches)
                   success-message
                   (str message-header ":\n"
                                (s/join "\n"
                                        (map #(str (subs (str (:file %)) (.length ^String source)) " line " (:line-number %) ": " (s/trim (:line %)))
                                             matches))))]
       (declare-result criteria-id
                       (comparison-fn matches)
                       (if manual-followup-required (str message "\n#### Manual followup required. ####") message)))))

(defn- api05-inject-serviceregistry-not-services
  [source content-index]
  (standard-validation source
                       content-index
                       :api05
                       "API05"
                       "Bean injections other than ServiceRegistry"
                       true
                       "The technology does not perform bean injection."
                       #(if (empty? %) true nil)))

(defn- com03-unique-module-identifier
  [source content-index]
  (let [matches (filter #(= :com03 (:regex-id %)) content-index)
        message (str "Module identifier(s):\n"
                     (s/join "\n"
                             (distinct (map #(second (first (:re-seq %)))
                                        matches))))]
      (declare-result "COM03"
                      (if (empty? matches)
                        "No module identifier provided."
                        (str message "\n#### Manual followup required - check that module identifier is sufficiently unique. ####")))))

(defn- com08-unique-namespace-prefixes
  [source content-index]
  (let [matches (filter #(= :com08 (:regex-id %)) content-index)
        message (str "Content model prefix(es):\n"
                     (s/join "\n"
                             (distinct (map #(second (first (:re-seq %)))
                                        matches))))]
      (declare-result "COM08"
                      (if (empty? matches)
                        "No content model namespaces defined."
                        (str message "\n#### Manual followup required - check that content model prefixes are sufficiently unique. ####")))))

(defn- stb08-stb09-use-of-synchronized
  [source content-index]
  (vector
    (standard-validation source
                         content-index
                         :stb08-stb09
                         "STB08"
                         "Uses of synchronized"
                         false
                         "The technology does not synchronize.")
    (standard-validation source
                         content-index
                         :stb08-stb09
                         "STB09"
                         "Uses of synchronized"
                         false
                         "The technology does not synchronize.")))

(defn- stb15-load-content-models-via-bootstrap
  [source content-index]
  (let [matches (filter #(= :stb15 (:regex-id %)) content-index)
        message (str "Dictionary bootstrap(s):\n"
                     (s/join "\n"
                             (map #(str (subs (str (:file %)) (.length ^String source)) " line " (:line-number %) ": " (:line %))
                                  matches)))]
      (declare-result "STB15"
                      (not (empty? matches))
                      (if (empty? matches)
                        "#### Manual followup required - content models (if any) are not loaded via bootstrap."
                        message))))

(defn- stb19-avoid-none-transactions
  [source content-index]
  (standard-validation source
                       content-index
                       :stb19
                       "STB19"
                       "Uses of <transaction>none</transaction>"
                       false
                       "The technology does not use <transaction>none</transaction>."))

(defn- stb20-avoid-transaction-setting
  [source content-index]
  (standard-validation source
                       content-index
                       :stb20
                       "STB20"
                       "Uses of <transaction> setting"
                       false
                       "The technology does not use the <transaction> setting."))

(defn- perf02-judicious-use-of-indexed-properties
  [source content-index]
  (standard-validation source
                       content-index
                       :perf02
                       "PERF02"
                       "Indexed content model properties"
                       true
                       "The technology does not index any content model properties."
                       #(if (empty? %) true nil)))

(defn- perf03-dont-store-property-values
  [source content-index]
  (standard-validation source
                       content-index
                       :perf03
                       "PERF03"
                       "Stored content model properties"
                       false
                       "The technology does not store any content model properties in the search engine indexes."))

(defn- sec03-none-authentication-in-web-scripts
  [source content-index]
  (standard-validation source
                       content-index
                       :sec03
                       "SEC03"
                       "'None' authentication in Web Scripts"
                       false
                       "The technology does not use 'none' authentication in Web Scripts."))

(defn- sec05-use-of-eval
  [source content-index]
  (standard-validation source
                       content-index
                       :sec05
                       "SEC05"
                       "Uses of eval() in Javascript"
                       false
                       "The technology does not use eval() in Javascript."))

(defn- up01-explorer-ui-extension
  [source file-index]
  (let [matches (:explorer-config file-index)
        message (str "Explorer UI extension files:\n"
                     (s/join "\n"
                             (map #(subs (str %) (.length ^String source))
                                  matches)))]
    (declare-result "UP01"
                    (empty? matches)
                    (if (empty? matches) "The technology does not extend the Explorer UI." message))))

(defn- up03-repo-min-max
  [source content-index]
  (let [repo-mins (distinct (map #(second (first (:re-seq %))) (filter #(= :up03-min (:regex-id %)) content-index)))
        repo-maxs (distinct (map #(second (first (:re-seq %))) (filter #(= :up03-max (:regex-id %)) content-index)))]
    (declare-result "UP03"
                    (or (not (empty? repo-mins)) (not (empty? repo-maxs)))
                    (s/join "\n"
                      (filter identity
                              (vector
                                (if (not-empty repo-mins)                       (str "module.repo.version.min(s): " (s/join "," repo-mins)))
                                (if (not-empty repo-maxs)                       (str "module.repo.version.max(s): " (s/join "," repo-maxs)))
                                (if (and (empty? repo-mins) (empty? repo-maxs)) "The technology does not specify module.editions in its module.properties file(s).")))))))

(defn- up04-module-editions
  [source content-index]
  (let [matches  (distinct (map #(second (first (:re-seq %))) (filter #(= :up04 (:regex-id %)) content-index)))
        editions (s/join "," matches)]
    (declare-result "UP04"
                    (not (empty? matches))
                    (if (empty? matches)
                      "The technology does not specify module.editions in its module.properties file(s)."
                      (str "module.editions: " editions)))))

(defn validate
  "Runs all source-based validations."
  [source source-index]
  (let [files-by-type (:source-files-by-type source-index)
        content-index (:source-content-index source-index)]
    (assert (not (nil? files-by-type)))
    (assert (not (nil? content-index)))
    (concat
      (vector
         (api05-inject-serviceregistry-not-services  source content-index)
         (com03-unique-module-identifier             source content-index)
         (com08-unique-namespace-prefixes            source content-index)
         (stb15-load-content-models-via-bootstrap    source content-index)
         (stb19-avoid-none-transactions              source content-index)
         (stb20-avoid-transaction-setting            source content-index)
         (perf02-judicious-use-of-indexed-properties source content-index)
         (perf03-dont-store-property-values          source content-index)
         (sec03-none-authentication-in-web-scripts   source content-index)
         (sec05-use-of-eval                          source content-index)
         (up01-explorer-ui-extension                 source files-by-type)
         (up03-repo-min-max                          source content-index)
         (up04-module-editions                       source content-index)
       )
       (stb08-stb09-use-of-synchronized source content-index)
     )))
  