ALTER TABLE dictionary_globalword RENAME dictionary_globalword_old;
--;;
CREATE TABLE dictionary_globalword (
  untranslated 		   varchar(100) COLLATE utf8_bin NOT NULL,
  uncontracted 		   varchar(100) COLLATE utf8_bin          DEFAULT NULL,
  contracted   		   varchar(100) COLLATE utf8_bin          DEFAULT NULL,
  type         		   smallint(6) unsigned          NOT NULL DEFAULT '0',
  homograph_disambiguation varchar(100) COLLATE utf8_bin NOT NULL DEFAULT '',
  UNIQUE KEY dictionary_localword_uniq (untranslated, type, homograph_disambiguation),
  INDEX (untranslated)
);
--;;
INSERT INTO dictionary_globalword (
  untranslated,
  uncontracted,
  contracted,
  type,
  homograph_disambiguation
)
SELECT t1.untranslated,
       t2.braille as uncontracted,
       t1.braille as contracted,
       t1.type,
       t1.homograph_disambiguation
FROM dictionary_globalword_old t1
LEFT JOIN dictionary_globalword_old t2
ON t1.untranslated = t2.untranslated
AND t1.type = t2.type
AND t1.homograph_disambiguation = t2.homograph_disambiguation
AND t1.grade <> t2.grade
WHERE t1.grade = 2
UNION DISTINCT
SELECT t1.untranslated,
       t1.braille as uncontracted,
       t2.braille as contracted,
       t1.type,
       t1.homograph_disambiguation
FROM dictionary_globalword_old t1
LEFT JOIN dictionary_globalword_old t2
ON t1.untranslated = t2.untranslated
AND t1.type = t2.type
AND t1.homograph_disambiguation = t2.homograph_disambiguation
AND t1.grade <> t2.grade
WHERE t1.grade = 1;


