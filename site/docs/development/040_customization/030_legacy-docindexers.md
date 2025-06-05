# Write a DocIndexer (legacy)

The following information is only relevant if you use the legacy DocIndexers instead of an input format configuration file (`.blf.yaml`).

## Passing indexing parameters

You can pass parameters to the DocIndexer class to customize its indexing process. This can be done in two ways: as options on the IndexTool command line, or via a properties file.

(if you use a [configuration file](/guide/how-to-configure-indexing.md) to index your files, you don't need this)

To pass a parameter as an option on the command line, use three dashes, like this:

    java -cp "blacklab.jar:lib/*" nl.inl.blacklab.tools.IndexTool create ---PARAMNAME PARAMVALUE ...

To pass parameters via a property file, use the --indexparam option:

    java -cp "blacklab.jar:lib/*" nl.inl.blacklab.tools.IndexTool create --indexparam PROPERTYFILE ...

**NOTE:** in addition to the --indexparam parameter, IndexTool will always look for a file named indexer.properties in the current directory, the input directory, the parent of the input directory, or the index directory. If the file is found in any of these locations, it is read and the values are used as indexing parameters. 

Use the DocIndexer.getParameter(name[, defaultValue]) method to retrieve parameters inside your DocIndexer. For an example of using DocIndexer parameters, see the DocIndexerTeiBase and MetadataFetcherSonarCmdi in the nl.inl.blacklab.indexers package. They use parameters to configure annotation names and where to fetch metadata from, respectively.

Configuring an external metadata fetcher (see "Metadata from an external source" below) and case- and diacritics sensitivity (see next section) is also done using a indexing parameter for now. Note that this parameter passing mechanism predates the index structure file (see "Configuring the index structure" below) and is likely to be deprecated in favour of that in future versions.

## Configuring case- and diacritics sensitivity per annotation

(if you use a [configuration file](/guide/how-to-configure-indexing.md) to index your files, you can specify this there)

You can also configure what "sensitivity alternatives" (case/diacritics sensitivity) to index for each annotation, using the "PROPNAME_sensitivity" parameter. Accepted values are "i" (both only insensitive), "s" (both only sensitive), "si" (sensitive and insensitive) and "all" (case/diacritics sensitive and insensitive, so 4 alternatives). What alternatives are indexed determines how specifically you can specify the desired sensitivity when searching.

If you don't configure these, BlackLab will pick (hopefully) sane defaults (i.e. word/lemma get "si", punct gets "i", _relation/starttag gets "s", others get "i"). (CAUTION: the special behaviour for word/lemma is deprecated. Please explictly configure the sensitivity for these annotations. Eventually they too will default to "i")

## Configuring the index structure

(if you use a [configuration file](/guide/how-to-configure-indexing.md) to index your files, you can specify all these settings there, 
so you don't need a separate index structure file)

What we call the "index structure" consists of some top-level index information (name, description, etc.), what word-level annotations 
(formerly called "properties") you want to index, what metadata fields there are and how they should be indexed, and more.

By default, a default index structure is determined by BlackLab and the DocIndexer you're using. However, you can influence exactly 
how your index is created using a customized index structure file. If you specify such an index structure file when creating the 
index, it will be used as a template for the index metadata file, and so you won't have to specify the index structure file again 
when updating your index later; all the information is now in the index metadata file. It is possible to edit the index metadata 
file manually as well, but use caution, because it might break something.

To use a custom indextemplate.yaml or indextemplate.json when creating an index, make sure the file is present either in the input 
directory, or in the parent directory of the input directory. IndexTool will automatically detect and use it. The resulting index 
metadata file will be saved in the index directory. 

There's a [commented example of indexstructure.yaml](/guide/how-to-configure-indexing.md#edit-index-metadata).

Please note: the settings pidField, titleField, authorField, dateField refer to the name of the field in the Lucene index, not an XML element name.

## When and how to disable the forward index for an annotation

(if you use a [configuration file](/guide/how-to-configure-indexing.md) to index your files, you can specify this there)

By default, all properties get a forward index. The forward index is the complement to Lucene's reverse index, and can 
quickly answer the question "what value appears in position X of document Y?". This functionality is used to generate
snippets (such as for keyword-in-context (KWIC) views), to sort and group based on context words (such as sorting on the word left of the hit) and will in the future be used to speed up certain query types.

However, forward indices take up a lot of disk space and can take up a lot of memory, and they are not always needed for every 
annotation. You should probably have a forward index for at least the word and punct annotations, and for any annotation you'd like to sort/group on or that you use heavily in searching, or that you'd like to display in KWIC views. But if you add an annotation that is only used in certain special cases, you can decide to disable the forward index for that annotation. You can do this by adding the annotation name to the "noForwardIndexProps" space-separated list in the indextemplate.json file shown above.

A note about forward indices and indexing multiple values at a single corpus position: as of right now, the forward index will only store the first value indexed at any position. We would like to expand this so that it is possible to quickly retrieve all values indexed at a corpus position, but that is not the case now.

Note that if you want KWICs or snippets that include annotations without a forward index (as well the rest of the original XML), you can switch to using the original XML to generate KWICs and snippets, at the cost of speed. To do this, pass `usecontent=orig` to BlackLab Server, or call `Hits.settings().setConcordanceType(ConcordanceType.CONTENT_STORE)`.

## Custom DocIndexers

### Indexing word-annotated XML

NOTE: an easier way of doing this is to [write a configuration file](/guide/how-to-configure-indexing.md) instead of Java code.

If you really want to implement your own DocIndexer class, see this [example](docindexer.md).

If you have an XML format in which each word has its own XML tag, containing any annotations, you should derive your DocIndexer class from DocIndexerXmlHandlers. This class allows you to add 'hooks' for handling different XML elements.

The constructor of your indexing class should call the superclass constructor, declare any annotations (e.g. lemma, pos) you're going to index and finally add hooks (called handlers)  for each XML element you want to do something with.

Declaring annotations (formerly called "properties" in BlackLab) is done using the DocIndexerXmlHandlers.addAnnotation(String). Store the result in a final variable so you can access it from your custom handlers. Note that the "main annotation" (usually called "word") and the "punct" annotation (whitespace and punctuation between words) have already been created by DocIndexerXmlHandlers; retrieve them using the mainAnnotation() and punctAnnotation() methods.

Adding a handler is done using the DocIndexerXmlHandlers.addHandler(String, ElementHandler) method. The first parameter is an xpath-like expression that indicates the element to handle. The expression looks like xpath but is very limited: only element names separated by slashes; it may start with either a single (absolute path) or a double (relative path) slash. DocIndexerXmlHandlers defines several default ElementHandlers: ElementHandler (does nothing but keep track of whether we're inside this element), DocumentElementHandler (creates and adds document to the index), several metadata handlers that deal with different types of metadata elements (assuming the document contains its own metadata - see below for external metadata), InlineTagHandler (adds inline tags from the content to the index, such as &lt;p&gt;, &lt;s&gt; (sentence), &lt;b&gt;), several word handlers (indexes words and whitespace/punctuation between words). You can also easily create or derive your own, usually as an anonymous inner class that overrides the startElement() and endElement() methods.

You need to add a handler for your document element (signifying the start and end of your logical documents; probably just use DocumentElementHandler), your word element (signifying a word to index; probably derive from WordHandlerBase), and any inline tags you wish to index (probably just use InlineTagHandler). Optionally, you may want to add a simple ElementHandler for your "body" tag, if you wish to restrict what part of the document is actually indexed; in this case, you should expand your word and inline tag handlers to check that you're inside this body element before processing the matched element. 

A word handler derived from DefaultWordHandler might retrieve lemma and part of speech from the attributes of the start tag and add them to the annotations you declared at the top of your DocIndexer-constructor. You can do this using the AnnotationWriter.addValue() method. DefaultWordHandler takes care of adding values to the standard annotations word and punct. For this, DefaultWordHandler assumes the word is simply the word element's text content. DefaultWordHandler also stores the character positions before and after the word (which are needed if you want to highlight in the original XML).

An important note about adding values to annotations: it is crucial that you call AnnotationWriter.addValue() to each annotation an equal number of times! Each time you call addValue(), you move that annotation to the next corpus position, but other annotations do not automatically move to the next corpus position; it is up to you to make sure all annotations stay at the same corpus position. If a annotation has no value at a certain position, just add an empty string. 

You should probably call consumeCharacterContent(), which clears the buffer of captured text content in the document, at the start of a document (or at the start of the body element, if you handle that separately). This prevents the first punct value containing already captured text content you don't want. Similarly, before storing the document, you should add one last punct value (using consumeCharacterContent() to get the value to store), so the last bit of whitespace/punctuation isn't skipped. BlackLab assumes that there's always an "extra closing token" containing only the last bit of whitespace/punctuation.

The [tutorial](docindexer.md) develops a simple TEI DocIndexer using the above techniques.

### Multiple values at one position, position gaps and adding annotation values at an earlier position

NOTE: this applies if you're implementing your own DocIndexer class. The other appraoch, using a [configuration file](/guide/how-to-configure-indexing.md), does support standoff annotations but has no support for multiple values at one position (yet). Please let us know if you need this. 

The AnnotationWriter.addValue(String) method adds a value to an annotation at the next corpus position. Sometimes you may want to add multiple values at a single corpus position, or you may want to skip a number of corpus positions. This can be done using the AnnotationWriter.addValue(String, Integer) method; the second parameter is the increment compared to the previous value. The default value for the increment is 1, meaning each value is indexed at the next corpus position.

To add multiple values to a single corpus position, only use the default increment of 1 for the first value you want to add at this position; for all subsequent values at this position, use an increment of 0. Note: if the value you added first was the empty string, adding the next value with an increment of 0 will overwrite this empty string. This can be convenient if you're not sure whether you want to add any values at a particular location, but you want to make sure the annotation stays at the correct corpus position regardless.

To skip a number of corpus positions when adding a value, use an increment that is higher than 1. So to skip one position (and therefore leave a "gap" one wide), use an increment of 2.

Finally, you may sometimes wish to add values to an earlier corpus position. Say you're at position 100, and you want to add a value to position 50. You can do so using the AnnotationWriter.addValueAtPosition(String, Integer) method. The first token has position 0.

### Subannotations, for e.g. making part of speech features separately searchable (EXPERIMENTAL)

(you should use a [configuration file](/guide/how-to-configure-indexing.md) for this)

### Storing extra information with annotation values, using payloads

(the [configuration file](/guide/how-to-configure-indexing.md) approach does not support this yet; let us know if you need this)

It is possible to add payloads to annotation values. When calling addAnnotation() at the start of the constructor, make sure to use the version that takes a boolean called 'includePayloads', and set it to true. Then use AnnotationWriter.addPayload(). You can use null if a particular value has no payload. There's also a addPayloadAtIndex() method to add payloads some time after adding the value itself, but that requires knowing the index in the value list of the value you want to add a payload for, so you should store this index when you add the value.

One example of using payloads can be seen in DocIndexerXmlHandlers.InlineTagHandler. When you use InlineTagHandler to index an inline element, say a sentence tag, BlackLab will index it to the '_relation' (or older 'starttag') annotation. When it encounters the end tag, it wil update the start tag value with a payload indication the element length. This is used when searching to determine what matches occur inside certain XML tags.

### Indexing non-XML file types

(the [configuration file](/guide/how-to-configure-indexing.md) approach directly supports tabular (CSV/TSV) input formats and the plain text input format)

If your input files are not XML or are not tokenized and annotated per word, you have two options: convert them into a tokenized, per-word annotated format, or index them directly.

Indexing them directly is not covered here, but involves deriving from DocIndexerAbstract or implementing the DocIndexer interface yourself. If you need help with this, please [contact us](/guide/about.md#contact-us).

## Metadata 

### In-document metadata

(the [configuration file](/guide/how-to-configure-indexing.md) support this directly)

Some documents contain metadata within the document. You usually want to index these as fields with your document, so you can filter on them later. You do this by adding a handler for the appropriate XML element.

There's a few helper classes for in-document metadata handling. MetadataElementHandler assumes the matched element name is the name of your metadata field and the character content is the value. MetadataAttributesHandler stores all the attributes from the matched element as metadata fields. MetadataNameValueAttributeHandler assumes the matched element has a name attribute and a value attribute (the attribute names can be specified in the constructor) and stores those as metadata fields. You can of course easily add your own handler classes to this if they don't suit your particular style of metadata (have a look at nl.inl.blacklab.index.DocIndexerXmlHandlers.java to see how the predefined ones are implemented).

### Metadata from an external source

(the [configuration file](/guide/how-to-configure-indexing.md) support this directly)

Sometimes, documents link to external metadata sources, usually using an ID.

The MetadataFetcher is instantiated by DocIndexerXmlHandlers.getMetadataFetcher(), based on the metadataFetcherClass indexer parameter (see "Passing indexing parameters" above). This class is instantiated with the DocIndexer as a parameter, and the addMetadata() method is called just before adding a document to the index. Your particular MetadataFetcher can inspect the document to find the appropriate ID, fetch the metadata (e.g. from a file, database or webservice) and add it to the document using the DocIndexerXmlHandlers.addMetadataField() method.

Also see the two MetadataFetcher examples in nl.inl.blacklab.indexers.

### Add a fixed metadata field to each document

(the [configuration file](/guide/how-to-configure-indexing.md) support this directly)

It is possible to tell IndexTool to add a metadata field with a specific value to each document indexed. An example of when this is useful is if you wish to combine several corpora into a single index, and wish to distinguish documents from the different corpora using this metadata field. You would achieve this by running IndexTool twice: once to create the index and add the documents from the first corpus, "tagging" them with a field named e.g. Corpus_title (which is the fieldname [Whitelab](https://github.com/Taalmonsters/WhiteLab2.0) expects) with an appropriate value indicating the first corpus. Then you would run IndexTool again, with command "append" to append documents to the existing index, and giving Corpus_title a different value for this set of documents.

There's two ways to add this fixed metadata field for an IndexTool run. One is to pass an option \"---meta-Corpus_title mycorpusname\" (note the 3 dashes!) to the IndexTool. The other is to place a property \"meta-Corpus_title=mycorpusname\" in a file called indexer.properties in the current directory. This file can be used for other per-run IndexTool configuration; see below.

### Controlling how metadata is fetched and indexed

(the [configuration file](/guide/how-to-configure-indexing.md) support this directly)

By default, metadata fields are tokenized, but it can sometimes be useful to index a metadata field without tokenizing it. One example of this is a field containing the document id: if your document ids contain characters that normally would indicate a token boundary, like a period (.) , your document id would be split into several tokens, which is usually not what you want. Use the indextemplate.json file (described above) to indicate you don't want a metadata field to be tokenized.

## Editing the index metadata

NOTE: it is best to [influence the index metadata](/guide/how-to-configure-indexing.md#influence-index-metadata) through your input format configuration file. That way, re-indexing your data doesn't overwrite your changes.

In addition to specifying this information in your input format configuration file as described above, it is also possible to edit the index metadata file manually. If you do this, be careful, because it might break something. It is best to use a text editor with support for YAML, and to validate the resulting file with a YAML validator such as [YAML Lint](http://www.yamllint.com/). Also remember that if you edit the index metadata file, and you later decide to generate a new index from scratch, your changes to the metadata file will be lost. If possible, it is therefore preferable to put this information in the input format configuration file directly. (If you find that this is not possible in your case, please let us know.)  

Here's a commented example of indexmetadata.yaml:

```yaml
---
# Display name for the index and short description
# (not used by BlackLab. None of the display name or description values
#  are used by BlackLab directly, but applications can retrieve them if they want)
displayName: OpenSonar
description: The OpenSonar corpus.

# What was the name of the input format first added to this index?
# (it is possible to add documents of different formats to the same index,
#  so this is not a guarantee that all documents have this format)
documentFormat: OpenSonarFolia

# Total number of tokens in this corpus.
tokenCount: 12345

# Information about the index format and when and how it was created
# (don't change this)
versionInfo:
    blackLabBuildTime: "2017-08-01 00:00:00"
    blackLabVersion: "1.7.0"
    indexFormat: "3.1"
    timeCreated: "2017-07-31 16:03:37"
    timeModified: "2017-07-31 16:03:37"
    alwaysAddClosingToken: true
    tagLengthInPayload: true

# Information about annotated (complex) and metadata fields
fieldInfo:
    titleField: title            # ((optional, detected if omitted); field in the index containing
                                #  document title; may be used by applications)
    authorField: author          # ((optional) field in the index containing author information;
                                #  may be used by applications)
    dateField: date              # ((optional) field in the index containing document date 
                                #  information; may be used by applications)
    pidField: id                 # ((optional, recommended) field in the index containing unique 
                                #  document id; may be used by applications to refer to documents;
                                #  may be used by BlackLab to directly update documents without 
                                #  the client having to manually delete the previous version)
    defaultAnalyzerName: DEFAULT # The type of analyzer to use for metadata fields
                                # by default (DEFAULT|whitespace|standard|nontokenizing)
    contentViewable: false       # is the user allowed to retrieve whole documents? 
    textDirection: LTR           # text direction of the corpus (e.g. LTR/RTL) (not used by BlackLab) 
    documentFormat: ''           # (not used by BlackLab. may be used by application to 
                                #  e.g. select which XSLT to use)
    unknownValue: unknown        # what value to index if field value is unknown [unknown]
    unknownCondition: NEVER      # When is a field value considered unknown?
                                # (other options: MISSING, EMPTY, MISSING_OR_EMPTY) [NEVER]
    
    # Information about specific metadata fields
    metadataFields:
    
    # Information about the author field
    author:
        displayName: author                      # (optional) How to display in interface
        description: The author of the document. # (optional) Description (e.g. tooltip) in interface
        type: tokenized                          # ..or text, numeric, untokenized [tokenized]
        analyzer: default                        # ..(or whitespace|standard|nontokenizing) [default]
        unknownValue: unknown                    # overrides default unknownValue for this field
        unknownCondition: MISSING_OR_EMPTY       # overrides default unknownCondition for this field
        uiType: select                           # (optional) Widget to use in interface (text|select|range)
        values:                                  # values indexed in this field
        - firstValue
        - secondValue
        valueListComplete: true                  # are all values listed here, or just a few (max. 50)?
        displayValues:                           # (optional) How to display certain values in this field
        tolkien: J.R.R. Tolkien                #   e.g. display value "tolkien" as "J.R.R. Tolkien"
        adams: Douglas Adams
        displayOrder:                            # (optional) Specific order to display values in
        - tolkien
        - adams
    
    # (optional)
    # This block allows you to define groups of metadata fields.
    # BlackLab Server will include this information on the index structure page.
    # This can be useful if you want to generate a user interface based on index metadata. 
    metadataFieldGroups:
    - name: First group
    fields:
    - author
    - title
    - name: Second group
    fields:
    - date
    - keywords
    addRemainingFields: true  # plus any fields that weren't mentioned yet 
    
    # Information about annotated fields
    # (these used to be called complex fields, hence the key name;
    #  similarly, we used to call annotations "properties", as you can see below)
    complexFields:
    
    # Information about the contents field
    contents:
        mainProperty: word         # main annotation. used for concordances; contains char. offsets
        displayName: contents      # (optional) how to display in GUI
        description: The text contents of the document.
        noForwardIndexProps: ''    # (optional) space-separated list of annotation
                                   # names that shouldn't get a forward index
        displayOrder:              # (optional) Order to display annotation search fields
        - word
        - lemma
        - pos
```

Please note: the settings pidField, titleField, authorField, dateField refer to the name of the field in the Lucene index, not an XML element name.
