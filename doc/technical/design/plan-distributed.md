# Enable distributed search using Solr

This is where we will keep track of our current goal of enabling BlackLab to integrate with Solr in order to utilize Solr's distributed indexing and search capabilities with BlackLab.

Integrating with Solr will involve the following steps.

## Solve existing issues

Probably prioritize [issues](https://github.com/instituutnederlandsetaal/BlackLab/issues) that:

- can be done quickly
- bugs that (are likely to) affect us(ers)
- features we actually need or were requested by users
 
Very complex issues and enhancements that may be of limited use should be tackled later.

## Incorporate all information into the Lucene index

### Metadata

- [ ] metadata may change during indexing after all? no more undeclared metadata field warning? OTOH, changing metadata as documents are added to the index would be tricky in distributed env... You should probably check for metadata document updates semi-regularly, and keep more critical information in field attributes.

### Forward index

- [ ] Check how `IndexInput.clone()` is used. This method is NOT threadsafe, so we must do this in a synchronized method!
- [ ] (maybe) capture tokens codec in a class as well, like `ContentStoreBlockCodec`. Consider pooling encoder/decoder as well if useful.

LATER?
- [ ] (PROBABLY VERY DIFFICULT AND MAY NOT BE WORTH THE EFFORT) can we implement a custom merge here like CS? i.e. copy bytes from old segment files to new segment file instead of re-reversing the reverse index.

### Content store

LATER? 
- [ ] ContentStoreSegmentReader getValueSubstrings more efficient impl? This is possible, but maybe not the highest priority.
- [ ] implement custom merge? The problem is that we need to split the `MergeState` we get into two separate ones, one with content store fields (which we must merge) and one with regular stored fields (which must be merged by the delegate), but we cannot instantiate `MergeState`. Probably doable through a hack (placing class in Lucene's package or using reflection), but let's hold off until we're sure this is necessary.


## How to phase out the global FI API

We would like to eventually eliminate the global forward index API. This means forward index related tasks (sort/group/filter on context, produce KWICs, NFA matching) should operate per index segment, followed by a merge step.

The merge step would use string comparisons instead of term sort order comparisons, so we don't need to keep track of global term sort orders, which is expensive and difficult to do when dynamically adding/removing documents. Such a merge would work in a similar way as with distributed search.

Other advantages of this appraoch: makes operations more parallellizable, minimizes resource contention, makes disk reads less disjointed, and stays closer to Lucene's design.

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
