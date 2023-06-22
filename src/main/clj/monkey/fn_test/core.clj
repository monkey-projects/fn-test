(ns monkey.fn-test.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]
            [clj-commons.byte-streams :as bs]
            [monkey.fn-test
             [http :as http]
             [path :as p]
             [socket :as s]])
  (:import [java.io StringWriter PrintWriter]
           [java.net StandardProtocolFamily UnixDomainSocketAddress]
           [java.nio.channels SocketChannel ServerSocketChannel]
           [java.nio ByteBuffer]
           [java.nio.file Path]))

(set! *warn-on-reflection* true)

(def fdk-version "0.0.2")

(defn- listener-from-env
  "Determines the unix listener socket from the env"
  []
  (let [fmt (System/getenv "FN_FORMAT")
        listener (System/getenv "FN_LISTENER")]
    (when (not= "http-stream" fmt)
      (throw (ex-info "Unsupported FN format" {:format fmt})))
    (when (empty? listener)
      (throw (ex-info "FN_LISTENER must be specified")))
    listener))

(defn- hex->int
  "Parse hex string"
  [s]
  (Integer/parseInt s 16))

#_(defn parse-body-lines
  "Parses body line seq, formatted as a sequence of [<length>, <text>].
   Returns a list of input strings."
  [body]
  (loop [in body
         acc []
         s nil
         n nil]
    (if (empty? in)
      acc
      (let [l (first in)]
        (if (nil? n)
          ;; New counter (hex)
          (recur (rest in)
                 acc
                 ""
                 (->hex l))
          ;; Otherwise it's content
          (if (nil? s)
            (recur (rest in)
                   acc
                   l
                   n)
            (if (< (count s) n)
              (recur (rest in)
                     acc
                     (str s "\n" l)   
                     n)
              (recur (rest in)
                     (conj acc s)
                     nil
                     nil))))))))

(defn parse-body-lines
  "Given a multiline body string, where each line is preceeded by the length
   of the next contents (as hex).  Returns a seq of parsed lines."
  [body]
  (let [newline #{\newline \return}
        skip (fn [n s]
               (->> s
                    (drop n)
                    (drop-while newline)))]
    (loop [in body
           acc []]
      (if (empty? body)
        acc
        (let [v (take-while (complement newline) in)]
          (if (empty? v)
            (drop-last acc)  ; Drop the last one, it's always empty
            (let [n (->> v
                         (apply str)
                         (hex->int))
                  in (skip (count v) in)]
              (recur (skip n in)
                     (conj acc (apply str (take n in)))))))))))

(defn- add-default-headers [resp]
  (assoc-in resp [:headers :fn-fdk-version] (str "fdk-clj/" fdk-version)))

(defmulti handle-request :path)

(defmethod handle-request "/call" [{:keys [handler channel] :as req}]
  (log/info "Handling regular request with body" (:body req) "and headers" (:headers req))
  ;; Delegate to ring-style handler and build a response
  (->> (update req :body parse-body-lines)
       (handler)
       (add-default-headers)
       (http/serialize-response)
       (s/write-to-channel channel)))

(defmethod handle-request "/exit" [{:keys [method]}]
  (when (= method :post)
    (log/info "Got exit request")
    :exit))

(defn- accept-and-reply
  "Accepts an incoming connection on the given server socket and sends
   a reply back."
  [^ServerSocketChannel chan handler]
  (log/debug "Waiting for incoming connection...")
  (let [in (.accept chan)
        {:keys [method path] :as req} (-> in
                                          (s/read-from-channel)
                                          (http/parse-incoming))]
    (log/info "Got incoming request:" method path)
    (try
      (-> req
          (assoc :handler handler
                 :channel in)
          (handle-request))
      (catch Exception ex
        (log/error "Failed to handle request" ex))
      (finally
        (.close in)))))

(defn- accept-connections [chan handler]
  ;; Run in a loop until the handler returns `:exit`
  (while (not= (accept-and-reply chan handler) :exit)))

(defn listen-on-socket [socket-path link-path handler]
  (log/debug "Listening on socket" socket-path)
  (let [chan (s/open-server-socket-channel socket-path)]
    ;; Make the file world writable and create a symlink.  This is necessary
    ;; because the file that's being created for the socket is not accessible
    ;; to the fn agent.
    (p/make-writable socket-path)
    (p/create-symlink socket-path link-path)
    (try
      (accept-connections chan handler)
      (finally
        ;; Clean up
        (s/close-and-delete! chan socket-path)
        (p/delete link-path)))))

(defn dummy-handler [{:keys [body]}]
  (log/info "Incoming body:" body)
  {:status 200
   :headers {:content-type "text/plain"}
   :body "This is a test reply"})

(defn -main [& args]
  (try
    (let [listener (listener-from-env)
          socket-path (p/->path (s/parse-socket-path listener))
          ;; Symlinks to sockets must be in the same directory
          actual-path (s/tmp-socket-path (.getParent socket-path))]
      (listen-on-socket actual-path socket-path dummy-handler))
    (catch Throwable t
      (log/error "Operation failed" t))))
