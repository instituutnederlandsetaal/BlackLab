# Corpora shared with me

Get the list of corpora that have been shared with the currently logged-in user (on this BlackLab Server instance).

Authentication and user corpora must be enabled.

**URL** : `/blacklab-server/shared-with-me`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

### Content examples

```json
{
  "corpora": [
    "my-friend@example.com:their-corpus-name",
    "someone-else@example:com:another-fine-corpus"
  ]
}
```
