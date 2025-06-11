# The Example Application

::: tip NOTE
For an even simpler example, see [Getting started](/guide/getting-started.md).
:::

Included in the library is a simple application that demonstrates indexing and searching all in one. Note that you would normally have two separate applications doing these two tasks.

The example application can be found in the nl.inl.blacklab.example package. The main class is Example. The application is fairly well-commented, but we'll give a quick overview here as well.

The main function consists of an indexing and a searching part. Indexing is done by creating a temporary directory, instantiating the Indexer class with the DocIndexerExample class as a parameter (see below), then looping over an array and indexing its values. Normally you would use one of the Indexer.index() functions to index a file or a whole directory tree, but indexing from an in-memory array simplifies the example.

The DocIndexerExample class that we passed to the Indexer constructor defines how to parse and index a specific input format. There are also DocIndexer classes for TEI P4, FoLiA, Alto, and so on. The example uses a very simple made-up XML structure. The DocIndexerExample constructor sets up the index structure and adds handlers for different elements. The standard index structure has a single annotated field, "contents". Annotated fields are like Lucene fields, but with multiple "annotations" per token position, so we can store lemma, part of speech, or any other annotations along with the word itself. Added to the annotated field are several annotations ("pos" for part of speech, "lemma" for lemma). Lemma is added with a different sensitivity setting than pos. Because of this, we can search case-sensitively as well as case-insensitively in lemma, but not in pos.

You should be able to figure out the rest of the DocIndexerExample class reading the included comments. However, it is very important to note that you must call addValue for all annotations for each token you encounter. If you don't call these methods exactly once for annotation for each token, the token positions start getting out of synch and searching will produce the wrong results.

(Have a look at the other DocIndexer classes for a bit more advanced indexing, including indexing sentence tags and metadata)

If you view a BlackLab Lucene index in the Luke index viewer (you would need to set a breakpoint to do this in the example application, because it cleans up the temporary directory at the end), you can see how annotations work: they are stored as separate fields in the Lucene index, and combined when searching.

The search part of the main function should be pretty self-explanatory, and hopefully demonstrates how easy it is to use BlackLab. Two things perhaps require some clarification: the statement "hits.findConcordances()" uses the forward index (a special way of storing the originally indexed content that allows us to quickly assemble specific parts of documents) to build XML context snippet for each of the hits found, so they can be displayed. The following loop uses the utility method xmlToPlainText to strip out any XML tags in the snippet.

That hopefully explains the basics of indexing and searching with BlackLab. If part of this is still unclear, or you have additional questions, don't hesitate to [contact us](/guide/about.md#contact-us).
