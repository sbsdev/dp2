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
WHERE title LIKE :search
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
-- :doc retrieve all global words of given `:grade` and optionally any of `:types`
SELECT untranslated,
/*~ (if (= (:grade params) 1) */
       braille AS uncontracted,
/*~*/
       braille AS contracted,
/*~ ) ~*/
        type,
	homograph_disambiguation
FROM dictionary_globalword
WHERE grade = :grade
--~ (when (:types params) "AND type IN (:v*:types)")

-- :name find-global-words :? :*
-- :doc retrieve all global words given a simple pattern for `untranslated`, a `limit` and an `offset`
(SELECT t1.untranslated, t2.braille as uncontracted, t1.braille as contracted, t1.type, t1.homograph_disambiguation
FROM dictionary_globalword t1
LEFT JOIN dictionary_globalword t2
ON t1.untranslated = t2.untranslated
AND t1.type = t2.type
AND t1.homograph_disambiguation = t2.homograph_disambiguation
AND t1.grade <> t2.grade
WHERE t1.untranslated like :untranslated
AND t1.grade = 2)
UNION DISTINCT
(SELECT t1.untranslated, t1.braille as uncontracted, t2.braille as contracted, t1.type, t1.homograph_disambiguation
FROM dictionary_globalword t1
LEFT JOIN dictionary_globalword t2
ON t1.untranslated = t2.untranslated
AND t1.type = t2.type
AND t1.homograph_disambiguation = t2.homograph_disambiguation
AND t1.grade <> t2.grade
WHERE t1.untranslated LIKE :untranslated 
AND t1.grade = 1)
ORDER BY untranslated
LIMIT :limit OFFSET :offset

-- :name insert-global-word :! :n
-- :doc Insert or update a word in the global dictionary.
INSERT INTO dictionary_globalword (untranslated, braille, type, grade, homograph_disambiguation)
VALUES (:untranslated, :braille, :type, :grade, :homograph_disambiguation)
ON DUPLICATE KEY UPDATE
braille = VALUES(braille)

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
-- :doc retrieve local words for a given document `id` and an optional `grade`
SELECT * FROM dictionary_localword
WHERE document_id = :id
--~ (when (:grade params) "AND grade = :grade")

-- :name get-local-words :? :*
-- :doc retrieve local words for a given document `id` and grade `grade`. The words contain  the hyphenation if it exists.
SELECT words.untranslated,
       IF(:grade = 1, words.braille, NULL) AS uncontracted,
       IF(:grade = 2, words.braille, NULL) AS contracted,
       words.type, words.homograph_disambiguation, words.document_id, words.isLocal,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = 644) AS spelling,
       hyphenation.hyphenation AS hyphenated
FROM dictionary_localword words
LEFT JOIN hyphenation_test.words AS hyphenation
ON words.untranslated = hyphenation.word
AND hyphenation.spelling =
  (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
  FROM  documents_document
  WHERE id = :id)
WHERE words.document_id = :id
AND words.isConfirmed = FALSE
AND words.grade = :grade
ORDER BY words.untranslated
LIMIT :limit OFFSET :offset

-- :name get-local-words-aggregated :? :*
-- :doc retrieve aggregated local words for a given document `id`. The words contain braille for both grades and the hyphenation if they exist. Optionally the results can be limited by `limit` and `offset`.
SELECT words.*,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :id) AS spelling,
       hyphenation.hyphenation AS hyphenated
FROM
  (SELECT DISTINCT w.untranslated, w.uncontracted, w.contracted, w.type, w.homograph_disambiguation, w.document_id, BIT_OR(w.isLocal) AS isLocal
  FROM
    ((SELECT t1.untranslated, t2.braille as uncontracted, t1.braille as contracted, t1.type, t1.homograph_disambiguation, t1.document_id, IFNULL(t1.isLocal OR t2.isLocal,FALSE) AS isLocal
      FROM dictionary_localword t1
      LEFT JOIN dictionary_localword t2
      ON t1.untranslated = t2.untranslated
      AND t1.type = t2.type
      AND t1.homograph_disambiguation = t2.homograph_disambiguation
      AND t1.grade <> t2.grade
      WHERE t1.document_id = :id
      AND t1.isConfirmed = FALSE
      AND t1.grade = 2)
    UNION DISTINCT
      (SELECT t1.untranslated, t1.braille as uncontracted, t2.braille as contracted, t1.type, t1.homograph_disambiguation, t1.document_id, IFNULL(t1.isLocal OR t2.isLocal,FALSE) AS isLocal
      FROM dictionary_localword t1
      LEFT JOIN dictionary_localword t2
      ON t1.untranslated = t2.untranslated
      AND t1.type = t2.type
      AND t1.homograph_disambiguation = t2.homograph_disambiguation
      AND t1.grade <> t2.grade
      WHERE t1.document_id = :id
      AND t1.isConfirmed = FALSE
      AND t1.grade = 1)
    ORDER BY untranslated
    ) AS w
  GROUP BY w.untranslated, w.uncontracted, w.contracted, w.type, w.homograph_disambiguation
  ) AS words
LEFT JOIN hyphenation_test.words AS hyphenation
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
INSERT INTO dictionary_localword (untranslated, braille, type, grade, homograph_disambiguation, document_id, isLocal, isConfirmed)
/*~ (if (:isconfirmed params) */
VALUES (:untranslated, :braille, :type, :grade, :homograph_disambiguation, :document_id, :islocal, :isconfirmed)
/*~*/
VALUES (:untranslated, :braille, :type, :grade, :homograph_disambiguation, :document_id, :islocal, DEFAULT)
/*~ ) ~*/
ON DUPLICATE KEY UPDATE
braille = VALUES(braille),
--~ (when (:isconfirmed params) "isConfirmed = VALUES(isConfirmed),")
isLocal = VALUES(isLocal)

-- :name delete-local-word :! :n
-- :doc Delete a word in the local dictionary.
DELETE FROM dictionary_localword
WHERE untranslated = :untranslated
AND type = :type
AND grade = :grade
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
-- :doc delete words that are not in the list of unknown words from
-- the local words for given `:document-id`
DELETE l
FROM dictionary_localword l
LEFT JOIN dictionary_unknownword u
ON u.untranslated = l.untranslated AND u.type = l.type AND u.document_id = l.document_id
WHERE u.untranslated IS NULL
AND l.document_id = :document-id

-- :name get-all-unknown-words :? :*
-- :doc given a `document-id` and a `:grade` retrieve all unknown
-- words for it. If `:grade` is 0 then return words for both grade 1
-- and 2. Otherwise just return the unknown words for the given
-- grade.This assumes that the new words contained in this document
-- have been inserted into the `dictionary_unknownword` table.
/*~ (if (= (:grade params) 0) */
(SELECT unknown.*,
       COALESCE(l1.braille, g1.braille) AS uncontracted,
       COALESCE(l2.braille, g2.braille) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l1 ON l1.untranslated = unknown.untranslated AND l1.grade = 1 AND l1.type IN (0,1,3)
LEFT JOIN dictionary_globalword g1 ON g1.untranslated = unknown.untranslated AND g1.grade = 1 AND g1.type IN (0,1,3)
LEFT JOIN dictionary_localword l2 ON l2.untranslated = unknown.untranslated AND l2.grade = 2 AND l2.type IN (0,1,3)
LEFT JOIN dictionary_globalword g2 ON g2.untranslated = unknown.untranslated AND g2.grade = 2 AND g2.type IN (0,1,3)
LEFT JOIN hyphenation_test.words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 0
AND ((g2.id IS NULL AND l2.id IS NULL) OR (g1.id IS NULL AND l1.id IS NULL)))
UNION
(SELECT unknown.*,
       COALESCE(l1.braille, g1.braille) AS uncontracted,
       COALESCE(l2.braille, g2.braille) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l1 ON l1.untranslated = unknown.untranslated AND l1.grade = 1 AND l1.type IN (1,2)
LEFT JOIN dictionary_globalword g1 ON g1.untranslated = unknown.untranslated AND g1.grade = 1 AND g1.type IN (1,2)
LEFT JOIN dictionary_localword l2 ON l2.untranslated = unknown.untranslated AND l2.grade = 2 AND l2.type IN (1,2)
LEFT JOIN dictionary_globalword g2 ON g2.untranslated = unknown.untranslated AND g2.grade = 2 AND g2.type IN (1,2)
LEFT JOIN hyphenation_test.words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 2
AND ((g2.id IS NULL AND l2.id IS NULL) OR (g1.id IS NULL AND l1.id IS NULL)))
UNION
(SELECT unknown.*,
       COALESCE(l1.braille, g1.braille) AS uncontracted,
       COALESCE(l2.braille, g2.braille) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l1 ON l1.untranslated = unknown.untranslated AND l1.grade = 1 AND l1.type IN (3,4)
LEFT JOIN dictionary_globalword g1 ON g1.untranslated = unknown.untranslated AND g1.grade = 1 AND g1.type IN (3,4)
LEFT JOIN dictionary_localword l2 ON l2.untranslated = unknown.untranslated AND l2.grade = 2 AND l2.type IN (3,4)
LEFT JOIN dictionary_globalword g2 ON g2.untranslated = unknown.untranslated AND g2.grade = 2 AND g2.type IN (3,4)
LEFT JOIN hyphenation_test.words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 4
AND ((g2.id IS NULL AND l2.id IS NULL) OR (g1.id IS NULL AND l1.id IS NULL)))
UNION
(SELECT unknown.*,
       COALESCE(l1.braille, g1.braille) AS uncontracted,
       COALESCE(l2.braille, g2.braille) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l1 ON l1.untranslated = unknown.untranslated AND l1.grade = 1 AND l1.type IN (5)
LEFT JOIN dictionary_globalword g1 ON g1.untranslated = unknown.untranslated AND g1.grade = 1 AND g1.type IN (5)
LEFT JOIN dictionary_localword l2 ON l2.untranslated = unknown.untranslated AND l2.grade = 2 AND l2.type IN (5)
LEFT JOIN dictionary_globalword g2 ON g2.untranslated = unknown.untranslated AND g2.grade = 2 AND g2.type IN (5)
LEFT JOIN hyphenation_test.words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 5
AND ((g2.id IS NULL AND l2.id IS NULL) OR (g1.id IS NULL AND l1.id IS NULL)))
/*~*/
(SELECT unknown.*,
       IF(:grade = 1, COALESCE(l.braille, g.braille), NULL) AS uncontracted,
       IF(:grade = 2, COALESCE(l.braille, g.braille), NULL) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l ON l.untranslated = unknown.untranslated AND l.grade = :grade AND l.type IN (0,1,3)
LEFT JOIN dictionary_globalword g ON g.untranslated = unknown.untranslated AND g.grade = :grade AND g.type IN (0,1,3)
LEFT JOIN hyphenation_test.words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 0
AND (g.id IS NULL AND l.id IS NULL))
UNION
(SELECT unknown.*,
       IF(:grade = 1, COALESCE(l.braille, g.braille), NULL) AS uncontracted,
       IF(:grade = 2, COALESCE(l.braille, g.braille), NULL) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l ON l.untranslated = unknown.untranslated AND l.grade = :grade AND l.type IN (1,2)
LEFT JOIN dictionary_globalword g ON g.untranslated = unknown.untranslated AND g.grade = :grade AND g.type IN (1,2)
LEFT JOIN hyphenation_test.words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 2
AND (g.id IS NULL AND l.id IS NULL))
UNION
(SELECT unknown.*,
       IF(:grade = 1, COALESCE(l.braille, g.braille), NULL) AS uncontracted,
       IF(:grade = 2, COALESCE(l.braille, g.braille), NULL) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l ON l.untranslated = unknown.untranslated AND l.grade = :grade AND l.type IN (3,4)
LEFT JOIN dictionary_globalword g ON g.untranslated = unknown.untranslated AND g.grade = :grade AND g.type IN (3,4)
LEFT JOIN hyphenation_test.words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 4
AND (g.id IS NULL AND l.id IS NULL))
UNION
(SELECT unknown.*,
       IF(:grade = 1, COALESCE(l.braille, g.braille), NULL) AS uncontracted,
       IF(:grade = 2, COALESCE(l.braille, g.braille), NULL) AS contracted,
       hyphenation.hyphenation AS hyphenated,
       (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END FROM documents_document WHERE id = :document-id) AS spelling
FROM dictionary_unknownword unknown
LEFT JOIN dictionary_localword l ON l.untranslated = unknown.untranslated AND l.grade = :grade AND l.type IN (5)
LEFT JOIN dictionary_globalword g ON g.untranslated = unknown.untranslated AND g.grade = :grade AND g.type IN (5)
LEFT JOIN hyphenation_test.words AS hyphenation
     ON unknown.untranslated = hyphenation.word
     AND hyphenation.spelling =
     	 (SELECT CASE language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END
	  FROM  documents_document
	  WHERE id = :document-id)
WHERE unknown.type = 5
AND (g.id IS NULL AND l.id IS NULL))
/*~ ) ~*/
ORDER BY untranslated
LIMIT :limit OFFSET :offset

-----------------------
-- Confirmable words --
-----------------------

-- :name get-confirmable-words-aggregated :? :*
-- :doc retrieve local words that are ready for confirmation. The words contain braille for both grades and the hyphenation if they exist.
SELECT words.*,
       (CASE doc.language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END) AS spelling,
       doc.title AS document_title,
       hyphenation.hyphenation AS hyphenated
FROM
  (SELECT DISTINCT w.untranslated, w.uncontracted, w.contracted, w.type, w.homograph_disambiguation, w.document_id, BIT_OR(w.isLocal) AS isLocal
  FROM
    ((SELECT t1.untranslated, t2.braille as uncontracted, t1.braille as contracted, t1.type, t1.homograph_disambiguation, t1.document_id, IFNULL(t1.isLocal OR t2.isLocal,FALSE) AS isLocal
      FROM dictionary_localword t1
      LEFT JOIN dictionary_localword t2
      ON t1.untranslated = t2.untranslated
      AND t1.type = t2.type
      AND t1.homograph_disambiguation = t2.homograph_disambiguation
      AND t1.grade <> t2.grade
      WHERE t1.isConfirmed = FALSE
      AND t1.grade = 2)
    UNION DISTINCT
      (SELECT t1.untranslated, t1.braille as uncontracted, t2.braille as contracted, t1.type, t1.homograph_disambiguation, t1.document_id, IFNULL(t1.isLocal OR t2.isLocal,FALSE) AS isLocal
      FROM dictionary_localword t1
      LEFT JOIN dictionary_localword t2
      ON t1.untranslated = t2.untranslated
      AND t1.type = t2.type
      AND t1.homograph_disambiguation = t2.homograph_disambiguation
      AND t1.grade <> t2.grade
      WHERE t1.isConfirmed = FALSE
      AND t1.grade = 1)
    ORDER BY untranslated
    ) AS w
  GROUP BY w.untranslated, w.uncontracted, w.contracted, w.type, w.homograph_disambiguation
  ) AS words
JOIN documents_document doc ON words.document_id = doc.id
LEFT JOIN hyphenation_test.words AS hyphenation
ON words.untranslated = hyphenation.word
AND hyphenation.spelling = (CASE doc.language WHEN "de" THEN 1 WHEN "de-1901" THEN 0 ELSE NULL END)
-- only get words from finished productions
where doc.state_id = (SELECT id FROM documents_state WHERE sort_order = (SELECT MAX(sort_order) FROM documents_state))
ORDER BY words.document_id, words.untranslated
LIMIT :limit OFFSET :offset
