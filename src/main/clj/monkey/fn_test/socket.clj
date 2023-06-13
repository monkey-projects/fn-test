(ns monkey.fn-test.socket
  (:require [clojure.tools.logging :as log]
            [clj-commons.byte-streams :as bs]
            [monkey.fn-test.path :as p])
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

(defn close-and-delete! [^ServerSocketChannel chan path]
  (try
    (log/debug "Closing socket at" path)
    (close-socket-channel chan)
    (finally
      (p/delete path))))

(defn read-from-channel [^SocketChannel chan]
  (let [buf (ByteBuffer/allocate 10000)
        n (.read chan ^ByteBuffer buf)]
    ;; TODO Support larger requests
    (log/debug "Read" n "bytes")
    (.flip buf)
    buf))

(defn write-to-channel [^SocketChannel chan body]
  (->> body
       (bs/to-byte-buffer)
       (.write chan)))

(defn tmp-socket-path [^Path dir]
  (.resolve dir (str "fn-" (random-uuid) ".sock")))
