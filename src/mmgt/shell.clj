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
      "view-track" (cloud/view-track (first args) options)
      "view-release" (cloud/view-release (first args) options)
      "tracks" (cloud/tracks (first args) options)
      "add-track" (cloud/add-track (first args) options)
      "update-track" (cloud/update-track (first args) (second args) (nth args 2) options)
      "add-release" (cloud/add-release (first args) options)
      "update-release" (cloud/update-release (first args) (second args) (nth args 2) options)
      "release" (cloud/release (first args) (second args) (nth args 2) options)
      "query" (cloud/query (str/join " " args) options)
      "export-data" (cloud/export-data (first args) options)
      "linked-data" (cloud/linked-data (first args) options)
      (println "Unknown command:" cmd ". Type 'help' for available commands."))))

(defn make-completer
  "Create a completer that completes command names on word index 0."
  []
  (reify Completer
    (complete [_this _reader line candidates]
      (let [word (.word line)
            word-index (.wordIndex line)]
        (when (zero? word-index)
          (doseq [cmd commands]
            (when (.startsWith ^String cmd word)
              (.add candidates (Candidate. ^String cmd)))))))))

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
