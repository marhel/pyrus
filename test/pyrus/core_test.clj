(ns pyrus.core-test
  (:require [midje.sweet :refer :all]
            [pyrus.core :as pyrus]))

(def context (pyrus/setup-context [:A :B :C]
                                     1 {:A [:a1 :a2 :a3]}
                                     2 {:B [:b1 :b2]
                                        :C [:c1 :c2 :c3]}))
(def larger-context (pyrus/setup-context [:A :B :C :D]
                                            1 {:A [:a1 :a2 :a3]}
                                            2 {:B [:b1 :b2]
                                               :C [:c1 :c2 :c3]
                                               :D [:d1 :d2]}))

(facts "context"
       context => (contains {:interactions anything
                             :param-position {:A 0 :B 1 :C 2}
                             :position-param {0 :A 1 :B 2 :C}
                             :num-params 3
                             :param-count {:A 3 :B 2 :C 3}
                             :combination-index anything})
       larger-context => (contains {:interactions anything
                                    :param-position {:A 0 :B 1 :C 2 :D 3}
                                    :position-param {0 :A 1 :B 2 :C 3 :D}
                                    :num-params 4
                                    :param-count {:A 3 :B 2 :C 3 :D 2}
                                    :combination-index anything}))

(facts "find most uncovered"
       (let [
             usage (pyrus/initialize-usage context)
             variation (:variation usage)
             initially-every-uncovered (every? pyrus/uncovered? variation)
             usages (pyrus/unused usage)
             best-combination (pyrus/find-most-uncovered context usage)
             new-usage (pyrus/cover! context usage best-combination)
             ]
         initially-every-uncovered => true
         usages => '{(:A) -3 (:B :C) -6}
         (pyrus/params context best-combination) => [:B :C]
         (map second best-combination) => [0 0]
         new-usage => (contains {:interaction anything
                                 :variation anything})
         (pyrus/unused usage) => '{(:A) -3 (:B :C) -5}

         (reduce + variation) => -1
         (nth variation (first (map first best-combination))) => 0
         (nth variation (second (map first best-combination))) => 0
         (some pyrus/uncovered? variation) => true
         (every? pyrus/uncovered? variation) => false
         ))

(facts "compatible"
       (let [
             variation (int-array (:num-params context) -1)
             filled-variation (int-array (:num-params context) 0)
             ]
         (pyrus/compatible? variation [[0 (int (rand 100000))]]) => true
         (pyrus/compatible? variation [[1 (int (rand 100000))][2 (int (rand 100000))]]) => true
         (pyrus/compatible? filled-variation [[0 100]]) => false
         (pyrus/compatible? filled-variation [[0 0]]) => true
         (pyrus/compatible? filled-variation [[1 0] [2 0]]) => true
         (pyrus/compatible? filled-variation [[1 0] [2 1]]) => false
         ))

(facts "indexed"
       (let [
             bd-combination (pyrus/indexed larger-context [[:B 1] [:D 1]])
             inverted-combination (pyrus/indexed larger-context [[:D 1] [:B 1]])]
         bd-combination => inverted-combination)
       )

(facts "find uncovered interactions"
       (let [
             a-usage (pyrus/initialize-usage context)
             bc-usage (pyrus/initialize-usage context)
             bd-usage (pyrus/initialize-usage larger-context)

             a-combination (pyrus/indexed context [[:A 1]])
             bc-combination (pyrus/indexed context [[:B 0] [:C 2]])
             bd-combination (pyrus/indexed larger-context [[:B 1] [:D 1]])

             a-usage (pyrus/cover! context a-usage a-combination)
             bc-usage (pyrus/cover! context bc-usage bc-combination)
             bd-usage (pyrus/cover! larger-context bd-usage bd-combination)
             ]
         a-combination => [[0 1]]
         bc-combination => [[1 0] [2 2]]
         bd-combination => [[1 1] [3 1]]
         (pyrus/remaining-interactions context a-usage) => (just {[:B :C] anything})
         (pyrus/remaining-interactions context bc-usage) => (just {[:A] anything})
         (pyrus/remaining-interactions larger-context bd-usage) => (just {[:A] anything [:B :C] anything [:C :D] anything})
         ))

(defn inorder-vals-for [f] (fn [& ks]
                             (let [original-pos (apply hash-map (flatten (map list ks (range))))
                                   original-order (fn [[pp pi]] (get original-pos (get-in larger-context [:position-param pp])))]
                               (sort (map vec (map (fn [combination] (map second (sort-by original-order combination))) (f ks)))))))

(facts "find compatible combinations"
       (let [usage (pyrus/initialize-usage larger-context)
             b-combination (pyrus/indexed larger-context [[:B 1] [:C 1]])
             usage (pyrus/cover! larger-context usage b-combination)
             uncovered (pyrus/remaining-interactions larger-context usage)
             spy (fn [v] (prn v) v)
             compatible (fn [ks] (get (pyrus/compatible-interactions larger-context usage uncovered) (pyrus/interaction-key larger-context ks)))
             compatible-vals-for (inorder-vals-for compatible)
             ]
         (compatible-vals-for :A) => [[0] [1] [2]]
         (compatible-vals-for :B :D) => [[1 0] [1 1]]
         ; just checking value order is controlled by parameter order
         (compatible-vals-for :C :D) => [[1 0] [1 1]]
         ))

(facts "fixed"
       (let [
             usage (pyrus/initialize-usage context)
             a-combination (pyrus/indexed context [[:A 1]])
             b-combination (pyrus/indexed context [[:B 1] [:C 1]])
             usage (pyrus/cover! context usage a-combination)
             usage (pyrus/cover! context usage b-combination)
             fixed (pyrus/fixed usage)]
         fixed => (set [[0 1] [1 1] [2 1]])
         ))

(facts "find uncovered combinations"
       (let [
             usage (pyrus/initialize-usage larger-context)
             b-combination (pyrus/indexed larger-context [[:B 1] [:C 1]])
             usage (pyrus/cover! larger-context usage b-combination)
             usage (pyrus/reset-variation larger-context usage)
             a-combination (pyrus/indexed larger-context [[:A 1]])
             usage (pyrus/cover! larger-context usage a-combination)
             remaining (pyrus/remaining-interactions larger-context usage)
             compatible (pyrus/compatible-interactions larger-context usage remaining)
             uncovered (fn [ks] (pyrus/uncovered-combinations larger-context usage (let [ik (pyrus/interaction-key larger-context ks)]
                                                                                        {ik (get compatible ik)})))
             uncovered-vals-for (inorder-vals-for uncovered)
             ]
         (uncovered-vals-for :A) => []
         (uncovered-vals-for :B :C) => [[0 0] [0 1] [0 2] [1 0] ; but not [1 1] as that was covered earlier already
                                        [1 2]
                                        ]
         (uncovered-vals-for :C :D) => [[0 0] [0 1] [1 0] [1 1] [2 0] [2 1]]
         (uncovered-vals-for :D :C) => [[0 0] [0 1] [0 2] [1 0] [1 1] [1 2]]
         ))

(facts "affected by"
       (let [
             usage (pyrus/initialize-usage larger-context)
             uncovered (->> (pyrus/remaining-interactions larger-context usage)
                            (pyrus/compatible-interactions larger-context usage)
                            (pyrus/uncovered-combinations larger-context usage))
             fixed (pyrus/fixed usage)
             affected-by (pyrus/affected-by uncovered fixed)
             fixed-affected-by (pyrus/affected-by uncovered (set [[1 1] [2 2] [3 0]]))
             ]
         (get affected-by [0 0]) => {:count 1, :items '[([0 0])]}
         (get affected-by [1 0]) => (contains {[2 0] {:count 1 :items '[([1 0] [2 0])]},
                                               [2 1] (contains {:count 1}),
                                               [2 2] (contains {:count 1}),
                                               [3 1] (contains {:count 1}),
                                               [3 0] (contains {:count 1})})
         (get affected-by [1 1]) => {[2 0] {:count 1 :items '[([1 1] [2 0])]},
                                     [2 1] {:count 1 :items '[([1 1] [2 1])]},
                                     [2 2] {:count 1 :items '[([1 1] [2 2])]},
                                     [3 1] {:count 1 :items '[([1 1] [3 1])]},
                                     [3 0] {:count 1 :items '[([1 1] [3 0])]}}
         (get affected-by [2 0]) => (contains {[3 1] (contains {:count 1}),
                                               [3 0] (contains {:count 1})})
         (get affected-by [2 1]) => (contains {[3 1] (contains {:count 1}),
                                               [3 0] (contains {:count 1})})
         (get affected-by [2 2]) => (contains {[3 1] (contains {:count 1}),
                                               [3 0] (contains {:count 1})})
         (get affected-by [3 0]) => nil
         (get affected-by [3 1]) => nil

         (get fixed-affected-by [0 0]) => {:count 1 :items '[([0 0])]}
         (get fixed-affected-by [1 0]) => (contains {:count 2
                                                     [2 0] (contains {:count 1}),
                                                     [2 1] (contains {:count 1}),
                                                     [3 1] (contains {:count 1})})
         (get fixed-affected-by [1 1]) => nil
         (get fixed-affected-by [2 0]) => {:count 2 :items '[([2 0] [3 0]) ([1 1] [2 0])], [3 1] {:count 1 :items '[([2 0] [3 1])]}}
         (get fixed-affected-by [2 1]) => {:count 2 :items '[([2 1] [3 0]) ([1 1] [2 1])], [3 1] {:count 1 :items '[([2 1] [3 1])]}}
         (get fixed-affected-by [2 2]) => nil
         (get fixed-affected-by [3 1]) => (contains {:count 2 :items anything})
         ))

(facts "affected count"
       (let [
             usage (pyrus/initialize-usage larger-context)
             uncovered (->> (pyrus/remaining-interactions larger-context usage)
                            (pyrus/compatible-interactions larger-context usage)
                            (pyrus/uncovered-combinations larger-context usage))
             fixed (set [[1 1] [2 2] [3 0]])
             fixed-affected-by (pyrus/affected-by uncovered fixed)
             ]
         (pyrus/affected-count fixed-affected-by fixed [[0 1]]) => 1          ; affected by [0 1] alone
         (pyrus/affected-count fixed-affected-by fixed [[1 0] [2 0]]) => (+ 2 ; affected by [1 0] alone
                                                                               1 ; affected by [1 0] [2 0]
                                                                               2 ; affected by [2 0] alone
                                                                               )
         ))

(facts "find most covering"
       (let [
             usage (pyrus/initialize-usage larger-context)
             uncovered (->> (pyrus/remaining-interactions larger-context usage)
                            (pyrus/compatible-interactions larger-context usage)
                            (pyrus/uncovered-combinations larger-context usage))
             fixed (set [[1 1] [2 2] [3 0]])
             fixed-affected-by (pyrus/affected-by uncovered fixed)
             ]
         fixed => (set [[1 1] [2 2] [3 0]])
         (pyrus/find-most-covering uncovered fixed-affected-by fixed) => [[1 0] [2 1]]
         (pyrus/affected-count fixed-affected-by fixed [[1 0] [2 1]]) => 5
         ))

(facts "find affected combinations"
       (let [
             usage (pyrus/initialize-usage larger-context)
             uncovered (->> (pyrus/remaining-interactions larger-context usage)
                            (pyrus/compatible-interactions larger-context usage)
                            (pyrus/uncovered-combinations larger-context usage))
             fixed (set [[1 1] [2 2] [3 0]])
             fixed-affected-by (pyrus/affected-by uncovered fixed)
             best-combination (pyrus/find-most-covering uncovered fixed-affected-by fixed)
             affected-combinations (pyrus/affected-combinations fixed-affected-by fixed best-combination)
             ]
         affected-combinations => '[([1 0] [3 0]) ([1 0] [2 2]) ([2 1] [3 0]) ([1 1] [2 1]) ([1 0] [2 1])]
         (count affected-combinations) => (pyrus/affected-count fixed-affected-by fixed best-combination)
         ))

(facts "update usage"
       (let [
             usage (pyrus/initialize-usage larger-context)
             ; needs doall to run before updates are done below, or lazyness will give the wrong results
             ; ugly side-effect of array mutation!
             uncovered (doall (->> (pyrus/remaining-interactions larger-context usage)
                                   (pyrus/compatible-interactions larger-context usage)
                                   (pyrus/uncovered-combinations larger-context usage)))
             unused (pyrus/unused usage)
             usage (pyrus/cover! larger-context usage [[[0 0]]] [[0 0]])
             one-used (pyrus/unused usage)
             usage (pyrus/cover! larger-context usage uncovered [[0 0]]) ; mark all uncovered as covered
             all-used (pyrus/unused usage)
             ]
         (reduce + (map (fn [[k v]] (count v)) (:interactions larger-context))) => 19
         (count uncovered) => 19
         unused => '{(:A) -3, (:B :C) -6, (:B :D) -4, (:C :D) -6}
         one-used => '{(:A) -2, (:B :C) -6, (:B :D) -4, (:C :D) -6}
         all-used => '{(:A) 0, (:B :C) 0, (:B :D) 0, (:C :D) 0}
         ))

(facts "find any compatible combination"
       (let [
             usage (pyrus/initialize-usage larger-context)
             usage (pyrus/cover! larger-context usage [[0 0]])
             ; needs doall to run before updates are done below, or lazyness will give the wrong results
             ; ugly side-effect of array mutation!
             compatible (->> (pyrus/remaining-interactions larger-context usage)
                             (pyrus/compatible-interactions larger-context usage))
             random-combination (pyrus/find-any-compatible usage compatible)
             ]
         random-combination => (just [(just number? number?) (just number? number?)])
         (mapcat identity (vals compatible)) => (contains [random-combination])
         ))

(defn any-of [alternatives] (fn [actual] ((set alternatives) actual)))
(def As? (any-of [:a1 :a2 :a3]))
(def Bs? (any-of [:b1 :b2]))
(def Cs? (any-of [:c1 :c2 :c3]))
(def Ds? (any-of [:d1 :d2]))

(facts "explain variation"
       (let [
             usage (pyrus/initialize-usage larger-context)
             explain-unused (pyrus/explain-variation larger-context usage)
             usage (pyrus/cover-variation! usage [[0 2] [1 1] [2 0] [3 1]])
             explain-used (pyrus/explain-variation larger-context usage)
             ]
         explain-unused => {:A nil :B nil :C nil :D nil}
         explain-used   => {:A :a3 :B :b2 :C :c1 :D :d2}
         ))

(facts "generate 1 variation"
       (let [
             usage (pyrus/initialize-usage larger-context)
             ; _ (prn "-------------------- take 1 generate")
             explained (first (pyrus/generate-variation larger-context usage 0))
             ]
         explained => (contains {:A As? :B Bs? :C Cs? :D Ds?})
         ))

(facts "generate all variations"
       (let [
             usage (pyrus/initialize-usage larger-context)
             ; _ (prn "-------------------- take all generate")
             all-explained (pyrus/generate-variation larger-context usage 0)
             ]
         (count all-explained) => 6
         (doseq [explained all-explained]
           explained => (contains {:A As? :B Bs? :C Cs? :D Ds?})
           )
         ))

(facts "all together now"
       (let [
             all-explained (pyrus/generate [:A :B :C :D]
                                              1 {:A [:a1 :a2 :a3]}
                                              2 {:B [:b1 :b2]
                                                 :C [:c1 :c2 :c3]
                                                 :D [:d1 :d2]})
             ]
         (doseq [explained all-explained]
           explained => (contains {:A As? :B Bs? :C Cs? :D Ds? }))
         (count all-explained) => 6
         ))
