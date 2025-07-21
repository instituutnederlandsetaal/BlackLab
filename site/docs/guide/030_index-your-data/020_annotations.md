# Annotations

## Declaring annotations

As we saw in the [simple example](simple-example.md), annotations are defined in the `annotations` section of the annotated field configuration. Each annotation has a `name`, a `valuePath` (XPath expression to get the value for this annotation), and a `sensitivity` setting (defaults to `insensitive`):

```yaml
## What element starts a new document?
documentPath: //document

annotatedFields:
    contents:
        # What element (relative to documentPath) contains this field's contents?
        containerPath: text
    
        # What are our word tags? (relative to containerPath)
        wordPath: .//w
    
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
```

Please note that when declaring annotations, the first annotation you declare will become the _main annotation_. The main annotation will:

- be searched when omitting annotation name in CQL (e.g. search for `"ship"` and it searches the main annotation).
- be used to generate concordances (the KWIC view).
- be returned as the value (text content) of the `<w>` tag (in the XML response).

The following sections will look at the various options you can use to configure annotations.

## Case- and diacritics sensitivity

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


## Standoff annotations

Standoff annotations are annotations that are specified in a different part of the document.

There are several strategies for dealing with standoff annotations.

### Using XPath

If possible, we recommend using XPath to resolve standoff annotations. Make sure you have `processor: saxon` at the 
top of your `.blf.yaml` file, which ensures modern XPath expressions are supported.

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
annotations:
    - name: word
      valuePath: .
      sensitivity: sensitive_insensitive
    
    # ... other token annotations...
         
    - name: color
      valuePath: /root/colors[current()/@colorId]
```

### Using unique tokens ids

If your tokens (`<w/>` tags) have unique ids, and your standoff annotations refer to these ids, you can use the 
`standoffAnnotations` setting to index these annotations.

Example input file:

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

Configuration:

```yaml
processor: saxon
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

It would also be possible to use XPath for this type of annotation. It is a matter of preference which method you use.


### Referring to inline anchors

Normally, `standoffAnnotations` described in the previous section refer to token ("word") ids, defined by the 
`tokenIdPath` setting at the `annotatedField` level.

But what if your XML includes inline anchor tags between words that you want to refer to?

For example:

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

## Multiple values at one position

It is possible to index several values for an annotation, such as multiple lemmatizations or multiple possible part of speech 
tags.

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
      sensitivity: sensitive_insensitive
```

When indexing multiple values at a single position, it can become possible to match the same value multiple times, for example when creating an annotation that combines word and lemma (useful for simple search). Because word and lemma can often be the same, this could lead to duplicate matches. That's why BlackLab will remove any duplicates automatically and only index unique values for the token position.

Multiple value annotations also work for tabular formats like `csv`, `tsv` or `sketch-wpl`. You can use a [processing step](processing-values.md) (`split`) to split a column value into multiple values. For example to define a `lemma` annotation that can have multiple `|`-separated values:

```yaml
    - name: lemma
      valuePath: 2    # second column in the csv file 
      sensitivity: sensitive_insensitive
      process:
      - action: split
        separator: "|"
```

## Disable the forward index

The index for your corpus can get very large. One way to reduce the size is to disable the forward index for some annotations.

By default, all annotations get a forward index. The forward index is the complement to Lucene's reverse index, and can
quickly answer the question "what value appears in position X of document Y?". This functionality is used to generate
snippets, sort and group based on context words and to speed up certain query types.

However, forward indexes take up a lot of disk space and can take up a lot of memory, and they are not needed for every
annotation. You should probably have a forward index for at least the `word` annotation, and for any annotation you'd 
like to sort/group on or that you use heavily in searching, or that you'd like to display in KWIC views. But if you add 
an annotation that is only used in certain special cases, you can decide to disable the forward index for that 
annotation. You can do this by adding `forwardIndex: false`:

```yaml
- name: wordId
  valuePath: @id
  forwardIndex: false
```

You can still get KWICs/snippets that include annotations without a forward index by generating KWICs and snippets from 
the original input file, at the cost of performance. To do this, pass `usecontent=orig` to BlackLab Server.

## Raw XML

An annotation can optionally capture the raw xml content:
```yaml
    - name: word_xml
      valuePath: .
      captureXml: true
```

## Custom properties

> Note that custom properties may be removed in a future version.

You can also specify a `displayName`, `description` and `uiType` for an annotation. This information is not used by BlackLab, but can be used by BlackLab Frontend or another application. For example, see [Change the type of input fields](https://blacklab-frontend.ivdnt.org/customizing_the_interface/search_form/widgets.html#annotations)

```yaml
- name: word
  valuePath: .
  sensitivity: sensitive_insensitive
  displayName: Word form
  description: The word form as it appears in the text.
  uiType: text
```

Again, note that these properties may be removed from the `.blf.yaml` file specification in the future. It makes more sense to configure the frontend directly, for example using a custom script. See [Customizing the interface](https://blacklab-frontend.ivdnt.org/customizing_the_interface/intro.html).

## Subannotations

> Note that the subannotations feature should be considered experimental and may change or be removed in a future version.

Part of speech sometimes consists of several features in addition to the main PoS, e.g. "NOU-C(gender=n,number=sg)". It would be nice to be able to search each of these features separately without resorting to complex regular expressions. BlackLab supports subannotations to achieve this.

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

