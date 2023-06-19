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

(defn- add-default-headers [resp]
  (assoc-in resp [:headers :content-type :fn-fdk-version] (str "fdk-clj/" fdk-version)))

(defmulti handle-request :path)

(defmethod handle-request "/call" [{:keys [handler channel] :as req}]
  ;; Delegate to ring-style handler and build a response
  (->> req
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

(defn dummy-handler [_]
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
