# Document contents

Retrieve the original input document, presuming it's stored in the corpus (it is by default).

**URL** : `/blacklab-server/<corpus-name>/docs/<pid>/contents`

**Method** : `GET`

#### Parameters

All parameters are optional.  `wordstart` and `wordend` refer to token position in a document, 0-based.

Partial contents XML output will be wrapped in `<blacklabResponse/>` element to ensure a single root element.

| Parameter     | Description                                                                                                                                                                                                                                                                                                    |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `patt`        | Pattern to highlight in the document. `<hl>...</hl>` tags will be added to highlight hits.                                                                                                                                                                                                                     |
| `field`       | Annotated field to get snippet for.  (default: main annotated field)<br>(**NOTE:** in case of a parallel corpus, you can shorten this to only the version, e.g. `nl` if you want to get the contents for field `contents__nl`)                                                                                 |
| `searchfield` | (parallel corpora only) Annotated field that was searched. Used in conjunction with the `rfield()` function (see [relations querying](/guide/query-language/relations)) to highlight the correct hits.<br>(**NOTE:** you can shorten this to only the version, e.g. `nl` if you searched field `contents__nl`) |
| `wordstart`   | First word position we want returned. -1 for document start.<br/>**NOTE:** when greater than -1, any content before the first word will _not_ be included in the response!                                                                                                                                     |
| `wordend`     | First word position we don't want returned. -1 for document end.<br/>**NOTE:** when greater than -1, any content after the last word will _not_ be included in the response!                                                                                                                                   |
| `adjusthits`  | (relations queries only) should query hits be adjusted so all matched relations are inside the hit? Default: `no`                                                                                                                                                                                              |


## Success Response

**Code** : `200 OK`

### Content examples

_(the original input document, be it XML or some other format)_

