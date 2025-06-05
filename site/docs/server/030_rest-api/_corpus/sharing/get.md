# Get sharing configuration for user corpus

**URL** : `/blacklab-server/<corpus-name>/sharing`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

### Content examples

```json
{
    "users[]": [
        "someone@example.com",
        "someone-else@example.com"
    ]
}
```

## TODO

- `users[]` is a bit of an odd JSON key to use (probably taken from the `users[]` parameter to the `POST` operation?), maybe just `users` instead?
