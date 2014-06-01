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

(ns alfresco-technical-validation.main
  (:require [clojure.string                     :as s]
            [clojure.tools.logging              :as log]
            [clojure.tools.cli                  :refer [parse-opts]]
            [jansi-clj.core                     :as jansi]
            [io.aviso.exception                 :as ave]
            [alfresco-technical-validation.core :as atv]
            [spinner.core                       :as spin]
            )
  (:use [clojure.tools.cli :only [cli]]
        [clojure.pprint :only [pprint]])
  (:gen-class))

(def ^:private cli-options
  [["-s" "--source SOURCE" "Source folder (mandatory)"
    :validate [#(and (.exists (clojure.java.io/file %))
                     (.isDirectory (clojure.java.io/file %))) "Source folder must exist and be a folder"]]
   ["-b" "--binaries BINARIES" "Binary folder or archive (mandatory)"
    :validate [#(.exists (clojure.java.io/file %)) "Binary folder or archive must exist"]]
   ["-n" "--neo4j-url NEO4J_URL" "URL of the Neo4J server to use (optional - see default)"
    :default "http://localhost:7474/db/data/"
    :validate [#(not (or (nil? %) (= 0 (.length ^String %)))) "URL must be provided"]]
   ["-r" "--report-file REPORT_FILE" "The filename of the output report (mandatory)"
    :validate [#(not (.exists (clojure.java.io/file %))) "Report file must not exist"]]
   ["-h" "--help" "This message"]])

(def ^:private check-mark (if spin/is-windows? (String. (.getBytes "√" "cp437")) "✔"))  ; Ugh Windoze you suck
(def ^:private spin-opts  { :characters (if spin/is-windows? (:spinner spin/styles) (:up-and-down spin/styles))
                            :fg-colour  :cyan } )

(defn -main
  "Command line access for Alfresco Technical Validation."
  [& args]
  (try
    (let [parsed-args     (parse-opts   args cli-options)
          options         (:options     parsed-args)
          source          (:source      options)
          binaries        (:binaries    options)
          neo4j-url       (:neo4j-url   options)
          report-filename (:report-file options)
          help            (:help        options)
          summary         (:summary     parsed-args)
          errors          (:errors      parsed-args)]
      (if errors
        (println errors)
        (if (or help (nil? binaries) (nil? source) (nil? report-filename))
          (println (str " ------------------------------+-------------------------------+--------------------------------------------------------\n"
                        "  Parameter                    | Default Value                 | Description\n"
                        " ------------------------------+-------------------------------+--------------------------------------------------------\n"
                        summary
                        "\n ------------------------------+-------------------------------+--------------------------------------------------------"))
          (do
            (jansi/install!)
            (log/debug "Starting...")  ; We do this primarily to force slf4j to initialise itself - it's rather whiny on startup
            (spin/spin! #(atv/validate-and-write-report source binaries neo4j-url report-filename spin/print) spin-opts)
            (println (str "\n" (jansi/green check-mark) " " report-filename))
            (flush))))
      nil)
    (catch Exception e
      (log/error e)
      (println (ave/format-exception e)))
    (finally
      (shutdown-agents))))
