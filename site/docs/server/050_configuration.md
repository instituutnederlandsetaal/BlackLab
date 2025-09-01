# Configuration

BlackLab Server settings can be configured in a configuration file.

## YAML vs. JSON

This file can be in YAML or JSON format. On this page, we will use the YAML format (as it allows comments and is arguably more readable), but it the two can be easily converted back and forth (for example [here](https://www.json2yaml.com/)). Just be sure to use the `.json` extension for JSON and `.yaml` or `.yml` for YAML.

## Config file locations

Where should this file (or files) be located? BlackLab looks for them in the following places:

- the directory specified in `$BLACKLAB_CONFIG_DIR`, if this environment variable was defined
- `$HOME/.blacklab`
- `/etc/blacklab`
- the Java classpath

In addition, BlackLab Server will also look for `blacklab-server.yaml` in the directory where the .war file is located, e.g. `/usr/share/tomcat/webapps`.

## Minimal config file

Here's a minimal configuration file for BlackLab Server. Name it `blacklab-server.yaml` and place it in one of the locations mentioned above, e.g. `/etc/blacklab`.

```yaml
---
configVersion: 2

# Where corpora (indexes) can be found
# (list directories whose subdirectories are corpora, or directories containing a single corpus)
indexLocations:
- /data/index
```

This simply tells BlackLab Server where to find its corpora. In this case, it will either expect `/data/index` to be a corpus, or will expect `/data/index/` to contain subdirectories that are corpora.

## Config file reference

Here we take a look at individual sections of the configuration file.

Note that you don't need all sections in your configuration file! Start with a minimal config file and only add those
sections where you want to change the default settings.

Here, the sections are ordered roughly from most important to least important.

### Corpora locations

The top-level settings in `blacklab-server` (i.e. not part of a section) determine where BlackLab Server can find its corpora (indexes).

```yaml
# Must be set to 2 at the moment.
configVersion: 2

# Where corpora (indexes) can be found
# (list directories whose subdirectories are corpora, or directories containing a single corpus)
indexLocations:
- /data/index

# Directory containing each users' private corpora
# (only works if you've configured an authentication system, see below)
userIndexes: /data/user-index
```

### Search

This controls how BlackLab searches your corpora.
Note that these are BlackLab defaults, _not_ the defaults for the BlackLab Server parameters.
See the [parameters](#parameters) section for those.

```yaml
# Defaults for searching
search:

    # Collator to use for sorting, grouping, etc.
    # (default: language: en)
    collator:
        language: nl   # required
        country: NL    # optional
        #variant: x     # optional

    # Default number of words around hit.
    # (default: 5)
    contextSize: 5

    # The default maximum number of hits to retrieve (and use for sorting, grouping, etc.).
    # -1 means no limit, but be careful, this may stress your server.
    # (default: 5000000)
    maxHitsToRetrieve: 1000000
    
    # The default maximum number of hits to count.
    # -1 means no limit, but be careful, this may stress your server.
    # (default: 10000000)
    maxHitsToCount: -1
``` 

(this section can also occur in `blacklab.yaml`, to apply to QueryTool; see [Configuring other tools](#configuring-other-tools))

::: details Advanced search settings

You probably won't need to change these settings, but they are available for advanced users:

```yaml
search:
    # (...above settings...)

    # How eagerly to apply "forward index matching" to certain queries
    # [advanced technical setting; don't worry about this unless you want to experiment]
    # [if you want to disable forward index matching, which may be beneficial
    #  if you corpora are small and your query volume is high, set this to 0]
    # (default: 900)
    fiMatchFactor: 900

    # Enable result sets larger than 2^31?
    # If you don't need this, you can disable it for slightly better performance.
    # (default: true)
    enableHugeResultSets: true

```

:::

### Parameters

This section configures the default values and maximum values for various (`GET`/`POST`) parameters that can be used in search requests.

```yaml
# Defaults and maximum values for parameters
# (NOTE: some values will affect server load)
parameters:

    # What REST API version to attempt compatibility with.
    # Valid values are: 3, 4, current, experimental.
    # (default: current, i.e. 4 for BlackLab 4.x)
    api: current

    # Are searches case/accent-sensitive or -insensitive by default?
    defaultSearchSensitivity: insensitive

    # The maximum number of hits to process (return as results, and 
    # use for sorting, grouping, etc.). -1 means no limit.
    # ("maxretrieve" parameter)
    # (higher values will put more stress on the server)
    # (defaults: 5000000 / 5000000)
    processHits:
        default: 1000000
        max: 2000000

    # The maximum number of hits to count. -1 means no limit.
    # ("maxcount" parameter)
    # (higher values will put more stress on the server)
    # (defaults: 10000000 / 10000000)
    countHits:
        default: -1
        max: 10000000

    # Number of results per page ("number" parameter). -1 means no limit.
    # (a very high max value might lead to performance problems)
    # (defaults: 50 / 3000)
    pageSize:
        default: 50
        max: 3000

    # Context around match ("context" parameter)
    # (higher values might cause copyright issues and may stress the server)
    # Set to 0 to omit the left and right context altogether.
    # (defaults: 5 / 200)
    contextSize:
        default: 5
        max: 20
```

::: details Advanced parameters settings

You probably won't need to change these settings, but they are available for advanced users:

```yaml
parameters:
    # (...above settings...)

    # By default, should we include the grouped hits in
    # grouped responses? If false, just include group 
    # identity and size. Defaults to false. Can be overridden 
    # using the "includegroupcontents" URL parameter.
    # (default: false)
    writeHitsAndDocsInGroupedHits: false

    # If we're capturing part of our matches, should
    # we include empty captures? This can happen when the
    # clause to capture is optional, e.g. A:[]?
    # Defaults to false. Can be overridden using the 
    # "omitemptycaptures" URL parameter.
    # (default: false)
    omitEmptyCaptures: false
```

:::

### Protocol

These settings control how BlackLab Server responds to requests.

```yaml
# Settings related to BlackLab Server's protocol, i.e. requests and responses
protocol:

    # Value for the Access-Control-Allow-Origin HTTP header
    # See [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS) for more information.
    # (default: *, i.e. allow access from all origins)
    accessControlAllowOrigin: "*"

    # Default response type
    # (XML/JSON; default: XML)
    defaultOutputType: XML

    # If true, omits empty annotation values from XML results.
    # (default: false)
    omitEmptyProperties: false
```

### Authentication

You can configure an authentication system in BlackLab. This does not take care of log in, sign up, etc., but only instructs BlackLab how to find the currently logged-in user (if there is one). This is useful if you want users to be able to create private corpora.

To read the user id from a request header, attribute or parameter:

```yaml
authentication:
    system:
        class: AuthRequestValue
        # attribute|header|parameter
        type: attribute
        # name of the attribute, header or parameter that contains the user id
        name: userId
```

To use HTTP Basic Authentication (if enabled in `web.xml`, which it is not by default):

```yaml
authentication:
    system:
        class: AuthHttpBasic
```

For testing, `AuthDebugFixed` can be useful:

```yaml
authentication:
    system:
        class: AuthDebugFixed
        userId: me@example.com
```

With this, BlackLab will simply assume the specified user is always logged in. This is obviously not safe to use in production.

To enable private user corpora, you also need to specify a `userIndexes` directory in which each user will get a subdirectory containing their corpora. See [User-managed corpora](user-corpora.md) for more about this.

::: details Advanced authentication settings

There's one more setting that can occur under `authentication`:

```yaml
authentication:
    # (...above settings...)
    
    # This is an insecure way of authenticating to BlackLab Server by sending
    # two HTTP headers. It is only intended for testing purposes.
    # 
    # Choose a 'secret' password here. Then send your requests to BlackLab Server 
    # with the extra HTTP headers X-BlackLabAccessToken (the 'secret' password) and
    # X-BlackLabUserId (the user you wish to authenticate as).
    # 
    # Needless to say this method is insecure because it allows full access to
    # all users' corpora, and the access token could potentially leak to an
    # attacker.
    #
    # DO NOT USE EXCEPT FOR TESTING
    #debugHttpHeaderAuthToken: secret
```

:::

### Indexing

Indexing options. These apply to BlackLab Server only if private user corpora are enabled (i.e. if you have configured an [authentication](#authentication) system and `userIndexes` directory).

```yaml
# Options for indexing operations, if enabled
# (right now, in BLS, they're only enabled for logged-in users in
#  their own private area)
indexing:

    # Default number of threads to use for indexing operations
    # (more threads is faster, but uses more memory)
    # (default: 2)
    numberOfThreads: 2

    # Max. number of corpora per user
    # (only relevant if you've configured private corpora and authentication)
    # (default: 10)
    maxNumberOfIndicesPerUser: 10
```

(this section can also occur in `blacklab.yaml`, to apply to IndexTool; see [Configuring other tools](#configuring-other-tools))

For more about indexing, see [Indexing with BlackLab](/guide/index-your-data/create-an-index.md).

::: details Advanced indexing settings

You probably won't need to change these settings, but they are available for advanced users:

```yaml
indexing:
    # (...above settings...)
    
    # Should inline tags and relations be indexed case- and accent-sensitively?
    # This used to be the default, but we've switched over to case-insensitive
    # indexing by default. Set to true to revert to the old behavior.
    # (default: false)
    relationsSensitive: false

    # Are http downloads of e.g. metadata allowed?
    # (default: false)
    downloadAllowed: false

    # Where to cache downloaded files
    # (default: none)
    downloadCacheDir: /tmp/bls-download-cache

    # Max. size of entire download cache in MB
    # (default: 100)
    downloadCacheSizeMegs: 100

    # Max. size of single download in MB
    # (default: 100)
    downloadCacheMaxFileSizeMegs: 1

    # Max. number of zip files to keep opened
    # (useful if referring to external zip files containing metadata)
    # (default: 10)
    zipFilesMaxOpen: 10

    # Max. number of values to store per metadata field
    # (DEPRECATED, only applies to the older external index format)
    # (default: 50)
    maxMetadataValuesToStore: 100
```

:::

### Cache

BlackLab Server caches search results, so that if you run the same search again, it can return the results much faster.
Here you can configure the size of the cache, how long to keep results, etc.

You can try tweaking these settings depending on your server resources, corpus size and expected search volume. Be 
careful to leave enough memory for the operating system's disk cache, which can also help speed up searches. A general
rough guideline could be to try to keep half the system's memory available for the OS disk cache.

It is important to ensure that the JVM has the memory available to BlackLab Server that you want to use for caching. See [Memory usage](/server/#memory-usage).

```yaml
#  Settings for job caching.
cache:

    # How much free memory the cache should shoot for (in megabytes) while cleaning up.
    # Because we don't have direct control over the garbage collector, we can't reliably clean up until
    # this exact number is available. Instead we just get rid of a few cached tasks whenever a
    # new task is added and we're under this target number.
    targetFreeMemMegs: 100

    # The minimum amount of free memory required to start a new search task. If this memory is not available,
    # your search will be queued.
    minFreeMemForSearchMegs: 50
    
    # Maximum number of searches that may be queued. If you try to add another search, this will return an error.
    # Queued searches don't take up memory, but it's no use building up a huge queue that will take a very long time
    # to get through. Better to ask users to return when server load is lower.
    maxQueuedSearches: 20,

    # How long after it was last accessed will a completed search task be removed from 
    # the cache? (in seconds)
    # (don't set this too low; instead, set targetFreeMemMegs, the target amount of free memory)
    # If you want to disable the cache altogether, set this to 0.
    maxJobAgeSec: 3600

    # After how much time should a running search be aborted?
    # (larger values put stress on the server, but allow complicated searches to complete)
    maxSearchTimeSec: 300

    # How long the client may keep results we give them in their local (browser) cache.
    # This is used to write HTTP cache headers. Low values mean clients might re-request
    # the same information, making clients less responsive and consuming more network resources.
    # Higher values make clients more responsive but could cause problems if the data (or worse,
    # the protocol) changes after an update. A value of an hour or so seems reasonable.
    clientCacheTimeSec: 3600
```

::: details Advanced cache settings

You probably won't need to change these settings, but they are available for advanced users:

```yaml
cache:
    # (...above settings...)

    # If a search is aborted, how long should we keep the search in the cache to prevent
    # the client from resubmitting right away? A sort of "deny list" if you will.
    denyAbortedSearchSec: 600
    
    # Maximum number of cache entries to keep.
    # Please note that memory use per cache entry may vary wildly,
    # so you may prefer to use targetFreeMemMegs to set a "free memory goal"
    # and/or maxJobAgeSec to set a maximum age for cache entries.
    maxNumberOfJobs: 100
    
    # The cache implementation to use.
    # (fully-qualified class name or simple class name (if in package nl.inl.blacklab.server.search) 
    # of SearchCache subclass to instantiate)
    # The default is BlsCache. An alternative is ResultsCache, which is more
    # efficient if you have a large number of small, short-lived corpora.
    implementation: BlsCache
```

:::

### Performance

Advanced settings related to tuning server load and client responsiveness.

Only experiment with these settings if you run into performance issues or if you want to optimize your server for a specific use case.

```yaml
performance:

    # How many search tasks should be able to run simultaneously
    # (set this to take advantage of the cores/threads available to the machine;
    # probably don't set it any larger, as this won't help and might hurt)
    # Note that this is a rough guideline, not an absolute maximum number of threads
    # that will ever be running. New searches are only started (unqueued) if there's 
    # fewer than this number of threads running.
    # Don't set this lower than 4, as BlackLab executes parts of a search in separate
    # threads, so a single search often needs multiple threads.
    # Setting this to -1 will try to determine the optimal number of threads to
    # use: one less than the number of CPU cores, with a minimum of 4.
    # (default: -1, automatic)
    maxConcurrentSearches: 6

    # How many threads may a single search task use at most?
    # (lower values will allow more simultaneous searches to run;
    # higher values improve search performance, but will crowd out other searches.
    # e.g. if you set this to the same number as maxConcurrentSearches, a single 
    # search may queue all other searches until it's done)
    # Setting this to -1 will calculate the optimal number of threads to use:
    # The highest number that's no more than maxConcurrentSearches and no more 
    # than 6, but at least 2.
    # (default: -1, automatic)
    maxThreadsPerSearch: 3

    # Abhort a count if the client hasn't asked about it for 30s
    # (lower values are easier on the server, but might abort a count too soon)
    abandonedCountAbortTimeSec: 30
```

### Plugins

Plugins allow you to automatically convert files (e.g. .html, .docx) or apply linguistic tagging before indexing via BLS (experimental functionality).

For more information about plugins, see [Plugins for converting/tagging](/development/customization/plugins.md).

```yaml
plugins:

    # Should we initialize plugins when they are first used?
    # (plugin initialization can take a while; during development, delayed initialization is
    # often convenient, but during production, you usually want to initialize right away)
    # (default: false)
    delayInitialization: false

    # # Individual plugin configurations
    plugins:

        # Conversion plugin
        OpenConvert:
            jarPath: "/home/jan/projects/openconvert_en_tagger/OpenConvertMaven/target/OpenConvert-0.2.0.jar"

        # Tagging plugin
        DutchTagger:
            jarPath: "/home/jan/projects/openconvert_en_tagger/DutchTagger/target/DutchTagger-0.2.0.jar"
            vectorFile:  "/home/jan/projects/openconvert_en_tagger/tagger-data/sonar.vectors.bin"
            modelFile:   "/home/jan/projects/openconvert_en_tagger/tagger-data/withMoreVectorrs"
            lexiconFile: "/home/jan/projects/openconvert_en_tagger/tagger-data/spelling.tab"
```

(this section can also occur in `blacklab.yaml`, to apply to IndexTool; see [Configuring other tools](#configuring-other-tools))

### Debug

These settings determine who can see debug information, and if and how metrics are collected.

```yaml
# Settings for diagnosing problems
debug:
    #  A list of IPs that will run in debug mode.
    #  In debug mode, ...
    #  - the /cache-info resource show the contents of the job cache
    #    (other debug information resources may be added in the future)
    #  - output is prettyprinted by default (can be overriden with the prettyprint
    #    GET parameter)
    # (Default: "127.0.0.1", "0:0:0:0:0:0:0:1", "::1")
    addresses:
    - 127.0.0.1       #  IPv4 localhost
    - 0:0:0:0:0:0:0:1 #  IPv6 localhost

    # Ignore the debug IP list and always allow debug info?
    # (default: false)
    alwaysAllowDebugInfo: false

    # For advanced monitoring. See https://github.com/instituutnederlandsetaal/BlackLab/tree/dev/instrumentation
    metricsProvider: ''
    requestInstrumentationProvider: ''
```

### Log

Settings related to logging. Currently only used for debugging purposes, but may be useful if you want to see what BlackLab Server is doing internally.

```yaml
# Settings related to logging
log:

    # What subjects to log messages for
    # (all default to false)
    trace:
        # BL trace settings
        indexOpening: false
        optimization: true
        queryExecution: true

        # BLS trace settings
        cache: true
```

(this section can also occur in `blacklab.yaml`, to apply to Query-/IndexTool; see [Configuring other tools](#configuring-other-tools))

## Configuring other tools

`blacklab-server.yaml` is a specific configuration file for BlackLab Server. In some (fairly rare) situations, you may want to configure other BlackLab tools such as QueryTool and IndexTool. For this, you can create a file called `blacklab.yaml` in one of the locations mentioned above.

The `log`, `search`, `indexing` and `plugins` sections can occur in `blacklab.yaml`. The other sections are specific to BlackLab Server and should not be in `blacklab.yaml`.

If you're not sure, you probably don't need `blacklab.yaml`.

**NOTE:** if it exists, BlackLab Server will read `blacklab.yaml` first, and only then read `blacklab-server.yaml`. So settings in the latter can override settings in the former.
