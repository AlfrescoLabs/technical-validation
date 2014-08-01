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

(ns alfresco-technical-validation.impl.validations.lgl
  (:require [clojure.string                          :as s]
            [clojure.tools.logging                   :as log]
            [clojurewerkz.neocons.rest               :as nr]
            [clojurewerkz.neocons.rest.cypher        :as cy]
            [alfresco-technical-validation.impl.util :refer :all]))


(def tests
  "List of LGL validation functions."
  [])

(def missing-tests
  "List of LGL tests that aren't yet implemented."
  ["LGL01" "LGL02"])
