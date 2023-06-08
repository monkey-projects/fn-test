(ns monkey.fn-test.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [byte-streams :as bs])
  (:import [java.net StandardProtocolFamily UnixDomainSocketAddress]
           [java.nio.channels ServerSocketChannel]
           [java.nio ByteBuffer]))

(defn open-socket-channel
  "Opens a Unix domain socket at given path and creates a
   listening channel for it."
  [path]
  (let [addr (UnixDomainSocketAddress/of path)]
    (doto (-> (ServerSocketChannel/open StandardProtocolFamily/UNIX)
              (.configureBlocking true))
      ;; Bind the channel to the address
      (.bind addr))))

(defn delete-address [path]
  (.delete (java.io.File. path)))

(defn close-socket-channel [c]
  (.close c))

(defn parse-socket-path [v]
  (let [prefix "unix:"]
    (subs v (count prefix))))

(defn handler
  [_ data]
  (str "Hello, World from Clojure using fdk-clj! Data is " data))

(defn- close-and-delete! [chan path]
  (try
    (log/debug "Closing socket at" path)
    (close-socket-channel chan)
    (finally
      (delete-address path))))

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

(defn- read-from-channel [chan]
  (let [buf (ByteBuffer/allocate 10000)
        n (.read chan buf)]
    (log/info "Read" n "bytes")
    (.flip buf)
    (let [s (bs/to-string buf)]
      (log/info "Read:" s))))

(defn -main [& args]
  (try
    (let [listener (listener-from-env)]
      (log/debug "Listening on socket" listener)
      (let [socket-path (parse-socket-path listener)
            chan (open-socket-channel socket-path)]
        (try
          (log/info "Waiting for incoming connection...")
          (read-from-channel (.accept chan))
          (finally
            (close-and-delete! chan socket-path)))))
    (catch Throwable t
      (log/error "Operation failed" t))))
