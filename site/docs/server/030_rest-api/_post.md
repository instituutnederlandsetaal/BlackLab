# Create user corpus

Add a user corpus.

**URL** : `/blacklab-server`

**Method** : `POST`

**Auth required**: YES

#### Parameters

- `name`: name for the new corpus. Must start with the user id, followed by a `:`, followed by a short index name (which must consist of letters, digits, underscore `_`, dash `-` or period `.`). Will become part of the URL. Example: (e.g. `jan.niestadt@ivdnt.org:my-fun-corpus`)
- `display`: display name for the new corpus (e.g. `My fun corpus!`)
- `format`: default input format name for the new corpus (e.g. `tei-p5`). Must refer to an available input format. 

## Success Response

**Code** : `201 Created`

### Content example

```json
{
    code: "SUCCESS",
    message: "Index created succesfully."
}
```

## Error Response

**Code**: `403 Forbidden`

### Content example

```json
{
    code: "FORBIDDEN_REQUEST",
    message: "You can only create indices in your own private area."
}
```

## Notes

The URL for the new corpus will be `/blacklab-server/userid:name/`, so e.g. `/blacklab-server/jan.niestadt@ivdnt.org:my-fun-corpus/`.

To add documents, POST to `/blacklab-server/userid:name/docs/`. See [here](corpus/docs/post.md).

## TODO

- The response should probably return the URL for the new corpus.
