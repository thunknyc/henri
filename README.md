# henri

Tracing for Clojure

## Usage

FIXME

## Example

```clojure
(ns example
  (:require
   [clojure.core.async :as a]
   [henri.core :refer [trace-var untrace-var]]
   [henri.hostedgraphite :as hostedgraphite]
   [henri.statsd :as statsd]))

(defn add [& xs] (apply + xs))
(defn mult [& xs] (apply * xs))
(defn sum-squares [& xs] (apply add (map #(mult % %) xs)))

#_(def stop-hostedgraphite-sender
    (hostedgraphite/trace-sender
     "ead729e8-3c0e-46b3-bda8-b79f261cbb2e"
     "test"
     stop-hostgraphite))

(def stop-statsd-sender! (statsd/trace-sender "test"))

(trace-var #'add)
(trace-var #'mult)
(trace-var #'sum-squares)

(defn run-test []
  (future
    (doseq [i (range (* 60 600))]
      (apply sum-squares (map (fn [_] (rand-int 10000))
                              (range (rand-int 10000))))
      (Thread/sleep 80))))
```
## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
