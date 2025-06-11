# Miscellaneous

## Forward index and multiple values

A note about forward indices and indexing multiple values at a single corpus position: as of right now, the forward index will only store the first value indexed at any position. This is the value used for grouping and sorting on this annotation. In the future we may add the ability to store multiple values for a token position in the forward index, although it is likely that the first value will always be the one used for sorting and grouping.



## Allow viewing documents

By default, BlackLab Server will not allow whole documents to be retrieved using `/docs/PID/contents`. This is to prevent accidentally distributing unlicensed copyrighted material.

You can allow retrieving whole documents by enabling the `corpusConfig.contentViewable` setting in the index format configuration file, or directly in the `indexmetadata.yaml` file in the index directory.

This setting can also be changed for individual documents by setting a metadat field with the name `contentViewable` to `true` or `false`.


## XPath support level

BlackLab supports two different XML processors: VTD and Saxon. While currently VTD is still the default, we would recommend Saxon for most users going forward.

VTD only supports XPath 1.0 and has some slight quirks (see below). Saxon uses more memory, but is often faster and supports XPath 3.1, which can make writing indexing configurations much easier.

Certain complex indexing features can be avoided when using Saxon; many things can be done in XPath directly. See [XPath examples](xpath-examples.md) to get an idea of the wide range of possibilities.

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


## Unicode normalization

Unicode normalization refers to the process of converting different ways of encoding the same character to a single, canonical form. For example, the character `é` can be encoded as a single character `é` (U+00E9), or as a combination of `e` (U+0065) and `´` (U+00B4).

BlackLab's builtin indexers should automatically normalize to NFC (Normalization Form Canonical Composition). This should prevent any issues when sorting or grouping.

[More about Unicode equivalence and normal forms](https://en.wikipedia.org/wiki/Unicode_equivalence)

## Automatic XSLT generation

If you're creating your own corpora by uploading data to BlackLab Frontend, you want to be able to view your documents as well, without having to write an XSLT yourself. BlackLab Server can generate a default XSLT from your format config file. However, because BlackLab is a bit more lenient with namespaces than the XSLT processor that generates the document view, the generated XSLT will only work correctly if you take care to define your namespaces correctly in your format config file.

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

## Configuration versions 1 and 2

There's an experimental version 2 of the `.blf.yaml` format. To try it out,
add `version: 2` to the top of your format file.

Version 2 of the format file introduces a few breaking changes to be aware of:

- default XML processor is now `saxon` (used to be `vtd`). Saxon is faster and supports modern XPath features, making it much more flexible.
- `baseFormat` key (to inherit from a different format config) is no longer allowed. Instead, you should copy the format and customize it to suit your needs.
- `word` and `lemma` no longer have a special default `sensitivity`. All user-defined annotations now default to `insensitive`. To remain compatible with the old behaviour, explicitly specify `sensitivity: sensitive_insensitive` for `word` and `lemma`.
- dash `-` in field or annotation name will no longer automatically be replaced with underscore `_` (this was never necessary; field and annotation names must be valid XML names, which may contain dashes) If you rely on this quirk, replace dash with underscore manually in your config.
- processing step `default` was renamed to `ifempty`, to better describe how it's commonly used.
- `inlineTags` keys `includeAttributes`, `excludeAttributes` and `extraAttributes` have been removed. Instead, use the `attributes` key to specify which attributes to index. Add `valuePath` if this is an extra attribute (that doesn't actually appear on the tag, but should be added based on the XPath expression). Use `exclude: true` to exclude an attribute. If the first entry contains no name, only `exclude: true`, this means "exclude any attribute not in this list".
- `append` processing step now has a `prefix` parameter in addition to the `separator` parameter. `separator` still defaults to a space, but is now only used to separate multiple metadata field values. `prefix` defaults to the empty string, and is used to prefix the value to be appended. This means you won't get an extra space by default when appending a value. Add `prefix: ' '` (or whatever you set as `separator`) for the old behaviour.
- The `multipleValues`, `allowDuplicateValues` keys on an annotations have been removed. Both work automatically now: if your config produces multiple values for an annotation, they will be indexed, and any duplicates that may arise are automatically removed.
- The `mapValues` key on metadata fields has been removed. Use the `map` processing step instead, which can be used anywhere where processing steps are allowed.


## Extending formats (deprecated)

> **NOTE: THIS FUNCTIONALITY IS DEPRECATED** <br/>
> Don't rely on this feature as it is no longer supported in .blf.yaml format version 2. Instead, simply copy the format file and make any changes you need.

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

