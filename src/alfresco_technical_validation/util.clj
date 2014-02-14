;
; Copyright © 2013,2014 Peter Monks (pmonks@alfresco.com)
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

(ns alfresco-technical-validation.util
  (:require [clojure.tools.logging :as log]
            [clojure.java.io       :as io]
            ))

(defn build-bookmark-map
  "Builds the map of bookmarks for a single validation criteria."
  ([criteria-id meets evidence] (build-bookmark-map criteria-id meets evidence ""))
  ([criteria-id meets evidence non-evidence]
   (if meets
     { (str criteria-id "_Evidence")    non-evidence
       (str criteria-id "_DoesNotMeet") ""
       (str criteria-id "_Remedy")      "" }
     { (str criteria-id "_Evidence")    evidence
       (str criteria-id "_Meets")       ""
       (str criteria-id "_NoRemedy")    "" } )))

(defn grep-file
  "Returns a sequence of maps representing the matching lines for the given regex in the given file.
   Each map in the sequence has these keys:
   {
     :file         ; same as file input parameter
     :line         ; text of line that matched
     :line-number  ; line-number of that line in the file
     :re-seq       ; the output from re-seq for this line and this regex
   }"
  [regex file]
  (with-open [reader (io/reader file)]
    (let [lines (line-seq reader)]
      (doall
        (filter #(not (nil? %))
                (map-indexed #(let [matches (re-seq regex %2)]
                               (if (not-empty matches)
                                 {
                                   :file        file
                                   :line        %2
                                   :line-number %1
                                   :re-seq      matches
                                 }))
                             lines))))))

(defn grep-files
  "Returns a sequence of maps representing the matching lines for the given regex in the given files.
   Each map in the sequence has these keys:
   {
     :file         ; same as file input parameter
     :line         ; text of line that matched
     :line-number  ; line-number of that line in the file
     :re-seq       ; the output from re-seq for this line and this regex
   }"
  [regex files]
  (flatten (map #(grep-file regex %) files)))

(defn- multi-grep-line
  [file regexes line-number line]
  (filter #(not (nil? %))
          (map #(let [matches (re-seq % line)]
                  (if (not-empty matches)
                    {
                      :file        file
                      :line        line
                      :line-number line-number
                      :regex       %
                      :re-seq      matches
                    }))
               regexes)))

(defn multi-grep-file
  "Returns a sequence of maps representing the matching lines for the given regexes in the given file.
   Each map in the sequence has these keys:
   {
     :file         ; same as file input parameter
     :line         ; text of line that matched
     :line-number  ; line-number of that line in the file
     :regex        ; the regex that matched this line
     :re-seq       ; the output from re-seq for this line and this regex
   }"
  [regexes file]
  (with-open [reader (io/reader file)]
    (let [lines   (line-seq reader)]
      (doall (flatten (map-indexed #(multi-grep-line file regexes %1 %2) lines))))))

(defn multi-grep-files
  "Returns a sequence of maps representing the matching lines for the given regexes in the given files.
   Each map in the sequence has these keys:
   {
     :file         ; the file that matched
     :line         ; text of line that matched
     :line-number  ; line-number of that line in the file
     :regex        ; the regex that matched this line
     :re-seq       ; the output from re-seq for this line and this regex
   }"
  [regexes files]
  (flatten (map #(multi-grep-file regexes %) files)))
