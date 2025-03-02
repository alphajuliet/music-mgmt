(ns mmgt.mm
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [mmgt.output :as output]
            [babashka.fs :as fs]
            [pod.babashka.go-sqlite3 :as sql]))

(def mmgt-version "0.1.0")

(def cli-options
  [["-h" "--help" "Show help information"]
   ["-v" "--version" "Show version information"]
   ["-f" "--format FORMAT" "Output format: json (default), edn, plain, or table"
    :default "json"
    :validate [#(contains? #{"json" "edn" "plain" "table"} %) "Must be one of: json, edn, plain, table"]]
   ["-d" "--db PATH" "Database file path"
    :default "data/tracks.db"]
   ["-b" "--backup-dir PATH" "Backup directory path"
    :default "data/backup"]
   [nil "--verbose" "Enable verbose output"]])

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

(defn current-date
  "Return the current date in the format YYYY-MM-DD"
  []
  (let [date (java.time.LocalDate/now)]
    (format "%04d-%02d-%02d" (.getYear date) (.getMonthValue date) (.getDayOfMonth date))))

(defn wrap-quote
  [s]
  (str "'" s "'"))

;; ------------------------------------------------------
(defn print-output
  "Print data according to the specified format"
  [data & {:keys [format] :or {format "json"}}]
  (println (output/format-output data format)))

(defn get-db [options]
  (or (:db options) "data/tracks.db"))

(defn tracks
  "List all tracks"
  [options]
  (let [db (get-db options)
        q "SELECT * from tracks"]
    (print-output (sql/query db q) :format (:format options))))

(defn releases
  "List all releases"
  [options]
  (let [db (get-db options)
        q "SELECT * from releases ORDER BY id"]
    (print-output (sql/query db q) :format (:format options))))

(defn lookup
  "Lookup tracks that match on title"
  [title options]
  (let [db (get-db options)
        q "SELECT * FROM tracks WHERE title LIKE ?"]
    (print-output (sql/query db [q (str "%" title "%")]) :format (:format options))))

(defn search
  "Search tracks by any field"
  [field value options]
  (let [db (get-db options)
        valid-fields #{"artist" "type" "title" "year"}]
    (if (contains? valid-fields field)
      (let [q (str "SELECT * FROM tracks WHERE " field " LIKE ?")]
        (print-output (sql/query db [q (str "%" value "%")]) :format (:format options)))
      (println "Invalid field. Valid fields are:" (str/join ", " valid-fields)))))

(defn view-track
  "View a track with a given ID"
  [id options]
  (let [db (get-db options)
        q (str "SELECT * FROM tracks WHERE id = ?")]
    (print-output (sql/query db [q id]) :format (:format options))))

(defn view-release
  "Show a release and the tracks"
  [id options]
  (let [db (get-db options)
        tracks (sql/query db ["SELECT title, track_number, tracks.id, length FROM releases
                              LEFT JOIN instances ON instances.release = releases.id
                              LEFT JOIN tracks ON instances.id = tracks.id
                              WHERE releases.id = ?
                              ORDER BY track_number" id])
        rel (sql/query db ["SELECT * FROM releases WHERE id = ?" id])
        duration (->> tracks
                      (map :length)
                      (map mmss-to-seconds)
                      (reduce +)
                      seconds-to-mmss)]
    (print-output (-> rel
                      first
                      (into {:tracks tracks :duration duration}))
                  :format (:format options))))

(defn add-track
  "Create a new track with minimal info"
  [title options]
  (try
    (let [db (get-db options)
          info {:artist "Cyjet"
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
      (sql/execute! db ["INSERT INTO tracks (?) VALUES (?)" fields values])
      (println "OK"))
    (catch Exception e
      (println (.getMessage e)))))

(defn update-track
  "Update track info"
  [id field value options]
  (try
    (let [db (get-db options)
          q (str "UPDATE tracks SET " field " = ? WHERE id = ?")]
      (sql/execute! db [q value id])
      (println "OK"))
    (catch Exception e
      (println (.getMessage e)))))

(defn add-release
  "Create a new release"
  [id options]
  (try
    (let [db (get-db options)
          q "INSERT INTO releases (id, status) VALUES (?, 'WIP')"]
      (sql/execute! db [q id])
      (println "OK"))
    (catch Exception e
      (println (.getMessage e)))))

(defn update-release
  "Update release info"
  [id field value options]
  (try
    (let [db (get-db options)
          q (str "UPDATE releases SET " field " = ? WHERE id = ?")]
      (sql/execute! db [q value id])
      (println "OK"))
    (catch Exception e
      (println (.getMessage e)))))

(defn release
  "Add a track to a release"
  [track-id release-id track-number options]
  (try
    (let [db (get-db options)
          q "INSERT INTO instances (id, release, track_number) VALUES (?, ?, ?)"]
      (sql/execute! db [q track-id release-id track-number])
      (println "OK"))
    (catch Exception e
      (println (.getMessage e)))))

(defn query
  "Query the db with SQL. No input checking is done."
  [query-str options]
  (let [db (get-db options)]
    (as-> query-str <>
      (sql/query db <>)
      (print-output <> :format (:format options)))))

(defn export-data [filename options]
  (let [db (get-db options)
        tracks-data (sql/query db "SELECT * FROM tracks")
        releases-data (sql/query db "SELECT * FROM releases")
        instances-data (sql/query db "SELECT * FROM instances")
        all-data {:tracks tracks-data
                  :releases releases-data
                  :instances instances-data}]
    (spit filename (json/generate-string all-data {:pretty true}))
    (println "Data exported to" filename)))

(defn backup
  "Create a backup of the database file to the nominated folder"
  [options]
  (let [db-file (:db-file options)
        backup-dir (:backup-dir options)
        timestamp (current-date)
        db-backup (fs/file-name (str/replace db-file #"\.db$" (str "_" timestamp ".db")))
        backup-file (fs/path backup-dir db-backup)]
    (fs/create-dirs backup-dir)
    (fs/copy db-file backup-file {:replace-existing true})
    (println "Backup created:" (str backup-file))))

(defn usage [options-summary]
  (str "Music Management CLI

Usage: bb -m mmgt.mm [options] command [args...]

Options:
" options-summary "

Commands:
    help                                 Show this help
    version                              Show version information
     
    tracks                               List all tracks
    lookup <title>                       Lookup tracks by title
    search <field> <value>               Search tracks by field
    add-track <title>                    Add a new track
    view-track <id>                      View a track
    update-track <id> <field> <value>    Update track info

    releases                             List all releases
    add-release <id>                     Add a new release
    view-release <id>                    View a release
    update-release <id> <field> <value>  Update release info
    release <track_id> <release_id> <track_number>  Add track to release

    query <sql>                          Run SQL query
    export-data <filename>               Export data to file
    backup                               Create a backup of the database"))

(defn help
  "Print help message"
  [options]
  (println (usage (:summary options))))

(defn version
  "Print version information"
  []
  (println "Music Management CLI version" mmgt-version))

(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      ;; Handle global flags
      (:help options) (do (println (usage summary)) 0)
      (:version options) (do (version) 0)
      errors (do (println "Error:" (str/join "\n" errors)) 1)
      
      ;; Handle commands
      :else (let [cmd (first arguments)
                  cmd-args (rest arguments)]
              (case cmd
                "help" (help {:summary summary})
                "version" (version)
                "tracks" (tracks options)
                "releases" (releases options)
                "lookup" (lookup (first cmd-args) options)
                "search" (search (first cmd-args) (second cmd-args) options)
                "view-track" (view-track (first cmd-args) options)
                "view-release" (view-release (first cmd-args) options)
                "add-track" (add-track (first cmd-args) options)
                "update-track" (update-track (first cmd-args) (second cmd-args) (nth cmd-args 2) options)
                "add-release" (add-release (first cmd-args) options)
                "update-release" (update-release (first cmd-args) (second cmd-args) (nth cmd-args 2) options)
                "release" (release (first cmd-args) (second cmd-args) (nth cmd-args 2) options)
                "query" (query (str/join " " cmd-args) options)
                "export-data" (export-data (first cmd-args) options)
                "backup" (backup {:db-file (:db options) :backup-dir (:backup-dir options)})
                ;; else
                (do
                  (println "Unknown command:" cmd)
                  (println (usage summary))
                  1))))))

;; The End
