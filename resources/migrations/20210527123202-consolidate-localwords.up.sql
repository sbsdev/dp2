ALTER TABLE dictionary_localword RENAME dictionary_localword_old;
--;;
CREATE TABLE dictionary_localword (
  untranslated 		   varchar(100) COLLATE utf8_bin NOT NULL,
  uncontracted 		   varchar(100) COLLATE utf8_bin          DEFAULT NULL,
  contracted   		   varchar(100) COLLATE utf8_bin          DEFAULT NULL,
  type         		   smallint(6) unsigned          NOT NULL DEFAULT '0',
  homograph_disambiguation varchar(100) COLLATE utf8_bin NOT NULL DEFAULT '',
  document_id              int(11)                       NOT NULL,
  isLocal                  tinyint(1)                    NOT NULL DEFAULT '0',
  isConfirmed              tinyint(1)                    NOT NULL DEFAULT '0',
  isDeferred               tinyint(1)                    NOT NULL DEFAULT '0',
  UNIQUE KEY dictionary_localword_uniq (untranslated, type, homograph_disambiguation, document_id),
  INDEX (untranslated),
  FOREIGN KEY (document_id) REFERENCES documents_document (id) ON DELETE CASCADE
);
--;;
INSERT INTO dictionary_localword (
  untranslated,
  uncontracted,
  contracted,
  type,
  homograph_disambiguation,
  document_id,
  isLocal,
  isConfirmed
)
SELECT DISTINCT
  w.untranslated,
  w.uncontracted,
  w.contracted,
  w.type,
  w.homograph_disambiguation,
  w.document_id,
  BIT_OR(w.isLocal) AS isLocal,
  BIT_OR(w.isConfirmed) AS isConfirmed
FROM
(SELECT t1.untranslated, t2.braille as uncontracted, t1.braille as contracted,
        t1.type, t1.homograph_disambiguation, t1.document_id,
	IFNULL(t1.isLocal OR t2.isLocal,FALSE) AS isLocal,
	IFNULL(t1.isConfirmed OR t2.isConfirmed,FALSE) AS isConfirmed
 FROM dictionary_localword_old t1
 LEFT JOIN dictionary_localword_old t2
 ON t1.untranslated = t2.untranslated
 AND t1.type = t2.type
 AND t1.homograph_disambiguation = t2.homograph_disambiguation
 AND t1.document_id = t2.document_id
 AND t1.grade <> t2.grade
 WHERE t1.grade = 2
 UNION DISTINCT
 SELECT t1.untranslated, t1.braille as uncontracted, t2.braille as contracted,
 	 t1.type, t1.homograph_disambiguation, t1.document_id,
 	 IFNULL(t1.isLocal OR t2.isLocal,FALSE) AS isLocal,
 	 IFNULL(t1.isConfirmed OR t2.isConfirmed,FALSE) AS isConfirmed
 FROM dictionary_localword_old t1
 LEFT JOIN dictionary_localword_old t2
 ON t1.untranslated = t2.untranslated
 AND t1.type = t2.type
 AND t1.homograph_disambiguation = t2.homograph_disambiguation
 AND t1.document_id = t2.document_id
 AND t1.grade <> t2.grade
 WHERE t1.grade = 1) AS w
 GROUP BY w.untranslated, w.uncontracted, w.contracted, w.type, w.homograph_disambiguation, w.document_id;


