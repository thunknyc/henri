(ns henri.statsd
  (:require [clojure.string :as s]
            [clojure.core.async :as a]
            [henri.core :refer [*trace-port*]]
            [henri.util :refer [printfe]]))

(defn tag->tag-string [tag]
  (if (string? tag) tag
      (format "%s:%s" (nth tag 0) (nth tag 1))))

(defn tags->string [tags]
  (if (not-empty tags)
    (str "|#" (s/join \, (map tag->tag-string tags)))
    ""))

(defn value->string
  [value type]
  (format "%d" value))

(defn statsd-payload
  [metric value type
   {:keys [sample-rate tags]
    :or {sample-rate 0.5
         tags []}
    :as options}]
  (let [value-string (value->string value type)
        tags-string (tags->string tags)]
    (format "%s:%s|%s|@%.2f%s"
            metric value-string type sample-rate tags-string)))

(defn prefixize
  [prefix metric]
  (if prefix
    (str prefix "." metric)
    metric))

(defn statsd-sender
  ([host port] (statsd-sender host port nil))
  ([host port prefix]
   (let [addr (java.net.InetSocketAddress. host port)
         socket (java.net.DatagramSocket.)]
     (fn f
       ([metric value type]
        (f metric value type nil))
       ([metric value type options]
        (let [payload
              (statsd-payload (prefixize prefix metric) value type options)
              bytes
              (.getBytes payload)
              dgram
              (java.net.DatagramPacket. bytes (count bytes) addr)]
          (.send socket dgram)))))))

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

(def statsd-endpoint
  {:host "localhost"
   :port 8125})

(defn- statsd-logger
  ([prefix] (statsd-logger prefix nil))
  ([prefix options]
   (let [sender (statsd-sender (:host statsd-endpoint)
                               (:port statsd-endpoint))]
     (fn [rec]
       (try
         (let [metric (stack->metric prefix (:stack rec))
               millis (-> (:nanos rec)
                          (* 0.000001)
                          Math/ceil
                          int)]
           (sender metric millis "ms" options)))))))

(defn trace-sender
  ([stop] (trace-sender nil stop))
  ([prefix stop]
   (a/go
     (let [log (statsd-logger prefix)]
       (a/loop [[rec ch] (a/alts! [*trace-port* stop])]
         (if rec
           (do (when (= :exit (:event rec)) (log rec))
               (recur (a/alts! [*trace-port* stop])))
           (printfe "statsd trace sender stopped.\n")))))))
