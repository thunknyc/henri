(ns henri.basic
  (:require [clojure.core.async :as a]
            [henri.util :refer [printfe]]))

(defn- print-trace-monitor-record
  [rec]
  (printfe "> %s %s => %s in %.1fµs.\n"
           (:stack rec) (:args rec) (:result rec)
           (* (:nanos rec) 0.001)))

(defn monitor
  [stop]
  (a/go-loop [[rec ch] (a/alts! [*trace-port* stop])]
    (if rec
      (do (when (= :exit (:event rec)) (print-trace-monitor-record rec))
          (recur (a/alts! [*trace-port* out])))
      (printfe "> monitor stopped."))))
