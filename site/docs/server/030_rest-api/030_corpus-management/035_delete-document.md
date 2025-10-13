# Delete a document

**URL** : `/blacklab-server/<corpus-name>/docs/<pid>`

**Method** : `DELETE`

_(will fail if you are not logged in or not authorized)_


## Success Response

**HTTP response code**: `200 OK`

### Content examples

```json
{
    "code": "SUCCESS",
    "message": "Document deleted succesfully."
}
```
