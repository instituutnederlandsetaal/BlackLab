# Search

The main endpoints for searching corpora:

* [Find hits](find-hits) : `GET /blacklab-server/<corpus-name>/hits`<br>Find matches for a text pattern and optionally group them.
* [Find documents](find-documents) : `GET /blacklab-server/<corpus-name>/docs`<br>Find documents and optionally group them.

There's also a few less common search endpoints:

* [Term frequencies](termfreq) : `GET /blacklab-server/<corpus-name>/termfreq`<br>Determine term frequencies for a field
* [Autocomplete](autocomplete) : `GET /blacklab-server/<corpus-name>/autocomplete`<br>
  Autocomplete a term in a field
* [Parse a pattern](parse-pattern.md) : `GET /blacklab-server/<corpus-name>/parse-pattern`<br>
  Parse a pattern, returning the parse tree
