---------------
-- Documents --
---------------

-- :name get-documents :? :*
-- :doc retrieve all documents given a limit and an offset
SELECT *, (CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END) AS spelling
FROM documents_document
LIMIT :limit OFFSET :offset

-- :name find-documents :? :*
-- :doc retrieve all documents given a `search` term, a `limit` and an `offset`
SELECT *, (CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END) AS spelling
FROM documents_document
WHERE LOWER(title) LIKE LOWER(:search)
LIMIT :limit OFFSET :offset

-- :name get-document :? :1
-- :doc retrieve a document record given the `id`
SELECT *, (CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END) AS spelling
FROM documents_document
WHERE id = :id

-- :name get-images :? :*
-- :doc retrieve the images for a given document `id`
SELECT * FROM documents_image
WHERE document_id = :id

--------------
-- Versions --
--------------

-- :name get-versions :? :*
-- :doc retrieve all versions of a document given a `document_id`
SELECT * FROM documents_version
WHERE document_id = :document_id

-- :name get-latest-version :? :1
-- :doc retrieve the latest version of a document given a `document_id`
SELECT * FROM documents_version
WHERE document_id = :document_id
AND created_at = (SELECT MAX(created_at) FROM documents_version WHERE document_id = :document_id)

------------------
-- Global Words --
------------------

-- :name get-global-words :? :*
-- :doc retrieve all global words where the column `braille` ("contracted" or "uncontracted") is not null optionally filtered by `types`
SELECT untranslated, uncontracted, contracted, type, homograph_disambiguation
FROM dictionary_globalword
WHERE :i:braille IS NOT NULL
--~ (when (:types params) "AND type IN (:v*:types)")

-- :name find-global-words :? :*
-- :doc retrieve all global words given a simple pattern for `untranslated`, a `limit` and an `offset`
SELECT untranslated, uncontracted, contracted, type, homograph_disambiguation
FROM dictionary_globalword
WHERE untranslated LIKE :untranslated
ORDER BY untranslated
LIMIT :limit OFFSET :offset

-- :name insert-global-word :! :n
-- :doc Insert or update a word in the global dictionary.
INSERT INTO dictionary_globalword (untranslated, uncontracted, contracted, type, homograph_disambiguation)
VALUES (:untranslated, :uncontracted, :contracted, :type, :homograph_disambiguation)
ON DUPLICATE KEY UPDATE
contracted = VALUES(contracted),
uncontracted = VALUES(uncontracted)

-- :name delete-global-word :! :n
-- :doc Delete a word in the global dictionary.
DELETE FROM dictionary_globalword
WHERE untranslated = :untranslated
AND type = :type
AND homograph_disambiguation = :homograph_disambiguation

-----------------
-- Local Words --
-----------------

-- :name get-local-words :? :*
-- :doc retrieve aggregated local words for a given document `id`. Optionally you can only get local words that match a `search` term. The words contain braille for both grades and the hyphenation if they exist. Optionally the results can be limited by `limit` and `offset`.
SELECT words.*,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :id) AS spelling,
       hyphenation.hyphenation AS hyphenated
FROM dictionary_localword as words
LEFT JOIN hyphenation_words AS hyphenation
ON words.untranslated = hyphenation.word
AND hyphenation.spelling =
  (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
  FROM  documents_document
  WHERE id = :id)
ORDER BY words.untranslated
--~ (when (:limit params) "LIMIT :limit")
--~ (when (:offset params) "OFFSET :offset")

-- :name get-local-word-count :? :1
-- :doc retrieve the number of local words for a given document `id` and `untranslated`
SELECT COUNT(*) FROM dictionary_localword
WHERE document_id = :id
AND untranslated = :untranslated

-- :name insert-local-word :! :n
-- :doc Insert or update a word in the local dictionary. Optionally specify `isconfirmed`.
INSERT INTO dictionary_localword (untranslated, contracted, uncontracted, type, homograph_disambiguation, document_id, isLocal, isConfirmed)
/*~ (if (:isconfirmed params) */
VALUES (:untranslated, :contracted, :uncontracted, :type, :homograph_disambiguation, :document_id, :islocal, :isconfirmed)
/*~*/
VALUES (:untranslated, :contracted, :uncontracted, :type, :homograph_disambiguation, :document_id, :islocal, DEFAULT)
/*~ ) ~*/
ON DUPLICATE KEY UPDATE
contracted = VALUES(contracted),
uncontracted = VALUES(uncontracted),
--~ (when (:isconfirmed params) "isConfirmed = VALUES(isConfirmed),")
isLocal = VALUES(isLocal)

-- :name delete-local-word :! :n
-- :doc Delete a word in the local dictionary.
DELETE FROM dictionary_localword
WHERE untranslated = :untranslated
AND type = :type
AND homograph_disambiguation = :homograph_disambiguation
AND document_id = :document_id

-------------------
-- Unknown words --
-------------------

-- :name delete-unknown-words :! :n
-- :doc empty the "temporary" table containing words from a new document
DELETE FROM dictionary_unknownword

-- :name insert-unknown-words :! :n
-- :doc insert a list of new `words` into a "temporary" table. This later used to join with the already known words to query the unknown words
INSERT INTO dictionary_unknownword (untranslated, type, homograph_disambiguation, document_id)
VALUES :tuple*:words

-- :name delete-non-existing-unknown-words-from-local-words :! :n
-- :doc delete words that are not in the list of unknown words from the local words for given `:document-id`
DELETE l
FROM dictionary_localword l
LEFT JOIN dictionary_unknownword u
ON u.untranslated = l.untranslated AND u.type = l.type AND u.document_id = l.document_id
WHERE u.untranslated IS NULL
AND l.document_id = :document-id

-- :name get-all-unknown-words :? :*
-- :doc given a `document-id` and a `:grade` retrieve all unknown words for it. If `:grade` is 0 then return words for both grade 1 and 2. Otherwise just return the unknown words for the given grade.This assumes that the new words contained in this document have been inserted into the `dictionary_unknownword` table.
-- NOTE: This query assumes that there are only records for the current document-id in the dictionary_unknownword table.
(SELECT unknown.*,
       COALESCE(l.uncontracted, g.uncontracted) AS uncontracted,
       COALESCE(l.contracted, g.contracted) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l ON l.untranslated = unknown.untranslated AND l.type IN (0,1,3) AND l.document_id = :document-id
     AND ((:grade = 0) OR (:grade = 1 AND l.uncontracted IS NOT NULL) OR (:grade = 2 AND l.contracted IS NOT NULL))
LEFT JOIN dictionary_globalword g ON g.untranslated = unknown.untranslated AND g.type IN (0,1,3)
     AND ((:grade = 0) OR (:grade = 1 AND g.uncontracted IS NOT NULL) OR (:grade = 2 AND g.contracted IS NOT NULL))
LEFT JOIN hyphenation_words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 0
AND (g.untranslated IS NULL AND l.untranslated IS NULL))
UNION
(SELECT unknown.*,
       COALESCE(l.uncontracted, g.uncontracted) AS uncontracted,
       COALESCE(l.contracted, g.contracted) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l ON l.untranslated = unknown.untranslated AND l.type IN (1,2) AND l.document_id = :document-id
     AND ((:grade = 0) OR (:grade = 1 AND l.uncontracted IS NOT NULL) OR (:grade = 2 AND l.contracted IS NOT NULL))
LEFT JOIN dictionary_globalword g ON g.untranslated = unknown.untranslated AND g.type IN (1,2)
     AND ((:grade = 0) OR (:grade = 1 AND g.uncontracted IS NOT NULL) OR (:grade = 2 AND g.contracted IS NOT NULL))
LEFT JOIN hyphenation_words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 2
AND (g.untranslated IS NULL AND l.untranslated IS NULL))
UNION
(SELECT unknown.*,
       COALESCE(l.uncontracted, g.uncontracted) AS uncontracted,
       COALESCE(l.contracted, g.contracted) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l ON l.untranslated = unknown.untranslated AND l.type IN (3,4) AND l.document_id = :document-id
     AND ((:grade = 0) OR (:grade = 1 AND l.uncontracted IS NOT NULL) OR (:grade = 2 AND l.contracted IS NOT NULL))
LEFT JOIN dictionary_globalword g ON g.untranslated = unknown.untranslated AND g.type IN (3,4)
     AND ((:grade = 0) OR (:grade = 1 AND g.uncontracted IS NOT NULL) OR (:grade = 2 AND g.contracted IS NOT NULL))
LEFT JOIN hyphenation_words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 4
AND (g.untranslated IS NULL AND l.untranslated IS NULL))
UNION
(SELECT unknown.*,
       COALESCE(l.uncontracted, g.uncontracted) AS uncontracted,
       COALESCE(l.contracted, g.contracted) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l ON l.untranslated = unknown.untranslated AND l.type IN (5) AND l.document_id = :document-id
     AND ((:grade = 0) OR (:grade = 1 AND l.uncontracted IS NOT NULL) OR (:grade = 2 AND l.contracted IS NOT NULL))
LEFT JOIN dictionary_globalword g ON g.untranslated = unknown.untranslated AND g.type IN (5)
     AND ((:grade = 0) OR (:grade = 1 AND g.uncontracted IS NOT NULL) OR (:grade = 2 AND g.contracted IS NOT NULL))
LEFT JOIN hyphenation_words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 5
AND (g.untranslated IS NULL AND l.untranslated IS NULL))
ORDER BY untranslated
LIMIT :limit OFFSET :offset

-----------------------
-- Confirmable words --
-----------------------

-- :name get-confirmable-words :? :*
-- :doc retrieve local words that are ready for confirmation. The words contain braille for both grades and the hyphenation if they exist.
SELECT words.*,
       (CASE doc.language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END) AS spelling,
       doc.title AS document_title,
       hyphenation.hyphenation AS hyphenated
FROM dictionary_localword as words
JOIN documents_document doc ON words.document_id = doc.id
LEFT JOIN hyphenation_words AS hyphenation
ON words.untranslated = hyphenation.word
AND hyphenation.spelling = (CASE doc.language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END)
-- only get words from finished productions
where doc.state_id = (SELECT id FROM documents_state WHERE sort_order = (SELECT MAX(sort_order) FROM documents_state))
ORDER BY words.document_id, words.untranslated
LIMIT :limit OFFSET :offset

------------------
-- Hyphenations --
------------------

-- :name get-hyphenation :? :*
-- :doc retrieve hyphenations given a `spelling` and optionally s `search` term, a `limit` and an `offset`
SELECT * FROM hyphenation_words
WHERE spelling = :spelling
--~ (when (:search params) "AND word LIKE :search")
--~ (when (:limit params) "LIMIT :limit")
--~ (when (:offset params) "OFFSET :offset")

-- :name insert-hyphenation :! :n
-- :doc Insert or update a hyphenation.
INSERT INTO hyphenation_words (word, hyphenation, spelling)
VALUES (:word, :hyphenation, :spelling)
ON DUPLICATE KEY UPDATE
hyphenation = VALUES(hyphenation)

-- :name delete-hyphenation :! :n
-- :doc Delete a hyphenation word `:word` and `:spelling` if there are no more references to it from either the local words, if `:document-id` is given, or the global words otherwise.
DELETE FROM hyphenation_words
WHERE word = :word
AND spelling = :spelling
/*~ (if (:document-id params) */
AND NOT EXISTS (
    SELECT * FROM dictionary_localword
    WHERE untranslated = :word
    AND document_id = :document-id
)
/*~*/
AND NOT EXISTS (
    SELECT * FROM dictionary_globalword
    WHERE untranslated = :word
)
/*~ ) ~*/
