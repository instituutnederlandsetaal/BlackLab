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
        "id": "HitGroupScorerSalience",
        "localId": "coll-salience"
      },
      {
        "id": "HitGroupScorerGroupSize",
        "localId": "coll-groupsize"
      },
      {
        "id": "HitGroupScorerDice",
        "localId": "coll-dice"
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
        "id": "QueryFunctionGap",
        "localId": "gap"
      },
      {
        "id": "QueryFunctionSymbol",
        "localId": "symbol"
      },
      {
        "id": "QueryFunctionFuzzy",
        "localId": "_fuzzy"
      },
      {
        "id": "QueryFunctionLen",
        "localId": "len"
      },
      {
        "id": "QueryFunctionMeetWithin",
        "localId": "meet_within"
      },
      {
        "id": "QueryFunctionInRange",
        "localId": "in_range"
      },
      {
        "id": "QueryFunctionStr",
        "localId": "str"
      },
      {
        "id": "QueryFunctionAbs",
        "localId": "abs"
      },
      {
        "id": "QueryFunctionEnd",
        "localId": "end"
      },
      {
        "id": "QueryFunctionQuery",
        "localId": "query"
      },
      {
        "id": "QueryFunctionFixed",
        "localId": "_fixed"
      },
      {
        "id": "QueryFunctionMeet",
        "localId": "meet"
      },
      {
        "id": "QueryFunctionUnion",
        "localId": "union"
      },
      {
        "id": "QueryFunctionStart",
        "localId": "start"
      },
      {
        "id": "QueryFunctionList",
        "localId": "list"
      }
    ],
    "QueryParserProvider": [
      {
        "id": "JsonParserProvider",
        "localId": "json-bql"
      },
      {
        "id": "BcqlParserProvider",
        "localId": "corpusql"
      },
      {
        "id": "ContextqlParserProvider",
        "localId": "contextql"
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
