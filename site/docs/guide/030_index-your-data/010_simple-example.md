# A simple configuration file

An input format configuration file describes the structure of your documents so that BlackLab can index them.

They can be used to index data from the commandline using the [IndexTool](create-an-index.md) or using BlackLab Frontend (if configured 
to allow a logged-in user to upload and index their own corpora).

BlackLab already [supports](create-an-index.md#supported-formats) a number of common input formats out of the box. Your data may differ slightly of 
course, so you may use the [predefined formats](@github:/engine/src/main/resources/formats) as a starting point and customize them to fit your data. But here 
we will look at writing a simple configuration file from scratch.

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

Below is the configuration file you would need to index files of this type. This uses [YAML](../yaml.md), but you can also use JSON if you prefer.

Note that the settings with names ending in "Path" are XPath expressions (at least if you're parsing XML files).


```yaml
processor: saxon  # use Saxon for modern XPath support

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
    # (valuePaths relative to wordPath)
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
    type: untokenized  # persistent identifier should be untokenized!

    # Each <meta/> tag corresponds with a metadata field
  - forEachPath: meta
    namePath: "@name"   # name attribute contains field name
    valuePath: .        # element text is the field value

corpusConfig:
  specialFields:
    # What metadata field persistently identifies our documents?
    # (should be configured with type: untokenized)
    pidField: docId
```

To use this configuration, you should save it with a name like `simple-input-format.blf.yaml` (`blf` stands for BlackLab Format) in either directory from which you will be using it, or alternatively one of `$BLACKLAB_CONFIG_DIR/formats/` (if this environment variable is set), `$HOME/.blacklab/formats/` or `/etc/blacklab/formats/`.

Read on to learn more about [annotations](annotations.md), [spans](spans.md), [metadata](metadata.md), and more.
