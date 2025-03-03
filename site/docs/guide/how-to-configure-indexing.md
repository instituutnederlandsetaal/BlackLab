---
sidebarDepth: 2
---

# Input format configuration

An input format configuration file describes the structure of your documents so that BlackLab can index them.

They can be used to index data from the commandline using the [IndexTool](/guide/indexing-with-blacklab.md#index-supported-format) 
or using BlackLab Frontend (configured to allow users to upload and index their own corpora).

BlackLab already [supports](/guide/indexing-with-blacklab.md#supported-formats) a number of common input formats out of the box. Your data may differ slightly of course, so you may use the predefined formats as a starting point and customize them to fit your data.


## Basics

### A simple example

Let's see how to write a configuration file for a simple custom corpus format.

Suppose our tokenized XML files look like this:

```xml
<?xml version="1.0" ?>
<root>
    <document>
        <metadata id='1234'>
            <meta name='title'>How to configure indexing</meta>
            <meta name='author'>Jan Niestadt</meta>
            <meta name='description'>Shedding some light on this indexing business!</meta>
        </metadata>
        <text>
            <s>
                <w lemma='this' pos='PRO'>This</w>
                <w lemma='be' pos='VRB'>is</w>
                <w lemma='a' pos='ART'>a</w>
                <w lemma='test' pos='NOU'>test</w>.
            </s>
        </text>
    </document>
    <!-- ...more documents... -->
</root>
```

Below is the configuration file you would need to index files of this type. This uses [YAML](yaml.md), but you can also use JSON if you prefer.

Note that the settings with names ending in "Path" are XPath expressions (at least if you're parsing XML files - more on other file types later).

For an important note about XPath support, see [XPath support level](#xpath-support-level) below.


```yaml
## What element starts a new document?
documentPath: //document

## Annotated, CQL-searchable fields
annotatedFields:

  # Document contents
  contents:

    # What element (relative to documentPath) contains this field's contents?
    containerPath: text

    # What are our word tags? (relative to containerPath)
    wordPath: .//w

    # What annotation can each word have? How do we index them?
    # (annotations are also called "(word) properties" in BlackLab)
    # (valuePaths relative to wordPath)
    # NOTE: forEachPath is NOT allowed for annotations, because we need to know all annotations before indexing,
    #       and with forEachPath you could run in to an unknown new annotation mid-way through.
    annotations:

      # Text of the <w/> element contains the word form
      # (first annotation becomes the main annotation)
    - name: word
      valuePath: .
      sensitivity: sensitive_insensitive

      # lemma attribute contains the lemma (headword)
    - name: lemma
      valuePath: "@lemma"
      sensitivity: sensitive_insensitive

      # pos attribute contains the part of speech
    - name: pos
      valuePath: "@pos"

    # What tags occurring between the word tags do we wish to index? (relative to containerPath) 
    inlineTags:
      # Sentence tags
      - path: .//s

## Embedded metadata in document
metadata:

  # What element contains the metadata (relative to documentPath)
  containerPath: metadata

  # What metadata fields do we have?
  fields:

    # <metadata/> tag has an id attribute we want to index as docId
  - name: docId
    valuePath: "@id"

    # Each <meta/> tag corresponds with a metadata field
  - forEachPath: meta
    namePath: "@name"   # name attribute contains field name
    valuePath: .        # element text is the field value

corpusConfig:
  specialFields:
    # What metadata field persistently identifies our documents?
    pidField: docId
```

To use this configuration, you should save it with a name like `simple-input-format.blf.yaml` (`blf` stands for BlackLab Format) in either directory from which you will be using it, or alternatively one of `$BLACKLAB_CONFIG_DIR/formats/` (if this environment variable is set), `$HOME/.blacklab/formats/` or `/etc/blacklab/formats/`.

Please note that when declaring annotations, the first annotation you declare will become the _main annotation_. The main annotation will:

- be searched when omitting annotation name in CQL (e.g. search for `"ship"` and it searches the main annotation).
- be used to generate concordances (the KWIC view).
- be returned as the value (text content) of the `<w>` tag (in the XML response).

The rest of this page will address how to accomplish specific things with the input format configuration. For a more complete picture that can serve as a reference, see the [annotated input format configuration file example](#full-example-of-a-configuration-file).


### XPath support level

BlackLab supports two different XML processors: VTD and Saxon. While currently VTD is still the default, we would recommend Saxon for most users going forward.

VTD only supports XPath 1.0 and has some slight quirks (see below). Saxon uses more memory, but is often faster and supports XPath 3.1, which can make writing indexing configurations much easier.

Certain [advanced indexing features](https://github.com/INL/BlackLab/issues/447) added in the past can be avoided when using Saxon; many things can be done in XPath directly. See [XPath examples](xpath-examples.md) to get an idea of the wide range of possibilities.

To use Saxon, place this in your input format config (.blf.yaml) file (at the top level):

```yaml
processor: saxon
```

This works for the current development version and releases 4.0 and up.

::: details Using Saxon with BlackLab 3.0.1 and older

In older versions of BlackLab (release 3.0.1 and before), there is basic Saxon support, but there are quite a few features missing.

It also didn't support the top-level `processor` key shown above; if you do want to use Saxon on these older releases, use:

```yaml
fileType: xml
fileTypeOptions:
  processing: saxon   # (instead of vtd, which is the default)
```

:::

::: details Beware of VTD quirks

If you do stick with the default processor VTD instead of switching to Saxon, be aware that in rare cases, a correct XPath may produce unexpected results. This one for example: `string(.//tei:availability[1]/@status='free')`. There's often a workaround for this, in this case changing it to `string(//tei:availability[1]/@status='free')` might fix it (although of course this means something slightly different, so do check thoroughly).

A future version of BlackLab will change the default from VTD to Saxon.

:::


### Case- and diacritics sensitivity

You can also configure what "sensitivity alternatives" (case/diacritics sensitivity) to index for each annotation using the "sensitivity" setting:

```yaml
- name: word
  valuePath: .
  sensitivity: sensitive_insensitive
```

Valid values for sensitivity are:

* `sensitive` or `s`: case+diacritics sensitive only
* `insensitive` or `i`: case+diacritics insensitive only
* `sensitive_insensitive` or `si`: case+diacritics sensitive and insensitive
* `all`: all four combinations of case-sensitivity and diacritics-sensivity

What alternatives are indexed determines how specifically you can specify the desired sensitivity when searching. Each alternative increases index size.

If you don't configure an annotation's `sensitivity` parameter, it will default to `insensitive`.

There is one exception: annotations named `word` or `lemma` default to `sensitive_insensitive` for now. This special behaviour is deprecated though, and will be removed eventually. It's best to explicitly declare a `sensitivity` for these two annotations so this future change won't impact you.

### Spans (inline tags)

As you can see in the example above, we've configured an "inline tag" or span annotation. It captures the `<s/>` elements in the input document and indexes them as spans.

```yaml
# What tags occurring between the word tags do we wish to index? (relative to containerPath) 
inlineTags:
    # Sentence tags
    - path: .//s
```

This means we can later run searches like:

    "oak" "tree" within <s/>

(more about matching spans [here](corpus-query-language.md#matching-xml-elements))

There's a few additional parameters you can set for inline tags (provided your `.blf.yaml` file uses `processor: saxon` instead of the (current) default VTD):

```yaml
# What tags occurring between the word tags do we wish to index? (relative to containerPath) 
inlineTags:
    # Sentence tags
    - path: .//s
    - excludeAttributes:
        # Don't index unique ids unless you need them; 
        # they slow down indexing and searching and increase index size
        - "xml:id" 
    - path: .//p
      includeAttributes:
        - "type"  # Only index this tag's type attribute
        
      # Any extra attributes to index with this tag using XPath
      extraAttributes:
        # if e.g. input is <p xml:id="par-12">...</p> , index  number="12"
        - name: "number"
          valuePath: "substring-after(@xml:id, 'par-')"
          
    - path: .//ne
      displayAs: named-entity    # what CSS class to use (when using autogenerated XSLT)    
```

As you can see, `excludeAttributes` can be used to prevent the index size ballooning because of a unique id (although of course you won't be able to search sentences by their id anymore), and `displayAs` can be used to give the span a different CSS class in the generated XSLT (see [Automatic XSLT generation](#automatic-xslt-generation)).

`extraAttributes` can be used to add attributes to the tag that are not actually on the tag in the input document, by evaluating an XPath expression. In addition, you can also apply `process` steps to attributes this way. This can even be done on attributes that do appear on the tag in the input document.

### Document metadata

The basic overview (see above) included a way to index embedded metadata. Let's say this is our input file:

```xml
<?xml version="1.0" ?>
<root>
    <document>
        <text>
            <!-- ... document contents... -->
        </text>
        <metadata id='1234'>
            <meta name='title'>How to configure indexing</meta>
            <meta name='author'>Jan Niestadt</meta>
            <meta name='description'>Shedding some light on this indexing business!</meta>
        </metadata>
    </document>
</root>
```

To configure how metadata should be indexed, you can either name each metadata field you want to index separately, or you can use "forEachPath" to index a number of similar elements as metadata:

```yaml
## Embedded metadata in document
metadata:

  # What element contains the metadata (relative to documentPath)
  containerPath: metadata

  # What metadata fields do we have?
  fields:

    # <metadata/> tag has an id attribute we want to index as docId
  - name: docId
    valuePath: "@id"

    # Each <meta/> tag corresponds with a metadata field
  - forEachPath: meta
    namePath: "@name"   # name attribute contains field name
    valuePath: .        # element text is the field value
```

It's also possible to process metadata values before they are indexed (see [Processing values](#processing-values)), although it's often preferable to do as much processing as possible in XPath.


#### Tokenize or not?

By default, metadata fields are tokenized, but it can sometimes be useful to index a metadata field without tokenizing it. One example of this is a field containing the document id: if your document ids contain characters that normally would indicate a token boundary, like a period (.) , your document id would be split into several tokens, which is usually not what you want.

To prevent a metadata field from being tokenized:

```yaml
metadata:

  containerPath: metadata

  fields:

    # This field should not be split into words
  - name: docId
    valuePath: @docId
    type: untokenized
```


### Allow viewing documents

By default, BlackLab Server will not allow whole documents to be retrieved using `/docs/PID/contents`. This is to prevent accidentally distributing unlicensed copyrighted material.

You can allow retrieving whole documents by enabling the `corpusConfig.contentViewable` setting in the index format configuration file, or directly in the `indexmetadata.yaml` file in the index directory. Also see the next section.

This setting can also be changed for individual documents by setting a metadat field with the name `contentViewable` to `true` or `false`.




## Intermediate

### Handling Part of Speech features (subannotations)

Part of speech sometimes consists of several features in addition to the main PoS, e.g. "NOU-C(gender=n,number=sg)". It would be nice to be able to search each of these features separately without resorting to complex regular expressions. BlackLab supports subannotations to achieve this.

> Note that this feature is still (somewhat) experimental and details may change in future versions.

Suppose your XML looks like this:

```xml
<?xml version="1.0" ?>
<root>
    <document>
        <text>
            <w>
                <t>Veel</t>
                <pos class='VNW(onbep,grad)' head='ADJ'>
                    <feat class="onbep" subset="lwtype"/>
                    <feat class="grad" subset="pdtype"/>
                </pos>
                <lemma class='veel' />
            </w>
            <w>
                <t>gedaan</t>
                <pos class='WW(vd,zonder)' head='WW'>
                    <feat class="vd" subset="wvorm" />
                    <feat class="zonder" subset="buiging" />
                </pos>
                <lemma class="doen"/>
            </w>
        </text>
    </document>
</root>
```

Here's how to define subannotations:

```yaml
documentPath: //document
annotatedFields:
  contents:
    containerPath: text
    wordPath: .//w

    annotations:
    - name: word  # First annotation becomes the main annotation
      valuePath: t
      sensitivity: sensitive_insensitive
    - name: lemma
      valuePath: lemma/@class
      sensitivity: sensitive_insensitive
    - name: pos
      basePath: pos         # "base element" to match for this annotation.
                            # (other XPath expressions for this annotation are relative to this)
      valuePath: "@class"   # main value for the annotation
      subannotations:       # structure of each subannotation is the same as a regular annotation
      - name: head         
        valuePath: "@head"  # "main" part of speech is found in head attribute of <pos/> element

        # forEachPath will get the name and value of a set of annotations from just two xpaths.
        # However you still need to declare all names in this config!
        # If it encounters an unknown name a warning will be emitted.
      - forEachPath: "feat" # other features are found in <feat/> elements
        namePath: "@subset" # subset attribute contains the subannotation name
        valuePath: "@class" # class attribute contains the subannotation value
      # now declare the expected names. See the example document above.
      # the forEachPath makes it so we don't have to repeatedly set the valuePath with specific attribute qualifiers here.
      - name: lwtype
      - name: pdtype
      - name: wvorm
      - name: buiging

      # Fully written out the above is equal to:
      # If there are many of these qualifiers, the forEach construction will probably also perform a little better.
      - name: lwtype
        valuePath: feat[@subset='lwtype']
      - name: pdtype
        valuePath: feat[@subset='pdtype']
      - name: wvorm
        valuePath: feat[@subset='wvorm']
      - name: buiging
        valuePath: feat[@subset='buiging']

```

Adding a few subannotations per token position like this will make the index slightly larger, but it shouldn't affect performance or index size too much.


### Standoff annotations

Standoff annotations are annotations that are specified in a different part of the document.
For example:

```xml
<?xml version="1.0" ?>
<root>
    <document>
        <text>
            <w id='p1'>This</w>
            <w id='p2'>is</w>
            <w id='p3'>a</w>
            <w id='p4'>test</w>.
        </text>
        <standoff>
            <annotation ref='p1' lemma='this' pos='PRO' />
            <annotation ref='p2' lemma='be' pos='VRB' />
            <annotation ref='p3' lemma='a' pos='ART' />
            <annotation ref='p4' lemma='test' pos='NOU' />
        </standoff>
    </document>
</root>
```

To index these types of annotations, use a configuration like this one:

```yaml
documentPath: //document
annotatedFields:
  contents:
    containerPath: text
    wordPath: .//w
    
    # If specified, the token position for each id will be saved,
    # so you can index standoff annotations referring to this id later.
    tokenIdPath: "@id"

    annotations:
    - name: word  # First annotation becomes the main annotation
      valuePath: .
      sensitivity: sensitive_insensitive
    standoffAnnotations:
    - path: standoff/annotation      # Element containing what to index (relative to containerPath)
      tokenRefPath: "@ref" # What token position(s) to index these values at
                                     # (may have multiple matches per path element; values will 
                                     # be indexed at all those positions)
      annotations:           # The actual annotations (structure identical to regular annotations)
      - name: lemma
        valuePath: "@lemma"
        sensitivity: sensitive_insensitive
      - name: pos
        valuePath: "@pos"
```

::: tip Try using XPath instead

it is often also possible to achieve the same effect using XPath expressions in the valuePath of a regular annotation, espcially when using Saxon as your XML processor. Where possible, this is recommended.

This approach doesn't work for spans (inline tags) and relations though; read on for those.

:::

#### Standoff annotations for spans (inline tags)

The default standoff annotations as shown above apply an annotation to a single token (or several tokens, but each get the annotation value separately). What if instead you want to define a span of tokens?

(you can also do this with `inlineTags`, but that relies on the tags being part of the document contents, e.g. `<p/>` or `<s/>`, and prevents you from having partially overlapping spans)

This is possible using `spanStartPath`, `spanEndPath` and `spanNamePath` (instead of `tokenRefPath` used above). So to index this XML:

```xml
<doc>
    <w xml:id="w1">The</w>
    <w xml:id="w2">quick</w>
    <w xml:id="w3">brown</w>
    <w xml:id="w4">fox</w>
    <w xml:id="w5">jumps</w>
    <w xml:id="w6">over</w>
    ...
    <span from="w1" to="w4" type="animal" speed="fast" />
</doc>
```

You can use this `standoffAnnotations` configuration:

```yaml
tokenIdPath: "@xml:id"

standoffAnnotations:
- path: .//span
  spanStartPath: "@from"
  spanEndPath: "@to"
  spanEndIsInclusive: true
  spanNamePath: "@type"
  annotations:
    - name: speed
      valuePath: "@speed"
```

Note the setting `spanEndIsInclusive: true` to indicate that the `to` attribute refers to the last token of the span, not the first token _after_ the span. (`true` is the default value for this setting, but it is included here for completeness)

The above would allow you to search for `<animal/> containing "fox"` or `<animal speed="fast" />` to find "The quick brown fox".


#### Standoff annotations for relations

See [Indexing (dependency) relations](#indexing-dependency-relations) below.

#### Referring to inline anchors instead of words

Normally, standoff annotations refer to token ("word") ids, defined by the `tokenIdPath` setting at the annotated field level.

But what if your XML includes inline anchor tags between words that you want to
refer to? For example:

```xml
<doc>
    <anchor id="here" />
    <w xml:id="w1">The</w>
    <w xml:id="w2">quick</w>
    <w xml:id="w3">brown</w>
    <w xml:id="w4">fox</w>
    <anchor id="there" />
    <w xml:id="w5">jumps</w>
    <w xml:id="w6">over</w>
    ...
    <span from="here" to="there" type="animal" speed="fast" />
</doc>
```

Use this configuration for this situation:

```yaml
## Capture the anchor ids.
## (each anchor id will point to the token FOLLOWING the anchor!)
inlineTags:
  - path: ./anchor
    tokenIdPath: "@id"

standoffAnnotations:
- path: .//span
  spanStartPath: "@from"
  spanEndPath: "@to"
  spanEndIsInclusive: false
  spanNamePath: "@type"
  annotations:
    - name: speed
      valuePath: "@speed"
```

As you can see, we capture the id of the `anchor` tokens and refer to them the same way as word tokens (this does means that ids must be unique in the document!).

Note the use of `spanEndIsInclusive: false` because the anchor id that `to` refers to will point to the first token _after_ the span.

#### Standoff annotations without a unique token id

There is an alternate way of doing standoff annotations that does not rely on a unique token id like the method described above (although you will need some way to connect the standoff annotation to the word, obviously). This will probably be slower, but in some cases, it may be useful.

Let's say you want to index a color with every word, and your document looks like this:

```xml
<?xml version="1.0" ?>
<root>
    <colors>
        <color id='1'>blue</color>
        <color id='2'>green</color>
        <color id='3'>red</color>
    </colors>
    <document>
        <text>
            <w colorId='1'>This</w>
            <w colorId='1'>is</w>
            <w colorId='3'>a</w>
            <w colorId='2'>test</w>.
        </text>
    </document>
</root>
```

A standoff annotation of this type is defined in the same section as regular (non-standoff) annotations. It relies on capturing one or more values to help us locate the color we want to index at each position. These captured values are then substituted in the valuePath that fetches the color value:

```yaml
- name: color
  captureValuePaths:                  # value(s) we need from the current word to find the color
  - "@colorId"
  valuePath: /root/colors[@id='$1']   # how to get the value for this annotation from the document,
                                      # using the value(s) captured.
```

### Indexing (dependency) relations

::: tip Supported from v4.0
Indexing and searching relations will be supported from BlackLab 4.0 (and current development snapshots).
:::

It is also possible to index relations (such as dependency relations) using [standoff annotations](#standoff-annotations). Aside from using the built-in `conll-u` DocIndexer, or implementing your own DocIndexer, this is currently the only way to index relations in BlackLab. Standoff annotations make the most sense as relations don't just apply to a span of words, but connect two different words (or word groups).

Please note that the relations features only work with the newer integrated index type. This type is the default now, so you don't need to pass any extra options to BlackLab.

```xml
<doc>
    <s xml:id="s1">
        <w xml:id="w1">I</w>
        <w xml:id="w2">support</w>
        <w xml:id="w3">the</w>
        <w join="right" xml:id="w4">amendment</w>
        <pc xml:id="w5">.</pc>
        <linkGrp targFunc="head argument" type="UD-SYN">
            <link ana="ud-syn:nsubj" target="#w2 #w1"/>
            <link ana="ud-syn:root" target="#s1 #w2"/>
            <link ana="ud-syn:det" target="#w4 #w3"/>
            <link ana="ud-syn:obj" target="#w2 #w4"/>
            <link ana="ud-syn:punct" target="#w2 #w5"/>
        </linkGrp>
    </s>
</doc>
```

You can use this configuration:

```yaml
documentPath: //doc
processor: saxon  # required to index relations
namespaces:
    xml: http://www.w3.org/XML/1998/namespace
annotatedFields:
    contents:
        # Both <w/> and <pc/> tags should be indexed as separate token positions
        wordPath: .//w|.//pc

        # If specified, the token position for each id will be saved,
        # so you can index standoff annotations referring to this id later.
        tokenIdPath: "@xml:id"

        annotations:
        - name: word  # First annotation becomes the main annotation
          valuePath: .
          sensitivity: sensitive_insensitive

        standoffAnnotations:
        - path: .//linkGrp[@targFunc='head argument']/link
          type: relation
          relationClass: dep   # the class of relation we're indexing here
          valuePath: "replace(@ana, 'ud-syn:', '')"  # relation type
          # Note that we make sure the root relation is indexed without a source, 
          # which is required in BlackLab.
          sourcePath: "if (./@ana = 'ud-syn:root') then '' else replace(./@target, '^#(.+) .+$', '$1')"
          targetPath: "replace(./@target, '^.+ #(.+)$', '$1')"
```

The above would allow you to search for `_ -nsubj-> "I"` to find "I support", with the relation information captured. [Learn more about how to query relations](corpus-query-language.md#relations-querying)

A note about the `relationClass` setting: you should declare the type of relation you're indexing here, using a short (i.e. 3-letter) code. By convention, dependency relations should use `dep`. Clients such as [BlackLab Frontend](https://github.com/INL/corpus-frontend) can use this information to display relations in a more user-friendly way, i.e. referring to the _head_ and _dependent_ of the dependency relation instead of the more generic _source_ and _target_.

### Indexing parallel corpora

::: tip Supported from v4.0
Indexing and searching parallel corpora will be supported from BlackLab 4.0 (and current development snapshots).
:::

TODO: how to index parallel corpus

If everything worked, you should be able to search for `<s/> ==>nl <s/>` to find alignments per sentence between the English and Dutch versions. For more, see [parallel corpus querying](corpus-query-language.md#parallel-corpus-querying).

### More about document metadata

#### Default GUI widgets

In the `fields` section, you can specify `uiType` for each field to override the default GUI widget to use for the field. By default, fields that have only a few values will use `select`, while others will use `text`. There's also a `range` type for a range of numbers.

Example:

```yaml
metadata:
    fields:
    - name: author
      uiType: select
      
    - name: year
      uiType: range
      
    - name: genre
      uiType: text
```

#### Add a fixed metadata value to each document

You can add a field with a fixed value to every document indexed. This could be useful if you plan to add several data sets to one index and want to make sure each document is tagged with the data set name. To do this, simply specify `value` instead of `valuePath`.

```yaml
metadata:

  containerPath: metadata

  fields:

    # Regular metadata field    
  - name: author
    valuePath: author

    # Metadata field with fixed value
  - name: collection
    value: blacklab-docs
```

#### Linking to external document metadata

> **NOTE:** this is a rather complex and little-used feature. We may decide to deprecate or change this in the future. See if you can achieve your desired results using the `document()` function in XPath with the Saxon XML processor.

Sometimes, documents link to external metadata sources, usually using an ID. You can configure linking to external files using a top-level element `linkedDocuments`. If our data looks like this:

```xml
<?xml version="1.0" ?>
<root>
    <document>
        <text>
            <!-- ... document contents... -->
        </text>
        <externalMetadata id="54321" />
    </document>
</root>
```

And the metadata for this document can be found at http://example.com/metadata?id=54321, this is how to configure the document linking:

```yaml
## Any document(s) we also want to index while indexing this one
## Usually just our external metadata.
linkedDocuments:

  # Name for what this linked document represents; used to choose a field name
  # when storing the document. "metadata" is usually a good choice.
  metadata:
  
    # Should we store the linked document in our index?
    # (in this case, a field metadataCid will be created that contains a content
    #  store id, allowing you to fetch the original content of the document later)
    store: true

    # Values we need for locating the linked document
    # (matching values will be substituted for $1-$9 below)
    linkValues:
    
      # The value we need to determine the URL to our metadata
      # (relative to documentPath)
    - valuePath: externalMetadata/@id

    # How to fetch the linked input file containing the linked document.
    # File or http(s) reference. May contain $x (x = 1-9), which will be replaced 
    # with linkValue
    inputFile: http://example.com/metadata?id=$1

    # (Optional)
    # If the linked input file is an archive (zip is recommended because it allows 
    # random access), this is the path inside the archive where the file can be found. 
    # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
    #pathInsideArchive: some/dir/$1

    # Format identifier for indexing the linked file
    inputFormat: my-metadata-format

    # (Optional)
    # XPath to the (single) linked document to process.
    # If omitted, the entire file is processed, and must contain only one document.
    # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
    #documentPath: /root/metadata[@docId = $2]
```

As you can see, it's possible to use local files or files via http; you can use archives and specify how to find the relevant metadata inside the archive; and if the linked file contains the metadata for multiple documents, you can specify a path to the specific metadata for this document.

Linking to external files is mostly done to fetch metadata to accompany a "contents" file, but there's no reason why you couldn't turn the tables if you wanted, and index a set of metadata files that link to the corresponding "contents" file. The mechanism is universal; it would even be possible to link to a document that links to another document, although that may not be very useful.


### Corpus metadata

Each BlackLab corpus has its own metadata, recording information such as the time the index was generated and the BlackLab version used, plus information about annotations and metadata fields. 

Some of this information is generated as part of the indexing process, and some of the information is copied directly from the input format configuration file if specified. This information is mostly used by applications to learn about the structure of the corpus, get human-friendly names for the various parts, and decide what UI widget to show for a metadata field.

The best way to influence the corpus metadata is by including a special section `corpusConfig` in your format configuration file. This section may contains certain settings to be copied directly into the index file when it is created:

```yaml
    # The settings in this block will be copied into indexmetadata.yaml
    corpusConfig:
  
      # Some basic information about the corpus that may be used by a user interface.
      displayName: OpenSonar              # Corpus name to display in user interface
      description: The OpenSonar corpus.  # Corpus description to display in user interface
      contentViewable: false              # Is the user allowed to view whole documents? [false]
      textDirection: LTR                  # What's the text direction of this corpus? [LTR]

      # Metadata fields with a special meaning
      specialFields:
        pidField: id           # unique persistent identifier, used for document lookups, etc.
        titleField: title      # used to display document title in interface
        authorField: author    # used to display author in interface
        dateField: date        # used to display document date in interface
      
      # How to group metadata fields in user interface
      metadataFieldGroups:
      - name: First group      # Text on tab, if there's more than one group
        fields:                # Metadata fields to display on this tab
        - author
        - title
      - name: Second group
        fields:
        - date
        - keywords
```

If you add `addRemainingFields: true` to one of the groups, any field that wasn't explicitly listed will be added to that group.

There's also a complete [annotated index metadata file](#full-example-of-a-configuration-file) if you want to know more details about that.

There are also (hacky) ways to make changes to the corpus metadata after it was indexed: you can export the metadata to a file and re-import it later (older indexes had an external `indexmetadata.yaml` file that could be edited directly). Start the `IndexTool` with `--help` to learn more, but be careful, as it is easy to make the index unusable this way. 


### Reducing index size

The index for your corpus can get very large. One way to reduce the size is to disable the forward index for some annotations.

By default, all annotations get a forward index. The forward index is the complement to Lucene's reverse index, and can
quickly answer the question "what value appears in position X of document Y?". This functionality is used to generate
snippets (such as for keyword-in-context (KWIC) views), to sort and group based on context words (such as sorting on the word left of the hit) and will in the future be used to speed up certain query types.

However, forward indices take up a lot of disk space and can take up a lot of memory, and they are not always needed for every
annotation. You should probably have a forward index for at least the word annotation, and for any annotation you'd like to sort/group on or that you use heavily in searching, or that you'd like to display in KWIC views. But if you add an annotation that is only used in certain special cases, you can decide to disable the forward index for that annotation. You can do this by adding a setting named "forwardIndex" with the value "false" to the annotation config:

```yaml
- name: wordId
  valuePath: @id
  forwardIndex: false
```

A note about forward indices and indexing multiple values at a single corpus position: as of right now, the forward index will only store the first value indexed at any position. This is the value used for grouping and sorting on this annotation. In the future we may add the ability to store multiple values for a token position in the forward index, although it is likely that the first value will always be the one used for sorting and grouping.

Note that if you want KWICs or snippets that include annotations without a forward index (as well the rest of the original XML), you can switch to using the original XML to generate KWICs and snippets, at the cost of speed. To do this, pass `usecontent=orig` to BlackLab Server, or call `Hits.settings().setConcordanceType(ConcordanceType.CONTENT_STORE)`

### Full example of a configuration file

Here's a more-or-less complete overview of what settings can occur in an input format configuration file, with explanatory comments.

Input format configuration files should be named `<formatIdentifier>.blf.yaml` or `.blf.json` (depending on the format chosen). By default, BlackLab looks in `$BLACKLAB_CONFIG_DIR/formats/` (if the environment variable is defined), `$HOME/.blacklab/formats/` and `/etc/blacklab/formats/`. IndexTool searches a few more directories, including the current directory and the parent of the input and index directories.

```yaml
## For displaying in user interface (optional, recommended)
displayName: OpenSonar FoLiA content format

## For describing input format in user interface (optional, recommended)
description: The file format used by OpenSonar for document contents.

## What type of input files does this handle? (content, metadata?)
## (optional; not used by BlackLab; could be used in user interface)
type: content

## What XML processor to use
## (optional; current default is VTD, but Saxon is recommended because it supports 
##  XPath 3.1 and is faster. Future format file versions will probably default to Saxon)
## (omit this setting when parsing CSV/TSV or some other file type)
processor: saxon

## Each file type may have options associated with it (for now, only "tabular" does)
## We've shown the options for tabular he're but commented them out as we're describing
## an xml format here.
##fileTypeOptions:
##  type: tsv         # type of tabular format (tsv or csv)
##  delimiter: "\t"   # delimiter, if different from default (determined by "type", tab or comma)
##  quote: "\""       # quote character, if different from default (double quote)
##  inlineTags: false # are there inline tags in the file like in the Sketch Engine WPL format?
##  glueTags: false   # are there glue tags in the file like in the Sketch Engine WPL format?

## What namespaces do we use in our XPaths?
## (if omitted: ignore namespaces)
namespaces:
  '': http://ilk.uvt.nl/folia    # ('' -> default namespace)

## What element starts a new document?
## (the only absolute XPath; the rest is relative)
documentPath: //FoLiA

## Should documents be stores in the content store?
## This defaults to true, but you can turn it off if you don't need this.
store: false

## Annotated, CQL-searchable fields.
## We usually have just one, named "contents".
annotatedFields:

  # Configuration for the "contents" field
  contents:
  
    # How to display the field in the interface (optional)
    displayName: Contents

    # How to describe the field in the interface (optional)
    description: Contents of the documents.

    # What element (relative to document) contains this field's contents?
    # (if omitted, entire document is used)
    containerPath: text

    # What are our word tags? (relative to container)
    wordPath: .//w

    # If specified, a mapping from this id to token position will be saved, so we 
    # can refer back to it for standoff annotations later. (relative to wordPath)
    tokenIdPath: "@xml:id"

    # What annotation can each word have? How do we index them?
    # (annotations are also called "(word) properties" in BlackLab)
    # (valuePaths relative to word path)
    annotations:

    # First annotation is the main annotation
    - name: word
      displayName: Words in the text
      description: The word forms occurring in the document text.
      valuePath: t
      sensitivity: sensitive_insensitive  # sensitive|s|insensitive|i|sensitive_insensitive|si|all
                                          # (please explicitly declare this for at least "word" and 
                                          #  "lemma"; all other annotations will default to insensitive)
      uiType: text                        # (optional) hint for use interface
      forwardIndex: true                  # should this annotation get a forward index [true]

    - name: lemma
      valuePath: lemma/@class

      # An annotation can have subannotations. This may be useful for e.g.
      # part-of-speech features.
    - name: pos
      basePath: pos          # subsequent XPaths are relative to this
      valuePath: "@class"    # (relative to basePath)

      # Subannotations
      subannotations:

        # A single subannotation
      - name: head
        valuePath: "@head"   # (relative to basePath)

        # Multiple subannotations defined at once:
        # visits all elements matched by forEachPath and
        # indexes subannotations based on namePath and valuePath 
        # for each. Note that all subannotations MUST be declared
        # here as well, they just don't need a valuePath. If you
        # don't declare a subannotation, it will generate errors.
      - forEachPath: "feat"  # (relative to basePath)
        namePath: "@subset"  # (relative to forEachPath)
        valuePath: "@class"  # (relative to forEachPath)

    # Standoff annotations are annotations that are defined separately from the word
    # elements, elsewhere in the same document. To use standoff annotations, you must
    # define a tokenIdPath (see above). This will make sure you can refer back
    # to token positions so BlackLab knows at what position to index a standoff annotation.
    standoffAnnotations:
    - path: //timesegment               # Element containing the values to index
      tokenRefPath: wref/@id  # What token position(s) to index these values at
                                        # (these refer back to the tokenIdPath values)
      annotations:                      # Annotation(s) to index there
      - name: begintime
        valuePath: ../@begintime        # relative to path
      - name: endtime
        valuePath: ../@endtime

    # XML tags within the content we'd like to index
    # Any attributes are indexed automatically include/excludeAttributes is used.
    # (paths relative to container)
    inlineTags:
    - path: .//s
      excludeAttributes:
      - "xml:id" # Skip unique ids (slower, bigger index)
    - path: .//p
      includeAttributes:
      - "type"   # Only index the "type" attribute
    - path: .//ne
      displayAs: named-entity    # what CSS class to use (when using autogenerated XSLT)    

## (optional)
## Analyzer to use for metadata fields if not overridden
## (default|standard|whitespace|your own analyzer)
metadataDefaultAnalyzer: default


## Embedded metadata
## (NOTE: shown here is a simple configuration with a single "metadata block";
##  however, the value for the "metadata" key may also be a list of such blocks.
##  this can be useful if your document contains multiple areas with metadata 
##  you want to index)
metadata:

  # Where the embedded metadata is found (relative to documentPath)
  containerPath: metadata[@type='native']

  # How each of the metadata fields can be found (relative to containerPath)
  fields:

    # Single metadata field
  - name: author
    valuePath: author    # (relative to containerPath)

    # Multiple metadata fields defined at once:
    # visits all elements matched by forEachPath and
    # adds a metadata entry based on namePath and 
    # valuePath for each)
  - forEachPath: meta    # (relative to containerPath)
    namePath: "@id"      # (relative to forEachPath)
    valuePath: .         # (relative to forEachPath)
    

## (optional)
## It is possible to specify a mapping to change the name of
## metadata fields. This can be useful if you capture a lot of
## metadata fields using forEachPath and want control over how they
## are indexed.    
indexFieldAs:
  lessThanIdealName: muchBetterName
  alsoNotAGreatName: butThisIsExcellent


## Linked metadata (or other linked document)
linkedDocuments:

  # What does the linked document represent?
  # (this is used internally to determine the name of the field to store content store id in)
  metadata:

    # Should we store the linked document?
    store: true

    # Values we need to locate the linked document
    # (matching values will be substituted for $1-$9 below - the first linkValue is $1, etc.)
    linkValues:
    - valueField: fromInputFile       # fetch the "fromInputFile" field from the Lucene doc

      # We process the raw value:
      # - we replace backslashes with forward slashes
      # - we keep only the last two path parts (e.g. /a/b/c/d --> c/d)
      # - we replace .folia. with .cmdi.
      # (processing steps like these can also be used with metadata fields and annotations!
      #  see elsewhere for a list of available processing steps)
      process:
        # Normalize slashes
      - action: replace
        find: "\\\\"
        replace: "/"
        # Keep only the last two path parts (which indicate location inside metadata zip file)
      - action: replace
        find: "^.*/([^/]+/[^/]+)/?$"
        replace: "$1"
      - action: replace
        find: "\\.folia\\."
        replace: ".cmdi."

    # How to fetch the linked input file containing the linked document
    # (file or http(s) reference)
    # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
    inputFile: /molechaser/data/opensonar/metadata/SONAR500NEW.zip

    # (Optional)
    # If the linked input file is an archive, this is the path inside the archive where the file can be found
    # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
    pathInsideArchive: SONAR500/DATA/$1

    # (Optional)
    # XPath to the (single) linked document to process.
    # If omitted, the entire file is processed, and must contain only one document.
    # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
    #documentPath: /CMD/Components/SoNaRcorpus/Text[@ComponentId = $2]

    # Format identifier of the linked input file
    inputFormat: OpenSonarCmdi

## Configuration to be copied into indexmetadata.yaml when a new index is created
## from this format. These settings do not influence indexing but are for 
## BlackLab Server and search user interfaces. All settings are optional.
corpusConfig:

    # Display name for the corpus
    displayName: My Amazing Corpus
    
    # Short description for the corpus 
    description: Quite an amazing corpus, if I do say so myself.

    # Is the user allowed to view whole documents in the search interface?
    # (used by BLS to either allow or disallow fetching full document content)
    # (defaults to false because this is not allowed for some datasets)
    contentViewable: true
    
    # Text direction of this corpus (e.g. "LTR", "left-to-right", "RTL", etc.).
    # (default: LTR)
    textDirection: LTR
    
    # You can divide annotations for an annotated field into groups, which can
    # be useful if you want to display them in a tabbed interface.
    # Our corpus frontend uses this setting.
    annotationGroups:
      contents:
      - name: Basic
        annotations:
        - word
        - lemma
      - name: Advanced
        annotations:
        - pos
        addRemainingAnnotations: true

    # You can divide your metadata fields into groups, which can
    # be useful if you want to display them in a tabbed interface.
    # Our corpus frontend uses this setting.
    metadataFieldGroups:
    - name: Tab1
      fields:
      - Field1
      - Field2
    - name: Tab2
      fields:
      - Field3
      - Field4
    - name: OtherFields
      addRemainingFields: true  # BLS will add any field not yet in 
                                # any group to this group   
    
    # (optional, but pidField is highly recommended)
    # You can specify metadata fields that have special significance here.
    # pidField is important for use with BLS because it guarantees that URLs
    # won't change even if you re-index. The other fields can be nice for
    # displaying document information but are not essential.
    specialFields:
      pidField: id         # unique document identifier. Used by BLS for persistent URLs
      titleField: title    # may be used by user interface to display document info
      authorField: author  # may be used by user interface to display document info
      dateField: pubDate   # may be used by user interface to display document info
```



## Advanced




### Unicode normalization

Unicode normalization refers to the process of converting different ways of encoding the same character to a single, canonical form. For example, the character `é` can be encoded as a single character `é` (U+00E9), or as a combination of `e` (U+0065) and `´` (U+00B4).

BlackLab's builtin indexers should automatically normalize to NFC (Normalization Form Canonical Composition). This should prevent any issues when sorting or grouping.

[More about Unicode equivalence and normal forms](https://en.wikipedia.org/wiki/Unicode_equivalence)

### Automatic XSLT generation

If you're creating your own corpora by uploading data to corpus-frontend, you want to be able to view your documents as well, without having to write an XSLT yourself. BlackLab Server can generate a default XSLT from your format config file. However, because BlackLab is a bit more lenient with namespaces than the XSLT processor that generates the document view, the generated XSLT will only work correctly if you take care to define your namespaces correctly in your format config file.

IMPORTANT: generating the XSLT might not work correctly if your XML namespaces change throughout the document, e.g. if you declare local namespaces on elements, instead of 

Namespaces can be declared in the top-level "namespaces" block, which is simply a map of namespace prefix (e.g. "tei") to the namespace URI (e.g. `http://www.tei-c.org/ns/1.0`). So for example, if your documents declare namespaces as follows:

```xml
<doc xmlns:my-ns="http://example.com/my-ns" xmlns="http://example.com/other-ns">
...
</doc>
```
  
Then your format config file should contain this namespaces section:

```yaml
namespaces:
  '': http://example.com/other-ns    # The default namespace
  my-ns: http://example.com/my-ns
```

If you forget to declare some or all of these namespaces, the document might index correctly, but the generated XSLT won't work and will likely show a message saying that no words have been found in the document. Updating your format config file should fix this; re-indexing shouldn't be necessary, as the XSLT is generated directly from the config file, not the index.

### Multiple values at one position

Standoff annotations (see below) provide a way to index additional values at the same token position. But it is also possible to just index several values for any regular annotation, such as multiple lemmatizations or multiple possible part of speech tags.

If your data looks like this:

```xml
<?xml version="1.0" ?>
<root>
    <document>
        <text>
            <w>
                <t>Helo</t>
                <lemma class='hello' />
                <lemma class='halo' />
            </w>
            <w>
                <t>wold</t>
                <lemma class="world"/>
                <lemma class="would"/>
            </w>
        </text>
    </document>
</root>
```

You can index all the values for lemma at the same token position like this:

```yaml
annotatedFields:
  contents:
    containerPath: text
    wordPath: .//w
    annotations:
    - name: word    # First annotation becomes the main annotation
      valuePath: t
      sensitivity: sensitive_insensitive
    - name: lemma
      valuePath: lemma
      multipleValues: true
      sensitivity: sensitive_insensitive
```

If you don't specify multipleValues, only the first value will be used. The reason you explicitly have to specify it is that this is relatively rare and could slow down the indexing process if automatically applied to all annotations.

When indexing multiple values at a single position, it is possible to match the same value multiple times, for example when creating an annotation that combines word and lemma (useful for simple search). This would lead to duplicate matches. If this is not what you want, you can set `allowDuplicateValues` to false.  
Note that duplicates are checked case-insensitive. The value in the index will keep its capitalization however.

```yaml
    - name: word_and_lemma
      valuePath: word or lemma
      multipleValues: true
      allowDuplicateValues: false 
```

Multiple value annotations also work for tabular formats like csv, tsv or sketch-wpl. You can specify a regular expression to use for splitting a column value into multiple values. The default is a semicolon `;`. You can change it as follows:

```yaml
    fileType: tabular
    fileTypeOptions:
      type: tsv
      multipleValuesSeparator: "/"
```
### Indexing raw XML

An annotation can optionally capture the raw xml content:
```yaml
    - name: word_xml
      valuePath: .
      captureXml: true
```

### Indexing CSV/TSV files

BlackLab works best with XML files, because they can contain any kind of (sub)annotations, (embedded or linked) metadata, inline tags, and so on. However, if your data is in a non-XML type like CSV, TSV or plain text, and you'd rather not convert it, you can still index it.

For CSV/TSV files, indexing them directly can be done by defining a tabular input format. These are "word-per-line" (WPL) formats, meaning that each line will be interpreted as a single token. Annotations simply specify the column number (or column name, if your input files have them).

(Technical note: BlackLab uses [Apache commons-csv](https://commons.apache.org/proper/commons-csv/) to parse tabular files. Not all settings are exposed at the moment. If you find yourself needing access to a setting that isn't exposed via de configuration file yet, please let us know)

Here's a simple example configuration, `my-tsv.blf.yaml`, that will parse tab-delimited files produced by the [Frog](https://languagemachines.github.io/frog/) tool:

```yaml
fileType: tabular

## Options for tabular format
fileTypeOptions:

  # TSV (tab-separated values) or CSV (comma-separated values, like Excel)
  type: tsv

  # Does the file have column names in the first line? [default: false]
  columnNames: false
  
  # The delimiter character to use between column values
  # [default: comma (",") for CSV, tab ("\t") for TSV]
  delimiter: "\t"
  
  # The quote character used around column values (where necessary)
  # [default: disable quoting column values]
  quote: "\""
  
annotatedFields:
  contents:
    annotations:
    - name: word  # First annotation becomes the main annotation
      valuePath: 2    # (1-based) column number or column name (if file has them) 
      sensitivity: sensitive_insensitive
    - name: lemma
      valuePath: 3
      sensitivity: sensitive_insensitive
    - name: pos
      valuePath: 5
```

(Note that the BlackLab JAR includes a default `tsv.blf.yaml` that is a bit different: it assumes a file containing column names. The column names are word, lemma and pos)

The Sketch Engine takes a tab-delimited WPL input format that document tags, inline tags and "glue tags" (which indicate that there should be no space between two tokens). Here's a short example:

```
<doc id="1" title="Test document" author="Jan Niestadt"> 
<s> 
This    PRO     this
is      VRB     be
a       ART     a
test    NOU     test
<g/>
.       SENT    .
</s>
</doc>  
```

Here's a configuration to index this format (`sketch-wpl.blf.yaml`, already included in the BlackLab JAR):

```yaml
fileType: tabular
fileTypeOptions:
  type: tsv
  
  # allows inline tags such as in Sketch WPL format
  # all inline tags encountered will be indexed
  inlineTags: true  
                    
  # interprets <g/> to be a glue tag such as in Sketch WPL format
  glueTags: true
  
  # If the file includes "inline tags" like <p></p> and <s></s>,
  # (like for example the Sketch Engine WPL format does)
  # is it allowed to have separated characters after such a tag?
  # [default: false]
  allowSeparatorsAfterInlineTags: false 
  
documentPath: doc   # looks for document elements such as in Sketch WPL format
                    # (attributes are automatically indexed as metadata)
annotatedFields:
  contents:
    annotations:
    - name: word  # First annotation becomes the main annotation
      valuePath: 1
      sensitivity: sensitive_insensitive
    - name: lemma
      valuePath: 3
      sensitivity: sensitive_insensitive
    - name: pos
      valuePath: 2
```

If one of your columns contains multiple values, for example multiple alternative lemmatizations, set the `multipleValues` option for that annotation to true and specify a regular expression to use for splitting a column value into multiple values in the `fileTypeOptions`. The default is a semicolon `;`. See also [here](#multiple-values-at-one-position).

```yaml
fileType: tabular
fileTypeOptions:
  type: tsv
  multipleValuesSeparator: "/"
```

If you want to index metadata from another file along with each document, you have to use `valueField` in the `linkValues` section (see <a href='#metadata-external'>below</a>). In the SketchWPL case, in addition to `fromInputFile` you can also use any document element attributes, because those are added as metadata fields automatically. So if the document element has an `id` attribute, you could use that as a `linkValue` to locate the metadata file.

### Indexing plain text files

Plain text files don't allow you to use a lot of BlackLab's features and hence don't require a lot of configuration either. If you need specific indexing features for non-tabular, non-XML file formats, please let us know and we will consider adding them. For now, here's how to configure a plain text input format (`txt.blf.yaml`, included in the BlackLab JAR):

```yaml
fileType: text

annotatedFields:
  contents:
    annotations:
    - name: word
      valuePath: .
      sensitivity: sensitive_insensitive
```

Note that a plain text format may only have a single annotated field. You cannot specify containerPath or wordPath. For each annotation you define, valuePath must be "." ("the current word"), but you can specify different processing steps for different annotations if you want.

There is one way to index metadata information along with plain text files, which is to look up the metadata based on the input file. The example below uses processing steps; see the relevant section below, and see the section on linking to external files for more information on that subject.

To index metadata information based on the input file path, use a section such as this one:

```yaml
linkedDocuments:
  metadata:
    store: true   # Should we store the linked document?

    # Values we need for locating the linked document
    # (matching values will be substituted for $1-$9 below)
    linkValues:
    - valueField: fromInputFile       # fetch the "fromInputFile" field from the Lucene doc
                                      # (this is the original path to the file that was indexed)
      process:
        # Normalize slashes
      - action: replace
        find: "\\\\"
        replace: "/"
        # Keep only the last two path parts (which indicate location inside metadata zip file)
      - action: replace
        find: "^.*/([^/]+/[^/]+)/?$"
        replace: "$1"
      - action: replace
        find: "\\.txt$"
        replace: ".cmdi"
    #- valueField: id                 # plain text has no other fields, but TSV with document elements
                                      # could, and those fields could also be used (see documentPath 
                                      # below)

    # How to fetch the linked input file containing the linked document.
    # File or http(s) reference. May contain $x (x = 1-9), which will be replaced 
    # with (processed) linkValue
    inputFile: http://server.example.com/metadata.zip

    # (Optional)
    # If the linked input file is an archive (zip is recommended), this is the path 
    # inside the archive where the file can be found. May contain $x (x = 1-9), which 
    # will be replaced with (processed) linkValue
    pathInsideArchive: some/dir/$1

    # Format of the linked input file
    inputFormat: cmdi

    # (Optional)
    # XPath to the (single) linked document to process.
    # If omitted, the entire file is processed, and must contain only one document.
    # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
    #documentPath: /root/metadata[@docId = $2]
```

### Indexing other files

For some types of files it is possible to automatically convert them to another file type that can be indexed.   
Support for this feature works through plugins and is still experimental.

Add the following lines to your configuration file to convert your files before indexing them according to the rest of the configuration.

```yaml
convertPlugin: OpenConvert
tagPlugin: DutchTagger
```

This setup will convert `doc, docx, txt, epub, html, alto, rtf and odt` into `tei`.


This will however not work until you provide the right .jar and data files to the plugins. Adding the following configuration to `blacklab-server.yaml` will enable the plugins to do their work.

```yaml
plugins:
  OpenConvert:
    jarPath: /path/to/OpenConvert-0.2.0.jar
  DutchTagger:
    jarPath: /path/to//DutchTagger-0.2.0.jar
    vectorFile: /path/to/duthtagger/data/vectors.bin
    modelFile: /path/to/dutchtagger/model
    lexiconFile: /path/to/dutchtagger/lexicon.tab
```

Currently the files and exact version of OpenConvert are not publically available, but look at the [plugins](/development/customization/plugins.md) page for more information on how write your own plugin.

### Processing values

> **NOTE:** when using Saxon as your XML processor, you can usually achieve the same results using XPath expressions, and this is the recommended approach. See [XPath examples](xpath-examples.md) for some examples.

It is often useful to do some simple processing on a value just before it's added to the index. This could be a simple search and replace, or combining two fields into one for easier searching, etc. Or you might want to map a whole collection of values to different values. Both are possible.

To perform simple value mapping on a metadata field, add the "mapValues" key to its config, like this:

```yaml
metadata:
  containerPath: metadata
  fields:
  - name: speciesGroup
    valuePath: species
    
    # Map (translate) values (key will be translated to corresponding value)
    # In this example: translate species to the group they belong to
    mapValues:
      dog: mammals
      cat: mammals
      shark: fish
      herring: fish
      # etc.
```

To perform string processing on (standoff) (sub)annotations, metadata values, and linkValues (in the linked document section, see "Linking to external (metadata) files").

For example, to process a metadata field value, simply add a "process" key with a list of actions to perform, like so:

```yaml
metadata:
  containerPath: metadata
  fields:
  - name: author
    valuePath: author
    
    # Do some processing on the contents of the author element before indexing
    process:
    
      # If empty, set a default value
      # (note that this could also be achieved using unknownCondition/unknownValue)
    - action: default
      value: "(unknown)"
                          
      # Normalize spaces
    - action: replace
      find: "\\s\\s+"
      replace: " "
```

These are all the available generic processing steps:

- `replace(find, replace)`: do a regex search for 'find' and replace each match with 'replace'. Group references may be used. An optional parameter `keep` can be set to `all` to keep both  the original strings and the results after applying the replace operation (annotation must have `multipleValues: true` for this to work).
- `default(value)` or `default(field)`: if the field is empty, set its value to either the specified value or the value of the specified field. If you refer to a field, make sure it is defined before this field (fields are processed in order).
- `append(value)` or `append(field)`: append the specified value or the value of the specified field, using a space as the separator character. You may also specify a different `separator` is you wish, including the empty string (`""`).
- `split(separator, keep)`: split the field's value on the given separator and keep only the part indicated by `keep` (0-based). If `keep` is omitted, keep the first part. If `separator` is omitted, use `;`.
  Note that the separator is a regex, and to split on special characters, those should be escaped by using a double backslash (`\\`).  
  `Keep` also allows two special values: `all` to keep all splits (instead of one the one at an index), and `both` to keep both the unsplit value as well as all the split parts. For `both` to work, the annotation should also specify `multipleValues` to be true.
- `strip(chars)`: strip specified chars from beginning and end. If `chars` is omitted, use space.

These processing steps are more specific to certain data formats:
- `parsePos(posExpr, fieldName)`: parse common part of speech expressions of the form `A(b=c,d=e)` where A is the main part of speech (e.g. 'N' for noun), and b=c is a part of speech feature such as number=plural, etc. If you don't specify field (or specify an underscore _ for field), the main part of speech is extracted. If you specify a feature name (e.g. "number"), that feature is extracted.
- `chatFormatAgeToMonths(chatFormatAge)`: convert age as reported in CHAT format to number of months
- `concatDate`: concatenate 3 separate date fields into one, substituting unknown months and days with the first or last possible value. The output format is YYYYMMDD. Numbers are padded with leading zeroes.
  Requires 4 arguments:
  `yearField`: the metadata field containing the numeric year
  `monthField`: the metadata field containing the numeric month (so "12" instead of "december" or "dec")
  `dayField`: the metadata field containing the numeric day
  `autofill`: `start` to autofill missing month and day to the first possible value (01), or `end` to autofill the last possible value (12 for months, last day of the month in that year for days - takes in to account leap years).
  This step requires that at least the year is known. If the year is not known, no output is generated.

If you would like a new processing step to be added, please let us know.

**NOTE:** value mapping using `mapValues` is applied *after* any processing steps.


### Extending formats (deprecated)

> **NOTE: THIS FUNCTIONALITY IS DEPRECATED** <br/>
> Don't rely on this feature as it will be removed in a future version. Instead, simply copy the format file and make any changes you need.

It is possible to extend an existing format. This is done by specifying the "baseFormat" setting at the top-level. You should set it to the name of the format you wish to extend.

It matters where baseFormat is placed, as it effectively copies values from the specified format when it is encountered. It's usually best to specify baseFormat somewhere at the top of the file. You can put it after 'name' and 'description' if you wish, as those settings are not copied.

To be precise, setting baseFormat does the following:

- copy type, fileType, documentPath, store, metadataDefaultAnalyzer
- copy the corpusConfig settings
- add all fileTypeOptions
- add all namespace declarations
- add all indexFieldAs entries
- add all annotatedFields entries
- add all metadata entries
- add all linkedDocument entries

In other words: setting a base format allows you to add or change file type options, namespace declarations, indexFieldAs entries, annotated fields or linked documents. You can also add (embedded) metadata sections.

Note that most blocks are not "merged": if you want to change annotated field settings, you will have to redefine the entire annoted field in the "derived" configuration file; you can't just specify the setting you wish to override for that field. It is also not possible to make changes to existing metadata sections.

