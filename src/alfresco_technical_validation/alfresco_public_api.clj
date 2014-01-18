;
; Copyright © 2013,2014 Peter Monks (pmonks@gmail.com)
;
; This work is licensed under the Creative Commons Attribution-ShareAlike 3.0
; Unported License. To view a copy of this license, visit
; http://creativecommons.org/licenses/by-sa/3.0/ or send a letter to Creative
; Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
;

(ns alfresco-technical-validation.alfresco-public-api
  (:require [clojure.tools.logging :as log]))


(def ^:private ^String api-page-url       "http://docs.alfresco.com/4.2/topic/com.alfresco.enterprise.doc/concepts/java-public-api-list.html")
(def ^:private ^String api-list-open-tag  "<pre class=\"pre codeblock\" xml:space=\"preserve\">")
(def ^:private ^String api-list-close-tag "</pre>")

(defn public-java-api
  ([] public-java-api api-page-url)
  ([url]
   (log/debug "Retrieving Alfresco Public Java API list from" url "...")
   (let [api-page-html ^String (slurp url)]
     (seq (.substring api-page-html (+ (.indexOf api-page-html api-list-open-tag) (.length api-list-open-tag))
                                       (.indexOf api-page-html api-list-close-tag))))))
