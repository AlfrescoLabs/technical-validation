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

(ns alfresco-technical-validation.writers.edn
  (:require [clojure.string        :as s]
            [clojure.tools.logging :as log]
            [clojure.java.io       :as io]
            [clojure.pprint        :as pp]))

(defn write
  ([validation-results edn-filename] (write validation-results edn-filename nil))
  ([validation-results edn-filename status-fn]
   (if status-fn (status-fn "\nGenerating EDN document... "))
   (with-open [w (io/writer (io/file edn-filename))]
     (pp/pprint validation-results w))
   nil))
