# Information

Endpoints that provide information about the server and corpora.

* [Server information](server-info) : `GET /blacklab-server`<br>Information about the server, such as BlackLab version, list of corpora, authentication status.
* [Corpus information](corpus-info) : `GET /blacklab-server/<corpus-name>/`<br>Information about the corpus such as size, document format, fields, and status.
* [Corpus status](status) : `GET /blacklab-server/<corpus-name>/status`<br>Status of the corpus, such as whether it is available for searching, and some basic metadata.
* [Field information](field-info) : `GET /blacklab-server/<corpus-name>/fields/<fieldname>`<br>Information about a (metadata or annotated) field in the corpus, such as a list of values.
* [Span and relation types](relations) : `GET /blacklab-server/<corpus-name>/relations`<br>What span and relation types occur in the corpus?

