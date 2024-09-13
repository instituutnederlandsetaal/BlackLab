# Files in the integrated index

This describes the new index format that integrates all previously external files (forward index, content store, index metadata, version files) into the Lucene index. The goal is to enable distributed indexing and search using Solr. Eventually this will become the default for new indexes.

> **NOTE:** this index format is in development and documentation may still be a little rough.

The integrated index has a codec name `BlackLab40Codec` and version of 1. (Additional versions or codecs may be added in the future)

## Index metadata

The index metadata (the equivalent to the `indexmetadata.yaml` file from the classic index format) is not written to a segment file (like information related to a document), but instead it is written to a special document in the Lucene index.

The document can be found by searching for a field with the name and value `__index_metadata_marker__`. The metadata is stored, in JSON form, in the field `__index_metadata__`. The JSON structure corresponds to the JAXB annotations in the `IndexMetadataIntegrated` class.

You can export the index metadata to a file using `IndexTool`. Use the `--help` option to learn more. It is even possible to change and re-import the file, although this can be risky.

## Annotated fields

Annotated fields are stored as a number of Lucene fields. For example, the `contents` field with annotations `word`, `lemma` and `pos` may have these fields:

- `contents%word@s`, the sensitive version of the `word` annotation
- `contents%word@i`, the insensitive version of the `word` annotation
- `contents%punct@s`, the punctuation annotation (any space and/or punctuation between token positions)
- `contents%lemma@s`, the sensitive version of the `lemma` annotation
- `contents%lemma@i`, the insensitive version of the `lemma` annotation
- `contents%pos@i`, the insensitive version of the `pos` annotation
- `contents%_relation@s`, any spans and relations in this field; see [spans and relations](#spans-and-relations)
- `contents%length_tokens`, the length of the document in number of tokens (numeric field).
- `contents%cs`, the original content of the document; see [content store](#content-store) 

Most annotation fields will have a [forward index](#forward-index) associated with them, unless specifically disabled in the index format configuration.


### Spans and relations

The special `_relation` annotation contains information about spans ("inline tags" such as `<p/>`, `<s/>`, etc.) and relations (e.g. dependency relations), provided these were configured to be indexed in the index format configuration.

Relations have:
- a source and a target, both of which may be a single token or a number of consecutive tokens (exception: root relations have no source, only a target)
- a class (e.g. `dep` for dependency relations)
- a type (e.g. `nsubj` for nominal subject)
- optionally, attributes (a map of key-value pairs)

Spans are indexed as special relations with class `__tag`, with the start of the span as the source of the relation and the end of the span (exclusive, so the first word after the span) as the target. Both source and target have a length of 0; that is source start and source end have the same value, and the same goes for target start and target end.

Relations are always indexed at the start of the source of the relation. Therefore, spans are always indexed at the first token in the span.

A relation is indexed once or twice: relations without attributes are indexed only once, but relations with attributes are indexed once with those attributes and once without. This makes searching without attribute filters faster in the case that there are many different attribute values (e.g. `<s/>` tags where each tag has a unique `id` attribute).

A relation is indexed as a term containing the relation class and type (which together we call the _full relation type_) and (optionally) attributes, with a payload containing the source and target information.


### Term

The term indexed is a string of one of these forms.

Without attributes:

    relClass::relType\u0001

With attributes:

    relClass::relType\u0001\u0003attrName1\u0002value1\u0001\u0003attrName2\u0002value2\u0001...

Again, we call `relClass::relType`, the relation class and the relation type, the _full relation type_. The relation class distinguishes between different types of relations, e.g. `__tag` for inline tags, `dep` for dependency relations, etc. The relation type is used to distinguish between different relations of the same class, e.g. `dep::subject` for subject relations, `dep::object` for object relations, `dep::nsubj` for nominal subject relations, etc.

If there are any attributes, they follow next. These are sorted alphabetically by name (so we can generate an efficient regular expression).

Three special characters delineate the separate parts of the term:

- `\u0001` ("value suffix") follows the full relation type and all attribute values.
- `\u0002` ("value separator") separates attribute names from their values.
- `\u0003` ("name prefix") precedes each attribute name.

These delineation characters make sure we can generate a regular expressions that avoid any unwanted matches (e.g. prefix or suffix matches).

Relations with attributes are indexed twice: once with, and once without
attributes. The second version is used to speed up queries that don't
filter on the attributes. This version is marked with a `\u0004` appended to the term, so we know to skip it when determining relations statistics.


### Payload

The payload uses Lucene's `VInt` (for non-negative numbers) and `ZInt` (an implementation of [variable-length quantity (VLQ)](https://en.wikipedia.org/wiki/Variable-length_quantity)). We store a relative position for the target end to save space.

Relation payloads are always stored at the source position.

The payload for a relation consists of the following fields:

* This number is either `relTargetStart` or `relationId`, depending on the `flags` byte (see below). `relationId` is a unique id for this relation, which can be used to look up extra information, such as attributes, and maybe other information in the future. For `relTargetStart`, see below. Default value: `1`.
* `flags: byte`: If `0x02` is set, the relation only has a target (root relation). If `0x04` is set, use a default length of 1 for `sourceLength` and `targetLength`. If `0x08` is set, the first number in the payload is the `relationId`. The other bits are reserved for future use and must not be set. Default: `0`.
* If flag `0x08` was set, `flags` is followed by `relTargetStart: ZInt`: relative position of the (start of the) target end. Default: `1`. If flag `0x08` was not set, this number will not be written here (in that case, the first number of the payload is `relTargetStart`, see above).
* `sourceLength: VInt`: length of the source end of the relation. For a single word this would be 1; for a span of words, greater than one. For inline tags, it will be set to 0 (start and end tags are considered to be zero-length). Default: `0` (normally) or `1` (if flag `0x04` is set)
* `targetLength: VInt`: length of the target end of the relation. For a single word this would be 1; for a span of words, greater than one. For inline tags, this will be set to 0 (start and end tags are considered to be zero-length). Default: `0` (normally) or `1` (if flag `0x04` is set)

Fields may be omitted from the end if they have the default value. Therefore, an empty payload means `{ relTargetStart: 1, flags: 0, sourceLength: 0, targetLength: 0 }`.

As another example, the payload `0x81; 0x04` would mean `{ relTargetStart: 1, flags: 4, sourceLength: 1, targetLength: 1 }`. Explanation: `0x81` is the `VInt` encoding for `1` (the lower seven bits giving the number and the high bit set because this is the last byte of the number). The flag `0x04` is set, so the lengths default to `1` instead of `0`.

### Calculate Lucene span from relation term

When found using BlackLab, a relation has a source and target (which may be word groups or single words) as well as a "regular" span.

The span normally runs between source and target, but operations combining relations may enlarge the span so this no longer holds.

To calculate the Lucene span from a matched relation term's payload, first a few helper values:

    thisStart = position the relation was indexed at
    thisEnd = thisStart + thisLength
    otherStart = thisStart + relOtherStart
    otherEnd = otherStart + otherLength

So the Lucene span for a relation is:

    [thisStart, max(thisEnd, otherEnd) )

(although it seems unlikely that we want the source and target to overlap)

(note that Lucene spans are half-open, i.e. the end position is not included in the span)



## Forward index

Forward index files currently have a codec name of `BlackLab40Postings` and a version of 1. (Additional versions or codecs may be added in the future)

### fields - where to find information about each Lucene field

**NOTE:** the Lucene field we're talking about here represents one sensitivity of an annotation on an annotated field. For example: the "insensitive" sensitivity of the "lemma" annotation for the "contents" field (Lucene field name: `contents%lemma@i`). The forward index is always stored with the main sensitivity of the annotation (sensitive if it exists, insensitive otherwise).

**NOTE:** we don't store number of fields; we just read until the end of the file.

**NOTE:** a Lucene field with a forward index will have a `FieldInfo` attribute `BL_hasForwardIndex` with a value of `true`. 

- For each field annotation:
  * Lucene field name (str), e.g. "contents%lemma"
  * number of terms in this field (int)
  * offset of field in termorder file (long)
  * offset of field in termindex file (long)
  * offset of field in tokensindex file (long)

This file will have an extension of `.blfi.fields`.

### termorder - indexbuffers for a sorted view on the terms

- int[number of terms n]: termID2InsensitivePos - sort positions of the terms (i.e. what position would the term have after sorting the list of terms insensitively)
- int[number of terms n]: insensitivePos2TermID - what term would be at this position if the list was sorted?
- int[number of terms n]: termID2SensitivePos - sort positions of the terms (i.e. what position would the term have after sorting the list of terms sensitively) 
- int[number of terms n]: sensitivePos2TermID - what term would be at this position if the list was sorted? 

### termindex - where to find term strings

**NOTE:** we don't store number of terms as it follows from the file size. If we decide to switch to using VInts, we could write the number of terms at the end of the file. We don't know it at the beginning, we can only iterate through terms at that time.

- For each field annotation:
  * For each term:
    - offset of term string in terms file (long)

This file will have an extension of `.blfi.termindex`.

### terms - term strings

**NOTE:** do we need this file (and the termindex file) at all, or can we just use Lucene's TermsEnum?
(we would have to keep all terms in memory for fast lookups, so maybe a memory-mapped file is better)

- For each field annotation:
  * For each term:
    - Term string (str)

This file will have an extension of `.blfi.terms`.

### termvec - occurrences of terms in documents (temporary)

This is a temporary file. It is eventually replaced by the tokens file.

- For each field annotation:
  * For each term:
    - For each doc term occurs in:
      * Number of occurrences (int n)
      - For each occurrence:
        * Position (int)

NOTE: we could use `VInt` here to save space (fixed-length fields only matter for random access lookup like the tokens file).

### tokensindex - where to find tokens (forward index) for each document

- For each field annotation:
  * For each document (fixed-length record so can access doc X quickly):
    - offset in the tokens file (long)
    - number of tokens in the document (int)
    - encoding used (byte)

See below for the tokens encodings.

This file will have an extension of `.blfi.tokensindex`.

### tokens - sequence of tokens in each document, per annotation

- For each field annotation:
  * For each document:
    - the document, in the encoding given by the tokensindex file. See below.

This file will have an extension of `.blfi.tokens`.

### Tokens encodings

| Name                | Code | Description                                                                                                           |
|---------------------|-----:|-----------------------------------------------------------------------------------------------------------------------|
| INT_PER_TOKEN       |    1 | One fixed-size integer for each token in the document. The codec parameter specifies the number of bytes per integer. |
| ALL_TOKENS_THE_SAME |    2 | A single 4-byte value representing the value of all the tokens in the document.                                       |


## Relation info

Relation info files currently have a codec name of `BlackLab40Postings` and a version of 1. (Additional versions or codecs may be added in the future)

The relation info ensures that we can always look up any attributes for any relations matched (including "inline tags" such as `<s/>`, which most often have attributes).

### fields - where to find information about each Lucene field

**NOTE:** the Lucene field we're talking about here represents the special `_relation` annotation on an annotated field. For example: `contents%_relation@s`).

**NOTE:** we don't store number of fields; we just read until the end of the file.

- For each relations field:
    * Lucene field name (str), e.g. "contents%_relation@s"
    * offset of field in docs file (long)

This file will have an extension of `.blri.fields`.

### docs - where to find information about each document

- For each relations field:
  * For each document:
    - starting offset in the relations file (long)

This file will have an extension of `.blri.docs`.

### relations - Information per unique relation id.

- For each relations field:
  * For each document:
    - For each relation id (first one is relation id 0):
      * offset in attrset file (long)

This file will have an extension of `.blri.relations`.

### attrsets - Information per unique set of attributes.

- For each unique attribute set:
  * number of attributes in set (int)
  * For each attribute in this set:
    - attribute name id (int)
    - attribute value offset (long)

This file will have an extension of `.blri.attrsets`.

### attrnames - Attribute names

This file will be read into memory when the index is opened, so it's quick to look up attribute names by index.

- For each unique attribute name (id 0 is the first attribute):
  * name (string)

This file will have an extension of `.blri.attrnames`.

### attrvalues - Attribute values

- For each unique attribute value:
  * value (string)

This file will have an extension of `.blri.attrvalues`.

### tmprelations - Attribute set offset per term (temporary)

This is a temporary file. It is eventually replaced by the relations file.

- For each attribute set:
  * For each doc term occurs in:
    - Number of occurrences (int)
    * For each occurrence:
      - Unique relation id (int)

This file will have an extension of `.blri.relations.tmp`.

## Content store

Content store files currently have a codec name of `BlackLab40ContentStore` and a version of 1. (Additional versions or codecs may be added in the future)

Please note: the classic (external) content store used a format with a fixed size compressed block; this was to enable reuse of space. This is no longer needed (Lucene will optimize the index by merging segments), so we now use a fixed-character block. This avoids wasting space and is easier to deal with during compression. The compressed blocks therefore all have different sizes, and we store a (relative) byte offset to each block.

> **FUTURE IMPROVEMENTS:** compression could be improved by using a preset dictionary (see https://www.ietf.org/rfc/rfc1950.txt). A reasonable approach could be to take a chunk from the middle of the first file added (middle to increase the chance we're inside actual text, not metadata) and use that as the dictionary for the entire segment. This should ensure common strings (e.g. XML tags, attributes, common words, etc.) are stored more efficiently in each block.

### fields - where to find information about each Lucene field

**NOTE:** the Lucene field we're talking about here belongs to an annotated field. For example, `contents%cs`.

**NOTE:** we don't store number of fields; we just read until the end of the file.

**NOTE:** a Lucene field with a content store will have a `FieldInfo` attribute `BL_hasContentStore` with a value of `true`.

- block size in characters `CHARS_PER_BLOCK` (int)
  Higher values improve compression ratio. Lower values may improve access time and reduce wasted space. Default is 4096.
- For each field with a content store:
  * Lucene field name (str), e.g. `contents%cs`

This file will have an extension of `.blcs.fields`.

### docindex - where each document starts in the valueindex file

- For each document:
  * Offset in the valueindex file (int)
  * Number of fields with a content store for this document (byte)

This file will have an extension of `.blcs.docindex`.

### valueindex - where each document+field value starts in the blockindex file

- For each document:
  * For each field with a content store:
    - field id (based on order in the fields file) (byte)
    - value length in characters (int)\
      from this follows: `NUMBER_OF_BLOCKS = Math.ceil(LENGTH_IN_CHAR / CHARS_PER_BLOCK)`.
    - codec used (byte)\
      (right now it can either be 0 (uncompressed) or 1 (basic zlib compression))
    - offset in the blockindex file (long)
    - base byte offset in the blocks file (long)\
      (block offsets in blockindex file are relative to this)

This file will have an extension of `.blcs.valueindex`.

### blockindex - where to find data blocks in the blocks file

- For each document:
  * For each field with a content store:
    - For each block:
      * byte offset AFTER this block in the blocks file, relative to base offset \[gotten from valueindex file] (int)\
        (determine block size by subtracting this offset from the previous int in the file, except for the first block, where the offset after the block is equal to the length)

The character offset of block `i` is obviously `i * CHARS_PER_BLOCK`, so the character at offset `j` is stored in the block with index `Math.floor(j / CHARS_PER_BLOCK)`, at offset `j % CHARS_PER_BLOCK`)

This file will have an extension of `.blcs.blockindex`.

### blocks - compressed blocks with the field values

- a number of variable-sized blocks (containing `CHARS_PER_BLOCK` characters) containing zlib-compressed utf-8 fragments of values.

`CHARS_PER_BLOCK` is recorded in the fields file, see above.

This file will have an extension of `.blcs.blocks`.
