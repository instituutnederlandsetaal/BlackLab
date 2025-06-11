# Solr (EXPERIMENTAL)

Various notes and thoughts on integrating BlackLab with Solr.

::: warning

BlackLab/Solr integration is still in early development. This information
is only provided for the curious who want to experiment.

Specifically, it is not yet possible to find patterns in text using Corpus Query
Language, which is the whole point of BlackLab.

Eventually, all BlackLab features will be made available through Solr, and distributed
corpus and search will be enabled.

:::

## Querying a BlackLab corpus

You can load an existing BlackLab corpus as a Solr core. For now, this only allows you to search for documents matching metadata fields.

### 1. Download a Solr release
Releases can be found [here](https://archive.apache.org/dist/lucene/solr/) for versions `<=8` and [here](https://archive.apache.org/dist/solr/solr/) for `>=9`.
Use the same version of Solr as BlackLab's Lucene version (SOlR's version always reflects the internal Lucene version).

### 2. Add BlackLab classes to Solr
Copy the following jars into `solr/server/solr-webapp/webapp/WEB-INF/lib`:
- `blacklab/engine/target/blacklab-engine.jar`
- `blacklab/engine/target/lib/*.jar` (maybe not all these jars are required, but some further testing is required).

> NOTE:  
Normally you'd copy the jars to the `lib` directory in the Solr core (as described [here](https://solr.apache.org/guide/8_4/libs.html#lib-directories)), but this did not work due a `ClassCastException`.  
During debugging, I noticed that the `BlackLab40Codec` was loaded using a different classloader from the `LuceneCodec`, which may explain things.  
The exception went away when moving the BlackLab jars into the webapp directly.

### 3. Create a BlackLab corpus
This must be done using the integrated format (using --integrate-external-files true)

### 4. Prepare the Solr core
- Create a directory for the core: `solr/server/solr/${corpus-name}`
- Create the following directory structure:
```
solr/server/solr/
    ${index-name}/
        data/
            index/
                contents of the BlackLab Corpus created in step 3
        schema.xml
        solrconfig.xml
```

### 5. Creating the solrconfig and schema

A default Solrconfig will probably work.
Tested using an edited version of [the solr-xslt-plugin config](https://github.com/instituutnederlandsetaal/solr-xslt-plugin/blob/0ee8901ba7cc215cebdf6372d9b9df8126fad59b/src/test/resources/solrDir/conf/solrconfig.xml), with all xslt related code removed.

#### (Optional) inspect the Index created using Luke
- Get a copy of `luke` from the correct lucene version (downloads [here](https://archive.apache.org/dist/lucene/java/))
- Add the following BlackLab jars to `luke/lib`:
    - `blacklab`
    - `blacklab-common`
    - `blacklab-engine`
    - `blacklab-content-store`
    - `blacklab-util`
- Run luke and inspect the index.

#### Add the fields
Add the fields you're interested in to the solr schema.
Below is the template schema used for testing, modify the metadata and annotations according to the current corpus.
> NOTE:
> Searching in terms doesn't work yet. Probably because of prohibited characters in the field names.

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<schema name="test" version="1.6">
    <field name="_version_" type="plong" indexed="false" stored="false"/>
    <uniqueKey>pid</uniqueKey>
  
    <!-- Removing these breaks it -->
    <fieldType name="string" class="solr.StrField" sortMissingLast="true" />
    <fieldType name="plong" class="solr.LongPointField" docValues="true"/>
    <fieldType name="text_general" class="solr.TextField" positionIncrementGap="100">
        <analyzer type="index">
            <tokenizer class="solr.StandardTokenizerFactory"/>
            <!-- <filter class="solr.StopFilterFactory" ignoreCase="true" words="stopwords.txt" /> -->
            <filter class="solr.LowerCaseFilterFactory"/>
        </analyzer>
        <analyzer type="query">
            <tokenizer class="solr.StandardTokenizerFactory"/>
            <!-- <filter class="solr.StopFilterFactory" ignoreCase="true" words="stopwords.txt" /> -->
            <!-- <filter class="solr.SynonymFilterFactory" synonyms="synonyms.txt" ignoreCase="true" expand="true"/> -->
            <filter class="solr.LowerCaseFilterFactory"/>
        </analyzer>
    </fieldType>
    
    <!-- Custom blacklab types. -->
    <fieldType name="metadata" class="solr.StrField" 
      indexed="true"
      stored="true" 
      docValues="true"
      multiValued="true" 
      omitNorms="true" 
      omitTermFreqAndPositions="false"
    />

    <fieldType name="metadata_pid" class="solr.StrField" 
      indexed="true"
      stored="true" 
      docValues="true"
      omitNorms="true" 
      omitPositions="true"
    />

    <fieldType name="term" class="solr.StrField"
      indexed="true"
      omitNorms="true"
      stored="false"
      termVectors="true"
      termPositions="true"
      termOffsets="true"
    />

    <fieldType name="numeric" class="solr.IntPointField" 
      indexed="true"
      stored="true"
      docValues="true"
      omitPositions="true"
    />

    <!-- example annotations. Searching in these yields no results for some reason. -->
    <field name="contents#_relation@s" type="term"/>
    <field name="contents#punct@i" type="term"/>
    <field name="contents#word@s" type="term"/>
    <field name="contents#word@i" type="term"/>
    <!-- <field name="contents#length_tokens" type="numeric"/> -->
    <!-- <field name="contents#cs" type="term"/> should not exist anyway -->

    <!-- example metadata -->
    <field name="pid" type="metadata_pid"/>
    <field name="cdromMetadata" type="metadata"/>
    <field name="subtitle" type="metadata"/>
    <field name="title" type="metadata"/>
    <field name="datering" type="metadata"/>
    <field name="author" type="metadata"/>
    <field name="category" type="metadata"/>
    <field name="editorLevel1" type="metadata"/>
</schema>

``` 

### 6. Run solr and register the core

Finally, start solr as you would normally (e.g. using `solr start` in the `solr/bin` directory)

In the solr admin panel, create the core with the name of the directory created above.
![creating the core](./create-solr-core.png)

Happy searching!
