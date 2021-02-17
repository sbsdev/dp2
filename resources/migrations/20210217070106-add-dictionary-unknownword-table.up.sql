-- The idea here is to insert the new words of a document in a
-- (temporary) table and then let the database do the appropriate
-- joins to get all words that are neither in the global nor in any
-- local dict.

CREATE TABLE dictionary_unknownword
SELECT * FROM dictionary_localword
LIMIT 0;

--;;

ALTER TABLE dictionary_unknownword
DROP COLUMN id,
DROP COLUMN grade,
DROP COLUMN braille,
DROP COLUMN isConfirmed,
DROP COLUMN isDeferred;
