# Autocomplete

Return terms with the specified prefix that occur in a (metadata or annotated) field.

**URL**

- Metadata field:<br>`/blacklab-server/<corpus-name>/autocomplete/<metadata-field-name>`
- Annotation on annotated field:<br>`/blacklab-server/<corpus-name>/autocomplete/<annotated-field-name>/<annotation>`

**Method** : `GET`

#### Parameters

| Parameter   | Description |
|-------------|-------------|
| `term`      | Prefix to find matches for. |
| `tokenized` | Metadata fields only. For tokenized metadata fields, return matching tokens (`true`) or original full field values (`false`, default). |

## Success Response

**HTTP response code**: `200 OK`

### Content examples

`/blacklab-server/my-corpus/autocomplete/contents/word?term=d`

```jsonc
[
  "d",
  "dabenis",
  "daniel",
  "dankers",
  "dankerts",
  "david",
  "davids",
  "de",
  "debora",
  "december",
  "decker",
  "dekker",
  "delvos-stoel",
  "den",
  "dennis",
  "der",
  "deters",
  "devolt",
  "dies",
  "dill",
  "dimmenssen",
  "dina",
  "dionijsius",
  "dirck",
  "dirk",
  "dirksen",
  "dirksz",
  "dis",
  "dorothea",
  "dorsmans",
  "doude"
]
```

## Notes

For a metadata field, if the field is tokenized, it will by default return whole field values containing matching tokens. Set `tokenized=true` to return matching tokens instead (the old behavior). For untokenized fields, whole field values are returned.

Currently, there's no way to specify case-/accent-sensitivity. Insensitive matching will be used for an annotation if it was indexed insensitively, otherwise it will fall back to sensitive matching.
