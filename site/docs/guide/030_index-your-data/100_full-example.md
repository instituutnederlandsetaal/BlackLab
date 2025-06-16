# Full configuration example

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
    # (paths relative to container)
    inlineTags:
    - path: .//s
      attributes:
      - name: "xml:id" # Skip unique ids (slower, bigger index)
        exclude: true
    - path: .//p
      attributes:
      - exclude: true
      - name: "type"   # Only index the "type" attribute
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
    # (BTW, make sure pidField is configured with "type: untokenized" to 
    # prevent issues with e.g. PIDs containing spaces)
    specialFields:
      pidField: id         # unique document identifier. Used by BLS for persistent URLs
      titleField: title    # may be used by user interface to display document info
      authorField: author  # may be used by user interface to display document info
      dateField: pubDate   # may be used by user interface to display document info
```
