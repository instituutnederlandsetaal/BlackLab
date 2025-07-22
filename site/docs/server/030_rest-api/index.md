--- 
title: API reference
---

# BlackLab Server REST API reference

## API compatibility

Use `api=4` or `api=5` to specify the API version to use. Configure `parameters.api` in your `blacklab-server.yaml` to set the default version to use. Without either of these, the BlackLab version will determine the API version used. Support for older version(s) is a transitionary measure and will eventually be dropped.

Full details can be found in [API versions](miscellaneous/api-versions).

## Output format

To request a specific output format, either:

- pass the HTTP header `Accept` with the value `application/json`, `application/xml` or `text/csv`, or
- pass the query parameter `outputformat` with the value `json`, `xml` or `csv`.

If both are specified, the parameter has precedence.

::: details Notes about CSV

For CSV hits/docs results, the parameters `csvsummary` determines whether to include a summary of the search parameters in the output `[no]` and `csvsepline` determines whether to include a separator declaration that will help Microsoft Excel read the file `[no]`.

`listvalues` can be a comman-separated list of annotations to include in the results. `listmetadatavalues` is the same for metadata fields.

If a metadata field has multiple values (e.g. if a document has multiple authors), they will be concatenated with `|` as the separator. `|`, `\n`, `\r` and `\\` will be backslash-escaped.

As is common in CSV, values may be double-quoted if necessary (e.g. if a value contains a comma). Any double quotes already in the values will be doubled, so `say "yes", or "no"?` will become `"say ""yes"", or ""no""?"`

:::

## Endpoints

The rest of this section documents all of BlackLab Server's endpoints. For a more guided introducion, see the [overview](../overview).

<!-- (used this [template](https://github.com/jamescooke/restapidocs/tree/master/examples)) -->

* [Server and corpus information](./information/)<br>Information about the server and available corpora.
* [Search](./search/)<br>How to search a corpus.
* [Documents](./documents/)<br>How to retrieve (snippets of) documents and document metadata.
* [Manage corpora](./corpus-management/)<br>How to create, update and delete (private user) corpora.
* [Input formats](./input-formats/)<br>How to manage input formats for indexing data.
* [Miscellaneous](./miscellaneous/)<br>Additional endpoints and information.

