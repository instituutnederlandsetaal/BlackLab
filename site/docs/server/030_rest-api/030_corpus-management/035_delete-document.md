# Delete a document

**URL** : `/blacklab-server/corpora/<corpus-name>/docs/<pid>`

**Method** : `DELETE`

<!-- @include: ../../../_must_be_authorized.md -->


## Success Response

**HTTP response code**: `200 OK`

### Content examples

```json
{
    "code": "SUCCESS",
    "message": "Document deleted succesfully."
}
```
