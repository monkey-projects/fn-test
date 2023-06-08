(ns monkey.fn-test.core
  (:gen-class :methods [[handler [String] String]]))

(defn ^String -handler
  "Entrypoint method for fn.  Configure this in the docker command
   as `monkey.fn_test.core::handler`."
  [_ ^String arg]
  (str "Hello, " (if (empty? arg) "world" arg) " from Clojure!"))
