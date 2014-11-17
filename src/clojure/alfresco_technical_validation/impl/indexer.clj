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

(ns alfresco-technical-validation.impl.indexer
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [clojure.java.io       :as io]
            [clojure.set           :as set]
            [depends.reader        :as dr]
            [depends.neo4jwriter   :as dn]
            [multigrep.core        :as mg])
  (:use [alfresco-technical-validation.impl.source-indexes]))

(defn- build-file-type-index
  [files file-type file-regex]
  { file-type (filter #(and (.isFile ^java.io.File %) (re-matches file-regex (.getName ^java.io.File %))) files) })

(defn- build-file-types-index
  [files]
  (into {} (map #(build-file-type-index files (key %) (val %)) file-types)))

(defn- build-content-index-for-file-type
  [file-type file-index]
  (let [relevant-files   (file-type file-index)
        relevant-regexes (vals (file-type content-regexes-by-file-type))
        regex-id-lookup  (set/map-invert (file-type content-regexes-by-file-type))
        raw-grep-result  (mg/grep relevant-regexes relevant-files)]
    (flatten (map #(assoc % :regex-id (get regex-id-lookup (:regex %))) raw-grep-result))))

(defn- build-content-index
  [file-index]
  (flatten (map #(build-content-index-for-file-type % file-index) (keys content-regexes-by-file-type))))

(defn- index-source
  "Indexes the sources in the given location and returns an in-memory index."
  [source]
  (let [source-files         (file-seq (io/file source))
        source-files-by-type (build-file-types-index source-files)
        source-content-index (build-content-index source-files-by-type)]
    { :source-files-by-type source-files-by-type
      :source-content-index source-content-index }))

(defn- index-binaries
  "Indexes the binaries in the given location to the specified Neo4J server."
  [neo4j-url binaries]
  (let [classes-info (dr/classes-info binaries)]
    (dn/write-dependencies! neo4j-url classes-info)))

(defn indexes
  "Returns a map containing the :binary-index and the :source-index."
  ([neo4j-url binaries source]
    (indexes neo4j-url binaries source nil))
  ([neo4j-url binaries source status-fn]
    (let [_            (if status-fn (status-fn "Indexing binaries... "))
          binary-index (index-binaries neo4j-url binaries)
          _            (if status-fn (status-fn "\nIndexing source... "))
          source-index (index-source source)]
  { :binary-index binary-index
    :source-index source-index } )))
