(ns org.parkerici.alzabo.unify.query
  "This namespace provides utilities for reading Unify metadata
  and schema information directly from a db, rather than from
  a Unify schema directory or alzabo schema."
  (:require [clojure.string :as str]
            [datomic.api :as d]))

(defn db-uri
  []
  (or (System/getenv "BASE_DATOMIC_URI")
      (throw (ex-info "Must set BASE_URI!"
                      {:config/cause "set BASE_DATOMIC_URI to datomic storage service."}))))

(defn db-name->uri
  "Uses `BASE_DATOMIC_URI` from config/env and passed `db-name` to
  construct a connection string. _Note_: not all conn protocols are
  handled, only ddb, dev, and sql have been tested."
  [db-name]
  (let [base-uri (db-uri)
        protocol (second (str/split base-uri #"\:"))]
    (if (= "sql" protocol)
      (str (subs base-uri 0 14)
           db-name
           (subs base-uri 14))
      (str base-uri db-name))))

(defn latest-db
  "Get the latest version of the database the alzabo cli or service is
  pointed at (see `uri` in this namespace)."
  [db-name]
  (-> (db-name->uri db-name)
      (d/connect)
      (d/db)))

(defn flatten-idents
  "Given a map with some fields of form db/ident, eg {:db/valueType
  {:db/ident :db.type/string}, flattens the ident containing map to
  just the keyword, e.g. to {:db/valueType :db.type/string} in our
  example."
  [ent-map]
  (into {} (for [[k v] ent-map]
             (cond
               (and (map? v)
                    (:db/ident v))
               [k (:db/ident v)]

               (and (coll? v)
                    (sequential? v)
                    (every? map? v)
                    (every? :db/ident v))
               [k (mapv :db/ident v)]

               :else
               [k v]))))

(defn- db-ns?
  "Returns true if the passed keyword is in the db namespace, either
  directly or from nesting (e.g. db/valueType or db.type/string would
  both return true)."
  [kw]
  (let [kw-ns (namespace kw)
        kw-ns-start (first (str/split kw-ns #"\."))]
    (= "db" kw-ns-start)))

(defn version-info
  "Returns the entity defined by Unify that contains the schema/version and
  schema/name fields."
  [db]
  (d/pull db
          '[:db/ident :unify.schema/version :unify.schema/name]
          :unify.schema/metadata))

(defn attrs
  "Returns all attributes installed by the user in the database. These may
  or may not have been installed by Unify."
  [db]
  (->> db
       (d/q '[:find (pull ?a [:db/ident
                              {:db/valueType [:db/ident]}
                              {:db/cardinality [:db/ident]}
                              {:db/unique [:db/ident]}
                              :db/tupleType
                              :db/tupleTypes
                              :db/doc])
              :where
              [_ :db.install/attribute ?a]])
       (map first)
       (remove #(db-ns? (:db/ident %)))
       (map flatten-idents)))

(defn kinds
  "Returns all kinds defined by Unify conventions in the database."
  [db]
  (->> db
       (d/q '[:find (pull ?k [:unify.kind/parent
                              :unify.kind/global-id
                              :unify.kind/context-id
                              :unify.kind/name
                              :unify.kind/ref-data
                              :unify.kind/allow-create-on-import
                              :unify.kind/need-uid])
              :where
              [?k :unify.kind/name]])
       (map first)))

(defn refs
  "Returns all reference annotations in the database, as defined per
  Unify conventions."
  [db]
  (->> db
       (d/q '[:find (pull ?r [:db/ident
                              :unify.ref/from
                              :unify.ref/to
                              :unify.ref/tuple-types])
              :where
              [?r :unify.ref/from]])
       ;; flatten nested tuple from query results
       (map first)
       ;; turn refs into form that appears in schema file
       (map (fn [ent-map]
              (-> ent-map
                  (assoc :db/id (:db/ident ent-map))
                  (dissoc :db/ident))))))

(defn enums
  "Returns all enums in the database, using the heuristic that entities
  that contain only a :db/ident and :db/id fields and no others are
  enums.

  Note: enums may be explicitly modeled in a future release of Unify, at
  which point the explicit model should be used to return enums instead."
  [db]
  (->> db
       (d/q '[:find (pull ?e [*]) ?ident
              :where
              [?e :db/ident ?ident]])
       (keep (fn [[ent-map ident]]
               (when-not (< 2 (count (keys ent-map)))
                 {:db/ident ident})))
       (remove #(db-ns? (:db/ident %)))))

(defn list-dbs []
  (let [placeholder-uri (db-name->uri "*")]
    (vec (d/get-database-names placeholder-uri))))


(comment
  (list-dbs))
