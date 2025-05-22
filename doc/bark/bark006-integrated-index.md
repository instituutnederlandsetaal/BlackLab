# BARK 6 - Integrated index format

- **type:** change
- **status:** finished

All external files incorporated into the Lucene index.

## Why?

- It is needed for distributed indexing and search. External files wouldn't be synchronized between nodes by SolrCloud; everything needs to be part of the Lucene index.
- The old external index format doesn't deal well with incremental indexing (esp. deleting and re-adding documents, which fragments the forward index and content store). Re-indexing a large corpus is slow.

## Related documents

- [Integrated index format files](../technical/index-formats/integrated.md)
- [Supporting distributed indexing and search](../technical/design/plan-distributed.md)

## Impact on users

We've been running the integrated format in production internally for quite some time now and are rolling out externally as well. It works well, so existing users should not be affected.

Starting with version 4.0, it is now the default index type in BlackLab for new indexes. For now, it's still possible to create a old external index by specifying an option. Support for the old external index will be dropped in version 5.0.
