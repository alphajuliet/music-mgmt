# Interactive Shell Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an interactive JLine3-based REPL shell that connects to the cloud REST API with tab-completion of track titles, release IDs, and field names.

**Architecture:** New `src/mmgt/shell.clj` entry point using JLine3 (bundled in Babashka). Reuses `cloud.clj` for API calls and command implementations. A completion cache (atom) holds tracks and releases fetched from the API on startup.

**Tech Stack:** Babashka, JLine3 (bundled), existing cloud REST API.

**Design doc:** `docs/plans/2026-03-05-interactive-shell-design.md`

---

### Task 1: Minimal REPL loop with command dispatch

**Files:**
- Create: `src/mmgt/shell.clj`

**Step 1: Create the shell namespace with JLine3 imports and REPL loop**

Create `src/mmgt/shell.clj` with the following:

```clojure
(ns mmgt.shell
  (:require [clojure.string :as str]
            [mmgt.cloud :as cloud]))

(import [org.jline.terminal TerminalBuilder]
        [org.jline.reader LineReaderBuilder
                          EndOfFileException
                          UserInterruptException])

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

(defn -main [& args]
  (let [api-url (or (first args)
                    (System/getenv "MUSIC_API_URL")
                    "https://music.cyjet.online/api/v1")
        options {:format "table" :api-url api-url}
        terminal (-> (TerminalBuilder/builder)
                     (.system true)
                     (.build))
        reader (-> (LineReaderBuilder/builder)
                   (.terminal terminal)
                   (.build))]
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
```

**Step 2: Test the shell manually**

Run: `bb -m mmgt.shell`

Verify:
- Welcome banner prints with version and API URL
- `mmgt> ` prompt appears
- `help` prints command list
- `all-tracks` fetches and displays tracks in table format
- `releases` fetches and displays releases
- `view-track 1` shows a track (use a known ID)
- `quit` exits cleanly
- Ctrl-D exits cleanly
- Ctrl-C prints message and continues
- Unknown commands print error message
- Quoted strings work: `lookup "some title"`

**Step 3: Commit**

```bash
git add src/mmgt/shell.clj
git commit -m "feat: add minimal interactive shell with command dispatch"
```

---

### Task 2: Command name completion

**Files:**
- Modify: `src/mmgt/shell.clj`

**Step 1: Add Completer import and command completer**

Add `Completer` and `Candidate` to the imports:

```clojure
(import [org.jline.terminal TerminalBuilder]
        [org.jline.reader LineReaderBuilder Completer Candidate
                          EndOfFileException UserInterruptException
                          LineReader$Option])
```

Add a completer function:

```clojure
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
```

**Step 2: Register completer with LineReader and enable auto-menu**

Update the `-main` function's `reader` binding:

```clojure
        completer (make-completer)
        reader (-> (LineReaderBuilder/builder)
                   (.terminal terminal)
                   (.completer completer)
                   (.build))
```

After building the reader, enable auto-list and auto-menu:

```clojure
    (.setOpt reader LineReader$Option/AUTO_LIST)
    (.setOpt reader LineReader$Option/AUTO_MENU)
```

**Step 3: Test manually**

Run: `bb -m mmgt.shell`

Verify:
- Type `vie<TAB>` → completes or shows `view-track` and `view-release`
- Type `a<TAB>` → shows `all-tracks`, `add-track`, `add-release`
- Type `all-tracks<ENTER>` still works normally
- Type `help<TAB>` → completes to `help`

**Step 4: Commit**

```bash
git add src/mmgt/shell.clj
git commit -m "feat: add tab-completion for command names"
```

---

### Task 3: Completion cache and track title completion

**Files:**
- Modify: `src/mmgt/shell.clj`

**Step 1: Add completion cache atom and fetch function**

Add `cheshire.core` to the requires (needed to parse API responses directly):

```clojure
(ns mmgt.shell
  (:require [clojure.string :as str]
            [mmgt.cloud :as cloud]))
```

Add cache atom and refresh function using cloud.clj's API functions:

```clojure
(def cache (atom {:tracks [] :releases []}))

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
```

Note: `cloud/api-get` is currently a private-ish function. We need it accessible. Check if it's public — it is, since Clojure functions are public by default unless declared with `defn-`.

**Step 2: Add title-to-ID resolution**

```clojure
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
```

**Step 3: Update dispatch to resolve track titles**

Update the `dispatch` function to use `resolve-track` for commands that take track IDs:

```clojure
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
```

**Step 4: Update make-completer to offer track titles**

Define which commands take track IDs as their first argument:

```clojure
(def track-id-commands #{"view-track" "update-track" "release"})
```

Update `make-completer` to complete track titles when word-index is 1 and the command takes a track ID:

```clojure
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
```

**Step 5: Call refresh-cache! on startup, show counts in banner**

In `-main`, after building the reader and before the REPL loop:

```clojure
    (println (str "Music Management Shell v" mmgt-version))
    (println (str "Connected to " api-url))
    (print "Loading data... ") (flush)
    (refresh-cache! options)
    (println (str "OK (" (count (:tracks @cache)) " tracks, "
                  (count (:releases @cache)) " releases)"))
    (println "Type 'help' for commands, TAB for completion")
    (println)
```

**Step 6: Test manually**

Run: `bb -m mmgt.shell`

Verify:
- Startup shows track/release counts
- Type `view-track <TAB>` → shows track titles from the database
- Type `view-track "Some Track Title"<ENTER>` → resolves to ID and shows track details
- Type `view-track 1<ENTER>` → still works with numeric IDs
- Adding a track then tab-completing shows the new track

**Step 7: Commit**

```bash
git add src/mmgt/shell.clj
git commit -m "feat: add track title completion and title-to-ID resolution"
```

---

### Task 4: Release ID and field name completion

**Files:**
- Modify: `src/mmgt/shell.clj`

**Step 1: Define command argument types**

```clojure
(def release-id-commands #{"view-release" "tracks" "add-release" "update-release"})
(def track-field-names ["title" "type" "artist" "year" "length" "bpm" "ISRC" "Genre" "song_fname"])
(def release-field-names ["Name" "Status" "UPC" "Catalogue" "ReleaseDate" "PromoLink" "Bandcamp"])
(def search-field-names ["artist" "type" "title" "year"])
```

**Step 2: Expand make-completer to handle all argument positions**

Replace `make-completer` with the full version:

```clojure
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
                (let [display-val (if (str/includes? title " ")
                                   (str "\"" title "\"")
                                   title)]
                  (.add candidates (Candidate. display-val))))))

          ;; Word 1 for release-ID commands: complete release IDs
          (and (= word-index 1) (contains? release-id-commands command))
          (doseq [release (:releases @cache)]
            (let [id (str (:ID release))]
              (when (.startsWith (str/lower-case id) lword)
                (.add candidates (Candidate. id)))))

          ;; Word 1 for search: complete field names
          (and (= word-index 1) (= command "search"))
          (doseq [field search-field-names]
            (when (.startsWith field lword)
              (.add candidates (Candidate. field))))

          ;; Word 2 for release command: complete release IDs
          (and (= word-index 2) (= command "release"))
          (doseq [release (:releases @cache)]
            (let [id (str (:ID release))]
              (when (.startsWith (str/lower-case id) lword)
                (.add candidates (Candidate. id)))))

          ;; Word 2 for update-track: complete track field names
          (and (= word-index 2) (= command "update-track"))
          (doseq [field track-field-names]
            (when (.startsWith field lword)
              (.add candidates (Candidate. field))))

          ;; Word 2 for update-release: complete release field names
          (and (= word-index 2) (= command "update-release"))
          (doseq [field release-field-names]
            (when (.startsWith field lword)
              (.add candidates (Candidate. field)))))))))
```

**Step 3: Test manually**

Run: `bb -m mmgt.shell`

Verify:
- `view-release <TAB>` → shows release IDs
- `update-track "SomeTrack" <TAB>` → shows field names (title, length, bpm, etc.)
- `update-release CYJET-EP01 <TAB>` → shows release fields (Name, Status, etc.)
- `search <TAB>` → shows searchable fields (artist, type, title, year)
- `release "SomeTrack" <TAB>` → shows release IDs for the second argument

**Step 4: Commit**

```bash
git add src/mmgt/shell.clj
git commit -m "feat: add release ID and field name completion"
```

---

### Task 5: Command history and polish

**Files:**
- Modify: `src/mmgt/shell.clj`

**Step 1: Add persistent command history**

Add import for `DefaultHistory`:

```clojure
(import [org.jline.terminal TerminalBuilder]
        [org.jline.reader LineReaderBuilder Completer Candidate
                          EndOfFileException UserInterruptException
                          LineReader$Option LineReader]
        [org.jline.reader.impl.history DefaultHistory])
```

In `-main`, set the history file variable before building the reader:

```clojure
        history-file (str (System/getProperty "user.home") "/.mmgt_history")
```

After building the reader, set the history file:

```clojure
    (.setVariable reader LineReader/HISTORY_FILE history-file)
```

Note: If `DefaultHistory` is not available in Babashka's allowlist, the `LineReader/HISTORY_FILE` variable approach should still work as JLine3's default LineReader implementation handles history file persistence automatically.

**Step 2: Add a `refresh` command to manually reload the cache**

Add `"refresh"` to the `commands` vector.

In `dispatch`, add:

```clojure
      "refresh" (do (print "Refreshing... ") (flush)
                    (refresh-cache! options)
                    (println (str "OK (" (count (:tracks @cache)) " tracks, "
                                  (count (:releases @cache)) " releases)")))
```

Add to `shell-help`:

```
    refresh                                 Reload track/release data from API
```

**Step 3: Handle API connection failure gracefully on startup**

If the API is unreachable on startup, print a warning but still start the shell (with empty cache). The current `refresh-cache!` already catches exceptions.

**Step 4: Test manually**

Run: `bb -m mmgt.shell`

Verify:
- Run a few commands, quit, relaunch — up-arrow shows previous commands
- `~/.mmgt_history` file is created
- `refresh` command reloads the cache and prints counts
- Starting with no network prints a warning but still shows the prompt

**Step 5: Commit**

```bash
git add src/mmgt/shell.clj
git commit -m "feat: add command history persistence and refresh command"
```

---

### Task 6: API URL flag and bb.edn task alias

**Files:**
- Modify: `bb.edn`

**Step 1: Add a bb task alias for easy launching**

Update `bb.edn` to add a `:tasks` section:

```clojure
{:paths ["src"]
 :deps {org.babashka/http-client {:mvn/version "0.4.22"}}
 :pods {org.babashka/go-sqlite3 {:version "0.2.4"}}
 :tasks {shell {:doc "Launch interactive shell"
                :task (exec 'mmgt.shell/-main)}}}
```

This allows launching with: `bb shell`

**Step 2: Test manually**

Run: `bb shell`

Verify:
- Shell launches with same behavior as `bb -m mmgt.shell`
- `bb shell` is shorter and easier to remember

**Step 3: Commit**

```bash
git add bb.edn
git commit -m "feat: add bb shell task alias for interactive shell"
```
