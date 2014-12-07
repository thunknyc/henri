(ns henri.hostedgraphite
  (:require [clojure.string :as s]
            [clojure.core.async :as a]
            [henri.core :refer [*trace-port*]]
            [henri.util :refer [printfe]])
  (:import [java.net Socket]))

(defn- label->str
  [l]
  (cond (var? l) (subs (str l) 2)
        :else "UNBOUND"))

(defn- stack->metric
  [prefix stack]
  (let [metric
        (-> (s/join \. (map label->str stack))
            (s/replace #"/" "."))]
    (if prefix (str prefix "." metric) metric)))

(def ^{:dynamic true} *graphite-endpoint*
  (atom {:host "carbon.hostedgraphite.com"
         :port 2003}))

(def ^{:dynamic true :private true} *graphite-outstream* (atom nil))


(defn- reset-graphite-outstream
  []
  (reset! *graphite-outstream*
          (-> (Socket. (:host @*graphite-endpoint*)
                       (:port @*graphite-endpoint*))
              .getOutputStream)))

(defn- graphite-outstream
  []
  (if-let [s @*graphite-outstream*] s (reset-graphite-outstream)))

(defn- graphite-logger
  [apikey prefix]
  (fn [rec]
    (try
      (let [out (graphite-outstream)
            metric (stack->metric prefix (:stack rec))
            micros (* (:nanos rec) 0.001)
            line (format "%s.%s %.1f\n" apikey metric micros)]
       (.write out (.getBytes line)))
      (catch Exception e
        (printfe "graphite logger network problem; resetting.\n")
        (try
          (reset-graphite-outstream)
          (printfe "graphite logger network reset succeeded.\n")
          (catch Exception e
            (printfe "graphite logger network reset failed.\n")))))))

(defn trace-sender
  ([apikey] (trace-sender apikey nil))
  ([apikey prefix]
   (let [stop (a/chan)]
     (a/go
       (let [log (graphite-logger apikey prefix)]
         (a/loop [[rec ch] (a/alts! [*trace-port* stop])]
           (if rec
             (do (when (= :exit (:event rec)) (log rec))
                 (recur (a/alts! [*trace-port* stop])))
             (printfe "graphite trace sender stopped.\n")))))
     (fn [] (a/close! stop)))))

 
