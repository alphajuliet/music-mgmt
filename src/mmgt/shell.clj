(ns mmgt.shell
  (:require [clojure.string :as str]
            [mmgt.cloud :as cloud]))

(import [org.jline.terminal TerminalBuilder]
        [org.jline.reader LineReaderBuilder Completer Candidate
                          EndOfFileException UserInterruptException
                          LineReader$Option])

(def mmgt-version "0.1.2")

(def commands
  ["help" "quit" "all-tracks" "lookup" "search" "view-track" "add-track"
   "update-track" "releases" "view-release" "tracks" "add-release"
   "update-release" "release" "query" "export-data" "linked-data"])

(def cache (atom {:tracks [] :releases []}))

(def track-id-commands #{"view-track" "update-track" "release"})

(defn refresh-cache!
  "Fetch tracks and releases from the API and update the cache."
  [options]
  (try
    (let [tracks (cloud/api-get "/tracks" options)
          releases (cloud/api-get "/releases" options)]
      (reset! cache {:tracks (if (sequential? tracks) tracks [])
                     :releases (if (sequential? releases) releases [])}))
    (catch Exception e
      (println "Warning: could not refresh cache:" (.getMessage e)))))

(defn parse-args
  "Parse a command line string into tokens, respecting quoted strings."
  [line]
  (let [matcher (re-matcher #"\"([^\"]*)\"|(\S+)" line)]
    (loop [tokens []]
      (if (.find matcher)
        (recur (conj tokens (or (.group matcher 1) (.group matcher 2))))
        tokens))))

(defn shell-help []
  (println "Commands:
    all-tracks                              List all tracks
    lookup <title>                          Lookup tracks by title
    search <field> <value>                  Search tracks by field
    view-track <title-or-id>                View a track
    add-track <title>                       Add a new track
    update-track <title-or-id> <field> <val>  Update track info

    releases                                List all releases
    view-release <id>                       View a release
    tracks <release-id>                     List tracks on a release
    add-release <id>                        Add a new release
    update-release <id> <field> <value>     Update release info
    release <track> <release-id> <track#>   Add track to release

    query <sql>                             Run SQL query
    export-data <filename>                  Export data to file
    linked-data <filename>                  Export as JSON-LD

    help                                    Show this help
    quit                                    Exit (also Ctrl-D)"))

(defn resolve-track
  "Resolve a track title or ID to a numeric ID string.
   If input is numeric, return it as-is.
   Otherwise search the cache for matching titles."
  [input]
  (if (re-matches #"\d+" input)
    input
    (let [matches (filter #(= (str/lower-case (:title %))
                              (str/lower-case input))
                          (:tracks @cache))]
      (case (count matches)
        0 (do (println (str "No track found matching \"" input "\""))
              nil)
        1 (str (:id (first matches)))
        (do (println "Multiple tracks match:")
            (doseq [t matches]
              (println (str "  " (:id t) ": " (:title t))))
            nil)))))

(defn dispatch
  "Dispatch a parsed command to the appropriate cloud.clj function."
  [tokens options]
  (let [cmd (first tokens)
        args (rest tokens)]
    (case cmd
      "help" (shell-help)
      "all-tracks" (cloud/all-tracks options)
      "releases" (cloud/releases options)
      "lookup" (cloud/lookup (first args) options)
      "search" (cloud/search (first args) (second args) options)
      "view-track" (when-let [id (resolve-track (first args))]
                     (cloud/view-track id options))
      "view-release" (cloud/view-release (first args) options)
      "tracks" (cloud/tracks (first args) options)
      "add-track" (do (cloud/add-track (first args) options)
                      (refresh-cache! options))
      "update-track" (when-let [id (resolve-track (first args))]
                       (cloud/update-track id (second args) (nth args 2) options)
                       (refresh-cache! options))
      "add-release" (do (cloud/add-release (first args) options)
                        (refresh-cache! options))
      "update-release" (do (cloud/update-release (first args) (second args) (nth args 2) options)
                           (refresh-cache! options))
      "release" (when-let [id (resolve-track (first args))]
                  (cloud/release id (second args) (nth args 2) options)
                  (refresh-cache! options))
      "query" (cloud/query (str/join " " args) options)
      "export-data" (cloud/export-data (first args) options)
      "linked-data" (cloud/linked-data (first args) options)
      (println "Unknown command:" cmd ". Type 'help' for available commands."))))

(defn make-completer
  "Create a completer that handles command names and context-dependent arguments."
  []
  (reify Completer
    (complete [_this _reader line candidates]
      (let [word (.word line)
            word-index (.wordIndex line)
            words (.words line)
            command (when (pos? word-index) (first words))
            lword (str/lower-case word)]
        (cond
          ;; Word 0: complete command names
          (zero? word-index)
          (doseq [cmd commands]
            (when (.startsWith ^String cmd word)
              (.add candidates (Candidate. ^String cmd))))

          ;; Word 1 for track-ID commands: complete track titles
          (and (= word-index 1) (contains? track-id-commands command))
          (doseq [track (:tracks @cache)]
            (let [title (:title track)]
              (when (.startsWith (str/lower-case title) lword)
                ;; Quote titles containing spaces
                (let [display-val (if (str/includes? title " ")
                                   (str "\"" title "\"")
                                   title)]
                  (.add candidates (Candidate. display-val)))))))))))

(defn -main [& args]
  (let [api-url (or (first args)
                    (System/getenv "MUSIC_API_URL")
                    "https://music.cyjet.online/api/v1")
        options {:format "table" :api-url api-url}
        terminal (-> (TerminalBuilder/builder)
                     (.system true)
                     (.build))
        completer (make-completer)
        reader (-> (LineReaderBuilder/builder)
                   (.terminal terminal)
                   (.completer completer)
                   (.build))]
    (.setOpt reader LineReader$Option/AUTO_LIST)
    (.setOpt reader LineReader$Option/AUTO_MENU)
    (println (str "Music Management Shell v" mmgt-version))
    (println (str "Connected to " api-url))
    (print "Loading data... ") (flush)
    (refresh-cache! options)
    (println (str "OK (" (count (:tracks @cache)) " tracks, "
                  (count (:releases @cache)) " releases)"))
    (println "Type 'help' for commands, TAB for completion")
    (println)
    (try
      (loop []
        (let [line (try
                     (str/trim (.readLine reader "mmgt> "))
                     (catch UserInterruptException _ :interrupted))]
          (cond
            (= line :interrupted)
            (do (println "\nType 'quit' to exit.")
                (recur))

            (str/blank? line)
            (recur)

            (= line "quit")
            (println "Goodbye!")

            :else
            (do
              (try
                (dispatch (parse-args line) options)
                (catch Exception e
                  (println "Error:" (.getMessage e))))
              (recur)))))
      (catch EndOfFileException _
        (println "\nGoodbye!"))
      (finally
        (.close terminal)))))
