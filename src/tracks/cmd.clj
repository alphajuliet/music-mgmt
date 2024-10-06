(ns tracks.cmd
  (:require [pod.babashka.go-sqlite3 :as sql]
            [clojure.pprint :refer [pprint]]))

(def DB "data/tracks.db")

(defn help
  "Print help message"
  []
  (println "Commands:
    help
    lookup <title>"))

(defn lookup
  "Lookup tracks by matching on title"
  [title]
  (let [q (str "SELECT * FROM tracks WHERE title LIKE '%" title "%'")] 
    (pprint (sql/query DB q))))

(defn -main
  [& _args]
  (case (first _args)
    "help" (help)
    "lookup" (lookup (second _args))
    :else (println "Usage: tracks <cmd> [<args>]")))
  
;; The End