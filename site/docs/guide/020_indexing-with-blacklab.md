# Indexing with BlackLab

## IndexTool

IndexTool is a simple commandline application to create a corpus and add documents to it.

Get the blacklab JAR and the required libraries (see [Getting started](/guide/getting-started.md#getting-blacklab)). The libraries should be in a directory called "lib" that's in the same directory as the BlackLab JAR (or elsewhere on the classpath).

Start the IndexTool without parameters for help information:

    java -cp "blacklab.jar:lib" nl.inl.blacklab.tools.IndexTool
 
(this assumes `blacklab.jar` and the `lib` subdirectory containing required libraries are located in the current directory)

(if you're on Windows, replace the classpath separator colon `:` with a semicolon `;`)

To create a new index:

    java -cp "blacklab.jar:lib" nl.inl.blacklab.tools.IndexTool create INDEX_DIR INPUT_FILES FORMAT

To add documents to an existing index:

    java -cp "blacklab.jar:lib" nl.inl.blacklab.tools.IndexTool add INDEX_DIR INPUT_FILES FORMAT

If you specify a directory as the `INPUT_FILES`, it will be scanned recursively. You can also specify a file glob (such as `*.xml`; single-quote it if you're on Linux so it doesn't get expanded by the shell) or a single file. If you specify a `.zip` or `.tar.gz` file, BlackLab will automatically index the contents.

For example, if you have TEI data in `/data/input/my-tei-files` and want to index your corpus to `/data/blacklab-corpora/my-corpus`, run the following command:

    java -cp "blacklab.jar:lib" nl.inl.blacklab.tools.IndexTool create /data/blacklab-corpora/my-corpus /data/input/my-tei-files tei

Your data is indexed and placed in a new BlackLab index in the `/data/blacklab-corpora/my-corpus` directory.

If you don't specify a glob, IndexTool will index `*.xml` by default. You can specify a glob (like `*.txt` or `*` for all files) to change this.

Also, please note that if you're indexing large files, you should [give java more than the default heap memory](https://docs.oracle.com/cd/E15523_01/web.1111/e13814/jvm_tuning.htm#PERFM161), using the `-Xmx` option. For really large files, and if you have the memory, you could use `-Xmx 6G`, for example.

To delete documents from an index:

    java -cp "blacklab.jar:lib" nl.inl.blacklab.tools.IndexTool delete INDEX_DIR FILTER_QUERY
    
Here, `FILTER_QUERY` is a metadata filter query in Lucene query language that matches the documents to delete. Deleting documents and re-adding them can be used to update documents.

## Supported formats

BlackLab supports a number of input formats that are common in corpus linguistics:

* `tei` ([Text Encoding Initiative](http://www.tei-c.org/), a popular XML format for linguistic resources, including corpora. indexes content inside the 'body' element; assumes part of speech is found in an attribute called 'type')
* `sketch-wpl` (the TSV/XML hybrid input [format the Sketch Engine/CWB use](https://www.sketchengine.co.uk/documentation/preparing-corpus-text/))
* `chat` ([Codes for the Human Analysis of Transcripts](https://en.wikipedia.org/wiki/CHILDES#Database_Format), the format used by the CHILDES project)
* `folia` (a [corpus XML format](https://proycon.github.io/folia/) popular in the Netherlands)
* `tsv-frog` (tab-separated file as produced by the [Frog annotation tool](https://languagemachines.github.io/frog/))

BlackLab also supports these generic file formats:

* `csv` (Comma-Separated Values file that should have column names "word", "lemma" and "pos")
* `tsv` (Tab-Separated Values file that should have column names "word", "lemma" and "pos")
* `txt` (A plain text file; will tokenize on whitespace and index word forms)

A number of less common formats are also supported:

* `pagexml` (OCR XML format)
* `alto` ([another OCR XML format](http://www.loc.gov/standards/alto/))

To add support for your own format, you just have to [write a configuration file](/guide/how-to-configure-indexing.md). (There's a [legacy way](/development/customization/docindexer.md) too, but that involves writing Java code and you probably don't need it) Please [contact us](/guide/about.md#contact-us) if you have any questions.

If you choose the first option, specify the format name (which must match the name of the .blf.yaml or .blf.json file) as the FORMAT parameter. IndexTool will search a number of directories, including the current directory and the (parent of the) input directory for format files.

If you choose the second option, specify the fully-qualified class name of your DocIndexer class as the FORMAT parameter.

## Add your own format

There's two approaches to adding support for a new input format in BlackLab. The preferred one is to write an input format configuration file in either YAML or JSON format. See [How to configure indexing](/guide/how-to-configure-indexing.md).

The other way is considered somewhat legacy, although it may still be useful in rare cases. It may offer slightly better performance and complete control over the indexing process. This is documented below. You're probably better off with the configuration file approach, though.

## Faster indexing

IndexTool will try to index two documents at the same time by default. If you have enough CPU cores and memory, you can increase this number by setting the `--threads n` option, where n is the number of threads to use (i.e. documents to index at the same time).

If you find that IndexTool is running out of memory, or becoming very slow, try a lower number of threads instead.

::: warning 

- multi-threaded indexing currently works per-file, so if all your documents are in a single large file, only 1 thread will be used.
- large files appear to gradually degrade indexing performance as we get further into the file.

For these reasons, it is currently better to spread your documents over multiple files, although it is not necessary to limit yourself to 1 document per file. Just make sure your files aren't larger than a few MB.

:::
