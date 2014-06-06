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

(ns alfresco-technical-validation.impl.validations.up
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.cypher        :as cy]
            [alfresco-technical-validation.impl.util :refer :all]))

(defn- up01
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        file-index    (:source-files-by-type source-index)
        matches       (:explorer-config file-index)
        message       (str "Explorer UI extension files:\n"
                           (s/join "\n"
                                   (map #(subs (str %) (.length ^String source))
                                        matches)))]
    (declare-result "UP01"
                    (empty? matches)
                    (if (empty? matches) "The technology does not extend the Explorer UI." message))))

(defn- up03
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)
        repo-mins     (distinct (map #(second (first (:re-seq %))) (filter #(= :up03-min (:regex-id %)) content-index)))
        repo-maxs     (distinct (map #(second (first (:re-seq %))) (filter #(= :up03-max (:regex-id %)) content-index)))]
    (declare-result "UP03"
                    (or (not (empty? repo-mins)) (not (empty? repo-maxs)))
                    (s/join "\n"
                      (filter identity
                              (vector
                                (if (not-empty repo-mins)                       (str "module.repo.version.min(s): " (s/join "," repo-mins)))
                                (if (not-empty repo-maxs)                       (str "module.repo.version.max(s): " (s/join "," repo-maxs)))
                                (if (and (empty? repo-mins) (empty? repo-maxs)) "The technology does not specify module.editions in its module.properties file(s).")))))))

(defn- up04
  [indexes]
  (let [source        (:source       indexes)
        source-index  (:source-index indexes)
        content-index (:source-content-index source-index)
        matches       (distinct (map #(second (first (:re-seq %))) (filter #(= :up04 (:regex-id %)) content-index)))
        editions      (s/join "," matches)]
    (declare-result "UP04"
                    (not (empty? matches))
                    (if (empty? matches)
                      "The technology does not specify module.editions in its module.properties file(s)."
                      (str "module.editions: " editions)))))

(def tests
  "List of UP validation functions."
  [up01 up03 up04])
