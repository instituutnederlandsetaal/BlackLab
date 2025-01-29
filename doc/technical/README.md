# Technical documents

This directory contains various documents related to BlackLab development:

- A bit of history:
  - [Origins of BlackLab's relations search syntax](./design/design-relations-queries.md)
- Technical design docs for major changes:
  - [Distributed search](./design/plan-distributed.md)
  - [Relations, syntactic search](./design/plan-relations.md)
  - [Upcoming indexing refactor](./design/plan-indexing.md)
- [BlackLab internals](./blacklab-internals.md), which describes the structure of the BlackLab project, important classes, etc.
- [BCQL debug functions](./bcql-debug-funcs.md), which can help with diagnosing (performance) problems in BCQL queries.
- Different index formats and the structure of their files:
    - [index with external files (classic)](./index-formats/external.md) (separate forward index, content store, indexmetadata.yaml, version.dat)
    - [integrated index format](./index-formats/integrated.md) (everything incorporated in the Lucene index; will eventually become the default for new indexes) 
