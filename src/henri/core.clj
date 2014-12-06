(ns henri.core
  (:require [richelieu.core :as r :refer :all]
            [clojure.core.async :as a]))

(def ^:dynamic *trace-port* (a/chan (a/sliding-buffer 10000)))
(def ^:dynamic *trace-stack* nil)

(defn ^:richelieu.core/no-advice deref-maybe
  [p]
  (if (instance? clojure.lang.IDeref p) @p p))

(defn channel-tracer
  "Given port `p`, return an advice function that puts function
  invocations and function returns unto it."
  [p]
  (fn [f & args]
    (binding [*trace-stack*
              ((fnil conj []) *trace-stack* *current-advised*)]
      (a/>!! (deref-maybe p) {:event :enter
                              :args args
                              :stack *trace-stack*})
      (let [nanos (System/nanoTime)
            result (apply f args)
            elapsed (- (System/nanoTime) nanos)]
        (a/>!! (deref-maybe p) {:event :exit
                                :args args
                                :result result
                                :nanos elapsed
                                :stack *trace-stack*})
        result))))

(def ^:richelieu.core/no-advice
  default-tracer (channel-tracer #'*trace-port*))

(defn trace-ns [ns] (advise-ns ns #'default-tracer))
(defn untrace-ns [ns] (unadvise-ns ns #'default-tracer))
(defn trace-var [var] (advise-var var #'default-tracer))
(defn untrace-var [var] (unadvise-var var #'default-tracer))


