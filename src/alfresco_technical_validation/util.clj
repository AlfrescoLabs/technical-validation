;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; This work is licensed under the Creative Commons Attribution-ShareAlike 3.0
; Unported License. To view a copy of this license, visit
; http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to Creative
; Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
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
  "Returns a sequence of {:line-number x :line y :re-seq z} maps for all lines in file that match the given regex."
  [regex file]
  (with-open [reader (io/reader file)]
    (let [lines (line-seq reader)]
      (doall
        (map-indexed #(let [matches (re-seq regex %2)]
                        (if (not-empty matches)
                          { :line-number %1 :re-seq matches :line %2 } ))
                     lines)))))

(defn grep-files
  "Returns a sequence of [file-name, [{:line-number x :line y :re-seq z}]] pairs that match the regex."
  [regex files]
  (filter #(empty? (second %))
          (map #(vector % (grep-file regex %))
               files)))

(defn- multi-grep-line
  [regexes line]
  (filter #(not (nil? %))
          (map #(let [[regex-id regex] %
                      matches         (re-seq regex line)]
                  (if (not-empty matches)
                    { :id regex-id :re-seq matches }))
               regexes)))

(defn multi-grep-file
  [regexes file]
  (with-open [reader (io/reader file)]
    (let [lines (line-seq reader)]
      (doall
        (filter #(not (nil? %))
                (map-indexed #(let [matches (multi-grep-line regexes %2)]
                                (if (not-empty matches)
                                  { :line-number %1
                                    :line        %2
                                    :matches     matches
                                  }))
                             lines))))))

(defn multi-grep-files
  [regexes files]
  (filter #(not (nil? %))
          (map #(let [matches (multi-grep-file regexes %)]
                  (if (not-empty matches)
                    { :file    %
                      :matches matches
                      }))
               files)))
