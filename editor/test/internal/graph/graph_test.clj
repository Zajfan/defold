(ns internal.graph.graph-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test :refer :all]
            [dynamo.graph :as g]
            [dynamo.types :as t]
            [internal.graph :as ig]
            [internal.graph.generator :as ggen]
            [internal.graph.types :as gt]
            [schema.core :as s]))

(defn occurrences [coll]
  (vals (frequencies coll)))

(defn random-graph
  []
  ((ggen/make-random-graph-builder)))

(deftest no-duplicate-node-ids
  (let [g (random-graph)]
    (is (= 1 (apply max (occurrences (ig/node-ids g)))))))

(deftest removing-node
  (let [v      "Any ig/node value"
        g      (random-graph)
        id     (inc (count (:nodes g)))
        g      (ig/add-node g id v)
        g      (ig/remove-node g id)]
    (is (nil? (ig/node g id)))
    (is (empty? (filter #(= "Any ig/node value" %) (ig/node-values g))))))

(defn- arcs-are-reflexive?
  [g]
  (and (every? #(= true %)
               (for [source       (ig/node-ids g)
                     source-label (gt/outputs (ig/node g source))
                     [target target-label] (map gt/tail (ig/arcs-from-source g source source-label))]
                 (some #(= % [source source-label]) (ig/sources g target target-label))))
       (every? #(= true %)
               (for [target       (ig/node-ids g)
                     target-label (gt/inputs (ig/node g target))
                     [source source-label] (ig/sources g target target-label)]
                 (some #(= % [target target-label]) (map gt/tail (ig/arcs-from-source g source source-label)))))))

(deftest reflexivity
  (let [g (random-graph)]
    (is (arcs-are-reflexive? g))))

(deftest transformable
  (let [g      (random-graph)
        id     (inc (count (:nodes g)))
        g      (ig/add-node g id {:number 0})
        g'     (ig/transform-node g id update-in [:number] inc)]
    (is (not= g g'))
    (is (= 1 (:number (ig/node g' id))))
    (is (= 0 (:number (ig/node g id))))))


(s/defrecord T1 [ident :- String])
(s/defrecord T2 [value :- Integer])

(deftest type-compatibility
  (are [first second cardinality compatible?]
    (= compatible? (g/type-compatible? first second cardinality))
    T1 T1               :one    true
    T1 T1               :many   false
    T1 T2               :one    false
    T1 T2               :many   false
    T1 [T1]             :many   true
    T1 [T1]             :one    false
    T1 [T2]             :many   false
    T1 [T2]             :one    false
    String String       :one    true
    String String       :many   false
    String [String]     :one    false
    String [String]     :many   true
    [String] String     :one    false
    [String] String     :many   false
    [String] [String]   :one    true
    [String] [String]   :many   false
    [String] [[String]] :many   true
    Integer  Number     :one    true
    Integer  t/Num      :one    true
    T1       t/Any      :one    true
    T1       t/Any      :many   true
    T1       [t/Any]    :one    false
    T1       [t/Any]    :many   true
    String   t/Any      :one    true
    String   t/Any      :many   true
    String   [t/Any]    :one    false
    String   [t/Any]    :many   true
    [String] t/Any      :one    true
    [String] t/Any      :many   true
    [String] [t/Any]    :one    true
    [String] [t/Any]    :one    true))
