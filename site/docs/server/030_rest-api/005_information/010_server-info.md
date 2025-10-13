# Server information

List available corpora and general information about the server environment, such as BlackLab version and whether a user is logged-in.

**URL**
- `/blacklab-server/` (API v4)
- `/blacklab-server/?api=exp` (future API v5)

**Method** : `GET`

## Success Response

**HTTP response code**: `200 OK`

### Content examples

A server with one corpus named *BaB* and no logged-in user might show this result:

:::tabs
=== API v4

```jsonc
{
  "apiVersion": "4.0",
  "blacklabBuildTime": 2025-06-16T09:06:31Z",
  "blacklabVersion": "4.0.0-SNAPSHOT",
  "blacklabScmRevision": "7e15de7",
  "corpora": {
    "BaB": {
      "status": "available",
      "documentFormat": "zeebrieven",
      "timeModified": "2020-12-08 10:06:26",
      "tokenCount": 459354
    }
  },
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
    "canCreateIndex": false,
    "debugMode": false
  }
}
```

=== API v5

```jsonc
{
  "apiVersion": "5.0",
  "blacklabBuildTime": 2025-06-16T09:06:31Z",
  "blacklabVersion": "4.0.0-SNAPSHOT",
  "blacklabScmRevision": "7e15de7",
  "corpora": {
    "BaB": {
      "status": "available",
      "documentFormat": "zeebrieven",
      "timeModified": "2020-12-08 10:06:26",
      "count": {
        "tokens": 459354,
        "documents": 802
      },
    }
  },
  "user": {
    "loggedIn": false,
    "canCreateIndex": false,
    "debugMode": false
  }
}
```

:::

### API version differences

The major differences between API v4 and v5 are:

- API v4 includes both `corpora` and `indices`. API v5 only has `corpora`, which doesn't include custom properties like `displayName` and `description` unless you specify `custom=true`.
- API v5 has a `count` object with `tokens` and `documents`, while API v4 has `tokenCount` and no document count.
