# Input formats

These give you information about input format configurations that BlackLab has access to: built-in formats, external format configuration files it found, and user formats if available.

There's also operations to add, update and delete private user formats; those are only available if user authentication and private user corpora are enabled.

* [List input formats](list-formats.md) : `GET /blacklab-server/input-formats`
* [Add or update input format](save-format.md) : `POST /blacklab-server/input-formats`
* [Input format configuration](get-format-config.md): `GET /blacklab-server/input-formats/<name>`
* [Input format XSLT](input-format-xslt.md): `GET /blacklab-server/input-formats/<name>/xslt`
* [Delete input format](delete-format.md) : `DELETE /blacklab-server/input-formats/<name>`

