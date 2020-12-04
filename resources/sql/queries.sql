-- :name create-user! :! :n
-- :doc create a new user record
INSERT INTO users
(id, first_name, last_name, email, pass)
VALUES (:id, :first_name, :last_name, :email, :pass)

-- :name update-user! :! :n
-- :doc update an existing user record
UPDATE users
SET first_name = :first_name, last_name = :last_name, email = :email
WHERE id = :id

-- :name get-user :? :1
-- :doc retrieve a user record given the id
SELECT * FROM users
WHERE id = :id

-- :name delete-user! :! :n
-- :doc delete a user record given the id
DELETE FROM users
WHERE id = :id

---------------
-- Documents --
---------------

-- :name get-documents :? :*
-- :doc retrieve all documents given a limit and an offset
SELECT * FROM documents_document LIMIT :limit OFFSET :offset

-- :name find-documents :? :*
-- :doc retrieve all documents given a `search` term, a `limit` and an `offset`
SELECT * FROM documents_document
WHERE title LIKE :search
LIMIT :limit OFFSET :offset

-- :name get-document :? :1
-- :doc retrieve a document record given the `id`
SELECT * FROM documents_document
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

-- :name get-local-word-count :? :1
-- :doc retrieve the number of local words for a given document `id` and `untranslated`
SELECT COUNT(*) FROM dictionary_localword
WHERE document_id = :id
AND untranslated = :untranslated

-- :name insert-local-word :! :n
-- :doc Insert or update a word in the local dictionary. Optionaly specify `isconfirmed`.
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

-- :name get-all-known-homographs :? :*
-- :doc given a list of `words` retrieve all (locally and globally) known homographs for a given `document_id`, `grade`
(SELECT homograph_disambiguation
FROM dictionary_globalword
WHERE grade = :grade
AND type = 5
AND homograph_disambiguation in (:v*:words))
UNION
(SELECT homograph_disambiguation
FROM dictionary_localword
WHERE grade = :grade
AND type = 5
AND document_id = :document_id
AND homograph_disambiguation in (:v*:words))

-- :name get-all-known-names :? :*
-- :doc given a list of `words` retrieve all (locally and globally) known names for a given `document_id`, `grade`
(SELECT untranslated
FROM dictionary_globalword
WHERE grade = :grade
AND type IN (1,2)
AND untranslated in (:v*:words))
UNION
(SELECT untranslated
FROM dictionary_localword
WHERE grade = :grade
AND type IN (1,2)
AND document_id = :document_id
AND untranslated in (:v*:words))

-- :name get-all-known-places :? :*
-- :doc given a list of `words` retrieve all (locally and globally) known places for a given `document_id`, `grade`
(SELECT untranslated
FROM dictionary_globalword
WHERE grade = :grade
AND type IN (3,4)
AND untranslated in (:v*:words))
UNION
(SELECT untranslated
FROM dictionary_localword
WHERE grade = :grade
AND type IN (3,4)
AND document_id = :document_id
AND untranslated in (:v*:words))

-- :name get-all-known-words :? :*
-- :doc given a list of `words` retrieve all (locally and globally) known words for a given `document_id`, `grade` 
(SELECT untranslated
FROM dictionary_globalword
WHERE grade = :grade
AND type NOT IN (2,4,5)
AND untranslated in (:v*:words))
UNION
(SELECT untranslated
FROM dictionary_localword
WHERE grade = :grade
-- exclude type 2,4 and 5 as these probably have a different
-- translations, so we do need to show these words if they are not
-- tagged even if they have an entry in the dictionary as a name or a
-- place.
AND type NOT IN (2,4,5)
AND document_id = :document_id
AND untranslated in (:v*:words))

-----------------------
-- Confirmable words --
-----------------------

-- :name get-confirmable-words :? :*
-- :doc retrieve local words that are ready for confirmation
SELECT dict.*, doc.language
FROM dictionary_localword AS dict,
     documents_document AS doc
WHERE dict.isConfirmed = false
AND dict.document_id = doc.id

