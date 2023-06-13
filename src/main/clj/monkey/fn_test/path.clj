(ns monkey.fn-test.path
  "File system path-related functions"
  (:require [clojure.tools.logging :as log])
  (:import [java.nio.file Files Path]
           [java.nio.file.attribute FileAttribute PosixFilePermission]))

(defn make-writable
  "Makes given path world-writable (and readable)."
  [^Path p]
  (Files/setPosixFilePermissions p (set (PosixFilePermission/values)))
  p)

(defn ^Path ->path
  "Converts `s` into a `Path`"
  [^String s]
  (Path/of s (make-array String 0)))

(defn create-symlink [^Path dest ^Path src]
  (log/debug "Creating symlink:" src "->" dest)
  (Files/createSymbolicLink src (.getFileName dest) (make-array FileAttribute 0)))

(defn delete
  "Deletes the file the path points to"
  [^Path p]
  (.. p (toFile) (delete)))
