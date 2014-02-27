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

(ns alfresco-technical-validation.util
  (:require [clojure.tools.logging :as log]
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
