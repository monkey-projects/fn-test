(ns monkey.fn-test.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [monkey.fn-test
             [core :as sut]
             [path :as p]
             [socket :as s]])
  (:import java.nio.ByteBuffer
           [java.net StandardProtocolFamily UnixDomainSocketAddress]
           [java.nio.channels SocketChannel]))

(def socket-path (p/->path "/tmp/test.sock"))
(def link-path (p/->path "/tmp/test.link"))
(def timeout 2000)

(defn exists? [p]
  (.exists (.toFile p)))

(defn delete-tmp-files [f]
  (f)
  (p/delete link-path)
  (p/delete socket-path))

(defn listen-async
  "Starts listening socket in another thread"
  [handler]
  (let [started? (promise)
        t (Thread.
           (fn []
             (deliver started? true)
             (sut/listen-on-socket
              socket-path
              link-path
              handler)))]
    (.start t)
    (deref started? timeout :timeout)))

(defn open-client-socket-channel [path]
  (let [ch (SocketChannel/open StandardProtocolFamily/UNIX)
        addr (UnixDomainSocketAddress/of path)]
    ;; Bind the channel to the address
    (.bind ch addr)
    ch))

(defn send-request
  "Send a request through the socket file, return the reply, then
   close the socket again."
  [req]
  (with-open [ch (open-client-socket-channel socket-path)
              req-str (str req) ; TODO
              b (ByteBuffer/allocate (* 2 (count req-str)))]
    (.put b (.getBytes req-str))
    (.flip b)
    (.write ch b)))

(use-fixtures :each delete-tmp-files)

#_(deftest listen-on-socket
  (testing "creates socket file"
    (is (false? (exists? socket-path)))
    (let [h (promise)]
      (is (true? (listen-async (fn [_]
                                 (deliver h (exists? socket-path))))))
      (is (pos? (send-request "test request")))
      (is (not= :timeout (deref h timeout :timeout))))))

(deftest parse-body-lines
  (testing "empty if nil or empty body"
    (is (empty? (sut/parse-body-lines "")))
    (is (empty? (sut/parse-body-lines nil))))
  
  (testing "extracts single line from body"
    (is (= ["test"] (sut/parse-body-lines "4\ntest\n0"))))
  
  (testing "extracts multiple lines from body"
    (is (= ["test" "other"] (sut/parse-body-lines "4\ntest\n5\nother\n0"))))

  (testing "handles multiline"
    (is (= ["first\nsecond"] (sut/parse-body-lines "c\nfirst\nsecond\n0")))))
