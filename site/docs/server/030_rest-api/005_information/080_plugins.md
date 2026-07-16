# Plugins

<!-- @include: ../../../_from_v5.md -->

An overview of available (web-safe) plugins.

- **URL**: `/blacklab-server/plugins/`

**Method**: `GET`


## Success Response

**HTTP response code**: `200 OK`

(some parts of the response are omitted for brevity)

```jsonc
{
  "plugins": {
    "FileConverter": [
    ],
    "DocTaskType": [
    ],
    "HitGroupScorerType": [
      {
        "id": "coll-salience"
      },
      {
        "id": "coll-groupsize"
      },
      {
        "id": "coll-dice"
      }
    ],
    "IndexSourceType": [
    ],
    "InputFormatType": [
      // ...
    ],
    "ProcessingInstruction": [
      // ...
    ],
    "QueryFunction": [
      {
        "id": "gap"
      },
      {
        "id": "symbol"
      },
      {
        "id": "_fuzzy"
      },
      {
        "id": "len"
      },
      {
        "id": "meet_within"
      },
      {
        "id": "in_range"
      },
      {
        "id": "str"
      },
      {
        "id": "abs"
      },
      {
        "id": "end"
      },
      {
        "id": "query"
      },
      {
        "id": "_fixed"
      },
      {
        "id": "meet"
      },
      {
        "id": "union"
      },
      {
        "id": "start"
      },
      {
        "id": "list"
      }
    ],
    "QueryParserProvider": [
      {
        "id": "json-bql"
      },
      {
        "id": "corpusql"
      },
      {
        "id": "contextql"
      }
    ],
    "AuthMethodProvider": [
      // ...
    ]
  }
}
```

## Notes

Only plugins that are indicated to be "web-safe" (i.e. their id is listed in the `plugins.allowed` list in `blacklab-server.yaml`) will be returned by this endpoint.

See [plugins](/development/customization/) for more information on plugins and how to write your own.
