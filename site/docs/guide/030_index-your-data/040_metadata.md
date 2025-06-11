# Metadata

The [simple example](simple-example.md) included a way to index embedded metadata. Let's say this is our input file:

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

To configure how metadata should be indexed, you can either name each metadata field you want to index separately, or you can use `forEachPath` to index a number of similar elements as metadata:

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

It's also possible to process metadata values before they are indexed (see [Processing values](processing-values.md)), although it's 
often preferable to do as much processing as possible in XPath.


## Tokenize or not?

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

## Custom properties

> Note that custom properties may be removed in a future version.

Just like with annotations, you can specify a `displayName`, `description` and `uiType` for a metadata field. This 
information is not used by BlackLab, but can be used by BlackLab Frontend or another application.
For example, see [Metadata (Filters)](https://blacklab-frontend.ivdnt.org/customizing_the_interface/search_form/widgets.html#metadata-filters)

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

Again, note that these properties may be removed from the `.blf.yaml` file specification in the future. It makes more sense to configure the frontend directly, for example using a custom script. See [Customizing the interface](https://blacklab-frontend.ivdnt.org/customizing_the_interface/intro.html).


## Add a fixed metadata value to each document

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

## Linking to external document metadata

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

## Corpus metadata

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

There's also a complete [annotated index metadata file](full-example.md) if you want to know more details about that.

There are also (hacky) ways to make changes to the corpus metadata after it was indexed: you can export the metadata to a file and re-import it later (older indexes had an external `indexmetadata.yaml` file that could be edited directly). Start the `IndexTool` with `--help` to learn more, but be careful, as it is easy to make the index unusable this way. 

