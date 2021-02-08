-----------------
-- Hyphenation --
-----------------

-- :name get-hyphenation :? :*
-- :doc retrieve hyphenations given a `spelling` and optionally s `search` term, a `limit` and an `offset`
SELECT * FROM words
WHERE spelling = :spelling
--~ (when (:search params) "AND word LIKE :search")
--~ (when (:limit params) "LIMIT :limit")
--~ (when (:offset params) "OFFSET :offset")

-- :name get-hyphenations-in :? :*
-- :doc retrieve hyphenations given a `spelling` for all given `words`
SELECT * FROM words
WHERE spelling = :spelling
/*~ (if (seq (:words params)) */
AND word IN (:v*:words)
/*~*/
-- when the list of words is empty obviously there are no
-- hyphenatrions. So we shouldn't even hit the db server. But to keep
-- the api small we use this hackish way to return an empty result
-- set.
AND false
/*~ ) ~*/

