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

(ns alfresco-technical-validation.source-validation
  (:require [clojure.string                     :as s]
            [clojure.tools.logging              :as log]
            [clojure.java.io                    :as io]
            [clojure.set                        :as set]
            [alfresco-technical-validation.util :refer :all]
            [multigrep.core                     :as mg]
            ))

(def ^:private file-types
  "The file types we're interested in, with symbolic names and filename regex to identify them."
  {
    :module-properties     #"module\.properties"
    :java                  #".*\.java"
    :javascript            #".*\.js"
    :freemarker            #".*\.ftl"
    :xml                   #".*\.xml"
    :web-script-descriptor #".*\.desc\.xml"
    :spring-app-context    #".*-context\.xml"
    :content-model         #".*[mM]odel.*\.xml"
    :explorer-config       #"web-client-config-custom\.xml"
    :ant                   #"build\.xml"
    :maven                 #"pom\.xml"
    :gradle                #"build\.gradle"
    :leiningen             #"project\.clj"
    :sbt                   #"build\.sbt"
    :make                  #"[mM]akefile"
    :pants                 #"BUILD"
  })

(defn- build-file-type-index
  [files file-type file-regex]
  { file-type (filter #(re-matches file-regex (.getName ^java.io.File %)) files) })

(defn- build-file-types-index
  [files]
  (into {} (map #(build-file-type-index files (key %) (val %)) file-types)))

(def ^:private content-regexes-by-file-type
  "Regexes we want to run over each file type."
  {
    :module-properties {
        :module-version #"module\.version\s*=(.*)\z"
        :repo-min       #"module\.repo\.version\.min\s*=(.*)\z"
        :repo-max       #"module\.repo\.version\.max\s*=(.*)\z"
        :alf-edition    #"module\.edition\s*=(.*)\z"
        :com03          #"module\.id\s*=(.*)\z"
      }
    :java {
        :stb08-stb09    #"(?:^|\s)synchronized(?:\s|$)"
      }
    :javascript {
        :sec05          #"(?:^|\s)eval\("
      }
    :web-script-descriptor {
        :stb19-stb20    #"<transaction>"
        :sec03          #"<authentication>\s*none\s*</authentication>"
      }
    :spring-app-context {
        :api05          #"(?:^|\s)ref="
      }
    :content-model {
        :perf02         #"<index\s+enabled\s*=\s*\"true"
        :perf03         #"<stored>\s*true\s*</stored>"
        :com08          #"<namespace\s+uri\s*=\s*\".*\"\s+prefix\s*=\s*\"(.*)\"\s*/>"
      }
    :ant {
        :ivy            #"antlib:org\.apache\.ivy\.ant"
      }
  })

(defn- build-content-index-for-file-type
  [file-type file-index]
  (let [relevant-files   (file-type file-index)
        relevant-regexes (vals (file-type content-regexes-by-file-type))
        regex-id-lookup  (set/map-invert (file-type content-regexes-by-file-type))
        raw-grep-result  (mg/multigrep-files relevant-regexes relevant-files)]
    (flatten (map #(assoc % :regex-id (get regex-id-lookup (:regex %))) raw-grep-result))))

(defn- build-content-index
  [file-index]
  (flatten (map #(build-content-index-for-file-type % file-index) (keys content-regexes-by-file-type))))

(defn- detect-build-tools
  [file-index]
  (let [build-tools (s/join ","
                            (filter #(not (nil? %))
                                    (vector (if (not-empty (:ant       file-index)) (if (empty? (mg/grep-files #"antlib:org\.apache\.ivy\.ant" (:ant file-index)))
                                                                                      "Ant"
                                                                                      "Ivy"))
                                            (if (not-empty (:maven     file-index)) "Maven")
                                            (if (not-empty (:gradle    file-index)) "Gradle")
                                            (if (not-empty (:leiningen file-index)) "Leiningen")
                                            (if (not-empty (:sbt       file-index)) "SBT")
                                            (if (not-empty (:make      file-index)) "Make")
                                            (if (not-empty (:pants     file-index)) "Pants"))))]
    { "BuildTools" (if (empty? build-tools) "Unknown" build-tools) }))

(defn- module-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :module-version (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "ModuleVersion" (if (empty? message) "Not specified" message) }))

(defn- alfresco-min-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :repo-min (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "AlfrescoVersionMin" (if (empty? message) "Not specified" message) }))

(defn- alfresco-max-versions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :repo-max (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "AlfrescoVersionMax" (if (empty? message) "Not specified" message) }))

(defn- alfresco-editions
  [content-index]
  (let [matches (distinct (map #(second (first (:re-seq %))) (filter #(= :alf-edition (:regex-id %)) content-index)))
        message (s/join "," matches)]
    { "AlfrescoEditions" (if (empty? message) "Not specified" message) }))

(defn- standard-validation
  ([source content-index regex-id criteria-id message-header manual-followup-required success-message]
   (standard-validation source content-index regex-id criteria-id message-header manual-followup-required success-message empty?))
  ([source content-index regex-id criteria-id message-header manual-followup-required success-message comparison-fn]
   (let [matches (filter #(= regex-id (:regex-id %)) content-index)
         message (if (empty? matches)
                   success-message
                   (str message-header ":\n"
                                (s/join "\n"
                                        (map #(str (subs (str (:file %)) (.length ^String source)) " line " (:line-number %) ": " (:line %))
                                             matches))))]
       (build-bookmark-map criteria-id
                           (comparison-fn matches)
                           (if manual-followup-required (str message "\n#### Manual followup required. ####") message)))))

(defn- api05-inject-serviceregistry-not-services
  [source content-index]
  (standard-validation source
                       content-index
                       :api05
                       "API05"
                       "Bean injections"
                       true
                       "The technology does not perform bean injection."))

(defn- com03-unique-module-identifier
  [source content-index]
  (let [matches (filter #(= :com03 (:regex-id %)) content-index)
        message (str "Module identifier(s):\n"
                     (s/join "\n"
                             (map #(str (subs (str (:file %)) (.length ^String source)) " line " (:line-number %) ": " (:line %))
                                  matches)))]
      (build-bookmark-map "COM03"
                          (not-empty matches)
                          (if (empty? matches) "No module identifier provided." (str message "\n#### Manual followup required - check that module identifier is sufficiently unique. ####")))))

(defn- com08-unique-namespace-prefixes
  [source content-index]
  (standard-validation source
                       content-index
                       :com08
                       "COM08"
                       "Namespace declarations"
                       true
                       "The technology does not define content model namespaces."))

(defn- stb08-stb09-use-of-synchronized
  [source content-index]
  (merge
    (standard-validation source
                         content-index
                         :stb08-stb09
                         "STB08"
                         "Uses of synchronized"
                         true
                         "The technology does not synchronized.")
    (standard-validation source
                         content-index
                         :stb08-stb09
                         "STB09"
                         "Uses of synchronized"
                         true
                         "The technology does not synchronized.")))

(defn- stb19-stb20-web-script-transaction-setting
  [source content-index]
  (merge
    (standard-validation source
                         content-index
                         :stb19-stb20
                         "STB19"
                         "Uses of <transaction> setting"
                         false
                         "The technology does not use the <transaction> setting.")
    (standard-validation source
                         content-index
                         :stb19-stb20
                         "STB20"
                         "Uses of <transaction> setting"
                         false
                         "The technology does not use the <transaction> setting.")))

(defn- perf02-judicious-use-of-indexed-properties
  [source content-index]
  (standard-validation source
                       content-index
                       :perf02
                       "PERF02"
                       "Indexed content model properties"
                       true
                       "The technology does not index any content model properties."))

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
                       "Uses of eval in Javascript"
                       false
                       "The technology does not use eval in Javascript."))

(defn- up01-explorer-ui-extension
  [source file-index]
  (let [matches (:explorer-config file-index)
        message (str "Explorer UI extension files:\n"
                     (s/join "\n"
                             (map #(subs (str %) (.length ^String source))
                                  matches)))]
    (build-bookmark-map "UP01"
                        (empty? matches)
                        (if (empty? matches) "The technology does not extend the Explorer UI." message))))

(defn validate
  "Runs all source-based validations."
  [source]
  (let [files         (file-seq (io/file source))
        file-index    (build-file-types-index files)
        content-index (build-content-index file-index)]
    (merge
      (detect-build-tools                         file-index)
      (module-versions                            content-index)
      (alfresco-min-versions                      content-index)
      (alfresco-max-versions                      content-index)
      (alfresco-editions                          content-index)
      (api05-inject-serviceregistry-not-services  source content-index)
      (com03-unique-module-identifier             source content-index)
      (com08-unique-namespace-prefixes            source content-index)
      (stb08-stb09-use-of-synchronized            source content-index)
      (stb19-stb20-web-script-transaction-setting source content-index)
      (perf02-judicious-use-of-indexed-properties source content-index)
      (perf03-dont-store-property-values          source content-index)
      (sec03-none-authentication-in-web-scripts   source content-index)
      (sec05-use-of-eval                          source content-index)
      (up01-explorer-ui-extension                 source file-index)
    )))
  