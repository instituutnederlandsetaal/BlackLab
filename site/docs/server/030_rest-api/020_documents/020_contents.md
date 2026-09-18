# Document contents

Retrieve the original input document, presuming it's stored in the corpus (it is by default).

**URL** : `/blacklab-server/<corpus-name>/docs/<pid>/contents`

**Method** : `GET`

#### Parameters

All parameters are optional.  `wordstart` and `wordend` refer to token position in a document, 0-based.

Partial contents XML output is wrapped in `<blacklabResponse/>`. For XML documents indexed with source-range storage, token bounds select complete configured containers; see below.

| Parameter     | Description                                                                                                                                                                                                                                                                                                    |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `patt`        | Pattern to highlight in the document. `<hl>...</hl>` tags will be added to highlight hits.                                                                                                                                                                                                                     |
| `field`       | Annotated field to get snippet for.  (default: main annotated field)<br>(**NOTE:** in case of a parallel corpus, you can shorten this to only the version, e.g. `nl` if you want to get the contents for field `contents__nl`)                                                                                 |
| `searchfield` | (parallel corpora only) Annotated field that was searched. Used in conjunction with the `rfield()` function (see [relations querying](/guide/query-language/relations)) to highlight the correct hits.<br>(**NOTE:** you can shorten this to only the version, e.g. `nl` if you searched field `contents__nl`) |
| `wordstart`   | First word position we want returned. -1 for document start.<br/>**NOTE:** new XML indexes expand token selections to complete containers.                                                                                                                                     |
| `wordend`     | First word position we don't want returned. -1 for document end.<br/>**NOTE:** new XML indexes expand token selections to complete containers.                                                                                                                                   |
| `adjusthits`  | (relations queries only) should query hits be adjusted so all matched relations are inside the hit? Default: `no`                                                                                                                                                                                              |


## Success Response

**HTTP response code**: `200 OK`

### Content examples

_(the original input document, be it XML or some other format)_

### XML in indexing order and source order

For XML documents indexed with source-range storage, token positions follow indexing order, while the returned XML retains source order. A bounded request returns the complete configured `containerPath` elements touched by its token selection. Without `containerPath`, it returns the complete stored document. This gives XSLT a complete tree to transform without cutting through word elements.

The response is ordinary XML, with the existing `<blacklabResponse>` wrapper for bounded requests. XML is the default for this endpoint. Request `outputformat=json` for an object containing a single `contents` string with the same XML. There is no separate metadata envelope or hit table; use the normal hits endpoint for hit positions and captures.

Each matching word element is wrapped once in `<hl start="N" end="N+1">`, with that word's token positions in indexing order. Multiple hits on a word share its wrapper. A multi-token hit produces one wrapper for each participating word; a zero-width hit adds no wrapper. XSLT can copy these position attributes to HTML to correlate highlights with hits even when XML source order differs from indexing order. Stylesheets should allow `hl` immediately around word elements, including nested word elements.

Complete containers preserve their own attributes and descendants. As with legacy partial contents, the response wrapper copies namespace declarations found near the stored document's root. It does not reconstruct omitted ancestors or their attributes; choose a container that includes the context your stylesheet needs. Entity declarations outside the stored fragment are not reconstructed.

Explicit token selections must be within the real token count; `-1` denotes the corresponding document boundary. An empty XML selection selects the following token's container, or the last token's container at the document end. Without configured containers, it returns the full document. A zero-token document can still be retrieved. Unavailable stored content returns `CONTENT_NOT_AVAILABLE`, illegal ranges return `ILLEGAL_BOUNDARIES`, and highlighting with another field's coordinates returns `UNSUPPORTED_CONTENT_REPRESENTATION`. For parallel corpora, use `rfield()` with `field`/`searchfield` as described above.

Existing authorization and query limits still apply. A small token selection can return a large container or document. Use [forward-index snippets](./snippet.md) for bounded reading-order text and punctuation. Old indexes and non-XML documents retain their existing retrieval behavior.
