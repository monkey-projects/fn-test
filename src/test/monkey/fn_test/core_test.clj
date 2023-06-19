(ns monkey.fn-test.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [monkey.fn-test
             [core :as sut]
             [path :as p]
             [socket :as s]])
  (:import java.nio.ByteBuffer))

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

(defn send-request
  "Send a request through the socket file, return the reply, then
   close the socket again."
  [req]
  (with-open [ch (s/open-client-socket-channel socket-path)
              req-str (str req) ; TODO
              b (ByteBuffer/allocate (* 2 (count req-str)))]
    (.put b (.getBytes req-str))
    (.flip b)
    (.write ch b)))

(use-fixtures :each delete-tmp-files)

(deftest listen-on-socket
  (testing "creates socket file"
    (is (false? (exists? socket-path)))
    (let [h (promise)]
      (is (true? (listen-async (fn [_]
                                 (deliver h (exists? socket-path))))))
      (is (pos? (send-request "test request")))
      (is (not= :timeout (deref h timeout :timeout))))))
