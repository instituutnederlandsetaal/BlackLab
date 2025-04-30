# BARK 1 - The story so far

- **type:** information

This will give a general overview of how BlackLab (Server) has changed since its inception. More details can be found in the [change log](../../core/src/site/markdown/changelog.md).

## Version 1: Retrieval Demonstrator

BlackLab was started as a demonstrator in the IMPACT (IMProving ACcess to Text) project, to showcase the results of using OCR on old texts. It used Lucene 3 and added an external forward index (for fast sorting/grouping on context) and content store (for displaying concordances and full documents with highlighting).

Eventually, we switched from using the content store to generate concordances to using the forward index, which was a lot faster. For this, the 'punct' annotation (with forward index) was added that stores space and punctuation between words.

The content store went through several iterations, first adding compression, then storing each document in (compressed) blocks so random access would still be feasible.

We struggled with performance, experimenting with ways to "warm up" the disk cache or keep entire files in memory using a tool like `vmtouch`.

## Version 2: Lucene 5, BlackLab Server, Maven Central

We upgraded to Lucene 5 in 2016, and BlackLab Server was also created around this time. BlackLab gained features such as hits sampling, and the ability for its tokens file to grow beyond 2 GB. Many bugs were fixed. BlackLab was first published to Maven Central, making it much easier to use in applications.

In 2016, forward index matching was added: a way to speed up certain queries, for example prefix queries matching many terms, using the forward index.

A new configurable indexing system was added, so that users only had too write a YAML or JSON file describing their input format, instead of having to implement a Java class. XML could be parsed with VTD-XML (memory efficient) or Saxon (better XPath support).

Finally, queries could now be resolved in parallel, using multiple threads.

## Version 3: Docker, Integration tests, Instrumentation, Lucene 8

After a period of slower development, it started picking up speed again in 2022. An experimental Docker image was added, integration tests and instrumentation modules. BlackLab was upgraded to Lucene 8. The way BlackLab handled hits was improved a lot, using large arrays instead of individual Hit instances.

## Version 4: New Index Format, Dependency Relations, Parallel Corpora

Between 2022 and 2025, we created a new index format that fully incorporates everything into the Lucene index. This has several advantages, such as improving incremental indexing, reducing disk size, allowing corpora to grow larger and improving parallellisation.

We also added two major new features that will enable whole new use cases: dependency relations and parallel corpora. Indexing dependency relations allows you to search your text by grammatical structure instead of just by the sequence of tokens. Support for parallel corpora means you can search for alignments between different languages or historical versions of the same text.

## Future version: Distributed search

We've long had plans to enable distributed search using Solr Cloud, for scaling to ever larger corpora and flexibly dealing with varying numbers of users. Development is in-progress, but may take a while to finish. Don't worry, the regular, non-distributed version of BlackLab will still be maintained and improved.
