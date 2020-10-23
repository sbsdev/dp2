-----------------
-- Hyphenation --
-----------------

-- :name get-hyphenation :? :*
-- :doc retrieve hyphenations given a `spelling` and optionally s `search` term, a `limit` and an `offset`
SELECT * FROM words
WHERE spelling = :spelling
--~ (when (:search params) "AND word REGEXP :search")
--~ (when (:limit params) "LIMIT :limit")
--~ (when (:offset params) "OFFSET :offset")

-- :name insert-hyphenation :! :n
-- :doc Insert or update a hyphenation.
INSERT INTO words (word, hyphenation, spelling)
VALUES (:word, :hyphenation, :spelling)
ON DUPLICATE KEY UPDATE
hyphenation = VALUES(hyphenation)

-- :name delete-hyphenation :! :n
-- :doc Delete a hyphenation given a `word` and a `spelling`.
DELETE FROM words
WHERE word = :word
AND spelling = :spelling
