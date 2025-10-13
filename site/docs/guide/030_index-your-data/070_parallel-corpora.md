# Parallel corpora

::: tip Supported from v4.0
Indexing and searching parallel corpora is supported from BlackLab 4.0.
:::

A parallel corpus contains multiple versions of the same document. These might be translations to different languages, historical versions, etc.

Let's look at a simple toy corpus with two versions, English (`en`) and Dutch (`nl`).

## Example XML

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<doc>
    <text lang="en">
        <s xml:id="en_s1">
            <w xml:id="en_w1">This</w>
            <w xml:id="en_w2">is</w>
            <w xml:id="en_w3">an</w>
            <w xml:id="en_w4">example</w>
        </s>
        <link source="en_s1" target="nl_s1" type="s"/>
        <link source="en_w1" target="nl_w1" type="w"/>
        <link source="en_w2" target="nl_w2" type="w"/>
        <link source="en_w3" target="nl_w3" type="w"/>
        <link source="en_w4" target="nl_w4" type="w"/>
    </text>
    <text lang="nl">
        <s xml:id="nl_s1">
            <w xml:id="nl_w1">Dit</w>
            <w xml:id="nl_w2">is</w>
            <w xml:id="nl_w3">een</w>
            <w xml:id="nl_w4">voorbeeld</w>
        </s>
        <link source="nl_s1" target="en_s1" type="s"/>
        <link source="nl_w1" target="en_w1" type="w"/>
        <link source="nl_w2" target="en_w2" type="w"/>
        <link source="nl_w3" target="en_w3" type="w"/>
        <link source="nl_w4" target="en_w4" type="w"/>
    </text>
</doc>
```

## Configuration

The input format configuration for a parallel corpus is similar to that of a regular corpus, but instead of a single
annotated field, it should define an annotated field for each version in the parallel corpus.

The versions should be named with the same prefix (usually just `contents`), followed by `__` and the version code.

For the above example XML, the index will contain an annotated field `contents__en` and `contents__nl`.

Here's how to configure indexing:

```yaml
processor: saxon  # modern XPath support

documentPath: //doc

annotatedFields:

    # English version
    contents__en:
        containerPath: text[@lang='en']
        wordPath: .//w
        # Capture ids so we can refer to them from standoffAnnotations
        tokenIdPath: "@xml:id"
        annotations:
            - name: word
              valuePath: .
              sensitivity: sensitive_insensitive
        standoffAnnotations:
            - path: .//link
              type: relation
              relationClass: al
              # What version does the target attribute refer to?
              targetVersionPath: "replace(@target, '^(\\w+)_.*$', '$1')"
              # Relation type
              valuePath: "@type"
              sourcePath: "@source"
              targetPath: "@target"
        inlineTags:
            - path: .//s
              tokenIdPath: "@xml:id"  # make sure we can refer to sentence tags as well

    # Dutch version
    contents__nl:
        containerPath: text[@lang='nl']
        wordPath: .//w
        # Capture ids so we can refer to them from standoffAnnotations
        tokenIdPath: "@xml:id"
        annotations:
            - name: word
              valuePath: .
              sensitivity: sensitive_insensitive
        standoffAnnotations:
            - path: .//link
              type: relation
              relationClass: al
              # What version does the target attribute refer to?
              targetVersionPath: "replace(@target, '^(\\w+)_.*$', '$1')"
              # Relation type
              valuePath: "@type"
              sourcePath: "@source"
              targetPath: "@target"
        inlineTags:
            - path: .//s
              tokenIdPath: "@xml:id"  # make sure we can refer to sentence tags as well
```


## Create the index

Index the above example XML with this configuration using the IndexTool:

```bash
java -cp blacklab.jar:lib nl.inl.blacklab.tools.IndexTool create index/ test.xml test.blf.yaml
```

If everything worked, you should be able to search for `"This" ==>nl []` to find the Dutch translation for "This", or `_ =s=>nl []` to find sentence alignments. For more, see [Parallel corpus querying](/guide/query-language/parallel.md).
