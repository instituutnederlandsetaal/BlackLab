# Add or update input format

Add a user input format, or update an existing one. 

**URL** : `/blacklab-server/input-formats`

**Method** : `POST`

**Auth required**: YES

#### Parameters

- `data`: a file upload of the configuration file

## Success Response

**Code** : `200 OK`

### Content example

```json
{
    code: "SUCCESS",
    message: "Format added."
}
```

## Error Response

**Code**: `400 Bad Request`

### Content example

```json
{
    code: "CANNOT_CREATE_INDEX",
    message: "Could not create/overwrite format. The server is not configured with support for user content."
}
```

## Notes

This creates/updates a user input format. Use input format names start with the userid, so if your input format is named `my-format` and your userid is `me@example.com`, the input format will be named `me@examples.com:my-format`.

## TODO

- Succes should probably return `201 Created`
- The success message should indicate whether the format was created or added.
- `CANNOT_CREATE_INDEX` is a misnomer and should probably be changed to a more generic CANNOT_CREATE.
- Maybe there should be an `overwrite` parameter that indicates whether or not it is your intention to overwrite an existing format.
