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

Grouping and sorting is currently not done per segment, but only once we've gathered hits from each segment. When we group/sort/filter on context, we thus have to return to the segment each hit came from, which is relatively inefficient.

It should be faster to perform the operations per segment, then merge the results: this is inheritently parallelizable (because segments are independent of one another), minimizes resource contention, and makes disk reads less disjointed.

The merge step would use string comparisons instead of term sort order comparisons. (similar to how merging would work in a distributed search scenario).



Caching in BlackLab splits search into stages, e.g. "unsorted hits" > "sorted hits" > "page X from sorted hits". The first stage should be able to give you "page X from unsorted hits" even while it's fetching more hits.

`HitsFromQuery` does this by merging hits from segments into a global list as the search progresses. But it's silly to build this merged list (with locking delays) and then "unmerge" it again so that we can sort hits per segment. Better would be to keep hits separated by segment. But then how do we get "page X from unsorted hits" before all hits have been fetched?

One approach is to build a global list and keep the segment lists as well, but this takes up extra memory.

We really just want a global _view_ of the unsorted hits. This view should grow over time as hits are fetched, and should be backed by the hits per segment. We could keep track of where hits can be found, i.e. "hit 13 in the global list is hit 3 from segment 5". This adds a layer of indirection, but only if we're making a (small) page of unsorted hits, which shouldn't be too bad. For efficiency, we could remember "stretches" of hits, i.e. "hit 13 in the global list is part of a stretch that starts at 10 and is 5 hits long, from segment 5 starting at 0". If the next step is sorting or grouping, we'll just use the hits from segments directly, skipping the layer of indirection. Even better, we may be able to lazy-initialize the global hits view, so we might not have to make it at all, or we might only need it after all hits have been fetched.



### Done so far

- remove old external index
- `HitsSimple`, implemented by the `HitsInternal*` classes among others, is where we can iterate over hits. `Hits` does not implement this interface anymore; call `getHits()` to get a `HitsSimple` object. This removes a layer of indirection and separates functionality.
- Got rid of global term ids. We use the per-segment forward index and terms object everywhere now. Large indexes should open much faster now, because we don't determine the global term id mappings anymore.
- could we do away with PropertyValue hierarchy and use Object / String/Integer/... directly?
- try to get rid of more useless abstract base classes like `Result` (?)
- 

### Still to do

- [ ] optimize how we use the per-segment forward index and terms objects (try to batch things by segment more)
- [ ] Check the last remnants of the global forward index; clean up if possible.


- [ ] Implement:
  - [ ] Ensure several of these can be merged into another `HitsInternal` object _dynamically_, while the hits are being gathered (in the case where no sort has been requested).
  - [ ] Add a subclass that gathers hits from a single segment, sorts them, and allows them to be merged based on the sort value later.
  - [ ] Follow a similar approach for grouping.

- [ ] Implement merging:
  - [ ] `HitsFromQuerySorted` merges hits from several `SpansSorted` instances
  - [ ] `HitsGroupedFromQuery` merges hits from several `GroupedSpans` instances
  
  Merging should be based on (`String`/`CollationKey` and maybe `Integer` and `Double` as well?) comparison of the stored sort and group values.

- [ ] Optimize `SpansSorted`, `GroupedSpans` for the new, per-segment situation (i.e. don't use `Hits`, `Kwics` anymore).


### Optimize handling of various codec objects

SMALLER ISSUES

- DocProperty per-segment (DocValues)

- Store sorted hits using indexes to the original hits object to save memory?

- `ForwardIndex.forField()` created two objects; can we do it with one? See below.

```java
  /**
    * Get a new FieldForwardIndex on this segment.
    * Though the reader is not Threadsafe, a new instance is returned every time,
    * So this function can be used from multiple threads.
    */
    public FieldForwardIndex forField(String luceneField) {
        return new FieldForwardIndex(new Reader(), fieldsByName.get(luceneField));
    }
```

## BL5 major refactorings (Dutch)

- type parameters weg uit Results etc. (ws. nog meer opruiming mogelijk)

- HitsSimple is nu het interface om met hits te werken, te itereren, etc.
  Hits is een compleet "zoekresultaat". Hits.getHits() retourneert een HitsSimple.
  (namen kunnen nog aangepast worden)
  HitsSimple wordt geimplementeerd door HitsInternal*, maar Hits heeft ook een
  eigen implementatie die bijv. ensureHitsRead() aanroept.

- Global term ids zijn verdwenen. Global Terms object ook.
  Overal waar met termen wordt gewerkt, gaat dit per-segment met id/sortposition of
  globally met strings.
  Zie bijv. HitPropertyContextBase.
  Doel is om uiteindelijk bijv. sorteren per segment te doen (met sortpositions), daarna een
  snelle merge op stringbasis.
  Nu wordt er nog wat te veel randomly tussen segmenten gesprongen (globalDocId > segment)

- (WIP)
  HitsFromQueryKeepSegments: alternatief voor (subclass van) HitsFromQuery.
  Doel is om hits per segment op te slaan maar ook een global view te bieden (zonder alles
  2x in geheugen te houden).
  Global view gebeurt door stretches bij te houden.
  (segment / start in segment / start in global view / length)
  Global view is relatief traag natuurlijk, maar wordt uiteindelijk hopelijk alleen gebruikt
  als je een pagina unsorted hits vraagt, dus niet heel performancekritisch. Andere operaties
  (sort, group, sample, etc.) werken met de hele set hits en gebruiken direct de hitlijsten per
  segment.

- andere TODOs:
    - minder met doc(index), start(index), etc. doen, zo veel mogelijk met getEphemeral(index, hit)



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
