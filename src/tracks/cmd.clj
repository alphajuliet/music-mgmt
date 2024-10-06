(ns tracks.cmd
  (:require [pod.babashka.go-sqlite3 :as sql]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def DB "data/tracks.db")

(defn help
  "Print help message"
  []
  (println "Commands:
    help
    tracks
    releases
    lookup <title>
    view-release <id>
    update-track <id> <field> <value>
    query <sql>"))

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
  "Lookup tracks that match on title"
  [title]
  (let [q (str "SELECT * FROM tracks WHERE title LIKE '%" title "%'")] 
    (println (json/generate-string (sql/query DB q)))))

(defn view-release
  "Show a release and the tracks"
  [id]
  (let [tracks (sql/query DB (str "SELECT title, track_number, length FROM releases "
                                  "LEFT JOIN instances ON instances.release = releases.id "
                                  "LEFT JOIN tracks ON instances.id = tracks.id "
                                  "WHERE releases.id='" id "'"
                                  "ORDER BY track_number"))
        rel (sql/query DB (str "SELECT * FROM releases WHERE id='" id "'"))]
    (println (json/generate-string (into (first rel) {:tracks tracks}) {:pretty true}))))

(defn update-track
  "Update track info"
  [id field value]
  (let [q (str "UPDATE tracks SET " field "='" value "' WHERE id='" id "'")]
    (sql/execute! DB q)))

(defn query
  "Query the db with SQL. No input checking is done."
  [query]
  (as-> query <>
    (sql/query DB <>)
    (json/generate-string <> {:pretty true})
    (println <>)))

(defn -main
  [& _args]
  (case (first _args)
    "help" (help)
    "tracks" (tracks)
    "releases" (releases)
    "lookup" (lookup (second _args))
    "view-release" (view-release (second _args))
    "update-track" (let [[_ id field value] _args]
                     (update-track id field value)) 
    "query" (query (str/join " " (rest _args)))
    (println "Usage: bb -m tracks.cmd <cmd>|help [<args>...]")))
  
;; The End
