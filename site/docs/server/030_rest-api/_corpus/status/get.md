# Corpus status

The current status of a corpus, plus some basic metadata. See notes below.

**URL** : `/blacklab-server/<corpus-name>/status`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

### Content examples

```json
{
  "indexName": "BaB",
  "displayName": "Brieven als Buit",
  "description": "Approximately 40,000 Dutch letters sent by sailors from the second half of the 17th to the early 19th centuries.",
  "status": "available",
  "documentFormat": "zeebrieven",
  "timeModified": "2020-12-08 10:06:26",
  "tokenCount": 459354
}
```

### Notes

The `status` field indicates whether or not the corpus is available for searching. While the corpus is being indexed, the `status` will be `indexing` and it will not be available for searching. A corpus that was just created will have a `status` of `empty`.

The `/corpus/<corpus-name>` endpoint also returns this information, but this transfers less data if you need to check the status regularly.
