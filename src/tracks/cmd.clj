(ns tracks.cmd
  (:require [pod.babashka.go-sqlite3 :as sql]
            [clojure.string :as str]
            [cheshire.core :as json]))

(defn mmss-to-seconds
  "Convert mm:ss to seconds"
  [mmss]
  (let [[m s] (str/split mmss #":")
        min (Integer/parseInt m)
        sec (Integer/parseInt s)]
    (+ (* min 60) sec)))

(defn seconds-to-mmss
  "Convert seconds to mm:ss"
  [sec]
  (str (quot sec 60) ":" (format "%02d" (rem sec 60))))

(defn current-year []
  (.getYear (java.time.LocalDate/now)))

(defn wrap-quote
  [s]
  (str "'" s "'"))

;; ------------------------------------------------------
  
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
    add-track <title>
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
    (println (json/generate-string (sql/query DB q) {:pretty true}))))

(defn view-release
  "Show a release and the tracks"
  [id]
  (let [tracks (sql/query DB (str "SELECT title, track_number, length FROM releases "
                                  "LEFT JOIN instances ON instances.release = releases.id "
                                  "LEFT JOIN tracks ON instances.id = tracks.id "
                                  "WHERE releases.id='" id "'"
                                  "ORDER BY track_number"))
        rel (sql/query DB (str "SELECT * FROM releases WHERE id='" id "'"))
        duration (->> tracks
                      (map :length)
                      (map mmss-to-seconds)
                      (reduce +)
                      seconds-to-mmss)]
    (println (-> rel
                 first
                 (into {:tracks tracks :duration duration})
                 (json/generate-string {:pretty true})))))

(defn add-track
  "Create a new track with minimal info"
  [title]
  (let [info {:artist "Cyjet"
              :type "Original"
              :title title
              :year (current-year)}
        fields (->> info
                    keys
                    (map name)
                    (str/join ", "))
        values (->> info
                    vals
                    (map wrap-quote)
                    (str/join ", "))
        q (str "INSERT INTO tracks (" fields ") VALUES (" values ")")]
    (sql/execute! DB q)))

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
    "add-track" (add-track (second _args))
    "update-track" (let [[_ id field value] _args]
                     (update-track id field value)) 
    "query" (query (str/join " " (rest _args)))
    (println "Usage: bb -m tracks.cmd <cmd>|help [<args>...]")))
  
;; The End