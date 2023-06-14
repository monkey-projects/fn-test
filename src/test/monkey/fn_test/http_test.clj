(ns monkey.fn-test.http-test
  (:require [clojure
             [string :as cs]
             [test :refer :all]]
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

(deftest print-lines
  (testing "generates newline separated string"
    (is (= "first\nsecond\n"
           (sut/print-lines ["first" "second"]))))

  (testing "empty for empty list"
    (is (= "" (sut/print-lines [])))))

(deftest serialize-response
  (testing "generates string from basic response"
    (is (= "HTTP/1.1 200 OK\n"
           (sut/serialize-response {:status 200}))))
  
  (testing "serializes headers"
    (is (cs/includes? (sut/serialize-response {:status 200
                                               :headers {"Content-Type" "text/plain"}})
                      "Content-Type: text/plain")))

  (testing "serializes keyword headers to HTTP casing"
    (is (cs/includes? (sut/serialize-response {:status 200
                                               :headers {:content-type "text/plain"}})
                      "Content-Type: text/plain")))

  (testing "serializes body"
    (is (cs/ends-with? (sut/serialize-response {:status 200
                                                :body "Test reply"})
                       "\n\nTest reply\n")))

  (testing "serializes multiline body"
    (is (cs/ends-with? (sut/serialize-response {:status 200
                                                :body "first\nsecond"})
                       "\n\nfirst\nsecond\n"))))
