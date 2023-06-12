(ns monkey.fn-test.path-test
  (:require [clojure.test :refer :all]
            [monkey.fn-test.path :as sut]))

(deftest ->path
  (testing "creates path from string"
    (is (instance? java.nio.file.Path (sut/->path "test.txt")))))

