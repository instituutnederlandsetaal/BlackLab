---
title: "Set sharing settings"
---
# Set who to share corpus with

Sets a new list of users to share the corpus with.

**URL** : `/blacklab-server/<corpus-name>/sharing`

**Method** : `POST`

_(will fail if you are not logged in or not authorized)_

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
