CREATE TABLE IF NOT EXISTS dictionary_globalword_new
  SELECT t1.untranslated, t2.braille as uncontracted, t1.braille as contracted, t1.type, t1.homograph_disambiguation
  FROM dictionary_globalword t1
  LEFT JOIN dictionary_globalword t2
  ON t1.untranslated = t2.untranslated
  AND t1.type = t2.type
  AND t1.homograph_disambiguation = t2.homograph_disambiguation
  AND t1.grade <> t2.grade
  WHERE t1.grade = 2
  UNION DISTINCT
  SELECT t1.untranslated, t1.braille as uncontracted, t2.braille as contracted, t1.type, t1.homograph_disambiguation
  FROM dictionary_globalword t1
  LEFT JOIN dictionary_globalword t2
  ON t1.untranslated = t2.untranslated
  AND t1.type = t2.type
  AND t1.homograph_disambiguation = t2.homograph_disambiguation
  AND t1.grade <> t2.grade
  WHERE t1.grade = 1;
--;;
CREATE UNIQUE INDEX IF NOT EXISTS dictionary_globalword_new_unique on dictionary_globalword_new (untranslated, type, homograph_disambiguation);

