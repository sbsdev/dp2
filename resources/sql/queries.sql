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

-----------
-- Words --
-----------

-- :name get-global-words :? :*
-- :doc retrieve global words given a `limit` and an `offset`
SELECT * FROM dictionary_globalword
LIMIT :limit OFFSET :offset

-- :name get-global-word :? :*
-- :doc retrieve global words for a given untranslated
SELECT * FROM dictionary_globalword
WHERE untranslated = :untranslated

-- :name find-global-words :? :*
-- :doc retrieve all global words given a simple pattern for untranslated, an optional grade, an optional type, a limit and an offset
SELECT * FROM dictionary_globalword
WHERE untranslated LIKE :search
--~ (when (:grade params) "AND grade = :grade")
--~ (when (:type params) "AND type = :type")
LIMIT :limit OFFSET :offset

-- :name get-local-words :? :*
-- :doc retrieve local words for a given document id
SELECT * FROM dictionary_localword
WHERE document_id = :id

-- :name get-all-known-homographs :? :*
-- :doc given a list of `words` retrieve all (locally and globally) known homographs for a given `document_id`, `grade`
(SELECT homograph_disambiguation
FROM dictionary_globalword
WHERE grade = :grade
AND type = 5
AND untranslated in (:v*:words))
UNION
(SELECT homograph_disambiguation
FROM dictionary_localword
WHERE grade = :grade
AND type = 5
AND document_id = :document_id
AND untranslated in (:v*:words))

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

