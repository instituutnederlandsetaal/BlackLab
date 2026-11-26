---
title: "Set sharing settings"
---
# Set who to share corpus with

Sets a new list of users to share the corpus with.

**URL** : `/blacklab-server/corpora/<corpus-name>/sharing`

**Method** : `POST`

<!-- @include: ../../../_must_be_authorized.md -->

#### Parameters

| Parameter | Description                                                                                                                              |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------|
| `users[]` | userids to share the corpus with. Parameter may be specified multiple times, with one userid each. These userids replace any previous userids the corpus was shared with.                                                                                                       |

## Success Response

**HTTP response code**: `200 OK`

### Content examples

```json
{
    "code": "SUCCESS",
    "message": "Index shared with specified user(s)."
}
```
