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
-- :doc retrieve a document record given the id
SELECT * FROM documents_document
WHERE id = :id

-- :name get-images :? :*
-- :doc retrieve the images for a given document id
SELECT * FROM documents_image
WHERE document_id = :id

-----------
-- Words --
-----------

-- :name get-global-words :? :*
-- :doc retrieve global words given a limit and an offset
SELECT * FROM dictionary_globalword
LIMIT :limit OFFSET :offset

-- :name get-global-word :? :*
-- :doc retrieve global words for a given untranslated
SELECT * FROM dictionary_globalword
WHERE untranslated = :untranslated

-- :name search-global-words :? :*
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
