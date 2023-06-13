(ns monkey.fn-test.core
  (:gen-class)
  (:require [clojure.java.io :as io]
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

(defn- make-reply [lines]
  (let [sw (StringWriter.)
        w (PrintWriter. sw)]
    (doseq [l lines]
      (.println w l))
    (.flush w)
    (.toString sw)))

(defn- accept-and-reply [^ServerSocketChannel chan]
  (log/debug "Waiting for incoming connection...")
  (let [in (.accept chan)
        req (-> in
                (s/read-from-channel)
                (http/parse-incoming))]
    (log/info "Got incoming request:" (:method req) (:path req))
    ;; TODO Delegate to ring-style handler and build an actual response
    (s/write-to-channel in
                        (make-reply ["HTTP/1.1 200 OK"
                                     "Fn-Fdk-Version: fdk-clj/0.0.1"
                                     "Content-type: text/plain"
                                     ""
                                     "The test has succeeded!"]))
    (.close in)))

(defn- accept-connections [chan]
  (while true
    (accept-and-reply chan)))

(defn listen-on-socket [socket-path link-path]
  (log/debug "Listening on socket" socket-path)
  (let [chan (s/open-socket-channel socket-path)]
    ;; Make the file world writable and create a symlink
    (p/make-writable socket-path)
    (p/create-symlink socket-path link-path)
    (try
      (accept-connections chan)
      (finally
        ;; Clean up
        (s/close-and-delete! chan socket-path)
        (p/delete link-path)))))

(defn -main [& args]
  (try
    (let [listener (listener-from-env)
          socket-path (p/->path (s/parse-socket-path listener))
          ;; Symlinks to sockets must be in the same directory
          actual-path (s/tmp-socket-path (.getParent socket-path))]
      (listen-on-socket actual-path socket-path))
    (catch Throwable t
      (log/error "Operation failed" t))))
