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

(ns alfresco-technical-validation.impl.source-indexes)

; We put these regexes in their own file because Sublime Text barfs on the embedded double quote characters
; and we lose all of syntax highlighting goodness.

(def file-types
  "The file types we're interested in, with symbolic names and filename regex to identify them."
  {
    :module-properties     #"module\.properties"
    :java                  #".*\.java"
    :javascript            #".*\.js"
    :freemarker            #".*\.ftl"
    :xml                   #".*\.xml"
    :web-script-descriptor #".*\.desc\.xml"
    :spring-app-context    #".*-context\.xml"
    :content-model         #".*[mM]odel.*\.xml"
    :explorer-config       #"web-client-config-custom\.xml"
    :ant                   #"build\.xml"
    :maven                 #"pom\.xml"
    :gradle                #"build\.gradle"
    :leiningen             #"project\.clj"
    :sbt                   #"build\.sbt"
    :make                  #"[mM]akefile"
    :pants                 #"BUILD"
  })

(def content-regexes-by-file-type
  "Regexes we want to run over each file type."
  {
    :module-properties {
        :module-version #"module\.version\s*=(.*)\z"
        :com03          #"module\.id\s*=(.*)\z"
        :up03-min       #"module\.repo\.version\.min\s*=(.*)\z"
        :up03-max       #"module\.repo\.version\.max\s*=(.*)\z"
        :up04           #"module\.editions\s*=(.*)\z"
      }
    :java {
        :stb08-stb09    #"(?:^|\s)synchronized(?:\s|$)"
        :stb11          #"(?:^|\s)catch(?:\s|$)"
        :stb12-1        #"System\.(out|err)\.print"
        :stb12-2        #"printStackTrace"
      }
    :javascript {
        :sec05          #"(?:^|\s)eval\s*\("
      }
    :web-script-descriptor {
        :stb19          #"<transaction>\s*none\s*</transaction>"
        :stb20          #"<transaction>"
        :sec03          #"<authentication>\s*none\s*</authentication>"
      }
    :spring-app-context {
        :api05          #"(?:^|\s)ref\s*=\s*\"(?!ServiceRegistry)"
        :sec01          #"(?:^|\s)ref\s*=\s*\"([^\"]+)\""             ; Can this be merged with :api05, without losing fidelity?
        :stb15          #"parent\s*=\s*\"dictionaryModelBootstrap\""
        :com10          #"<bean[^>]*id\s*=\s*\"([^\"]+)\""
      }
    :content-model {
        :properties     #"<property\s+name\s*=\s*\"([^\"]+)\""
        :perf02         #"<index\s+enabled\s*=\s*\"true"
        :perf03         #"<stored>\s*true\s*</stored>"
        :com08          #"<namespace\s+uri\s*=\s*\".*\"\s+prefix\s*=\s*\"([^\"]+)\"\s*/>"
      }
    :ant {
        :ivy            #"antlib:org\.apache\.ivy\.ant"
      }
  })
