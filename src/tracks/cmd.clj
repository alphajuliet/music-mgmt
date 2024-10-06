(ns tracks.cmd
  (:require [pod.babashka.go-sqlite3 :as sql]
            [cheshire.core :as json]
            [clojure.pprint :refer [pprint]]))

(def DB "data/tracks.db")

(defn help
  "Print help message"
  []
  (println "Commands:
    help
    tracks
    releases
    lookup <title>"))

(defn tracks 
  "List all tracks"
  []
  (let [q "SELECT * from tracks"]
    (println (json/generate-string (sql/query DB q) {:pretty true}))))

(defn releases
  "List all releases"
  []
  (let [q "SELECT * from releases ORDER BY id"]
    (println (json/generate-string (sql/query DB q) {:pretty true}))))

(defn lookup
  "Lookup tracks by matching on title"
  [title]
  (let [q (str "SELECT * FROM tracks WHERE title LIKE '%" title "%'")] 
    (println (json/generate-string (sql/query DB q)))))

(defn -main
  [& _args]
  (case (first _args)
    "help" (help)
    "tracks" (tracks)
    "releases" (releases)
    "lookup" (lookup (second _args))
    (println "Usage: bb -m tracks.cmd <cmd>|help [<args>...]")))
  
;; The End