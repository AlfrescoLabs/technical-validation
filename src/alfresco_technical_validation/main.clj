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
  (:require [clojure.string                    :as s]
            [clojure.tools.logging             :as log]
            [clojure.tools.cli                 :refer [parse-opts]]
            [jansi-clj.core                    :as jansi]
            [io.aviso.exception                :as ave]
            [alfresco-technical-validation.api :as atv]
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

(def ^:private os-name     (System/getProperty "os.name"))
(def ^:private is-windows? (.startsWith (.toLowerCase ^String os-name) "windows"))
(def ^:private check-mark  (if is-windows? (String. (.getBytes "√" "cp437")) "✔"))  ; Ugh Windoze
(def ^:private spinner-styles
  {
    :spinner         "|/-\\"
    :unicode-spinner "⋮⋰⋯⋱"
    :up-and-down     "▁▃▄▅▆▇█▇▆▅▄▃"
    :fade-in-and-out " ░▒▓█▓▒░"
    :side-to-side    "▉▊▋▌▍▎▏▎▍▌▋▊▉"
    :quadrants       "┤┘┴└├┌┬┐"
  })

(defn- infini-spinner
  ([] (infini-spinner 100 (if is-windows? :spinner :up-and-down)))
  ([delay-in-ms spinner-style]
    (try
      (loop [characters ^String (spinner-style spinner-styles)
             i                  0]
        (print (str (jansi/cursor-left 2) (jansi/cyan (nth characters i)) " "))
        (flush)
        (Thread/sleep delay-in-ms)
        (recur characters (mod (inc i) (.length characters))))
      (catch InterruptedException ie
        (comment "Swallow exception and terminate.")))))

(defn- start-spinner
  []
  (doto 
    (Thread. ^Runnable infini-spinner)
    (.setDaemon true)
    (.start)))

(defn- spin
  [fn]
  (let [spinner (start-spinner)]
    (try
      (fn)
      (finally
        (.interrupt ^Thread spinner)))))

(defn -main
  "Command line access for Alfresco Technical Validation."
  [& args]
  (try
    (let [parsed-args     (parse-opts args cli-options)
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
          (let [message "Reticulating splines...   "]
            (jansi/install!)
            (print message)
            (flush)
            (spin #(atv/validate-and-write-report source binaries neo4j-url report-filename))
            (println (str (jansi/cursor-left (.length message)) (jansi/erase-line)
                          (jansi/green check-mark) " " report-filename))
            (flush))))
      nil)
    (catch Exception e
      (log/error e)
      (println (ave/format-exception e)))
    (finally
      (shutdown-agents))))
