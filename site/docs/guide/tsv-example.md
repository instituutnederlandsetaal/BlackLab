---
excludeFromSidebar: true # see vitepress sidebar config
description: Contains examples that we link to sometimes, but aren't in the main doc navs
---

Below is an example of a TSV file that will be parsed correctly by the builtin 'tsv' input format.

**NOTE:** the whitespace between words **MUST** be tab characters.

The first line contains the column names: word, lemma and pos. This line must be at the top of your file too (also lowercase). (The order of the columns may differ from the example below if you wish)

```
word	lemma	pos
The	the	ART
quick	quick	ADJ
brown	brown	ADJ
fox	fox	N
```
