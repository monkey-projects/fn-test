(ns build.build)

(ns build
  (:require [clojure.tools.build.api :as b]))

(def basis (b/create-basis {:project "deps.edn"
                            :aliases [:uberjar]}))
(def class-dir "target/classes")
(def uberjar "target/fn-test-standalone.jar")
(def src-dir "src/main/clj")

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (b/copy-dir {:src-dirs [src-dir]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs [src-dir]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uberjar
           :basis basis
           :main 'monkey.fn-test.core}))
