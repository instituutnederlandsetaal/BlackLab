---
title: Versions; output format
---
# General notes about the API

## API versions

Use `api=4` or `api=5` to specify the API version to use. Configure `parameters.api` in your `blacklab-server.yaml` to set the default version to use. Without either of these, the BlackLab version will determine the API version used. Support for older version(s) is a transitionary measure and will eventually be dropped.

Full details can be found in [API versions](miscellaneous/api-versions).

## Output format

To request a specific output format, either:

- pass the HTTP header `Accept` with the value `application/json`, `application/xml` or `text/csv`, or
- pass the query parameter `outputformat` with the value `json`, `xml` or `csv`.

If both are specified, the parameter has precedence.

::: details Notes about CSV

For CSV hits/docs results, the parameters `csvsummary` determines whether to include a summary of the search parameters in the output `[no]` and `csvsepline` determines whether to include a separator declaration that will help Microsoft Excel read the file `[no]`.

`listvalues` can be a comma-separated list of annotations to include in the results. `listmetadatavalues` is the same for metadata fields. `listspanattributes` for span attributes (e.g. if your document has `<speech speaker="Joe">` tags, then `listspanattributes=speech.speaker` will include a column with the speaker of the matched text). For each of these parameters, `*` means include all.

If a metadata field has multiple values (e.g. if a document has multiple authors), they will be concatenated with `|` as the separator. `|`, `\n`, `\r` and `\\` will be backslash-escaped.

As is common in CSV, values may be double-quoted if necessary (e.g. if a value contains a comma). Any double quotes already in the values will be doubled, so `say "yes", or "no"?` will become `"say ""yes"", or ""no""?"`

:::

**NOTE:** if you're using JSON, you can see a tree view of the JSON response in most browsers using the network tab in the debug console (F12).

