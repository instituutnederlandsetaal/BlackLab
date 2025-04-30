# Enable distributed search using Solr

This is where we will keep track of our current goal of enabling BlackLab to integrate with Solr in order to utilize Solr's distributed indexing and search capabilities with BlackLab.

Integrating with Solr will involve the following steps.

## Incorporate all information into the Lucene index

### Metadata

- [ ] metadata may change during indexing after all? no more undeclared metadata field warning? OTOH, changing metadata as documents are added to the index would be tricky in distributed env... You should probably check for metadata document updates semi-regularly, and keep more critical information in field attributes.

### Forward index

- [x] Check how `IndexInput.clone()` is used. This method is NOT threadsafe, so we must do this in a synchronized method!
- [ ] (maybe) capture tokens codec in a class as well, like `ContentStoreBlockCodec`. Consider pooling encoder/decoder as well if useful.

LATER?
- [ ] (PROBABLY VERY DIFFICULT AND MAY NOT BE WORTH THE EFFORT) can we implement a custom merge here like CS? i.e. copy bytes from old segment files to new segment file instead of re-reversing the reverse index.

### Content store

LATER? 
- [ ] ContentStoreSegmentReader getValueSubstrings more efficient impl? This is possible, but maybe not the highest priority.
- [ ] implement custom merge? The problem is that we need to split the `MergeState` we get into two separate ones, one with content store fields (which we must merge) and one with regular stored fields (which must be merged by the delegate), but we cannot instantiate `MergeState`. Probably doable through a hack (placing class in Lucene's package or using reflection), but let's hold off until we're sure this is necessary.


## Improve parallellisation for group/sort

Grouping and sorting is currently not done per segment, but done once we have gathered hits from each segment. When we group/sort/filter on context, we thus have to return to the segment each hit came from, which is expensive.

It should be faster to perform the operations per segment, then merge the results: this is inheritently parallelizable (because segments are independent of one another), minimizes resource contention, and makes disk reads less disjointed.

The merge step would use string comparisons/hashes instead of term sort order comparisons. Such a merge would work in a similar way as with distributed search.

The global forward index API with global term ids and sort order would not be needed anymore after this change. This is good, because it's expensive to keep track of global term ids, especially when dynamically adding/removing documents.

How would we approach this:

- Implement `SpansSorted` (gather and sorts its hits and keeps track of the sort value per hit (CollationKey?), for later merging), `GroupedSpans` (gathers and groups hits into several `Spans`). The first implementations are proofs-of-concept and should just use existing classes like `Hits`, `Kwics` etc. internally. Later versions will be specifically optimized for the new, per-segment situation.
- Implement the merging steps for `SpansSorted` and `GroupedSpans` based on (string/CollationKey) comparison of the stored sort and group values. Use these in `HitsFromQuery`. (we probably need a separate `GroupedHitsFromQuery` as well to produce the merged grouped hits).
- `ForwardIndexAccessor` is used in forward index matching (NFAs). The way these are used is already per-segment, so these "just" need to be updated to use the per-segment forward index directly.
- Also convert "special cases" such as `HitGroupsTokenFrequencies` and `CalcTokenFrequencies`, and calculating the total number of tokens in `IndexMetadataIntegrated`.
- Eliminate the global `ForwardIndex` and `Terms` objects, which should not be used anymore now.


This is how the global forward index is currently used, and what it would take to change these uses, from hardest to easiest:

- Kwics / Contexts (constructor, makeKwicsSingleDocForwardIndex, getContextWordsSingleDocument)<br>
  Sorting, grouping, filtering and making KWICs should be done per segment, followed by a merge step that does not use sort positions but string comparisons.
- HitGroupsTokenFrequencies / CalcTokenFrequencies. Should be converted to work per segment. A bit of work but very doable.
- ForwardIndexAccessor: forward index matching (NFAs). Should be relatively easy because forward index matching happens from Spans classes that are already per-segment.
- IndexMetadataIntegrated: counting the total number of tokens. Doesn't use tokens or terms file, and is easy to do per segment.


## Refactoring opportunities

- [ ] Tasks:
    - [ ] search for uses of `instanceof`; usually a smell of bad design
          (but allowable for legacy exceptions that will go away eventually)
    - [ ] addToForwardIndex shouldn't be a separate method in DocIndexers and Indexer; there should be an addDocument method that adds the document to all parts of the BlackLab index.
    - [ ] Don't rely on BlackLab.defaultConfigDirs() in multiple places.
      Specifically DocIndexerFactoryConfig: this should use an option from blacklab(-server).yaml,
      with a sane default. Remove stuff like /vol1/... and /tmp/ from default config dirs.
- [ ] Principles:
  - [ ] refactor for looser coupling / improved testability.
  - [ ] Use more clean interfaces instead of abstract classes for external API.

## Optimization opportunities

The first implementation of the integrated index is slow, because we just want to make it work for now. There are a number of opportunities for optimizing it.

Because this is a completely new index format, we are free to change its layout on disk to be more efficient.

- [ ] ForwardIndexDocumentImpl does a lot of work (e.g. filling chunks list with a lot of nulls), but it regularly used to only read 1-2 tokens from a document; is it worth it at all? Could we use a more efficient implementation?
- [ ] Use more efficient data structures in the various `*Integrated` classes, e.g. those from fastutil
- [ ] Investigate if there is a more efficient way to read from Lucene's `IndexInput` than calling `readInt()` etc. repeatedly. How does Lucene read larger blocks of data from its files? (you can read/write blocks of bytes, but then you're responsible for endianness-issues)
- [ ] Interesting (if old) [article](https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html) about Lucene and memory-mapping. Recommends 1/4 of physical memory should be Java heap, rest for OS cache. Use `iotop` to check how much I/O swapping is occurring.
- [ ] [Compress the forward index?](https://github.com/instituutnederlandsetaal/BlackLab/issues/289), probably using VInt, etc. which Lucene incorporates and Mtas already uses.<br>(OPTIONAL BUT RECOMMENDED)


## BlackLab Proxy

The proxy supports the full BlackLab Server API, but forwards requests to be executed by another server:

- Solr (standalone or SolrCloud)
- it could even translate version 2 of the API to version 1 and forward requests to an older BLS. This could help us support old user corpora in AutoSearch.

LATER?
- [ ] (optional) implement logic to decide per-corpus what backend we need to send the request to. I.e. if it's an old index, send it to the old BLS, otherwise send it to Solr. Also implement a merged "list corpora" view.


## Enable Solr distributed

- [ ] Experiment with non-BlackLab distributed Solr, to learn more about e.g. ZooKeeper
- [ ] Enable distributed indexing
- [ ] Make one of the search operations (e.g. group hits) work in distributed mode
- [ ] Make other search operations work in distributed mode
- [ ] Create a Docker setup for distributed Solr+BlackLab
- [ ] Make it possible to run the tests on the distributed Solr version
