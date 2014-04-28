;
; Copyright © 2014 Peter Monks (pmonks@gmail.com)
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

(ns alfresco-technical-validation.loc-counter
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [clojure.java.io       :as io]
            [me.raynes.conch       :refer [with-programs]]))

(defn- ohcount-output-to-map
  [ohcount-output]
  {
    (nth ohcount-output 0) [ (nth ohcount-output 1)    ; Number of files
                             (nth ohcount-output 2) ]  ; Lines of code, excluding comments
  })

(defn- line-count
  [file]
  (with-open [reader (io/reader file)]
    (count (line-seq reader))))

(defn- count-freemarker
  [source-index]
  (let [freemarker-files (:freemarker (:source-files-by-type source-index))]
    { "freemarker" [(str (count freemarker-files)) (str (reduce + (map line-count freemarker-files)))] } ))

;####TODO: ADD COUNTING OF CONTENT MODELS, ETC.

(defn count-locs
  [source source-index]
  (with-programs [ohcount]
    (try
      (let [raw-output       (ohcount source {:seq true})
            output-wo-header (drop 6 raw-output)                                     ; OHCount header is 6 lines
            output-wo-footer (take (- (count output-wo-header) 2) output-wo-header)  ; OHCount footer is 2 lines
            split-lines      (map #(s/split % #"\s+") output-wo-footer)
            mapped-lines     (into (count-freemarker source-index) (map ohcount-output-to-map split-lines))]
        mapped-lines)
      (catch java.io.IOException ioe
        (log/warn "Unable to fork ohcount - is it installed?" ioe)))))
