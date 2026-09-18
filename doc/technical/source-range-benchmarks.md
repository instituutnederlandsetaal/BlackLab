# Source-range rollout benchmark — 2026-09-17

These historical measurements predate the smaller container records and response
simplifications in the [2026-09-18 review](out-of-order-review.md).

This is a desktop regression screen for the initial source-range implementation,
not a production capacity estimate. The native baseline uses the same current
code with the source-range codec flag cleared before indexing. It isolates the
storage/order paths; it does **not** compare all prerequisite changes against
the original branch. Runs used an evolving worktree during the creation-rollout
slice, not one immutable revision; exact cross-revision reproduction is not
claimed. No numerical release budget was agreed before these runs.

## Method

Apple M1 Pro, 10 CPU cores, 16 GB RAM, macOS 15.7.7, Homebrew Java 21.0.9;
fresh JVM per run, assertions enabled, `-Xms512m -Xmx3g`. The desktop was active
(load roughly 2–3), with no concurrent build during measurements. Browser and
system processes remained active. The vector incubator module was not enabled.

A temporary Java generator/harness created XML outside the timed section and
used the real `Indexer`, index close/reopen, Lucene merge, searches, forward-index
snippets, and source views. Each synthetic document has groups of 100 words,
2,000 vocabulary entries, word/lemma/POS annotations, all four sensitivities,
and two lemma alternatives. Large tokens add 256 text characters and 128
attribute characters. The matrix uses three modes:

- Native offsets, with existing source-order behavior.
- Source ranges with implicit word units.
- Source ranges with explicit 100-token containers.

Each of the four shapes below contains 300,000 tokens. Three fresh-index runs
per shape/mode give 36 runs and 10.8 million indexed tokens. Full create/index/
close time is measured; input generation and a subsequent force-merge are
separate. Token counts, indexing errors, and search counts are checked. Search
counts agree across modes within each shape. Most matrix runs already have one
segment; their near-zero merge times are not useful merge benchmarks.

The temporary `SourceBench.java`, generated indexes, and raw logs were retained
locally under `/private/tmp/blacklab-source-bench.fIJJIi/`, not added to production
or test tooling. The retained harness is the final variant: dense-overlap and
append checks were added after the matrix, so matrix RSS excludes dense checks
and rerunning this variant may use more memory. Its arguments are output directory, mode (`native`, `word`,
`container`), shape, document count, words per document, and large-token boolean.
For example, `word long-short 3 100000 false` exercises 300,000 tokens. Run each
invocation under `/usr/bin/time -l` for process maximum RSS.

## Indexing and storage

Mean build seconds; median complete index size after force-merge, in decimal MB:

| Shape | Documents × tokens | Native seconds / MB | Word seconds / MB | Container seconds / MB |
| --- | --- | --- | --- | --- |
| Long, short tokens | 3 × 100,000 | 5.400 / 16.126 | 5.220 / 17.096 | 5.228 / 17.146 |
| Many, short tokens | 3,000 × 100 | 6.606 / 12.433 | 6.635 / 13.091 | 6.651 / 13.110 |
| Long, large tokens | 3 × 100,000 | 9.005 / 19.498 | 8.836 / 20.317 | 8.744 / 20.367 |
| Many, large tokens | 3,000 × 100 | 9.781 / 18.318 | 9.896 / 19.390 | 9.768 / 19.412 |

There is no clear indexing slowdown signal: mean differences span about −3.3%
to +1.2%. This is not evidence of a speedup. Word-mode indexes are 4.2–6.0% larger;
containers add another 19–50 KB per 300,000 tokens. Those are whole-index deltas,
not isolated range-vector byte counts. Word mode stores no container-unit blob.

Median process maximum RSS (decimal MB):

| Shape | Native | Word | Container |
| --- | --- | --- | --- |
| Long, short tokens | 748 | 755 | 757 |
| Many, short tokens | 740 | 734 | 736 |
| Long, large tokens | 1,169 | 1,165 | 1,093 |
| Many, large tokens | 974 | 908 | 941 |

Median GC totals range from 74 to 112 ms. The harness also records summed heap
pool peaks, but those peaks can occur at different times and are **not** a
simultaneous live-heap measurement. RSS includes retrieval after indexing.

## Lookup and source-view observations

Each run performs six read iterations. Median final-iteration latency in ms,
for a ten-token selection near the middle of the first document:

| Shape | Native offset / source view | Word offset / source view | Container offset / source view |
| --- | --- | --- | --- |
| Long, short tokens | 17.971 / 21.692 | 11.754 / 24.488 | 13.168 / 15.236 |
| Many, short tokens | 0.624 / 2.004 | 0.894 / 3.452 | 0.957 / 2.327 |
| Long, large tokens | 18.120 / 17.718 | 12.895 / 26.582 | 13.227 / 17.440 |
| Many, large tokens | 0.569 / 3.132 | 1.048 / 3.243 | 1.040 / 4.146 |

These source-view columns are **different work**, not direct performance
equivalents: native highlights one interval, word mode emits per-token markers,
and container mode expands to whole units. For example, long/large outputs are
4,626, 4,726, and 90,984 UTF-16 characters respectively. Small selections still
incur document-sized Lucene term-vector loading. The prefix reader stops after
the highest requested token, but occurrences scanned were not instrumented.

Final-iteration FI snippet medians were 0.4–1.0 ms; term-query medians were
0.5–3.3 ms. First iterations are first-call/OS-hot measurements after writing the
index, **not cold-I/O measurements**. No filesystem-cache purge was performed.

## Additional exercised shapes

- Full supplied Alpino corpus: 7,136 documents, 140,780 tokens, 21.6 MB input;
  checked run 8.878 s, about 740 MB maximum RSS. Sentence 2231's cross-branch
  source view returns nine tokens and contains the expected Santerra tree.
- Full-document reverse order: 300,000 tokens, 5.023 s; assertions verify
  decreasing source locations for increasing token positions.
- `punctPath` with 100,000 selected words per evaluation: 300,000 tokens,
  5.706 s. This is a performance case; durable unit tests use distinctive
  punctuation values to check association correctness.
- Alternating far-apart source positions: 300,000 tokens, 6.248 s. A ten-token
  request produces 3,548,552 characters; final source-view iteration 125 ms.
- Oversized document containers: three 100,000-token documents (300,000 total),
  5.244 s. A ten-token request returns one complete 100,000-token,
  7,085,091-character unit; final iteration 241 ms.
- Dense overlap: 1,000 hits over 100 tokens produces 100,000 `<hl>` markers,
  about 2.10 million characters in 215–267 ms in the checked ordinary/reverse/
  punctuation runs. The distant-window case produces 5.64 million characters
  in 252 ms. These timings include construction and regex marker-count
  verification, not just rendering. No new response cap is imposed.
- Reopen/append plus a real merge: two sessions, six documents, 600,000 tokens.
  Native/source build 8.976/8.441 s, merge 2.230/2.162 s, final indexes
  31.756/33.702 MB. Persisted codec, document counts, and token counts are checked.

Mode order was mostly fixed and only three matrix repetitions were made, so
thermal/cache/order effects remain possible. No isolated Saxon tree, sort,
duplicate-bitset, token-ID-map, or range-buffer allocation profile was captured.
Cold I/O and sustained multi-hour throughput remain unmeasured. These results
support the rollout regression check, not formal latency or heap guarantees.
