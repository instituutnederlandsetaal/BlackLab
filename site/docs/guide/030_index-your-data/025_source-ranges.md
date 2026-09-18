# Token order and original XML

New Lucene indexes store token locations separately from the searchable annotations, using the `source-range-vectors-v2` codec. XML words are indexed in the order returned by XPath, while original-content retrieval retains XML source order. Existing indexes keep their native-offset storage and document-order sorting when appended to; rebuild into a separate new index to use the new behavior.

## Choosing words and XML containers

```yaml
annotatedFields:
  contents:
    containerPath: .//s
    wordPath: >-
      sort(.//w, (), function($w) { xs:integer(exactly-one($w/@n)) })
    punctBeforePath: "if (empty($previousWord)) then '' else ' '"
    punctAfterLastWordPath: "@punct-after"
    annotations:
      - name: word
        valuePath: .
```

`containerPath` and `wordPath` retain XPath result order. Ordinary location paths normally return document order; use `sort()` or another sequence expression when needed. XPath sort keys are not token positions: positions are consecutive, starting at zero.

An explicitly configured container is both an XPath evaluation context and a complete XML element returned by bounded document-content requests. Without `containerPath`, original-content retrieval returns the complete stored document. Choose containers for the XML context your XSLT needs. Use forward-index snippets when you need only a few words of reading-order text.

Words and containers must be material elements from the current input document with reliable literal source ranges. Words must lie within their current container, including when selected using an absolute XPath. Duplicate words and nonempty token IDs are rejected. Nested words are allowed; overlapping containers are allowed if they select distinct words. Elements produced by entity expansion without a literal source interval are rejected. For parallel fields, each field must resolve to one container, which is stored as that version's source.

## Explicit punctuation

`punctBeforePath` is evaluated on each word. `punctAfterLastWordPath` is evaluated once on the last selected word of each container evaluation. Their results populate the existing `punct` annotation, including its final closing position. A trailing gap and the next container's leading gap are concatenated in that order.

During these evaluations, `.` is the current word; `$container` is the current container; and `$previousWord`/`$nextWord` are the neighboring selected words in indexing order, or `()` at the boundaries. Neighbors do not cross container evaluations. Trailing evaluation uses the last word and its predecessor. `position()` and `last()` refer to the singleton context. These three variable names cannot also be configured as variables in explicit punctuation mode.

Node and atomic string values are concatenated in XPath result order without separators. `()` and `''` mean no punctuation, with no replacement space. Maps, arrays, and functions are errors. When only the trailing expression is configured, the missing before expression supplies a single space before every word; a missing trailing expression supplies nothing. An empty configuration string is invalid: use the XPath expression `''` instead.

Explicit punctuation bypasses legacy whitespace normalization, default-space replacement, and node deduplication. XML parser normalization and the existing punctuation-value length limits and truncation still apply. It cannot be combined with `punctPath`. These expressions also work when appending to existing native-offset indexes, which retain document-order indexing and use that order for the neighboring-word variables. No rebuild is needed for punctuation improvements alone.

Existing `punctPath` keeps its normalization policy. For reordered words, punctuation stays attached to the word following it in source order, then travels with that word into indexing order. Trailing punctuation remains after the last emitted word of that evaluation. Neither new property changes the default-space behavior when both are omitted.

## Spans and dependencies

Inline spans use token positions on entry and exit. Reordering within an open span is allowed. Once any span closes, a later token cannot move backwards before that span's source end. Thus `<s>A B</s> C` permits `B A C`, but rejects `A C B`; `A <s>B C</s>` rejects both `B A C` and `B C A`. Empty spans and anchors establish the same barrier. Use standoff spans when structural order is incompatible with this conservative rule.

Standoff references are resolved after token positions have been assigned. Use `tokenIdPath` and token references for contiguous spans and relation endpoints. The supplied `contrib/input-formats/alpino.blf.yaml` indexes Alpino terminals by numeric `@begin`, retrieves complete sentence trees, and maps dependencies to emitted token positions.

## Retrieval and compatibility

[Document contents](/server/rest-api/documents/contents) return original XML with each matching word element wrapped in `<hl start="N" end="N+1">`. These attributes identify the word's token position; there is no separate hit table or response metadata envelope. XSLT can preserve the attributes in HTML to correlate highlights with ordinary search hits. Bounded requests select complete configured containers, or the whole document when no container is configured.

[Forward-index snippets](/server/rest-api/documents/snippet) retain their existing schema, punctuation, and indexing order. Original-content snippets (`usecontent=orig`) return `UNSUPPORTED_CONCORDANCE_REPRESENTATION` for new-format XML; use document contents when you need XML for rendering.
Original source ranges count UTF-16 code units and cover complete XML word elements, including self-closing elements. They are independent of normalized word values, alternatives, and annotation sensitivities. XML and non-XML documents can share a compatible index; persisted document syntax, not XML-looking text, selects XML processing. Documents with content storage disabled still index ranges, but original-content requests return an availability error.

Non-XML formats retain their existing range semantics, including zero-length ranges; the new codec does not improve those parsers' source coordinates. Direct `InputFormat` plugins must opt in with `supportsSourceRangeVectors()` only after implementing both the range-vector and document-status contract. The standard document base supplies this bookkeeping, and converter wrappers delegate the capability. Unmigrated direct plugins can still append to legacy indexes.

The codec version lives in the existing index metadata; document-global flags record XML syntax and source-range storage. Token length remains per field/version. These internal fields are not public annotations or metadata. Unknown codec versions and missing required range data are errors. Solr does not yet support creating this format; supported legacy Solr operations remain available.

Selected XML containers are returned whole. The existing partial-response wrapper carries document-root namespace declarations, but omitted ancestors, their attributes, and external entity declarations are not reconstructed. Existing frontend stylesheets display `hl` elements, but hit navigation based on their DOM order must be adapted to use token positions when tokens are reordered or multi-token hits produce several highlights. Existing query limits apply; selecting a small token interval can return a large container or document.

Use a corpus-specific stylesheet for reordered tree formats. The automatically generated stylesheet supports simple XML paths; arbitrary XPath sequence expressions such as `sort()` cannot be used as XSLT match patterns. It also assumes document ancestors from the input configuration are present. A stylesheet for complete containers should match their local structure and allow `hl` around word elements.
