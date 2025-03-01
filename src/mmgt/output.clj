(ns mmgt.output
  (:require [clojure.string :as str]
            [cheshire.core :as json]))

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
