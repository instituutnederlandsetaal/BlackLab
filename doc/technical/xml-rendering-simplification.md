# XML rendering simplification — 2026-09-18

This second review starts at `3774321c9` (`Simplifications`). The first review's
indexing, punctuation, collocation, and publishing fixes are preserved. The
starting point is backed up as
`backup/out-of-order-before-rendering-simplification-20260918`.

## The architectural change

The frontend needs reading-order snippets and XML that its stylesheet can
transform. Those needs already have endpoints: forward-index snippets and
document contents. They do not require another result model containing a second
hit table, captures, requested/returned bounds, and context-resolution metadata.

The new path therefore returns ordinary XML from `/contents`, or the same XML
in the existing JSON `contents` string. Bounded XML requests select complete
configured containers. Each selected container is fetched separately, in source
order; nested selections are returned once. Intervening source and invented
ancestors are unnecessary. The stored document is the default container.

Matching word elements get one highlight wrapper per token. Its `start` and
`end` identify the token in indexing order. Overlapping hits share this wrapper;
the normal hits response retains the individual hit ranges and captures. The
existing highlighter's `index` attribute is only a marker ordinal within each
returned container, not a hit identifier. Correlation uses token coordinates.

Three subagents reviewed the container representation, API/frontend contract,
and indexing/retrieval regressions. The implementation reuses the existing
highlighter's literal-span insertion and the existing partial-response wrapper.

## Measurable tradeoffs

| Change | Effect compared with `3774321c9` |
| --- | --- |
| Remove the source-view records, hit-membership expansion, dedicated serialization, and structural snippet mode; simplify container selection/configuration | 258 fewer production Java lines: 169 added, 427 removed, measured with `git diff --numstat -- '*/src/main/*'` |
| Highlight the union of matching tokens | The regression with 1,000 identical 100-token hits emits 100 wrappers and 5,963 characters. The removed algorithm would emit 100,000 wrappers and 2,090,991 characters, before its separate hit table. These are deterministic output sizes, not latency measurements. |
| Treat omitted `containerPath` and `"."` identically | No source-unit record for either: saves 12 uncompressed bytes plus stored-field overhead per document/field previously using an explicit root container; removes explicit/default tracking and custom serialization |
| Fetch selected containers separately | No source-envelope gap or tag-balance scan. Returned size depends on the selected containers, rather than the distance between them in source XML. |

Highlighting streams source offsets and allocates/sorts spans only for matching
tokens inside the returned containers. A compact bit set tracks matching token
positions; no document-wide offset arrays are created for bounded requests.
The sequential vector scan still reaches the final matching token. Avoiding
that would require additional coverage metadata, which this change does not
introduce. Filtering by source also preserves highlights from nested containers
whose tokens occur elsewhere in indexing order.

Default-container bounded requests now return the whole stored document rather
than isolated word elements. That can increase response size, deliberately, to
provide usable XML context without another parser or ancestor-reconstruction
subsystem. Configure a smaller `containerPath` when bounded XML response size
matters; use forward-index snippets for short reading-order text.

The three-integer representation remains for actual smaller containers. Token
offsets alone cannot identify their enclosing elements. Deleting those records
would require parsing source on retrieval or returning larger documents. There
is no further codec change: this still uses `source-range-vectors-v2`.

## Frontend evidence and compatibility

The sibling blacklab-frontend implementation requests `/contents` and passes
the response directly to XSLT (`BlackLabApi.java` and `ArticleUtil.java`). Its
default article stylesheet renders all text nodes. The previous source-view
envelope could therefore leak response metadata into the rendered article.
Restoring ordinary XML also restores this existing integration contract. An
API regression transforms returned XML with a real custom stylesheet and
checks the resulting text and token-coordinate attributes.

This removes an experimental branch API: structural `usecontent=orig` snippets
now report `UNSUPPORTED_CONCORDANCE_REPRESENTATION` and direct callers to
document contents. Forward-index snippets, including named-span context and
punctuation, retain their existing behavior. Native-offset and non-XML original
concordances remain supported. Empty XML selections return the following
token's container, or the last token's container at EOF.

Existing frontend hit navigation uses DOM marker order. Reordered tokens and
multi-token hits require a corpus stylesheet/client to preserve and use token
coordinates; this patch does not change that frontend code. Automatically
generated stylesheets also cannot translate arbitrary XPath sequence expressions
such as `sort()` into XSLT match patterns. Use a corpus-specific stylesheet for
such tree formats.

Complete containers preserve their attributes and descendants. The existing
partial-response wrapper copies root namespace declarations; omitted ancestors,
their attributes, and external entity declarations are not reconstructed.

## Validation

Regression coverage includes Alpino reading order, parallel fields, Unicode
offsets, nested tokens/containers, separate selected containers, zero-width
hits/selections, overlap deduplication, raw XML/JSON responses, custom XSLT,
content authorization, and legacy/plaintext retrieval.

- `mvn -q -B package --file pom.xml`: 595 tests across 109 suites;
  594 passed, one skipped, no failures or errors.
- The Docker HTTP suite against the rebuilt server: 62 passed, including
  indexing, contents, snippets, collocations, and parallel-field responses.
  Its isolated project used freshly generated test indexes.
- `git diff --check` and `node --check test/test/docs.js`: passed.

The saved full-document response changes only by regaining the ordinary XML
declaration. The isolated Docker test project and its generated data were
removed after validation. The integration section below records the final
committed validation.

## Integration with upstream metadata fragments

The branch was rebased onto `origin/dev` at `9e504f3c9`. Its previous history is
preserved as `codex/backup-before-dev-rebase-20260918`. Three subagents reviewed
XML indexing, storage/retrieval, and collocation/API integration.

Metadata fragments and source containers remain separate. Fragments are token
spans split at metadata boundaries and stored as child Lucene documents. They do
not retain XML element boundaries. Reusing them for rendering would still need
source ranges and would replace each compact three-integer container record
with a child document. The existing 12-byte container representation is simpler
and smaller for this purpose; no new source format or response model is needed.

Inline and standoff metadata use the same token coordinates as reordered words.
An element may be both a searchable inline span and a metadata fragment. Empty
fragments do not acquire following tokens, and fragment state is cleared between
documents. Explicit metadata inheritance rules also apply when a fragment has
no local value, preserving document IDs configured as `fragments: separate`.
Copied configurations retain these rules and nested metadata blocks.

Fragment-filtered collocation frequencies reuse the existing hit query path,
including overlapping-fragment and full-document precedence rules. This avoids
a second interval-counting implementation. Ordinary document filters retain the
existing frequency fast paths. Retrieval handles fragment children preceding
their parent and parent documents in later segments. Fragment iteration uses
its existing local state instead of a redundant mutable flag on the query,
and handles segments without fragment fields.

After resolving textual conflicts, the additional compatibility fixes add 32
net production Java lines. The fragment iterator and single-document selection
fixes have no net LOC increase; fragment frequency counting reuses hit queries
rather than adding another traversal or interval representation.

The API comparisons use upstream's corpus-specific fixture paths and helpers.
XML and JSON document contents still share the original highlighted snapshot.
Standalone upstream fixes lead the history: fragment query/document selection,
configuration copying, and fragment indexing/inheritance. Their regressions run
on `dev` without source-range support. Feature-specific adaptations stay in
their corresponding commits; no remote branch is pushed.

Validation after integration: `mvn -q -B clean package` runs 611 tests across
112 suites (one skipped, no failures/errors); the HTTP suite passes all 75 tests
against freshly indexed data. One newly introduced upstream snippet fixture is
updated to retain the literal final exclamation mark in `fragments2.xml`, as
required by the existing punctuation fix. The full-document highlight snapshot
is unchanged.

All 15 code commits in the final history also pass `mvn -q -B clean test`
independently in isolated worktrees. The documentation commit passes the
whitespace check. Temporary validation worktrees and HTTP test containers are
removed after validation.
