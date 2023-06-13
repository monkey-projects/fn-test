(ns monkey.fn-test.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clj-commons.byte-streams :as bs]
            [monkey.fn-test
             [http :as http]
             [path :as p]])
  (:import [java.io StringWriter PrintWriter]
           [java.net StandardProtocolFamily UnixDomainSocketAddress]
           [java.nio.channels SocketChannel ServerSocketChannel]
           [java.nio ByteBuffer]
           [java.nio.file Path]))

(set! *warn-on-reflection* true)

(defn ^ServerSocketChannel open-socket-channel
  "Opens a Unix domain socket at given path and creates a
   listening channel for it."
  [^Path path]
  (let [addr (UnixDomainSocketAddress/of path)
        ch (ServerSocketChannel/open StandardProtocolFamily/UNIX)]
    (.configureBlocking ch true)
    ;; Bind the channel to the address
    (.bind ch addr)))

(defn close-socket-channel [^ServerSocketChannel c]
  (.close c))

(defn parse-socket-path [v]
  (let [prefix "unix:"]
    (subs v (count prefix))))

(defn- close-and-delete! [^ServerSocketChannel chan path]
  (try
    (log/debug "Closing socket at" path)
    (close-socket-channel chan)
    (finally
      (p/delete path))))

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

(defn- read-from-channel [^SocketChannel chan]
  (let [buf (ByteBuffer/allocate 10000)
        n (.read chan (into-array java.nio.ByteBuffer [buf]))]
    ;; TODO Support larger requests
    (log/debug "Read" n "bytes")
    (.flip buf)
    buf))

(defn- write-to-channel [^SocketChannel chan body]
  (->> (make-reply ["HTTP/1.1 200 OK"
                    "Fn-Fdk-Version: fdk-clj/0.0.1"
                    "Content-type: text/plain"
                    ""
                    body])
       (bs/to-byte-buffer)
       (vector)
       (into-array java.nio.ByteBuffer)
       (.write chan)))

(defn- tmp-socket-path [^Path dir]
  (.resolve dir (str "fn-" (random-uuid) ".sock")))

(defn- accept-and-reply [^ServerSocketChannel chan]
  (log/debug "Waiting for incoming connection...")
  (let [in (.accept chan)
        req (-> in
                (read-from-channel)
                (http/parse-incoming))]
    (log/info "Got incoming request:" (:method req) (:path req))
    ;; TODO Delegate to ring-style handler and build an actual response
    (write-to-channel in "The test has succeeded!")
    (.close in)))

(defn- accept-connections [chan]
  (while true
    (accept-and-reply chan)))

(defn listen-on-socket [socket-path link-path]
  (log/debug "Listening on socket" socket-path)
  (let [chan (open-socket-channel socket-path)]
    ;; Make the file world writable and create a symlink
    (p/make-writable socket-path)
    (p/create-symlink socket-path link-path)
    (try
      (accept-connections chan)
      (finally
        ;; Clean up
        (close-and-delete! chan socket-path)
        (p/delete link-path)))))

(defn -main [& args]
  (try
    (let [listener (listener-from-env)
          socket-path (p/->path (parse-socket-path listener))
          ;; Symlinks to sockets must be in the same directory
          actual-path (tmp-socket-path (.getParent socket-path))]
      (listen-on-socket actual-path socket-path))
    (catch Throwable t
      (log/error "Operation failed" t))))
