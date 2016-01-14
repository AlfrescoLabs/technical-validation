(ns alfresco-technical-validation.strict)

(def strict-value (atom nil))

(defn strict-arg
  ([strict] (reset! strict-value strict))  
  ([] @strict-value))