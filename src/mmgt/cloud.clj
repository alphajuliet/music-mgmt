(ns mmgt.cloud
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [mmgt.output :as output]
            [babashka.http-client :as http]))

(def mmgt-version "0.1.2")
(def api-version "1.0.0")

(def cli-options
  [["-h" "--help" "Show help information"]
   ["-v" "--version" "Show version information"]
   ["-f" "--format FORMAT" "Output format: table (default), json, edn, or plain"
    :default "table"
    :validate [#(contains? #{"json" "edn" "plain" "table"} %) "Must be one of: table, json, edn, plain"]]
   ["-u" "--api-url URL" "API base URL"
    :default "https://music.cyjet.online/api/v1"]
   [nil "--verbose" "Enable verbose output"]])

;; Utility functions (same as local CLI)
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

;; HTTP Client functions
(defn get-api-url [options]
  (or (:api-url options) 
      (System/getenv "MUSIC_API_URL")
      "https://music.cyjet.online/api/v1"))

(defn make-request
  "Make HTTP request to API"
  [method url options & [body]]
  (try
    (let [request-opts (cond-> {:headers {"Content-Type" "application/json"}}
                         body (assoc :body (json/generate-string body)))
          response (case method
                     :get (http/get url request-opts)
                     :post (http/post url request-opts)
                     :put (http/put url request-opts))]
      (if (>= (:status response) 400)
        {:success false :message (str "HTTP " (:status response) ": " (:body response))}
        (try
          (json/parse-string (:body response) true)
          (catch Exception e
            {:success false :message (str "Failed to parse response: " (.getMessage e))}))))
    (catch Exception e
      {:success false :message (str "Request failed: " (.getMessage e))})))

(defn api-get [endpoint options]
  (let [url (str (get-api-url options) endpoint)]
    (make-request :get url options)))

(defn api-post [endpoint options body]
  (let [url (str (get-api-url options) endpoint)]
    (make-request :post url options body)))

(defn api-put [endpoint options body]
  (let [url (str (get-api-url options) endpoint)]
    (make-request :put url options body)))

;; Output functions
(defn print-output
  "Print data according to the specified format"
  [data & {:keys [format] :or {format "table"}}]
  (println (output/format-output data format)))

(defn handle-api-response
  "Handle API response and print output or error"
  [response options]
  (cond
    ;; If response is a map with explicit success field and it's false, show error
    (and (map? response) (:success response) (false? (:success response)))
    (println "Error:" (:message response))
    
    ;; If response is a map with success=true, extract the data
    (and (map? response) (:success response))
    (let [data (or (:results response) (dissoc response :success :message))]
      (print-output data :format (:format options)))
    
    ;; If no success field or not a map, assume it's direct data (for GET endpoints)
    :else
    (print-output response :format (:format options))))

;; Command implementations
(defn all-tracks
  "List all tracks"
  [options]
  (let [response (api-get "/tracks" options)]
    (handle-api-response response options)))

(defn lookup
  "Lookup tracks that match on title"
  [title options]
  (let [encoded-title (java.net.URLEncoder/encode title "UTF-8")
        response (api-get (str "/tracks/search?q=" encoded-title) options)]
    (handle-api-response response options)))

(defn search
  "Search tracks by any field"
  [field value options]
  (let [valid-fields #{"artist" "type" "title" "year"}]
    (if (contains? valid-fields field)
      (let [encoded-field (java.net.URLEncoder/encode field "UTF-8")
            encoded-value (java.net.URLEncoder/encode value "UTF-8")
            response (api-get (str "/tracks/search?field=" encoded-field "&value=" encoded-value) options)]
        (handle-api-response response options))
      (println "Invalid field. Valid fields are:" (str/join ", " valid-fields)))))

(defn view-track
  "View a track with a given ID"
  [id options]
  (let [response (api-get (str "/tracks/" id) options)]
    (handle-api-response response options)))

(defn add-track
  "Create a new track with minimal info"
  [title options]
  (let [track-data {:title title
                    :artist "Cyjet"
                    :type "Original"
                    :year (current-year)
                    :length "00:00"}
        response (api-post "/tracks" options track-data)]
    (if (:success response)
      (println "OK - Track created with ID:" (:id response))
      (println "Error:" (:message response)))))

(defn update-track
  "Update track info"
  [id field value options]
  (let [valid-fields #{"title" "type" "artist" "year" "length" "bpm" "ISRC" "Genre" "song_fname"}]
    (if (contains? valid-fields field)
      (let [update-data {(keyword field) value}
            response (api-put (str "/tracks/" id) options update-data)]
        (if (:success response)
          (println "OK")
          (println "Error:" (:message response))))
      (println "Invalid field. Valid fields are:" (str/join ", " valid-fields)))))

(defn releases
  "List all releases"
  [options]
  (let [response (api-get "/releases" options)]
    (handle-api-response response options)))

(defn view-release
  "Show a release and the tracks"
  [id options]
  (let [response (api-get (str "/releases/" id) options)]
    (handle-api-response response options)))

(defn tracks
  "Just return the track list for a given release"
  [id options]
  (let [response (api-get (str "/releases/" id "/tracks") options)]
    (handle-api-response response options)))

(defn add-release
  "Create a new release"
  [id options]
  (let [release-data {:id id}
        response (api-post "/releases" options release-data)]
    (if (:success response)
      (println "OK")
      (println "Error:" (:message response)))))

(defn update-release
  "Update release info"
  [id field value options]
  (let [valid-fields #{"Name" "Status" "UPC" "Catalogue" "ReleaseDate" "PromoLink" "Bandcamp"}]
    (if (contains? valid-fields field)
      (let [update-data {(keyword field) value}
            response (api-put (str "/releases/" id) options update-data)]
        (if (:success response)
          (println "OK")
          (println "Error:" (:message response))))
      (println "Invalid field. Valid fields are:" (str/join ", " valid-fields)))))

(defn release
  "Add a track to a release"
  [track-id release-id track-number options]
  (let [request-data {:track_id (Integer/parseInt track-id)
                      :track_number (Integer/parseInt track-number)}
        response (api-post (str "/releases/" release-id "/tracks") options request-data)]
    (if (:success response)
      (println "OK")
      (println "Error:" (:message response)))))

(defn query
  "Query the cloud database with SQL"
  [query-str options]
  (let [request-data {:query query-str}
        response (api-post "/query" options request-data)]
    (handle-api-response response options)))

(defn export-data 
  "Export data from cloud database to file"
  [filename options]
  (let [response (api-get "/export" options)]
    (if (:success response)
      (do
        (spit filename (json/generate-string response {:pretty true}))
        (println "Data exported to" filename))
      (println "Error:" (:message response)))))

(defn usage [options-summary]
  (str "Music Management Cloud CLI

Usage: bb -m mmgt.cloud [options] command [args...]

Options:
" options-summary "

Commands:
    help                                 Show this help
    version                              Show version information
     
    all-tracks                           List all tracks
    lookup <title>                       Lookup tracks by title
    search <field> <value>               Search tracks by field
    add-track <title>                    Add a new track
    view-track <id>                      View a track
    update-track <id> <field> <value>    Update track info

    releases                             List all releases
    add-release <id>                     Add a new release
    view-release <id>                    View a release
    tracks <id>                          List the tracks on a release
    update-release <id> <field> <value>  Update release info
    release <track_id> <release_id> <track_number>  Add track to release

    query <sql>                          Run SQL query on cloud database
    export-data <filename>               Export cloud data to file

Note: This CLI connects to the cloud API. Use mmgt.mm for local database access."))

(defn help
  "Print help message"
  [options]
  (println (usage (:summary options))))

(defn version
  "Print version information"
  []
  (println "Music Management Cloud CLI version" mmgt-version)
  (println "API version" api-version))

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
                "all-tracks" (all-tracks options)
                "releases" (releases options)
                "lookup" (lookup (first cmd-args) options)
                "search" (search (first cmd-args) (second cmd-args) options)
                "view-track" (view-track (first cmd-args) options)
                "view-release" (view-release (first cmd-args) options)
                "tracks" (tracks (first cmd-args) options)
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
