# Delete corpus

Delete one of your private corpora.

**URL** : `/blacklab-server/<corpus-name>`

**Method** : `DELETE`

_(will fail if you are not logged in or not authorized)_

## Success Response

**HTTP response code** : `200 OK`

```json
{
    code: "SUCCESS",
    message: "Index deleted succesfully."
}
```

## Error Response

**HTTP response code**: `401 Not Authorized`

```json
{
    code: "NOT_AUTHORIZED",
    message: "Unauthorized operation. Can only delete private indices."
}
```

## TODO

- We should harmonize HTTP status codes (when do we use 403 Forbidden, when 401 Not Authorized, etc.?)
