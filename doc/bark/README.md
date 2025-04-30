# BlackLab Archives of Relevant Knowledge (BARKs)

This is intended to be a collection of short documents about the development of BlackLab.

They describe processes and plans for BlackLab. These are intended to be like RFCs or Python's PEPs, but even less formal.

Types of BARKs:

- **Information**, some information about BlackLab, e.g. a bit of history.
- **Process**, about how we develop BlackLab, for example a backwards compatibility policy.
- **Change**, about a new feature or implementation in BlackLab, for example better support for searching tree-like structures.

See below for the current list. Each BARK can be extended and improved over time. Previous versions are recorded in version control.

Changes also have a status: planned, in development, experimental or production.


| BARK# | Name                                                                       | Description                                                                   | Type    | Status       |
|------:|----------------------------------------------------------------------------|-------------------------------------------------------------------------------|---------|--------------|
|     1 | [The story so far](bark001-story-so-far.md)                                | Quick overview of how BlackLab (Server) has changed between versions and why. | info    |              |
|     2 | [Evolution and backward compatibility](bark002-backwards-compatibility.md) | How we evolve BlackLab over time and deal with backward compatibility.        | process |              |
|     3 | [Code style](bark003-code-style.md)                                        | The layout of our code.                                                       | info    |              |
|     4 | [Performance and resource requirements](bark004-performance-resources.md)  | Why BlackLab has limits, and general ways to improve matters.                 | info    |              |
|     5 | [Relationship with Lucene](bark005-lucene.md)                              | How BlackLab uses Lucene and why staying up to date is important.             | info    |              |
|     6 | [Integrated index format](bark006-integrated-index.md)                     | All external files incorporated into the Lucene index.                        | change  | finished |
|     7 | [Solr integration](bark007-solr-integration.md)                            | How we'll make use of Solr's feature set.                                     | change  | experimental |
|     8 | [Distributed search](bark008-distributed-search.md)                        | Better scaling to huge corpora and many users.                                | change  | development  |
|     9 | [Optional content store](bark009-optional-content-store.md)                | Provide for alternatives to the built-in content store.                       | change  | planned      |
|    10 | [Searching tree-like structures](bark010-tree-search.md)                   | Search for grammatical structures (treebanks-like search)                     | change  | finished      |
|    11 | [Parallel corpora](bark011-parallel-corpora.md)                            | Corpora containing multiple languages.                                        | change  | finished     |
|    12 | [Project goals](bark012-project-goals.md)                                  | High-level goals of the BlackLab project.                                     | info    |              |
