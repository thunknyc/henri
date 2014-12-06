(ns henri.util)

(defn printfe
  [& args]
  (binding [*out* *err*] (apply printf args)))
