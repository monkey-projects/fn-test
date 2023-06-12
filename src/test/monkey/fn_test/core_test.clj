(ns monkey.fn-test.core-test
  (:require [clojure.test :refer :all]
            [monkey.fn-test.core :as sut]))

(deftest socket-channels
  (testing "can open and close"
    (let [path (sut/->path "test.socket")
          c (sut/open-socket-channel path)]
      (try
        (is (some? c))
        (sut/close-socket-channel c)
        (finally
          (sut/delete-address path))))))
        
(deftest parse-socket-path
  (testing "extracts full path from the arg"
    (is (= "/test/socket" (sut/parse-socket-path "unix:/test/socket")))))
