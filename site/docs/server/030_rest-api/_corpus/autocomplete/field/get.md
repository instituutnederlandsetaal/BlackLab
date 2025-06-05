# Autocomplete

Return words matching a given prefix.

**URL** : `/blacklab-server/<corpus-name>/autocomplete/<metadata-field-name>`

**URL** : `/blacklab-server/<corpus-name>/autocomplete/<annotated-field-name>/<annotation>`

**Method** : `GET`

#### Parameters

| Parameter   | Description                 |
|-------------|-----------------------------|
| `term`      | Prefix to find matches for. |


## Success Response

**Code** : `200 OK`

### Content examples

```json
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

For a metadata field, if the field is tokenized, it will find individual words. If the field is untokenized, the whole field value will be returned.

Currently, there's no way to specify case/accent sensitivity. Insensitive matching will be used for an annotation if it was indexed insensitively, otherwise it will fall back to sensitive matching.
