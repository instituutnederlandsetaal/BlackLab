# Add documents

**URL** : `/blacklab-server/<corpus-name>/docs`

**Method** : `POST`

_(will fail if you are not logged in or not authorized)_

#### Basic parameters

Files uploaded may be regular files or `.zip` or `.tar.gz` archives.

The document format is always the index' default format set during creation. 

| Parameter    | Description                                                                                                                              |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `data`       | File to index. Single file upload.                                                                                                       |
| `data[]`     | Files to index. Multiple file uploads can be handled with this.                                                                          |
| `linkeddata` | Linked data file. Single file upload. Only relevant if your input format uses linked documents (e.g. a document containing the metadata) |
| `linkeddata` | Linked data files. Multiple file uploads can be handled with this.                                                                       |
| `converters` | Extra `FileConverter` plugins to apply before indexing. See below.                                                                       |

::: details <b>The <code>converters</code> parameter explained</b>

(available from version `5.0` and current `dev`)

You can use the `converters` parameter to request extra `FileConverter` [plugins](/development/customization/) to be applied before indexing. The parameter uses a JSON structure:

```json
{
    "first": [
        {
            "id": "my-converter1",
            "quality": 5
        }
    ],
    "last": [
        {
            "id": "my-converter2",
            "lookup-url": "https://example.com/some/api"
        }
    ]
}
```

Converters under `first` will be applied _before_ any converters that may be defined in the `.blf.yaml` input format config you're using. Converters under `last` will be applied _after_ these converters. You can specify either `first` or `last`, or both.

The plugins referred to must be web-safe. That is, the plugin's `isWebSafe()` method must return true, or the plugin id must be specified in the [`plugins.allowed`](/server/configuration#plugins) list in `blacklab-server.yaml`. If neither of these is true, the plugin is not allowed to run. This is intended to be a safety measure. You as the administrator of the server are responsible for making sure any plugins that are enabled this way are actually safe.

:::

## Success Response

**HTTP response code**: `200 OK`

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
