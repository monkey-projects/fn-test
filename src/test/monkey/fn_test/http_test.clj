(ns monkey.fn-test.http-test
  (:require [clojure.test :refer :all]
            [monkey.fn-test.http :as sut]))

(deftest parse-incoming
  (testing "parses basic incoming http request"
    (let [r (sut/parse-incoming "GET /call HTTP/1.1\r\n")]
      (is (= "/call" (:path r)))
      (is (= :get (:method r)))
      (is (= "1.1" (:version r)))))

  (testing "parses headers"
    (let [r (sut/parse-incoming "GET /call HTTP/1.1\r\nContent-Type: application/json\r\n")]
      (is (= {:content-type "application/json"} (:headers r)))))

  (testing "parses body as seq of lines"
    (let [r (sut/parse-incoming "GET /call HTTP/1.1\r\n\r\nThis is the body\r\n")]
      (is (= ["This is the body"] (:body r))))))
