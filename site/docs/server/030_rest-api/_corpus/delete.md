# Delete user corpus

Add a user corpus.

**URL** : `/blacklab-server/<corpus-name>`

**Method** : `DELETE`

**Auth required**: YES

## Success Response

**Code** : `200 OK`

### Content example

```json
{
    code: "SUCCESS",
    message: "Index deleted succesfully."
}
```

## Error Response

**Code**: `401 Not Authorized`

### Content example

```json
{
    code: "NOT_AUTHORIZED",
    message: "Unauthorized operation. Can only delete private indices."
}
```

## TODO

- We should harmonize HTTP status codes (when do we use 403 Forbidden, when 401 Not Authorized, etc.?)
