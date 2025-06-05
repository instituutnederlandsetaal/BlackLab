# Update sharing configuration for user corpus

Sets a new list of users to share the corpus with.

**URL** : `/blacklab-server/<corpus-name>/sharing`

**Method** : `POST`

**Auth required**: YES

#### Parameters

- `users[]`: userids to share the corpus with. Parameter may be specified multiple times, with one userid each. These userids replace any previous userids the corpus was shared with.

## Success Response

**Code** : `200 OK`

### Content examples

```json
{
    "code": "SUCCESS",
    "message": "Index shared with specified user(s)."
}
```
