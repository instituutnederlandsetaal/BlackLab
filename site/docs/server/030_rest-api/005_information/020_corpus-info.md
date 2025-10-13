# Corpus information

Return the corpus metadata, including size, document format, annotated and metadata fields, status and more.

**URL**
- `/blacklab-server/<corpus-name>` (API v4)
- `/blacklab-server/corpora/<corpus-name>` (future API v5)

**Method** : `GET`

| Parameter     | Description                                                                                                |
|---------------|------------------------------------------------------------------------------------------------------------|
| `limitvalues` | Maximum number of values to return for fields. Default: `200`                                              |
| `custom`      | **(API v5)** Whether to include custom properties like `displayName`, `description`, etc. Default: `false` |

## Success Response

**HTTP response code**: `200 OK`

### Content examples

::: tabs
=== API v4

```jsonc
// API v4: /blacklab-server/parlamint/
{
  "indexName": "parlamint",
  "displayName": "ParlaMint BE federaal",
  "description": "Corpus van Parlementaire zittingen",
  "textDirection": "ltr",
  "status": "available",
  "contentViewable": true,
  "documentFormat": "parlamint-saxon",
  "tokenCount": 50672559,
  "documentCount": 2349,
  "versionInfo": {
    "blacklabBuildTime": "2025-03-20T12:32:20Z",
    "blacklabVersion": "4.0.0-SNAPSHOT",
    "blacklabScmRevision": "7efeb8d",
    "indexFormat": "4",
    "timeCreated": "2025-04-29 11:44:20",
    "timeModified": "2025-04-29 11:44:20"
  },
  "pidField": "pid",
  "fieldInfo": {
    "pidField": "pid",
    "titleField": "meeting",
    "authorField": "",
    "dateField": "datering"
  },
  "mainAnnotatedField": "contents",
  "annotatedFields": {
    "contents": {
      "fieldName": "contents",
      "isAnnotatedField": true,
      "tokenCount": 50672559,
      "documentCount": 2349,
      "displayName": "Contents",
      "description": "Contents of the documents.",
      "hasContentStore": true,
      "hasXmlTags": true,
      "mainAnnotation": "word",
      "displayOrder": [
        "word",
        "lemma",
        "pos",
      ],
      "annotations": {
        "word": {
          "displayName": "Word",
          "description": "",
          "uiType": "select",
          "hasForwardIndex": true,
          "sensitivity": "SENSITIVE_AND_INSENSITIVE",
          "offsetsAlternative": "s",
          "isInternal": false
        },
        "lemma": {
          "displayName": "Lemma",
          "description": "",
          "uiType": "select",
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
          "isInternal": false
        },
        "punct": {
          "displayName": "Punct",
          "description": "",
          "uiType": "",
          "hasForwardIndex": true,
          "sensitivity": "ONLY_INSENSITIVE",
          "offsetsAlternative": "",
          "isInternal": true
        }
      }
    }
  },
  "metadataFields": {
    "pid": {
      "fieldName": "pid",
      "isAnnotatedField": false,
      "type": "UNTOKENIZED",
      "analyzer": "DEFAULT",
      "fieldValues": {
        "ParlaMint-BE_2014-06-19-54-plenair-ip001x.ana": 1
        // ...
      },
      "valueListComplete": false
    },
    "datering": {
      "fieldName": "datering",
      "isAnnotatedField": false,
      "type": "TOKENIZED",
      "analyzer": "DEFAULT",
      "displayName": "Datering",
      "description": "",
      "uiType": "select",
      "unknownCondition": "NEVER",
      "unknownValue": "unknown",
      "displayValues": {
      },
      "fieldValues": {
        "2014-06-19": 1,
        "2014-06-30": 1,
        "2014-07-17": 2
        // ...
      },
      "valueListComplete": false
    },
    "fromInputFile": {
      "fieldName": "fromInputFile",
      "isAnnotatedField": false,
      "type": "UNTOKENIZED",
      "analyzer": "DEFAULT",
      "displayName": "From input file",
      "description": "",
      "uiType": "",
      "unknownCondition": "NEVER",
      "unknownValue": "unknown",
      "displayValues": {
      },
      "fieldValues": {
        "/path/to/input/file.xml": 1
        // ...
      },
      "valueListComplete": false
    }
  },
  "metadataFieldGroups": [
    {
      "name": "Basic",
      "fields": [
        "datering",
        "fromInputFile"
      ]
    }
  ],
  "annotationGroups": {
    "contents": [
      {
        "name": "Basic",
        "annotations": [
          "word",
          "lemma",
          "pos"
        ]
      }
    ]
  }
}
```

=== API v5

```jsonc
// API v5: /blacklab-server/corpora/parlamint/
{
  "corpusName": "parlamint",
  "status": "available",
  "contentViewable": true,
  "documentFormat": "parlamint-saxon",
  "count": {
    "tokens": 50672559,
    "documents": 2349
  },
  "versionInfo": {
    "blacklabBuildTime": "2025-03-20T12:32:20Z",
    "blacklabVersion": "4.0.0-SNAPSHOT",
    "blacklabScmRevision": "7efeb8d",
    "indexFormat": "4",
    "timeCreated": "2025-04-29 11:44:20",
    "timeModified": "2025-04-29 11:44:20"
  },
  "pidField": "pid",
  "mainAnnotatedField": "contents",
  "annotatedFields": {
    "contents": {
      "fieldName": "contents",
      "isAnnotatedField": true,
      "count": {
        "tokens": 50672559,
        "documents": 2349
      },
      "hasContentStore": true,
      "mainAnnotation": "word",
      "annotations": {
        "word": {
          "hasForwardIndex": true,
          "sensitivity": "SENSITIVE_AND_INSENSITIVE",
          "offsetsAlternative": "s",
          "isInternal": false
        },
        "lemma": {
          "hasForwardIndex": true,
          "sensitivity": "SENSITIVE_AND_INSENSITIVE",
          "offsetsAlternative": "",
          "isInternal": false
        },
        "pos": {
          "hasForwardIndex": true,
          "sensitivity": "ONLY_INSENSITIVE",
          "offsetsAlternative": "",
          "isInternal": false
        }
      }
    }
  },
  "metadataFields": {
    "pid": {
      "fieldName": "pid",
      "isAnnotatedField": false,
      "type": "UNTOKENIZED",
      "analyzer": "DEFAULT",
      "fieldValues": {
        "ParlaMint-BE_2014-06-19-54-plenair-ip001x.ana": 1
        // ...
      },
      "valueListComplete": false
    },
    "datering": {
      "fieldName": "datering",
      "isAnnotatedField": false,
      "type": "TOKENIZED",
      "analyzer": "DEFAULT",
      "fieldValues": {
        "2014-06-19": 1
        // ...
      },
      "valueListComplete": false
    },
    "fromInputFile": {
      "fieldName": "fromInputFile",
      "isAnnotatedField": false,
      "type": "UNTOKENIZED",
      "analyzer": "DEFAULT",
      "fieldValues": {
        "/path/to/input/file.xml": 1
        // ...
      },
      "valueListComplete": false
    }
  }
}
```
:::

### API version differences

The major differences between API v4 and v5 are:

- API v5 omits custom properties like `displayName`, `description` and `fieldInfo` unless you specify `custom=true`. These properties are not used by BlackLab itself, but may be useful to some clients.
- API v5 has a `count` object with `tokens` and `documents`, while API v4 has `tokenCount` and no document count.

### Notes

- `versionInfo` gives information about when the corpus was created/updated, as well as what version of BlackLab it was created with.
