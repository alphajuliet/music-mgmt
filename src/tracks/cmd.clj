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
    add-track <title>
    view-track <id>
    update-track <id> <field> <value>
    add-release <id>
    view-release <id>
    update-release <id> <field> <value>
    release <track_id> <release_id> <track_number>
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
  (let [q "SELECT * FROM tracks WHERE title LIKE ?"] 
    (println (json/generate-string (sql/query DB [q (str "%" title "%")]) {:pretty true}))))

(defn view-track
  "View a track with a given ID"
  [id]
  (let [q (str "SELECT * FROM tracks WHERE id = ?")] 
    (println (json/generate-string (sql/query DB [q id]) {:pretty true}))))

(defn view-release
  "Show a release and the tracks"
  [id]
  (let [tracks (sql/query DB ["SELECT title, track_number, tracks.id, length FROM releases
                              LEFT JOIN instances ON instances.release = releases.id
                              LEFT JOIN tracks ON instances.id = tracks.id
                              WHERE releases.id = ?
                              ORDER BY track_number" id])
        rel (sql/query DB ["SELECT * FROM releases WHERE id = ?" id])
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
  (try
    (let [info {:artist "Cyjet"
                :type "Original"
                :title title
                :length "00:00"
                :year (current-year)}
          fields (->> info
                      keys
                      (map name)
                      (str/join ", "))
          values (->> info
                      vals
                      (map wrap-quote)
                      (str/join ", "))]
      (sql/execute! DB ["INSERT INTO tracks (?) VALUES (?)" fields values]))
    (catch Exception e
      (println (.getMessage e)))))

(defn update-track
  "Update track info"
  [id field value]
  (try
    (let [q (str "UPDATE tracks SET " field " = ? WHERE id = ?")]
      (sql/execute! DB [q value id])
      (println "OK"))
    (catch Exception e
      (println (.getMessage e)))))

(defn add-release
  "Create a new release"
  [id]
  (try
    (let [q "INSERT INTO releases (id, status) VALUES (?, 'WIP')"]
      (sql/execute! DB [q id]))
    (println "OK")
    (catch Exception e
      (println (.getMessage e)))))

(defn update-release
  "Update release info"
  [id field value]
  (try
    (let [q (str "UPDATE releases SET " field " = ? WHERE id = ?")]
      (sql/execute! DB [q value id])
      (println "OK"))
    (catch Exception e
      (println (.getMessage e)))))

(defn release
  "Add a track to a release"
  [track-id release-id track-number]
  (try
    (let [q "INSERT INTO instances (id, release, track_number) VALUES (?, ?, ?)"]
      (sql/execute! DB [q track-id release-id track-number])
      (println "OK"))
    (catch Exception e
      (println (.getMessage e)))))

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
    "view-track" (view-track (second _args))
    "view-release" (view-release (second _args))
    "add-track" (add-track (second _args))
    "update-track" (let [[_ id field value] _args]
                     (update-track id field value)) 
    "add-release" (add-release (second _args))
    "update-release" (let [[_ id field value] _args]
                       (update-release id field value)) 
    "release" (let [[_ track-id release-id track-number] _args]
                (release track-id release-id track-number))
    "query" (query (str/join " " (rest _args)))
    ;; else
    (println "Usage: bb -m tracks.cmd <cmd>|help [<args>...]")))

;; The End
