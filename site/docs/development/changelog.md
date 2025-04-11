# Change Log

## Improvements in dev

### New

- Major new features:
    - indexing and searching (dependency) relations. Support for the CoNLL-U format, and configuring your own XML-based format using special standoff annotations.
    - proper support for multiple annotated fields per document
    - parallel corpora
- BCQL:
    - you can request all relations in a sentence now (`within rcapture(<s/>)`).
    - new `overlap` operator to e.g. find hits in the overlaps of two tags (`'the' within <heading /> overlap <named-entity/>`). Useful to build a query when you don't know the hierarchy of tags.
    - integer range query for annotations (`[pos='verb' & pos_confidence=in[50,100]]`) and attribute values (`<verse number=in[1,10]/>`)
    - lookahead/lookbehind: `(?<=...)` and `(?=...)` for positive lookbehind and lookahead, and `(?<!...)` and `(?!...)` for negative lookbehind and lookahead.
    - `punctBefore`/`punctAfter` pseudo-annotations: `[word='good' & punctAfter=',']` (translates to `[word='good'] (?= [punct=','])`)
    - `with-spans(...)` function that will capture all overlapping spans for each hit.
    - option `omitEmptyCaptures` to omit empty captures
    - `csvdescription` parameter
- Matching:
  - match info includes captures, inline tags and relations. Captured tags are returned with their attributes.
  - hit uniqueness now considers match info (capture groups etc.) as well, so multiple hits with the same doc, start and end may be reported (with each having different match info)
  - version 2 of the .blf.yaml format, changing the default processor to Saxon and removing various quirks. See [here](../guide/how-to-configure-indexing.md#differences-between-version-1-and-2).
  - support for indexing extra attributes with inline tags using XPath; only indexing certain attributes; or excluding all except certain attributes.
- BlackLab Server:
  - Evolving the BLS API. See [API versions](../server/rest-api/api-versions.md).
  - you can pass `context=s` to get a whole sentence as context around a hit.
  - group by capture, e.g. `capture:word:s:A` to group on the words from the group captured as `A` in your query.
  - group on a tag in a list of overlapping tags, e.g. `capture:word:s:with-spans[named-entity]` to group on the whole named entity (if the match overlap with one) for a query like `with-spans('baker')`.
  - group on a span attribute, e.g. `span-attribute:speech:lang:c` for the contents of the span named `speech` (your pattern might be `"test" within <speech />`
  - new BLS endpoint `/relations` gives a list of all inline tags and relations in the index, including attribute values and frequencies.
  - new BLS endpoint `/shared-with-me` that returns a list of corpora shared with the currently logged-in user, if any. Also reported on the `/` (server info) page.
  - new `limitvalues` parameter to control the length of value lists (i.e. field, attribute values)
  - a JSON form of each query is returned by the API. You can also pass this JSON form to the REST API as an alternative to BCQL. This is useful for e.g. implementing a query builder. There is also an operation (/parse-pattern) that just returns the JSON form of a query.
  - `userIndexMaxTokenCount` setting to configure limits on user index size
- Indexing:
    - integrated index
    - much improved Saxon indexer
    - (limited) XInclude support
    - new DocIndexerExample gives a better example of low-level custom indexing
    - you can now use a custom DocIndexerConfig subclass by specifying `fileTypeOptions.docIndexerClass` in your .blf.yaml file.
    - standoff annotations can now be used to index spans and relations as well
- Misc:
    - support for Docker
    - ability to run BlackLab as a Solr plugin (work in progress), and a proxy so Solr with plugin behaves the same as BLS.
    - much improved unit and CI testing
    - more warnings are logged if something is wrong, e.g. indexing without a persistent document identifier (pidField) configured.
    - QueryTool can now be used for performance and correctness testing
    - BlackLab .war now contains Git commit id in its manifest. BLS will report it on the `/` (info) page.

### Changed

- annotation setting allowDuplicateValues defaults to false, which is usually what you want.
- various performance improvements, e.g. when opening huge indexes. Also Lucene's two-phase iterators are used where possible.
- BlackLab Corpus Query Language (BCQL) queries now allow dashes in names. Integrated index type allows dash in forEach names.
- BCQL now allows the not-operator (`!`) to be used at the top level, and "global" constraints (`::`) to be used within parentheses as well.
- Unicode normalization is applied to documents while indexing.
- If a corpus directory is named `index`, we used to look at the parent directory for the "real" corpus name, but this quirk has been removed.
- There is no longer an arbitrary term length limit.
- If a group identity is actually no value (i.e. no term), this is now returned as '(no value)' in the BLS response.
-

### Fixed

- Querying numeric fields was broken but should work correctly again.
- Escaping issues in BCQL. Literal string mode.
- Many more bug fixes; see [Git commit history](https://github.com/INL/BlackLab/commits/dev/) for details.

### Removed

- Removed `vmtouch` from repo (no longer recommended)

### Docs

- New site, with full REST API docs
- Added BARKs ((BlackLab Archives of Relevant Knowledge)
- Documented CQL operator precedence, query rewriting, etc.

## Improvements in 3.0.1

### Changed

- Remove Solr dependencies (via SlowCompositeReaderWrapper). Those made the WAR huge and
  caused problems with some application servers (because Jetty was being pulled in).


## 3.0.0

### Changed

- Minimum Java version was raised from 8 to 11.
- Based on Lucene 8. Thanks to @zhyongwei for the initial version update. Further
  changes were made to how DocValues are used, as this API is now sequential instead
  of random-access.
- Smarter default config values based on number of CPU cores and max. heap memory.
  A debug message will show that and how the default value for a missing was determined.
- Corpora larger than 2^31 tokens are now supported. The few operations
  that don't support this yet will produce a clear error message. This functionality can
  be disabled with the `search.enableHugeResultSets` setting (default `true`) that might
  slightly improve performance.
- Warn if an annotation named 'word' or 'lemma' has no explicit `sensitivity` declared. Due to a special case, these will automatically get sensitivity `sensitive_insensitive`, but this quirk is deprecated and should not be relied upon.
- Clearer error message if no `indexLocations` were found.
- BLS now resolves symlinks while scanning indexLocations.
- BLS now allows dots in index names (in addition to underscore and dash).
- DocIndexerXPath now throws an exception if it encounters a non-UTF8 doc.
- FileProcessor should now handle files larges than 4G (although such files may lead to other problems, e.g. excessive memory use).
- When search is interrupted, there should now be a better indicating as to why.
- Stack trace should be included in more error responses if in debug mode.
- 'Unauthorized to view content' error now refers to documentation.
- If a format config contains an error, report the file it occurs in.
- Document that the first annotation declared becomes the main annotation.
- BLS now also looks at `X-Forwarded-For` header to determine debug mode.
- BLS now accepts wildcards in the debug mode ip configuration.
- Update Jackson, revert YAML bug workaround.
- Improve how search/count times are reported in BLS.


### New

- Added `naf` (NLP Annotation Format) to the builtin formats.
- `FrequencyTool` is a commandline tool that allows you to get frequency lists for an entire corpus.


### Java API

- `Hits`, `HitsInternal(Mutable)`, CapturedGroups and other interfaces refactored to make
  (im)mutability more explicit.
- `Doc` and `DocImpl` classes were removed. Now that we use `DocValues` everywhere, caching
  Lucene documents doesn't make sense.
- Searches should no longer get stuck queued even if maxConcurrentSearches is set to a low value.


### Fixed

- Fix usecontent=orig with outputformat=json
- Fix metadata value frequency reading, which due to a bug with how YAML was handled would all be read back as 0.
- Fix an issue where `HitProperty.contextIndices` would seemingly change during a sort operation.
- Prevent NPE if no patt specified with `/hits` request.
- Fix `hitsProcessedAtLeast()` method not always blocking. It may not be clear from the name, but this method will wait for the specified amount of hits to be processed, or will return `false` if all hits were processed and there were fewer than that amount.
- Fix NPE for malformed sort string like `docid,`.
- Don't hardcode "word" as the main annotation.
- Fix errors when running tests in parallel.


### Removed

- support for previous BlackLab indexes (because Lucene 8 cannot read Lucene 5 indexes);
  you must reindex your data to use this version. If this is impractical, please keep 
  using v2.3.0 for now. We would like to provide a conversion tool at some point.
- support for obsolete content store and forward index files (cs types "utf8" and "utf8zip",
  fi version 3; these were all replaced with newer versions six years ago. older indexes
  will need to be re-indexed)
- Some deprecated settings. A warning will be shown if the setting is still found.
- Deprecated methods from `Indexer`, among others.

## 2.3.1

### Fixed

- If another search needs a queued search, always unqueue it (avoids deadlock)
- Respect chosen context size for CSV export
- Update list of builtin formats so tei-p5 and the legacy tei formats can be found
- If format with same name is found, include the name in the exception
- Don't include metadataGroupInfo with every result for `/hits` and `/docs` responses, this was never intended and produced invalid JSON
- Don't crash if an unreadable index from a different BlackLab version is found, just skip it
- Add version and build time to WAR manifest.

### Changed

- IndexTooOld/IndexTooNew replaced with IndexVersionMismatch. See exception message for details.


## 2.3.0

### Changed

- Language level set to Java 9, so we can use `Arrays.compare()` among other things
  (and stay on a supported Java version).
- GitHub Action to automatically build and run integration tests on push

### Documentation

- Added to blacklab-internals page.
- Update roadmap, who uses blacklab.
- Documentation, comments and code now use the terms "annotated field" and "annotation"
  everywhere (some lingering occurrences of the older terms "complex field" and
  "property" were removed).

### New

- Alternative cache implementation (`ResultsCache` by `@eginez` of Lexion) that may be
  faster in high-throughput scenarios. Note that this implementation currently does not
  support queueing or aborting searches or getting a running totals count.
- Add processing step to concatenate separate date fields into one.
- Added format configuration `tei-p5.blf.yaml` that uses more standard `pos` attribute.
  Renamed existing TEI format configurations to `-legacy`.

### Fixed

- Gracefully shut down ExecutorServices to avoid hanging on application exit.
- Fixed intermittent crashes using synchronization.
- Fixed count hanging if underlying search was aborted due to timeout.
- Ensure all BLSpans are startpoint-sorted.
- Lowercase (desensitize) display values when grouping case-insensitively.
- Always run at least one search (don't queue if no searches are running)
- Fix convert and tag plugin functionality.
- Various smaller bugfixes.

### Performance

- Only load document fields we actually need
- Some more optimizations for hit frequencies

### Removed

- empty module `interfaces`
- SQLite logging. Was never fully realized, and the new approach using Prometheus (see
  `instrumentation` modules) is better.
- `checkAgainMs` (from BLS response).
- several long-deprecated methods.

## 2.2.0

### Changed

* Make forward-index matching much less frequent, as it's difficult to predict when it will help, and it often hurts.
* Split into several projects that each handle specific functionality. More to do here.
* If either `wordsaroundhit` or `parameters.contextSize` are set to 0, don't include left and right context in the 
  results at all.  
* The branch that corresponds to BlackLab's latest release is now called _main_ instead of _master_. The default 
  branch remains the _dev_ branch (up to date but potentially unstable) 

### New

* Added `instrumentation` modules to collect metrics using e.g. Prometheus.
* Greatly improve startup time and passive memory usage.
* Improved time to first result in (very) large indices.
* Improved performance by instantiating fewer objects (e.g. using `IntArrayLists` instead of `Hit` objects)
* Greatly improved speed of grouping on "any token" query, e.g. generating a frequency list for all or part of the corpus.
* Searches will now be queued (not started yet) if server load is too high.
  Pausing already-started searches was removed, as this can keep a lot of memory from being used for running searches.
* If a search is aborted for taking too long, it will be held in cache for a while to prevent clients from immediately resubmitting.
* `/search-test/` debug interface (experimental).
* Improved experimental Dockerfile.
* BlackLab Server test suite using Docker, Mocha and Chai.
* Simplified code for better maintainability.
* Documented blacklab internals (see https://blacklab.ivdnt.org/blacklab-internals.html)
* New setting `debug.alwaysAllowDebugInfo`, so each BLS request is considered in debugMode, e.g. /cache-info works, etc.
* New setting `indexing.maxNumberOfIndicesPerUser` to configure how many private indices each user is allowed.
* New setting `parameters.writeHitsAndDocsInGroupedHits` and query parameter includegroupcontents to include hits
  with the grouped results.
* Handle index paths that are relative symlinks.

### Fixed

* Upgrade to `log4j-2.17.1` (fixes [CVE-2021-44228](https://nvd.nist.gov/vuln/detail/CVE-2021-44228) and [CVE-2021-45046](https://nvd.nist.gov/vuln/detail/CVE-2021-45046)).
* Bug that could cause metadata values from a previously indexed document to also be added to the next.
* YAML bug with metadata values containing newlines and colons.
* Bug in `TermsWriter` that caused a crash when indexing more than 134M unique terms.
* Deadlock because of fixed thread count and some subtasks getting run but not others. Now either allows entire search operation or queues it until later.
* Fix not all hits always counted when grouping/sorting.
* Fix metadata queries on numeric fields not working.
* `HitsFromQueryParallel` queries can now be correctly aborted if they take too long.
* `HitsFromQueryParallel` should work correctly with capture groups now. Sampled hits as well.
* Prevent NPE when not all hits have captured groups.
* Prevent `ConcurrentModificationException` caused by not synchronizing.
* Fix some caching bugs by consistently defining `hashCode()` and `equals()`.
* Report correct search time (used to underreport).
* Fixed slowdown when a lot of indexes are open at the same time.
* Many smaller bugfixes.

### Removed

* Support for pre-2.0 configuration files. See [BLS configuration file](/server/configuration.md).
* Support for `useOldElementnames` (old BLS element names, using "properties" instead of "annotations")


## 2.1.1

### Fixed

* Upgrade to log4j-2.16.0 (fixes CVE-2021-45046).

## 2.1.0

### Changed

* BLS: /termfreq operation no longer requires a filter query.


### New 

* Add MetadataFieldsWriter for programmatically setting the special fields
  (in addition to doing so in .blf.yaml files)

### Fixed

* Fix crash during indexing if terms file got very large.
* BLS: Fix incorrect check who user-owned formats.

## 2.0.0

### API changes

#### BLS
* (breaking change) BLS responses will now report metadata values as arrays,
  because metadata fields can have multiple values.
* BLS now reports capture groups (in a captureGroups element for each hit) (by @severian)
* BLS now allows all headers in CORS requests
* You can now specify listvalues and/or listmetadatavalues to limit what annotations and metadata fields are returned.
  If omitted, all annotations/fields are returned.
* BLS responses now include displayNames and "tab grouping" (if any) for metadata fields (thanks @eduarddrenth)
 
#### Java library
* (breaking change) Completely refactored Java API to be more clear and consistent.
  See the migration guide.
* (only for advanced Java library usage)
  Made the concept of a BlackLab instance ("BlackLabEngine") explicit, so you can 
  e.g. decide how many threads you want your BlackLabEngine to have.

### New
* In addition to VTD-XML, Saxon may be used to parse input files for indexing. Saxon supports XPath 3, which
  contains many features useful for configuring input formats. It is also faster, at the cost of increased memory 
  usage for large files.
* Multiple values may be indexed for a metadata field (this is automatic when your XPath matches multiple values).
  (this is is the reason why BLS will now report all metadata as lists of values, even if there's only one)
* Added mapValues option to map metadata field values while indexing
* It is now possible to configure global unknown condition and value (use metadataDefaultUnknownCondition 
  and metadataDefaultUnknownValue at the top-level of your indexing config)
* You can now set isInternal to true on annotations to prevent searching and grouping on it in corpus-frontend
* Added annotation option allowDuplicateValues (defaults to true). If multipleValues is true and allowDuplicateValues 
  is false, duplicates encountered will not be indexed twice, preventing double hits.
* Add support for retrieving occurrences of a list of terms, /blacklab-server/INDEX_NAME/termfreq

### Changed
* Speeded up searches by allowing hits to be fetched in parallel
* New Search system that allows better optimization and integrates result
  caching, allowing you to define an application-specific cache behaviour.
* There is a new version of the blacklab and blacklab-server configuration 
  files. The old version still works but will be removed in the future.
  See migration-guide.md for the details of the new format. 
* IndexTool now defaults to multithreaded indexing and the number of threads can be configured
* Calculating tokens in a subcorpus (using includetokencount=true on a docs query with 
  just a metadata filter) is much faster (through the use of DocValues). 
  We use DocValues in more places for performance improvements.
* In new indexes, subannotations are part of the index structure. As a result of this,
  you can enumerate the subannotations and their values much more efficiently.
  You can also specify any metadata you can specify for a regular annotation,
  such as displayName, uiType, etc. Querying subannotations should be more efficient 
  as well.
* Indexing should use less memory now.
* Sparse annotations take up less disk space.
* Forward indexes take up less memory while searching, and are initialized in the 
  background for quicker startup.
* A number of new metadata settings are supported, such as annotationGroups.
  More extensive documentation will follow.
* Added optional detailed SQLite logging for diagnosing performance issues.
* Improved error message when passing invalid regex expression or when query contains unknown annotation
* If punctPath matches an empty tag, replace it with a space. This deals with e.g. TEi <lb/> tags as punctuation.
* Don't autodetect titleField. If not set, use pidField. If that's not set, use fromInputFile.
* Allow leading wildcards in metadata filter queries
* If performance.maxThreadsPerSearch is set to 1, use HitsFromQuery instead of HitsFromQueryParallel
* Many smaller improvements.

### Fixed
* Fixed a concurrency bug that could cause a search to get stuck.
* Capture groups work with parallel hit fetching (by @severian)
* HitGroups retain capture group information (by @severian)
* Fixed and sped up determining subcorpus size (for relative frequencies) when grouping
* Restore namespace on document element when fetching part of a document (by @eduarddrenth)
* maxConcurrentSearches is now always at least 4, to avoid deadlocks on single-core machines
* Warn if value is not stored because it's too long; increase maximum value length.
  (if value exceeds 32K, it will be truncated when storing in DocValues)
* Only use NumericDocValues from a single thread at a time (reported by @severian)
* Sanitize field names containing characters not allowed in XML element names, so BLS responses are valid XML
* Fixed textDirection from config not working when creating new index
* When displaying left context, ensure the words are in original order
* Gap filling works when cql query contains a newline
* Fixed deleting documents from index, accidentally including deleted docs in results.
* Fix groups with a colon in the group name not working correctly
* Many smaller bugs in indexing and searching fixed
* Library versions updated

### Removed
* Really old index formats (pre 1.4 or so) are no longer supported.
  BlackLab will report this when you try to open such an old index. 
* BLS no longer supports the rarely used block=no parameter for 
  polling-based asynchronous search. All search requests block until
  the response is available. Only the "total results" count will be 
  reported asynchronously unless the "waitfortotal" parameters is true.

## 1.7.2

### New
* Issue warning if unrecognized params passed to Auth* class.

### Changed
* Rewrite queries like [lemma=".*"] to [] for efficiency

### Fixed
* Sort matches for XPaths (VTD-XML does breadth-first search and doesn't 
  necessarily return matches in document order; this can be a problem for 
  more complex XPaths).
* Made timeout longer for hits queries to avoid Timeout error.
* Jackson and commons-compress libraries were updated because of patch 
  security vulnerabilities. 

## 1.7.1

### Changed
* Default maximum file upload size is now 1GB. 

### Fixed
* Don't block until all hits have been counted.
* Fixed bug when maxHitsToRetrieve and maxHitsToCount are the same value

## 1.7.0

### New
* Much user-friendlier indexing using .json/.yaml config files.
  Added support for tabular formats and plain text.
  Includes built-in format configurations for many popular formats.
  See documentation.
* Added "linked document" indexing, which provides e.g. the ability 
  to automatically locate and index the corresponding metadata for 
  each document.
* BLS can now return results in CSV format.
* BLS now supports an "autocomplete" request for metadata fields as well as
  property values. 
* Corpora now have a text direction setting that the user interface can 
  use to change how things are displayed.
* contentViewable can now be specified at the document level as well 
  as corpus-wide, by adding a metadata field named "contentViewable"
  with the value "true" or "false". The per-document setting overrides 
  the corpus-wide setting.
* BLS now allows private corpus owners to explicitly share the corpus with
  other users.
* Many small additions to make it easier to generate a user-friendly
  user interface based on corpus structure information from BlackLab Server,
  such as displayName for fields and values, uiType for field and 
  properties, etc.
* BLS can now omit empty properties in the results if desired. Default is
  to include empty properties. Use requests.omitEmptyProperties setting to 
  change.
* BLS will now send the "Access-Control-Allow-Origin: *" header by 
  default, allowing a frontend on another server to access it.
  This can be overridden in the requests.accessControlAllowOrigin
  setting.
* BLS can attempt to generate a default XSLT from a (XML-based) input format
  configuration.
* Added a MetadataFetcher that reads from CSV files.
* Added plugin capability for document conversion and tagging before indexing,
  allowing BLS to e.g. take ePub input files, convert them to a corpus format, 
  linguistically annotate them and index them. This feature is somewhat 
  experimental and as of yet undocumented.
* Many smaller improvements.

### Changed
* BlackLab now requires Java 8.
* BLS no longer trims leading/trailing whitespace from parameter values. 

### Fixed
* Untokenized fields weren't lowercased while indexing, but were lowercased
  when searching, causing problems. Now they are treated the same.
* Numerous other bugfixes.

## 1.6.0

### New
* Added Searcher.getBlackLabVersion(). Also added blacklabVersion to index.
* Added Hits.filteredBy() to filter Hits on a HitProperty.

### Fixed
* Fixed bug with case-insensitive Terms.indexOf().
* Fixed buffer overrun while writing Terms file.
* Avoid problems with truncating mapped files on Windows. 
* Fixed bug when rewriting "n-grams containing at start/end ..." queries.
* Fixed incorrect matching if ANDNOT has multiple negative clauses.
* Use Arrays.hashCode instead of Object.hashCode for int arrays.
* Fixed "createWeight" bug when filtering on range queries.
* Made it possible to create, close, and re-open an empty index.
* Memory leak in BLS because of bug in TextPattern.rewrite().
* Fixed bug with regexes including character class negations.
* Fixed some rare NullPointerExceptions.

### Changed
* Added an alternative way of matching subqueries, using the forward index
  and a nondeterministic finite automaton (NFA; similar to many regular expression
  engines). Added an optimizer to choose when to use NFA matching.
  Still tweaking, but certain types of queries should be faster with this. 
* Made global constraints on capture groups possible.
* Added HitPropertyHitPosition for fast reproducible sorts.
* Made sure SpansSequenceSimple (which is fast) is used more often.
* Updated library versions, including migration to log4j 2.x.
* Indexer.index(File) now indexes all files by default instead of *.xml.
  If you want to index *.xml, use Indexer.index(File, String glob).
  IndexTool still defaults to *.xml; specify different glob if you want
  to index other file types.
* Upgraded from Lucene 5.2.1 to 5.5.2. Also made some preparations for
  eventually upgrading to Lucene 6.x.
  Deprecated methods that use the deprecated Filter and replaced them with
  a variant that takes Query.
* Removed several (long-)deprecated methods.
* SpanQueryFiltered.rewrite() rewrites its filter as well.
* BLSpanQuery is now the base class of all our SpanQuery classes, and is
  used throughout the code. Only the API hasn't been updated yet, but
  will throw IllegalArgumentException if a SpanQuery argument is not a 
  BLSpanQuery.
* Moved all TextPattern classes to nl.inl.blacklab.search package.
  Moved all SpanQuery/Spans classes to nl.inl.blacklab.search.lucene.
  Made some internal TextPattern methods package-private.
* Renamed SpanQueryBase to BLSpanQueryAbstract.
* Many improvements to documentation based on feedback and new features.
* Many optimizations, large and small.
* Many little fixes.

### BlackLab Server
* Cleaned up logging, made debug logging configurable by subject.
* Improved configuration error handling, made index scanning recursive.
* Added /explain?patt=... to explain how queries are optimized.
* Made long-running count operations pause if client doesn't check status.
* Added parameterized querying (filling in gaps with TSV data). 
* Added hitfiltercrit and hitfilterval parameters to filter hits on a criterium,
  like "word left of matched text". This allows you to view the hits in a single group
  after grouping, and then still allows you to group on these hits again, unlike the
  existing "viewgroup" parameter.
* Fixed cache bug that would cause "Cannot decrement refs, job was already 
  cleaned up!" message
* Made sure control characters are escaped properly in JSON and XML.
* Made sure regular and error output aren't mixed.
* In debug mode, include stacktrace with exception error. 
* Added support for enumerating subproperties.
* Made sure TooManyClauses is caught and dealth with properly.
* Fixed bug when trying to sort hits by a metadata field.
* Improved how context words group property expressions are interpreted.
* Added blacklabVersion to server info page.
* When grouping, show groups from large to small unless another sort is requested.
* Return clean error message when user passes empty group parameter.
* When trying to sample more hits than available, just return all hits.
* If no pattern given, return a proper error message.
* Return sample parameters for all search types, not just ungrouped hits.
* Also include "numberOfHits" stat when viewing group of documents.
* Added error message when 'viewgroup' is used without 'group'.
* Don't add whitespace into leaf XML elements.

## 1.5.0

### Fixed
* Two rare, subtle matching bugs in SpansExpansion and SpansPositionFilter.
* Fixed indexing bug where the compression code would occasionally get stuck in a loop.  

### Changed
* Deprecated TextPattern.toString() methods that take arguments.
* Gave HitProperty and DocProperty default toString() implementation.
* Added methods for iterating over all Lucene documents, forward index documents,
  content store documents.
* Added a test utility that can export your original corpus from the Lucene 
  index and content store (nl.inl.blacklab.testutil.ExportCorpus).
* Many RuntimeExceptions were changed to more specific subclasses like
  IllegalArgumentException or UnsupportedOperationException.
* Made it possible to add a metadata field with a fixed value to every
  document in a single IndexTool run. Useful if you want to combine multiple
  corpora into a single index: add each corpus in a separate IndexTool run,
  with a field Corpus with the appropriate name.

### BlackLab Server
* Made it possible to use POST for queries, so you can execute very large
  queries (many kilobytes). Note that very long (>30KB) regular expressions 
  can trigger problems in Lucene though.
* Allowed previously forbidden all-docs query (now that large document 
  queries are faster and less memory hungry).
* Grouped results are sorted by identity by default now.
* Made docpid a general way to easily filter on a single document PID.
* Replaced building a DataObject tree and serializing it with directly
  streaming the response data using DataStream, saving memory and time.
* Heavily refactored to be more modular.
* Removed some settings related to nonblocking mode, as they didn't seem
  very useful.
* Used JSON.org library instead of copy of the code.
* FIXED: if counting hits takes too long, don't error out but show the 
  results we have.
* FIXED: Do returns results even if counting all the results was interrupted 
  because it took too long.


## 1.4.1

### Fixed
* Potential overflow bug in ContentStoreFixedBlock when retrieving content.
* ContentStoreFixedBlock decompression bug that occurred with highly compressable content.
  (N.B. there is no need to re-index if you experienced either of the above bugs)

### Changed
* Don't store Document objects in DocResults, saving memory and time.
* Changed BL-CQL subproperty separator (e.g. for querying part of speech features separately)
  from ':' to '/'.

### BlackLab Server
* Made sure missing options in blacklab-server.json don't cause problems.

## 1.4.0

### New
* Added experimental support for "subproperties": properties that are indexed in the same Lucene 
  field, using prefixes, but don't each have a forward index. For now, mainly useful for
  indexing each part of speech feature separately, but in the future, BlackLab could possibly move 
  to indexing all properties in a single Lucene field. See DocIndexerOpenSonar,
  QueryExecutionContext.subpropPrefix(). 
* Added HitsSample and its implementation class, which can take a random sample of a larger
  set of hits.
* Added HitPropertyContextWords, which gives the user more options for sorting/grouping on
  context words, such as "group on the first and last words of the matched text" or "group on the
  second and third words to the left of the matched text".

### Changed
* Performed some code cleanups, moved some internal classes to different packages.
* Made Searcher an abstract base class to SearcherImpl; Hits an abstract base class to HitsImpl.
  Added mock classes for Searcher, Hits, ForwardIndex, Terms; used them to add tests.
* Moved hits-related settings from Searcher and Hits into a shared HitsSetting class;
  Searcher has a default set of HitsSettings that Hits objects "inherit".
* Updated gs-collections 6.1 to eclipse-collections 7.1. Replaced Map&lt;Integer, T&gt; with
  IntObjectMap, Map&lt;Integer, Integer&gt; with IntIntMap, ArrayList&lt;Integer&gt; with IntArrayList
  a number of times (mainly) for more memory-efficiency.
* Started using commons-lang to replace certain utility functions.
* Moved some basic Lucene functionality unrelated to the rest of BlackLab from Searcher to 
  LuceneUtil.

## 1.3.7

### Fixed
* Opening a large "fixed-block" content store took a really long time. 

## 1.3.6

### Fixed
* Content store growing larger than 2 GB caused an integer overflow.
* DocIndexersXmlHandlers element matching didn't work correctly for some XML structures.
* Storing document in ContentStoreDirFixedBlock would very rarely crash due to a
  block resizing bug.

## 1.3.5

### New
* Added default unknown condition and value to indextemplate.json, so you can specify what to do if a metadata field value is missing without specifying it for each field separately.

### Fixed
* BLSpanOrQuery would occasionally miss valid hits due to a
  bug in the advanceStartPoint() method.

### Changed
* Switched to JavaCC 6.x.

## 1.3.4

### Fixed
* Lone carriage return characters in JSON output were not escaped;
  Windows line endings were escaped as a single \\n.

## 1.3.3

### Fixed
* Indices with old terms file format (pre-1.3) produced empty concordances.

## 1.3.2

### Fixed
* Query rewrite bug when combining identical clauses with different repetitions,
  i.e. \[pos="AA.*"\]\[pos="AA.*"\]* --> \[pos="AA.*"\]+
* Throw a descriptive error if an index contains no fields.

### Changed
* Some small code quality improvements, like using .isEmpty() instead of .size() == 0.
* Added -javadoc and -sources JARs to Maven build, in preparation for publishing to Maven Central.
* Added distributionManagement section for deploying to OSSRH staging area. 

## 1.3.1

### New
* Added new default content store format "fixedblock", that improves space re-use when updating documents. 

### Fixed
* Bug in SpanQueryAnd which caused incorrect hits to be reported.

### Changed
* Special OSX and Windows files are skipped, even if they occur inside archives.

## 1.3

### Added
* Searcher now implements Closeable, so it can be used with the try-with-resources statement.
* You can specify that properties should not get a forward index using the complexField property "noForwardIndexProps" (space-separated list of property names) in indextemplate.json.

### Fixed
* Forward index terms is no longer limited to around 2 GB.

### Changed

## 1.2.1

### Fixed
* Queries containing only a document filter (metadata filter) would return incorrect results.

## 1.2.0

### Changed
* Switched build from Ant to Maven, and added generating a project site with javadocs, reports, etc.
* Using less memory by switching some Maps in the forward index to the gs-collections one.
* Updated to Lucene 5.2.1.
* Added Maven project site, available at https://blacklab.ivdnt.org/
* Removed Lucene query parser for corpus queries.
* Keep tag end position in payload of start tag, which results in much faster tag searches.
* Rewrote many slower queries to make them (much) faster. Particularly searches with "not" parts and "containing"/"within" should be faster.
* Sped up "containing", "within", and other such filter queries.
* TextPatternAnd was renamed and expanded to TextPatternAndNot. TextPatternAnd is still available as a synonym, but has been deprecated.
* Added TextPatternFilterNGrams to speed up queries of the form: []{2,3} containing "water" (or the "within" equivalent).
* Added BLSpans.advanceStartPosition(target) to "skip" within a document.
* Commons-compress (used for reading .gz files) is statically linked now.
* Limited token length to 1000 (really long tokens would cause problems otherwise).

### Fixed
* Empty version file causes NullPointerException.
* Missing manifest file causes NullPointerException.
* ContentStoreDir contained dependencies on the default encoding.
* A number of subtle search bugs.
* Opening an index by passing a symbolic link throws an exception.
* Miscellaneous small fixes.

## 1.1.0
* Upgraded from Lucene 3.6 to Lucene 4.2. This should speed up regular expression searching, among other things. The required Lucene 4 modules are: core, highlighter, queries, queryparser, analyzers-common. Thanks to Marc Kemps-Sneijders from the Meertens Institute for the code submission!
* The awkwardly-named classes RandomAccessGroup(s) were renamed to HitGroup(s). Also, DocGrouper was renamed to DocGroups to match this naming scheme. The old versions are still around but have been deprecated.
* HitPropValue classes now need a Hits object to properly serialize/deserialize their values in a way that doesn't break after re-indexing.
* Manual object construction was replaced with method calls where possible, for convenience, speed and ease of refactoring. Examples: use Hits.window() instead of new HitsWindow(); use Hits.groupedBy() instead of new ResultsGrouper(); use DocResults.groupedBy() instead of new DocGroups(). (code to the HitGroups/DocGroups APIs instead of to the concrete type ResultsGrouper/DocGrouper). Same for DocResults.
* HitGroups now iterates over HitGroup (used to iterate over Group)
* If you just want to query documents (not find hits for a pattern), use Searcher.queryDocuments(Query) (returns DocResults without hits).
* Preferably use Hits.sortedBy() (returns a new Hits instance) instead of Hits.sort() (modifies Hits instance). In a future version, we want Hits to become immutable to facilitate caching in a multithreaded application. Note that although you get a new Hits instance, the hits themselves are not all copied (no need, because the Hit class is now immutable).
* LuceneQueryParser.allowLeadingWildcard now defaults to false like in Lucene itself. Call LuceneQueryParser.setAllowLeadingWildcard() to change the setting.
* If you want to control how indexing errors are handled, subclass IndexListener and override the errorOccurred() method. This method receives information on what file couldn’t be indexed and why.
* Visibility for some internal classes and methods has been reduced from public to package-private to trim the public API footprint, promoting ease-of-use and facilitating future refactoring. This should not affect applications. If it does affect you, please let me know.
* Some other methods have been renamed, are no longer needed, etc. and have been deprecated. Deprecated methods state the preferred alternative in the @deprecated Javadoc directive.


## 1.0

### Features
* Sorting/grouping on multiple properties now works correctly. Use HitPropertyMultiple.
* You can now sort/group (case-/accent-) sensitively or insensitively.
* You can now easily get a larger snippet of context around a single hit (say, 100 words before and after the hit). Call Hits.getConcordance(String, Hit, int) for this purpose.
* Indexing classes now work using element handlers, making them much more readable. Supporting a new file format has become simpler as a result of this. TEI P4/P5 and FoLiA indexing classes are included with BlackLab now. See nl.inl.blacklab.indexers package.
* It is now possible to delete documents from your index. The forward indices will reuse the free space for new documents.
* The Hits class should be thread-safe now. This makes several things possible: paging through hits without re-executing the query and quickly displaying the first few hits while a background thread fetches the rest. You can even display a running counter while hits are being fetched.
* nl.inl.blacklab.tools.IndexTool is a new generic indexing tool. It is command-line utility that lets you create new indices, add documents to them and delete them again. Indexing can be customized via commandline parameters and/or a properties file. Pass --help for more info.
* QueryTool, the command-line search tool and demonstration program, has been improved with many little features, including a performance test mode.
* Long-running queries may be interrupted using Thread.interrupt(); this will stop the gathering of hits and return control to the caller.
* Hacked in (very) experimental SRU CQL (Contextual Query Language) support. Still needs a bit more love though. :-)

### Performance-/memory-related
* Concordances (for KWIC views) are constructed using the forward indices now (including the one for the new ‘punct’ property, containing punctuation and whitespace between words – if you created your own indexer, it pays to update it to include this property). Before they were constructed using the content store, but this method is much faster and more disk cache friendly. 
* Startup speed has been improved, and there is an option to automatically “warm up” forward indices (i.e. prime the disk cache) in a background thread on startup. Enable this by calling Searcher.setAutoWarmForwardIndices(true); before constructing a Searcher object. This may become the default behaviour in future versions.
* Applications have more control over the maximum number of hits to retrieve, and the maximum hits to count. “Unlimited” is also an option. By default, no more than 1M hits are retrieved, but all hits are counted.
* Several types of queries (notably, phrase searches) have been sped up using the ‘guarantee’ methods in BLSpans to determine when certain operations can be skipped.
* Several other small improvements in performance and memory use.

### Other
* Opening the BlackLab index should now be done using Searcher.open() instead of directly through constructor. See the [https://github.com/INL/BlackLab/commit/d1d1b71ca8d5ef2aea25eab5a6e12b7e51cf5f65 commit message] for the rationale.
* Several superfluous methods were deprecated to simplify the API. The Javadoc will indicate why a method was deprecated and what alternative you should use. Deprecated methods will be removed in the next major version.
* Many small bugs fixed, comments added and code structure improved.
