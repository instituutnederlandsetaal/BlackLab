# Non-XML files

## Indexing CSV/TSV files

BlackLab works best with XML files, because they can contain any kind of (sub)annotations, (embedded or linked) metadata, inline tags, and so on. It is also often easier to display them in BlackLab Frontend (using XSLT). However, if your data is in a non-XML type like CSV, TSV or plain text, and you'd rather not convert it, you can still index it.

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

If one of your columns contains multiple values, for example multiple alternative lemmatizations, use a processing step (`action: split`) to split the column value. See also [here](#multiple-values-at-one-position).

If you want to index metadata from another file along with each document, you have to use `valueField` in the `linkValues` section (see [here](metadata.md#linking-to-external-document-metadata)). In the SketchWPL case, in addition to `fromInputFile` you can also use any document element attributes, because those are added as metadata fields automatically. So if the document element has an `id` attribute, you could use that as a `linkValue` to locate the metadata file.

## Indexing plain text files

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

## Indexing other files

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

