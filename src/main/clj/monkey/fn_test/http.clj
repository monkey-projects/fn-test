(ns monkey.fn-test.http
  "HTTP request/reply functionality"
  (:require [camel-snake-kebab.core :as csk]
            [clj-commons.byte-streams :as bs]))

(def request-line-pattern #"([A-Z]+)\s+(\S+)\s+HTTP/(\S+)")

(defn- parse-request-line [line]
  (if-let [[_ method path version] (re-matches request-line-pattern line)]
    {:method (csk/->kebab-case-keyword method)
     :path path
     :version version}))

(defn- parse-header [state line]
  (if (empty? line)
    (assoc state :state :body)
    (let [[k v] (clojure.string/split line #":")]
      (assoc-in state [:result :headers (csk/->kebab-case-keyword k)] (.trim v)))))

(defn parse-incoming
  "Parses incoming http request using very naive state machine."
  [buf]
  (let [ls (bs/to-line-seq buf)]
    (loop [state {:state :initial}
           lines ls]
      (if-let [line (first lines)]
        (let [{:keys [state] :as new-state}
              (case (:state state)
                :initial (assoc state
                                :state :headers
                                :result (parse-request-line line))
                :headers (parse-header state line)
                :body (-> state
                          (assoc :state :done)
                          (assoc-in [:result :body] lines)))]
          (if (not= :done state)
            (recur new-state (rest lines))
            (:result new-state)))
        (:result state)))))
