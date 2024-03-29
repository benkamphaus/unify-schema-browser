(ns org.parkerici.alzabo.config
  (:require [clojure.edn :as edn]
            [org.parkerici.multitool.core :as u]))


(def the-config (atom nil))

(defn set-config!
  [config]                              ;filename or map
  (let [config (if (string? config)
                 (edn/read-string (slurp config))
                 config)]
    (reset! the-config config)))

(defn set!
  [att value]
  (swap! the-config assoc att value))

(defn config*
  ([key] (get @the-config key))
  ([] @the-config))

(def ^:dynamic config config*)

(defn expand-template-string
  "Template is a string containing {foo} elements, which get replaced by corresponding values from bindings"
  [template bindings]
  (let [matches (->> (re-seq u/template-regex template) 
                     (map (fn [[match key]]
                            [match (or (bindings key) (bindings (keyword key)) "")])))]
    (reduce (fn [s [match key]]
              (clojure.string/replace s (u/re-pattern-literal match) (str key)))
            template matches)))

(defn output-path
  ([]
   (config :output-path))
  ([filename]
   (str (expand-template-string (config :output-path) config)
        filename)))





