# Enable distributed search using Solr

This is where we will keep track of our current goal of enabling BlackLab to integrate with Solr in order to utilize Solr's distributed indexing and search capabilities with BlackLab.

Integrating with Solr will involve the following steps.

## Incorporate all information into the Lucene index

### Metadata

- [ ] metadata may change during indexing after all? no more undeclared metadata field warning? OTOH, changing metadata as documents are added to the index would be tricky in distributed env... You should probably check for metadata document updates semi-regularly, and keep more critical information in field attributes.

### Forward index

- [ ] (maybe) capture tokens codec in a class as well, like `ContentStoreBlockCodec`. Could be useful to run-length encode sparse fields that only have a value occasionally, or usually have the same default value. Consider pooling encoder/decoder as well if useful.

LATER?
- [ ] (PROBABLY VERY DIFFICULT AND MAY NOT BE WORTH THE EFFORT) can we implement a custom merge here like CS? i.e. copy bytes from old segment files to new segment file instead of re-reversing the reverse index.

### Content store

LATER? 
- [ ] ContentStoreSegmentReader getValueSubstrings more efficient impl? This is possible, but maybe not the highest priority.
- [ ] implement custom merge? The problem is that we need to split the `MergeState` we get into two separate ones, one with content store fields (which we must merge) and one with regular stored fields (which must be merged by the delegate), but we cannot instantiate `MergeState`. Probably doable through a hack (ctor is package-private, so place class in Lucene's package or use reflection), but let's hold off until we're sure this is necessary.


## Improve parallellisation and get rid of global term ids

Grouping and sorting is currently not done per segment, but only once we've gathered hits from each segment. When we group/sort/filter on context, we thus have to return to the segment each hit came from, and translate between global and local term ids, which is expensive. Determining the mapping also takes up time when opening an index.

It should be faster to perform the operations per segment, then merge the results: this is inheritently parallelizable (because segments are independent of one another), eliminates term id conversions, minimizes resource contention, and makes disk reads less disjointed.

The merge step would use string comparisons/hashes instead of term sort order comparisons. Such a merge would work in a similar way as with distributed search.

The global forward index API with global term ids and sort order would not be needed anymore after this change. This is good, because it's expensive to keep track of global term ids, especially when dynamically adding/removing documents. It would be especially impractical for distributed search, because then the term ids would need to be shared between nodes, which would be difficult and slow.

How would we approach this:

- [x] Remove the old external index format code. It relies on global forward index and global term ids and cannot (and should not) be supported going forward.

- [ ] Implement:
  - [ ] Gather hits from a single index segment into a `HitsInternal` object.
  - [ ] Ensure several of these can be merged into another `HitsInternal` object _dynamically_, while the hits are being gathered (in the case where no sort has been requested).
  - [ ] Add a subclass that gathers hits from a single segment, sorts them, and allows them to be merged based on the sort value later.
  - [ ] Follow a similar approach for grouping.

- [ ] Implement merging:
  - [ ] `HitsFromQuerySorted` merges hits from several `SpansSorted` instances
  - [ ] `HitsGroupedFromQuery` merges hits from several `GroupedSpans` instances
  
  Merging should be based on (`String`/`CollationKey` and maybe `Integer` and `Double` as well?) comparison of the stored sort and group values.

- [ ] Optimize `SpansSorted`, `GroupedSpans` for the new, per-segment situation (i.e. don't use `Hits`, `Kwics` anymore).

- [ ] Eliminate global term ids from classes like `Kwic(s)`, `Contexts`, etc. These are generally only done for a small window of (already-merged) hits, so we still need to be able to get the context for a hit after merging, but these should be in string form.

- [x] Update NFA matching to use per-segment term ids. NFAs will have to be customized per-segment. `ForwardIndexAccessor` needs to be updated to use the per-segment forward index as well (should be easy as this class is already used per-segment, that is, from `Spans` classes).

- Convert "special cases" such as: 
  - `HitGroupsTokenFrequencies` and `CalcTokenFrequencies`
  - calculating the total number of tokens in `IndexMetadataIntegrated`
  - more?

- Eliminate uses of the global `(Annotation)ForwardIndex` and `Terms` objects, such as in `HitProperty` and `PropertyValue`, and replace them with per-segment alternatives.

- Deal with any unexpected problems that arise.

- Clean up, removing any now-unused classes.


## Optimization opportunities

The first implementation of the integrated index is slow, because we just want to make it work for now. There are a number of opportunities for optimizing it.

Because this is a completely new index format, we are free to change its layout on disk to be more efficient.

- [ ] ForwardIndexDocumentImpl does a lot of work (e.g. filling chunks list with a lot of nulls), but it regularly used to only read 1-2 tokens from a document; is it worth it at all? Could we use a more efficient implementation?
- [ ] Use more efficient data structures in the various `*Integrated` classes, e.g. those from fastutil
- [ ] Interesting (if old) [article](https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html) about Lucene and memory-mapping. Recommends 1/4 of physical memory should be Java heap, rest for OS cache. Use `iotop` to check how much I/O swapping is occurring.


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
