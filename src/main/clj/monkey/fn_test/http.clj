(ns monkey.fn-test.http
  "HTTP request/reply functionality"
  (:require [camel-snake-kebab.core :as csk]
            [clj-commons.byte-streams :as bs]))

(def request-line-pattern #"([A-Z]+)\s+(\S+)\s+HTTP/(\S+)")

(defn- parse-request-line [state lines]
  (if-let [[_ method path version] (re-matches request-line-pattern (first lines))]
    (assoc state
           :result {:method (csk/->kebab-case-keyword method)
                    :path path
                    :version version}
           :state :headers)))

(defn- parse-header [state lines]
  (if (empty? lines)
    (assoc state :state :done)
    (let [line (first lines)]
      (if (empty? line)
        (assoc state :state :body)
        (let [[k v] (clojure.string/split line #":")]
          (assoc-in state [:result :headers (csk/->kebab-case-keyword k)] (.trim v)))))))

(defn- parse-body [state lines]
  (-> state
      (assoc :state :done)
      (assoc-in [:result :body] lines)))

(def initial-state {:state :initial})

(def state-handlers
  {:initial parse-request-line
   :headers parse-header
   :body parse-body})

(defn parse-incoming
  "Parses incoming http request using very naive state machine."
  [buf]
  (let [ls (bs/to-line-seq buf)]
    (loop [state {:state :initial}
           lines ls]
      (let [handler (get state-handlers (:state state))
            {:keys [state] :as new-state} (handler state lines)]
        (if (not= :done state)
          (recur new-state (rest lines))
          (:result new-state))))))
