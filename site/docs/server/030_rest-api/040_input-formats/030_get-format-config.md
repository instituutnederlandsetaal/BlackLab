# Get input format

Get the complete format configuration file for one of the available formats. 

**URL** : `/blacklab-server/input-formats/<name>`

**Method** : `GET`

## Success Response

**HTTP response code**: `200 OK`

### Content examples

```json
{
  "formatName": "tei-p5",
  "configFileType": "yaml",
  "configFile": "(format configuration)"
}
```

`configFile` contains an entire input format configuration in the format specified by `configFileType` (`yaml` or `json`).

## Error Response

**HTTP response code**: `404 Not Found`

### Content example

```json
{
  "code": "NOT_FOUND",
  "message": "The format <name> is not configuration-based, and therefore cannot be displayed."
}
```

## TODO

- maybe use a different error than `404 Not Found` if the format exists but does not target XML.
