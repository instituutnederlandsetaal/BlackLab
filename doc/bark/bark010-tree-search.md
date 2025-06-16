# BARK 10 - Searching tree-like structures

- **type:** change
- **status:** finished

We want to enable additional search operations on tree-like structures.

## Why?

We want to be able to search dependency and syntax trees.
This task is part of the CLARIAH-NL project.

## How?

It's now possible to index relationships between (groups of) words, such as dependency relations, and query on them. Querying is limited to explicit parent-child relationships; that is, restrictions on descendants are not supported (yet). We've extended Corpus Query Language to enable relations search. Performance seems to be decent, but certain dedicated solutions may be faster and/or provide more features.

See the [documentation](https://blacklab.ivdnt.org/guide/query-language/relations.html) for more details.

## When?

This feature is available from the development branch and (soon to be released) BlackLab 4.0.

## Impact on users

None. This is an optional feature.
