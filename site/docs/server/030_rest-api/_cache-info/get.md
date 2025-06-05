# Show results cache

Show the contents of the query results cache. 

**URL** : `/blacklab-server/<corpus-name>/cache-info`

**Method** : `GET`

Only available in debug mode.

## Success Response

**Code** : `200 OK`

### Content examples

```json
{
  "cacheStatus": {
    "sizeBytes": 144,
    "freeMemory": 4358369288,
    "maxJobAgeSec": 600,
    "numberOfSearches": 6,
    "maxQueuedSearches": 20,
    "targetFreeMemMegs": 3000,
    "maxSearchAgeSec": 600,
    "minFreeMemForSearchMegs": 1000,
    "countsPerStatus": {
      "running": 0,
      "queued": 0,
      "finished": 6,
      "cancelled": 0
    },
    "maxSearchTimeSec": 300
  },
  "cacheContents": [
    {
      "stats": {
        "userWaitTime": 0.102,
        "notAccessedFor": 315.777,
        "type": "search",
        "numberOfStoredHits": 1,
        "status": "finished"
      },
      "class": "SearchHitsWindow",
      "jobDesc": "window(hits(FILTER(TERM(contents%word@i:nieuwkoop), SingleDocIdFilter(106681))), 0, 2147483647)"
    }, 
    ...
  ]
}
```

## TODO

We could collect any debug endpoints under a `/debug` path. This would become `/debug/cache` and would encompass both viewing the cache and clearing it (e.g. with a `POST` and parameter `clear=true`)
