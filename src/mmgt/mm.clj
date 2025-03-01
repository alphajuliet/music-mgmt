(ns mmgt.mm
  (:require [pod.babashka.go-sqlite3 :as sql]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]))

(def mmgt-version "0.1.0")

(def cli-options
  [["-h" "--help" "Show help information"]
   ["-v" "--version" "Show version information"]
   ["-f" "--format FORMAT" "Output format: json (default), edn, plain, or table"
    :default "json"
    :validate [#(contains? #{"json" "edn" "plain" "table"} %) "Must be one of: json, edn, plain, table"]]
   ["-d" "--db PATH" "Database file path"
    :default "data/tracks.db"]
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

(defn wrap-quote
  [s]
  (str "'" s "'"))

;; ------------------------------------------------------

(defn format-as-table
  "Format data as an ASCII table with header row"
  [data]
  (if (and (coll? data) (seq data) (map? (first data)))
    (let [;; Extract all keys from all maps to handle cases where not all maps have the same keys
          all-keys (->> data
                        (mapcat keys)
                        distinct
                        (map name))
          ;; Convert all values to strings
          string-data (map (fn [row]
                             (reduce (fn [acc k]
                                       (assoc acc k (str (get row (keyword k) ""))))
                                     {}
                                     all-keys))
                           data)
          ;; Calculate column widths (max of header and all data)
          col-widths (reduce (fn [widths row]
                               (reduce (fn [w k]
                                         (let [val-width (count (get row k ""))
                                               header-width (count k)
                                               current-width (get w k 0)]
                                           (assoc w k (max current-width val-width header-width))))
                                       widths
                                       all-keys))
                             {}
                             string-data)
          ;; Format a row with proper padding
          format-row (fn [row]
                       (str "| " (str/join " | " (map (fn [k]
                                                        (let [val (get row k "")
                                                              width (get col-widths k 0)]
                                                          (format (str "%-" width "s") val)))
                                                      all-keys))
                            " |"))
          ;; Create header row
          header-row (format-row (zipmap all-keys all-keys))
          ;; Create separator row
          separator (str "+-" (str/join "-+-" (map (fn [k]
                                                     (apply str (repeat (get col-widths k 0) "-")))
                                                   all-keys))
                         "-+")
          ;; Format all data rows
          data-rows (map format-row string-data)]
      ;; Combine all parts
      (str/join "\n" (concat [separator header-row separator] data-rows [separator])))
    ;; If data is not a collection of maps, fall back to plain format
    (cond
      (map? data) (str/join "\n" (map #(str (name (key %)) ": " (val %)) data))
      (coll? data) (str/join "\n" (map str data))
      :else (str data))))

(defn format-output
  "Format data according to the specified format"
  [data format]
  (case format
    "json" (json/generate-string data {:pretty true})
    "edn" (pr-str data)
    "plain" (cond
              (map? data) (str/join "\n" (map #(str (name (key %)) ": " (val %)) data))
              (coll? data) (str/join "\n" (map #(str/join " | " (map (fn [[_ v]] (str v)) %)) data))
              :else (str data))
    "table" (format-as-table data)
    ;; default to json
    (json/generate-string data {:pretty true})))

(defn print-output
  "Print data according to the specified format"
  [data & {:keys [format] :or {format "json"}}]
  (println (format-output data format)))

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
    export-data <filename>               Export data to file"))

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
                ;; else
                (do
                  (println "Unknown command:" cmd)
                  (println (usage summary))
                  1))))))

;; The End
