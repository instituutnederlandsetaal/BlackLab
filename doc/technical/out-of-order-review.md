# Out-of-order indexing review — 2026-09-18

This records the first review, committed as `3774321c9`. The subsequent
[XML rendering simplification](xml-rendering-simplification.md) supersedes this
report's source-view API description and records the second review's changes.

Reviewed the 70 branch commits through `3a8d4c0fd` against the common ancestor
with fetched `origin/dev`, `773abdcb9`. Upstream `dev` is now `1413ee4f8` and has
five divergent commits, including fragment metadata work. Those commits were
inspected for overlap but were not merged. The original branch is retained as
`backup/out-of-order-tokens-before-review-20260918`.

Three subagents reviewed indexing/punctuation, source retrieval, and
publishing/collocations. The proposal in `workdocs` supplied capabilities only;
its architecture and restrictions were not treated as requirements.

## Changes and measurable effects

The changes remove 62 production Java lines in total (`git diff --numstat`
against the backup, restricted to `src/main`). Tests and documentation are
additional.

| Change | Measurable effect | Preserved capability |
| --- | --- | --- |
| Container records contain source start/end and exclusive token end; derive token start from the preceding record | 20 to 12 bytes per container before compression: 40% smaller; two fewer indexing-buffer integers per container | Return complete XML containers selected by token bounds |
| Remove public container IDs, unit lists, and unit mode | No per-container response objects/list copies or serialization | Same XML, expansion bounds, hit markers and captures |
| Full-document retrieval skips loading and resolving containers | No container byte-array allocation or per-container decoding for this path | Retrieve and highlight the complete source |
| Remove visible-interval lists derivable from hit and returned bounds | Two fewer objects per nonempty visible hit; two fewer interval objects per source snippet | Identify clipped/overlapping/zero-width hits and their captures |
| Remove the duplicate-container set and punctuation-state wrapper | One fewer O(container-count) hash set and one fewer wrapper per explicit-punctuation field | Duplicate tokens still fail; repeated empty containers can be harmless |
| Allow explicit punctuation on native-offset indexes | Remove a loop and conditional, six production lines; no required rebuild for punctuation alone | Exact punctuation with legacy document-order words and native offsets |
| Short-circuit reads of already available hits | Regression workload: one publisher activation instead of 401 for 100 repeated doc/start/end/iterator reads | Demand-driven fetching and failure propagation |
| Replace per-segment frequency map with `LongAdder` | Five fewer production lines; no boxed map-counter replacements or final map summation | Correct collocation frequencies across segments |

Two correctness fixes are included: invalid sublist error reporting no longer
attempts to fetch all hits under the aggregate read lock, and XPath iterators
support `next()` without a preceding `hasNext()` call, including across contexts.

## Alternatives rejected after investigation

**Binary stored offsets instead of term vectors.** A prototype removed the
synthetic-term writer and prefix scan. A small Lucene 9.11.1 experiment used one
100,000-token reverse-order document, an in-memory directory, three rounds with
alternating representation order, 40 warmups, 500 metadata reads and 100
late-token lookups per round. Warmed measurements were:

| Representation | Metadata-only read | Last-ten-token lookup | Index bytes |
| --- | --- | --- | --- |
| Existing vectors | 0.004 ms | 1.28–1.36 ms | 811,571 |
| Binary stored pairs | 0.073–0.082 ms | 0.114–0.131 ms | 804,722 |

The binary representation improved offset lookup but made metadata reads roughly
20 times slower: Lucene must decompress intervening stored-field blocks even
when the field is excluded. Moving fields and adding special visitors would
introduce more machinery. The prototype was discarded. These are isolated,
warm microbenchmarks, not end-to-end throughput or production latency claims.
The temporary harness is `/tmp/SourceStorageBench.java`.

**Replacing inline event handling with derived min/max spans.** Scanning words
for every inline gives O(words × inlines); traversing ancestors gives
O(words × XML depth), adds bookkeeping, and needs special handling for empty
anchors and annotation insertion order. The current cursor/stack is
O(words + inlines) after sorting. No measured tradeoff justified replacing it.
Its conservative ordering restriction remains.

**Dropping stored container boundaries altogether.** Exact token offsets cannot
recover enclosing XML container boundaries. Returning whole documents would
increase snippet output, while reparsing stored XML would add retrieval work.
The remaining three-integer records directly support the XSLT capability.
Upstream's fragment metadata feature is not a drop-in source-boundary index.

## Compatibility

The experimental codec marker advances to `source-range-vectors-v2` because
container records changed layout. Indexes built by the earlier branch's v1
implementation must be rebuilt; they fail explicitly rather than being
misinterpreted. Existing native-offset indexes remain readable and appendable.
Their new punctuation support does not enable out-of-order token indexing.

The experimental source-view API no longer exposes `units`, `unitMode`, or
per-hit `visible` lists. Clients use returned bounds, complete hit bounds,
captures, and the same `<hl index="N">` XML markers. Source-order XML and
indexing-order forward-index snippets are preserved.

The existing fragment namespace/entity and ancestor-context limitations remain.
No new restoration subsystem or pagination feature was introduced.

## Validation

Regression coverage exercises source-container byte layout, reverse token/source
order, clipped and zero-width highlights, XML/JSON responses, legacy punctuation
across reopen/append, malformed ranges, iterator behavior, publisher activation,
and invalid-sublist completion.

- `mvn -q -B package --file pom.xml`: 600 tests, 599 passed, one skipped,
  no failures or errors, across all modules.
- Fresh Docker server/indexes and the repository HTTP suite: 61 passed,
  including XML/JSON source views, bounded container expansion, snippets,
  collocations, and parallel-field responses.
- `git diff --check` and `node --check test/test/docs.js`: passed.

The Docker test project and its generated data were removed after validation.
These code changes were committed as `3774321c9`; the backup branch remains intact.
