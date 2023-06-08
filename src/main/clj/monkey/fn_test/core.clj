(ns monkey.fn-test.core
  (:gen-class)
  (:require [fdk-clj.core :as fdk]))

(defn handler
  "Entrypoint method for fn.  Configure this in the docker command
   as `monkey.fn_test.core::handler`."
  [_ data]
  (str "Hello, World from Clojure using fdk-clj! Data is " data))

(defn -main [& args]
  (fdk/handle handler))
