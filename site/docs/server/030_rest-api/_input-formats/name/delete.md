# Delete input format

Delete one of the user's input formats.

**URL** : `/blacklab-server/input-formats/<name>`

**Method** : `DELETE`

**Auth required**: YES

## Success Response

**Code** : `200 OK`

### Content example

```json
{
    code: "SUCCESS",
    message: "Format deleted."
}
```

## Error Response

**Code**: `404 Not Found`

### Content example

```json
{
    code: "FORMAT_NOT_FOUND",
    message: "Specified format was not found"
}
```
**Code**: `400 Bad Request`

### Content example

```json
{
    code: "CANNOT_DELETE_INDEX",
    message: "Could not delete format. The format is still being used by a corpus."
}
```

## Notes

This deletes a user input format. Use input format names start with the userid, so if your input format is named `my-format` and your userid is `me@example.com`, you should refer to the input format as `me@examples.com:my-format`.
