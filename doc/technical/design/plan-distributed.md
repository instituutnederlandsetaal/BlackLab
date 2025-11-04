# Enable distributed search using Solr

This is where we will keep track of our current goal of enabling BlackLab to integrate with Solr in order to utilize Solr's distributed indexing and search capabilities with BlackLab.

Integrating with Solr will involve the following steps.

## Incorporate all information into the Lucene index

### Metadata

- [ ] metadata may change during indexing after all? no more undeclared metadata field warning? OTOH, changing metadata as documents are added to the index would be tricky in distributed env... You should probably check for metadata document updates semi-regularly, and keep more critical information in field attributes.

### Forward index

- [x] (maybe) capture tokens codec in a class as well, like `ContentStoreBlockCodec`. Could be useful to run-length encode sparse fields that only have a value occasionally, or usually have the same default value.

LATER?
- [ ] (PROBABLY VERY DIFFICULT AND MAY NOT BE WORTH THE EFFORT) can we implement a custom merge here like CS? i.e. copy bytes from old segment files to new segment file instead of re-reversing the reverse index.

### Content store

LATER? 
- [ ] ContentStoreSegmentReader getValueSubstrings more efficient impl? This is possible, but maybe not the highest priority.
- [ ] implement custom merge? The problem is that we need to split the `MergeState` we get into two separate ones, one with content store fields (which we must merge) and one with regular stored fields (which must be merged by the delegate), but we cannot instantiate `MergeState`. Probably doable through a hack (ctor is package-private, so place class in Lucene's package or use reflection), but let's hold off until we're sure this is necessary.


## Improve parallellisation

### Improvements to be made

- do we unique twice in HitPublisherSpans? (query usually includes a unique step at the end as well)

STARTUP TIME
- BLTerms.close() is never called...
- MetadataFieldValuesFromIndex takes quite a while.
  Can we speed it up?
  Can we run it in a separate thread, and only block when we actually need the values?

OTHER ISSUES
- Look at QueryInfo / SearchSettings
- try to get rid of more useless abstract base classes like `Result` (?)


## Optimization opportunities

There are a number of opportunities for optimizing the integrated index format.

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
