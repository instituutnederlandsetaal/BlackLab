# Add documents to user corpus

**URL** : `/blacklab-server/<corpus-name>/docs`

**Method** : `POST`

**Auth required**: YES

#### Basic parameters

Files uploaded may be regular files or `.zip` or `.tar.gz` archives.

The document format is always the index' default format set during creation. 

| Parameter    |     | Description                                                                                                                              |
|--------------|:----|------------------------------------------------------------------------------------------------------------------------------------------|
| `data`       |     | File to index. Single file upload.                                                                                                       |
| `data[]`     |     | Files to index. Multiple file uploads can be handled with this.                                                                          |
| `linkeddata` |     | Linked data file. Single file upload. Only relevant if your input format uses linked documents (e.g. a document containing the metadata) |
| `linkeddata` |     | Linked data files. Multiple file uploads can be handled with this.                                                                       |


## Success Response

**Code** : `200 OK`

### Content examples

```json
{
    "code": "SUCCESS",
    "message": "Data added succesfully."
}
```

## TODO

- add `format` parameter to make it possible to override the default document format, so you can add documents of several formats to one index
- should probably return `201 Created`
- should this automatically update existing documents based on `pidField`? Or at least as an option, e.g. `overwrite=true`?
