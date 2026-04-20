# Autocomplete

Return terms with the specified prefix that occur in a (metadata or annotated) field.

**URL**

- Metadata field:<br>`/blacklab-server/<corpus-name>/autocomplete/<metadata-field-name>`
- Annotation on annotated field:<br>`/blacklab-server/<corpus-name>/autocomplete/<annotated-field-name>/<annotation>`

**Method** : `GET`

#### Parameters

| Parameter  | Description                                                                                                                                                                                  |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `term`     | Prefix to find matches for.                                                                                                                                                                  |
| `complete` | (`dev`/`5.x`; applies to metadata fields only) `full` returns the original field values; `term` returns indexed terms (lowercased, accents removed, individual words if field is tokenized). |

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

Currently, there's no way to specify case-/accent-sensitivity. Insensitive matching will be used for an annotation if it was indexed insensitively, otherwise it will fall back to sensitive matching. Metadata fields will always return insensitive (lowercased, no accents) unless `complete=full` is specified, in which case the unchanged original value is returned.
