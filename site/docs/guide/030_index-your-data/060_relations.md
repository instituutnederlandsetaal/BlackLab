# Relations

::: tip Supported from v4.0
Indexing and searching relations is supported from BlackLab 4.0.
:::

BlackLab can index relations (such as dependency relations), allowing for a whole new class of queries on your corpus.

Indexing relations can be done using the built-in `conll-u` DocIndexer, or by implementing your own DocIndexer, but the
recommended way is to index from XML input files using `standoffAnnotations`.

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

The above would allow you to search for `_ -nsubj-> "I"` to find "I support", with the relation information captured. See [Relations querying](/guide/query-language/relations.md).

A note about the `relationClass` setting: you should declare the type of relation you're indexing here, using a short (i.e. 3-letter) code. By convention, dependency relations should use `dep`. [BlackLab Frontend](https://blacklab-frontend.ivdnt.org/) can use this information to display relations in a more user-friendly way, i.e. referring to the _head_ and _dependent_ of the dependency relation instead of the more generic _source_ and _target_.
