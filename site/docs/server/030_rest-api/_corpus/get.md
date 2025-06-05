# Corpus information

Return the corpus metadata, including size, document format, annotated and metadata fields, status and more.

**URL** : `/blacklab-server/<corpus-name>`

**Method** : `GET`

| Parameter       | Description                                                  |
|-----------------|--------------------------------------------------------------|
| `limitvalues`   | Maximum number of values to return for fields. Default: `200` |

## Success Response

**Code** : `200 OK`

### Content examples

```json
{
  "indexName": "BaB",
  "displayName": "Brieven als Buit",
  "description": "Approximately 40,000 Dutch letters sent by sailors from the second half of the 17th to the early 19th centuries.",
  "status": "available",
  "contentViewable": true,
  "textDirection": "ltr",
  "documentFormat": "zeebrieven",
  "tokenCount": 459354,
  "documentCount": 1033,
  "versionInfo": {
    "blackLabBuildTime": "2019-12-18 11:33:58",
    "blackLabVersion": "2.0.0-RC1",
    "indexFormat": "3.1",
    "timeCreated": "2020-12-08 10:06:26",
    "timeModified": "2020-12-08 10:06:26"
  },
  "fieldInfo": {
    "pidField": "pid",
    "titleField": "title",
    "authorField": "author",
    "dateField": "witnessYear_from"
  },
  "annotatedFields": {
    "contents": {
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
        "lemma",
        "grouping_lemma",
        "isclitic",
        "iswordpart",
        "grouping_pos",
        "grouping_pos_full",
        "lempos",
        "wordform_group_id",
        "pos",
        "pos_type",
        "pos_subtype",
        "pos_wordpart"
      ],
      "annotations": {
        "word": {
          "displayName": "Word",
          "description": "",
          "uiType": "combobox",
          "hasForwardIndex": true,
          "sensitivity": "SENSITIVE_AND_INSENSITIVE",
          "offsetsAlternative": "",
          "isInternal": false
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
        "pos": {
          "displayName": "Part of speech",
          "description": "",
          "uiType": "pos",
          "hasForwardIndex": true,
          "sensitivity": "ONLY_INSENSITIVE",
          "offsetsAlternative": "",
          "isInternal": false,
          "subannotations": [
            "pos_type",
            "pos_wordpart",
            "pos_subtype"
          ]
        },
        ...
      }
    }
  },
  "metadataFields": {
    "adr_loc_land_norm": {
      "fieldName": "adr_loc_land_norm",
      "isAnnotatedField": false,
      "displayName": "Country",
      "description": "",
      "uiType": "",
      "type": "TOKENIZED",
      "analyzer": "DEFAULT",
      "unknownCondition": "NEVER",
      "unknownValue": "unknown",
      "displayValues": {
      },
      "fieldValues": {
        "Belgi\u00EB": 27,
        "China": 3,
        ...
      },
      "valueListComplete": true
    },
    ...
  },
  "metadataFieldGroups": [
    {
      "name": "Letter",
      "fields": [
        "datum_jaar",
        "type_brief",
        "autograaf",
        "signatuur"
      ]
    },
    ...
  ],
  "annotationGroups": {
    "contents": [
      {
        "name": "Basics",
        "annotations": [
          "word",
          "lemma",
          "grouping_lemma",
          "pos",
          "grouping_pos",
          "grouping_pos_full",
          "isclitic",
          "iswordpart"
        ]
      },
      {
        "name": "Part of Speech features",
        "annotations": [
          "pos_type",
          "pos_wordpart"
        ]
      }
    ]
  }
}
```

### Notes

- Not all information in the response is used by BlackLab itself. There is also information that may be useful to the client, such as display names for things, display order, logical groupings of fields, etc.
- `versionInfo` gives information about when the corpus was created/updated, as well as what version of BlackLab it was created with.
- `fieldInfo` gives information about certain special fields: `pidField` is the field used as a persistent identifier to refer to documents. The other fields, such as `titleField` are not used by BlackLab, but may be used by an application when showing search results.
- To be consistent with the server-info response, the Solr version uses `blacklabVersion` and `blacklabBuildTime` (with a lowercase `l`).

### TODO

- It would be better to isolate custom information (not used by BlackLab) into `"custom": { ... }` objects that are completely free-form. Whether or not these are returned could be controlled using a parameter.

