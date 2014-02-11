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

