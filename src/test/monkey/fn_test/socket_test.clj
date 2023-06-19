(ns monkey.fn-test.socket-test
    (:require [clojure.test :refer :all]
            [monkey.fn-test
             [socket :as sut]
             [path :as p]]))

(deftest socket-channels
  (testing "can open and close"
    (let [path (p/->path "test.socket")
          c (sut/open-server-socket-channel path)]
      (try
        (is (some? c))
        (sut/close-socket-channel c)
        (finally
          (p/delete path))))))
        
(deftest parse-socket-path
  (testing "extracts full path from the arg"
    (is (= "/test/socket" (sut/parse-socket-path "unix:/test/socket")))))
