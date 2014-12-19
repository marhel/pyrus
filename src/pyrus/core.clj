(ns pyrus.core
  (:require [clojure.math.combinatorics :as combinatorics]))

(set! *warn-on-reflection* true)

(defn- spy [t f] (fn
                   ([] (println t "=?"))
                   ([x] (let [fv (f x)] (println t x "=>" fv) fv))
                   ([x y]
                     (print t x y "=> ")
                     (let [fv (f x y)] (println fv) fv))
                   ([x y z]
                     (print t x y z "=> ")
                     (let [fv (f x y z)] (println fv) fv))))

(defn i-ndx [n param-list]
  (let [counts (apply hash-map (flatten param-list))]
    (into {} (map (fn [param-interaction]
                    {param-interaction (apply combinatorics/cartesian-product
                                              (map (fn [key] (range 0 (key counts))) param-interaction))})
                  (combinatorics/combinations (map first param-list) n)))))

(defn map-vals [f m]
  (reduce (fn [r [k v]] (assoc r k (f v))) {} m))

(def uncovered? neg?)

(defn invert-map [m]
  (reduce (fn [r [k v]] (assoc r v k)) {} m))

(defn min-idx [^ints best-piv]
  (areduce best-piv i t [-1 -1]
           (let [[idx min] t
                 v (aget best-piv i)]
             (if (or (> min v) (= -1 min))
               [i v]
               t))))

(defn in-position-order
  ([context m]
    (in-position-order m (:param-count context) (:param-position context)))
  ([m param-count param-position]
    (map (fn [k] [k (get param-count k)]) (map first (sort-by (fn [[k v]] (get param-position k)) m)))))

(defn setup-context [& args]
  (let [
        [param-position args] (if (even? (count args))
                                [(zipmap (mapcat (comp keys second) (partition 2 args)) (range)) args]
                                [(apply hash-map (flatten (map-indexed (fn [i k] [k i]) (first args)))) (rest args)])
        param-values (apply merge (map second (partition 2 args)))
        position-param (invert-map param-position)
        param-count (map-vals count param-values)
        interactions (apply merge (map (fn [[n pm]] (i-ndx n (in-position-order pm param-count param-position))) (partition 2 args)))
        interactions (reduce (fn [r [key vals]]
                               (assoc r key (map (fn im [val] (map (fn [pk pi] [(pk param-position) (nth val pi)])
                                                                   key
                                                                   (range)))
                                                 vals))) {} interactions)
        combination-index (reduce (fn [r [k cs]]
                                    (first (reduce (fn [[r index] combination]
                                                     [(assoc r combination index) (inc index)]
                                                     ) [r 0] cs))) {} interactions)
        ]
    {
     :param-values param-values
     :param-position param-position
     :position-param position-param
     :num-params (count position-param)
     :param-count param-count
     :interactions interactions
     :combination-index combination-index
     }))

(defn reset-variation [context usage]
  (assoc usage :variation (int-array (:num-params context) -1)))

(defn initialize-usage [context]
  (reset-variation context {:interaction (map-vals #(do (int-array (count %) 0)) (:interactions context))}))

(defn unused [usage]
  (let [interaction-usage (:interaction usage)]
    (map-vals (fn [^ints u] (areduce u i t 0 (+ t (if (zero? (aget u i)) -1 0)))) interaction-usage)))

(defn find-most-uncovered [context usage]
  (let [
        interactions (:interactions context)
        interaction-usage (:interaction usage)
        best-interaction-key (first (first (sort-by second (unused usage))))
        ^ints combination-usage (get interaction-usage best-interaction-key)
        [least-used-combo-ndx _] (min-idx combination-usage)
        combination (nth (get interactions best-interaction-key) least-used-combo-ndx)
        ]
    combination)
  )

(defn interaction-key [context param-key-list]
  (sort-by #(get-in context [:param-position %]) param-key-list))

(defn params [context combination]
  (map #(get (:position-param context) (first %)) combination))

(defn indexed [context combination]
  (sort-by first (map (fn [[pp pi]] [(get (:param-position context) pp) pi]) combination)))

(defn- cover-combination! [context usage combination]
  (let [
        combination-index (:combination-index context)
        ; _ (prn "cover-combination" combination)
        index (get combination-index combination)
        ^ints combination-usage (get (:interaction usage) (params context combination))
        ]
    (when (nil? combination-usage)
      (throw (IllegalArgumentException. (str "Combination usage null for combination" combination))))
    ; mutate usage
    (aset combination-usage index (int (inc (aget combination-usage index))))
    usage))

(defn cover-variation! [usage combination]
  (let [
        ^ints variation (:variation usage)
        ; _ (prn "cover-variation" combination)
        vlen (count variation)
        ]
    ; mutate variation
    (doall (map (fn mutate-variation [[ndx val]]
                  (when-not (< -1 ndx vlen)
                    (throw (IndexOutOfBoundsException. (format "Illegal parameter index %d for %d parameters" ndx vlen))))
                  (aset variation ndx (int val))) combination))
    usage))

(defn cover!
  ([context usage combination]
    (cover-combination! context usage combination)
    (cover-variation! usage combination))
  ([context usage affected-combinations combination]
    (doseq [a-cmb (if (empty? affected-combinations) [combination] affected-combinations)]
      (cover-combination! context usage a-cmb))
    (cover-variation! usage combination)))

(defn remaining-interactions [context usage]
  (let [
        ^ints variation (:variation usage)
        ndx-uncovered (set (->>
                             (map-indexed list variation)
                             (filter (comp uncovered? second))
                             (map first)))
        uncovered-params (set (->> (:param-position context)
                                   (filter #(ndx-uncovered (second %)))
                                   (map first)))
        uncovered-interactions (into {} (filter (fn [ia] (some #(uncovered-params %) (first ia))) (:interactions context)))]
    uncovered-interactions))

(defn compatible? [^ints variation combination]
  (every? (fn [[ndx val]]
            (when-not (< -1 ndx (count variation))
              (throw (IndexOutOfBoundsException. (format "Illegal parameter index %d for %d parameters" ndx (count variation)))))
            (let [picked (nth variation ndx)]
              (cond
                (uncovered? picked) true
                (= val picked) true
                :else false)))
          combination))

(defn compatible-interactions [context usage remaining-interactions]
  (let [
        ^ints variation (:variation usage)
        ]
    (map-vals (fn [combinations]
                (filter (partial compatible? variation) combinations))
              remaining-interactions)))

(defn uncovered-combinations [context usage compatible-interactions]
  (let [interaction-usage (:interaction usage)]
    (mapcat (fn [[ik combinations]]
              (let [^ints combination-usage (get-in usage [:interaction ik])]
                (when (nil? combination-usage)
                  (throw (IllegalArgumentException. (str "Combination usage null for interaction " ik))))
                (filter
                  (fn used? [combination]
                    (let [index (get-in context [:combination-index combination])]
                      (zero? (aget combination-usage index))))
                  combinations)))
            compatible-interactions)))

(defn fixed [usage]
  (let [^ints variation (:variation usage)]
    (apply sorted-set (->>
                        (map-indexed list variation)
                        (remove (comp uncovered? second))
                        (map vec)))))

(defn affected-by [uncovered-combinations fixed]
  (reduce (fn [r combination]
            (let [path (clojure.set/difference (apply sorted-set combination) fixed)
                  cpath (concat path [:count])
                  ipath (concat path [:items])
                  append-combination (fn [old] (conj old combination))
                  ]
              (-> r
                  (update-in cpath (fnil inc 0))
                  (update-in ipath (fnil append-combination []))))) {} uncovered-combinations))

(defn- affected [affected-by fixed combination suffix reducer unit]
  (let [path (clojure.set/difference (apply sorted-set combination) fixed)
        path (concat path [suffix])]
    (->> (range 1 (count path))
         (mapcat (fn [i] (combinatorics/combinations combination i)))
         (map (fn [path] (get-in affected-by (concat path [suffix]) unit)))
         (reduce reducer unit)
         )))

(defn affected-count [affected-by fixed combination]
  (affected affected-by fixed combination :count + 0))

(defn affected-combinations [affected-by fixed combination]
  (affected affected-by fixed combination :items concat []))

(defn find-most-covering [uncovered-combinations affected-by fixed]
  (when-not (empty? uncovered-combinations)
    (reduce (partial max-key (partial affected-count affected-by fixed)) uncovered-combinations))
  )

(defn find-any-compatible [usage compatible-interactions]
  ; (prn "3: pick-random-covering")
  ; should possibly take actual usage into account, preferring least used
  (rand-nth (mapcat identity (vals compatible-interactions))))

(defn explain-variation [context usage]
  (let [^ints variation (:variation usage)]
    (into {} (map-indexed (fn [pp pi]
                            (let [pk (get-in context [:position-param pp])]
                              (if (uncovered? pi)
                                {pk nil}
                                {pk (nth (get-in context [:param-values pk]) pi)}))) variation))))

(defn pick-most-uncovered [context usage]
  ; (prn "1: pick-most-uncovered")
  (cover! context usage (find-most-uncovered context usage)))

(defn pick-most-covering [context usage]
  ;(prn "2: pick-most-covering")
  (let [
        compatible (->> (remaining-interactions context usage)
                        (compatible-interactions context usage))
        uncovered (uncovered-combinations context usage compatible)
        fixed (fixed usage)
        affected-by (affected-by uncovered fixed)
        most-covering (find-most-covering uncovered affected-by fixed)
        chosen-combination (or most-covering (find-any-compatible usage compatible))
        affected-combinations (affected-combinations affected-by fixed chosen-combination)
        ]
    (cover! context usage affected-combinations chosen-combination)))

(defn generate-variation [context usage n]
  (let [
        usage (reset-variation context usage)
        unused (unused usage)
        variation (:variation usage)
        ]
    ; (prn "generate-variation" n "reset")
    (when (some uncovered? (vals unused))         ; additional variations needed?
      (while (some uncovered? variation)                    ; variation incomplete?
        (if (every? uncovered? variation)                   ; variation empty?
          (pick-most-uncovered context usage)
          (pick-most-covering context usage)
          ))
      (cons
        (explain-variation context usage)
        (lazy-seq (generate-variation context usage (inc n)))))))

(defn generate [& args]
  (let [context (apply setup-context args)
        usage (initialize-usage context)]
    (generate-variation context usage 0)))
