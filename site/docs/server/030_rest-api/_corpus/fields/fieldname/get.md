# Field information

Metadata about a field, including indexing settings, field values and more.

**URL** : `/blacklab-server/<corpus-name>/fields/<fieldname>`

**Method** : `GET`

#### Parameters

| Parameter     | Description                                                                                                                                                                                                                       |
|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `listvalues`  | Comma-separated list of annotations we want to list the values for. Terms and frequencies will be returned under the `terms` key (API v4+) or `values` key (old API). `valueListComplete` will indicate if all values are listed. |
| `limitvalues` | Maximum number of values to return. Default: `200`                                                                                                                                                                                 |

## Success Response

**Code** : `200 OK`

### Content examples

Response for a metadata field:

```json
{
  "indexName": "BaB",
  "fieldName": "title",
  "isAnnotatedField": false,
  "displayName": "Title",
  "description": "",
  "uiType": "",
  "type": "TOKENIZED",
  "analyzer": "DEFAULT",
  "unknownCondition": "NEVER",
  "unknownValue": "unknown",
  "displayValues": {
  },
  "fieldValues": {
    "To A. Soomerkamp, 7 november 1780": 1,
    "To A.W.H. Nolthenius, 11 november 1780": 1,
    "To Aart van den Broeke, 21 oktober 1780": 1,
    ...
  },
  "valueListComplete": false
}
```

Response for an annotated field:

```json
{
  "indexName": "BaB",
  "fieldName": "contents",
  "isAnnotatedField": true,
  "displayName": "Contents",
  "description": "Contents of the documents.",
  "hasContentStore": true,
  "hasXmlTags": true,
  "mainAnnotation": "word_or_lemma",
  "displayOrder": [
    "word_or_lemma",
    "word",
    "word_xml",
    ...
  ],
  "annotations": {
    "word_or_lemma": {
      "displayName": "Word or lemma",
      "description": "",
      "uiType": "combobox",
      "hasForwardIndex": true,
      "sensitivity": "ONLY_INSENSITIVE",
      "offsetsAlternative": "i",
      "isInternal": false
    },
    "word": {
      "displayName": "Word",
      "description": "",
      "uiType": "combobox",
      "hasForwardIndex": true,
      "sensitivity": "SENSITIVE_AND_INSENSITIVE",
      "offsetsAlternative": "",
      "isInternal": false
    },
    "word_xml": {
      "displayName": "",
      "description": "",
      "uiType": "",
      "hasForwardIndex": true,
      "sensitivity": "ONLY_INSENSITIVE",
      "offsetsAlternative": "",
      "isInternal": true
    },
    "lemma": {
      "displayName": "Lemma",
      "description": "",
      "uiType": "combobox",
      "hasForwardIndex": true,
      "sensitivity": "SENSITIVE_AND_INSENSITIVE",
      "offsetsAlternative": "",
      "isInternal": false
    },
    ...
  }
}
```

### Notes

This is the same information the `/corpus/<corpus-name>` endpoint returns for all fields.
