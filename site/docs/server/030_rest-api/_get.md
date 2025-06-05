# Corpora and server information

List available corpora and general information about the server environment, such as BlackLab version and whether a user is logged-in.

**URL** : `/blacklab-server`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

### Content examples

A server with one corpus named *BaB* and no logged-in user might show this result:

```json
{
  "blacklabBuildTime": "2022-01-01 00:00:00",
  "blacklabVersion": "3.0.0",
  "indices": {
    "BaB": {
      "displayName": "Brieven als Buit",
      "description": "Approximately 40,000 Dutch letters sent by sailors from the second half of the 17th to the early 19th centuries.",
      "status": "available",
      "documentFormat": "zeebrieven",
      "timeModified": "2020-12-08 10:06:26",
      "tokenCount": 459354
    }
  },
  "user": {
    "loggedIn": false,
    "canCreateIndex": false
  }
}
```
