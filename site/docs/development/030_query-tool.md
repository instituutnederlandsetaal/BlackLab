# QueryTool

## What is it?

QueryTool is a simple command-driven search tool that provides a demonstration of the querying features of BlackLab. It allows you to search an index, for example to test that indexing was succesful.

## Starting the QueryTool

Once you've indexed your data (see [Getting Started](/guide/getting-started.md)), you can start QueryTool to test if indexing went well:

```bash
java -cp "blacklab-VERSION.jar:lib" nl.inl.blacklab.tools.QueryTool /data/blacklab-corpora/my-corpus
```

You can type a [BCQL](/guide/query-language/) query at the prompt. 

Here's a few things to try:

- Type `help` to see a list of commands.
- For a simple query, just enclose each word between double quotes: `"the" "egg"` searches for "the" followed by "egg"
- You can user regular expressions: `".*g"` searches for words ending with 'g'
- If you want to get more of a feel for what kinds of matches were found, try grouping by matched text using the command "group match". Then, if you want to view one of the groups, use `group *n*`.
- Type `struct` to see the structure of the index, including available annotations and metadata fields.
- If you have a `lemma` annotation, you can use `[lemma="work"]` to find forms of the verb "work". Similarly, if you have a `pos` annotation, you can use `[pos="NOU-C"]` to find common nouns.

## Paging, sorting and grouping

As a first query, type e.g. `"the"` and press Enter. The first twenty hits for this query are shown. To page through the results, use the commands `next` and `previous` (or their one-letter abbreviations). You can also change the number of hits displayed per page by using the `pagesize` command followed by a number.

You can sort the hits using the command `sort <criterium>`. The criterium can be e.g. `match`, `left` or `right`. `match` sorts by matched text, `left` sorts by left context (the text to the left of the matched text), and `right` sorts by right context. You can also specify an annotation to sort on, e.g. `word`, `lemma`, or `pos`. If you don't specify this, hits will be sorted by `word`.

You can group the hits using `group <criterium>`. Again, `match`, `left` and `right` can be used. Here, `left` and `right` group on the single word occurring to the left or right of the matched text. Just like sort, you can optionally specify an annotation (`word`, `lemma`, `pos`) to group on. `word` is the default.

Once you group hits, you enter group mode. The groups are displayed in columns: group number, group size and group identity. If there are many groups, you can page through the groups using the same command as for hits. You can also sort the groups by `identity` or `size`.

To examine the hits in a group, enter `group n`, where n is the group number displayed at the beginning of the line (the second number is the group size). To leave group mode and go back to showing all hits, enter `hits`. To get back to group mode, enter `groups`.

## BlackLab Corpus Query Language (BCQL)

The demo starts out in BlackLab Corpus Query Language (BCQL) mode, which by far the most versatile of the supported languages. This query language expresses queries as sequences of token queries. It is therefore mainly useful to find specific types of phrases in a larger text.

An example of a simple query (note that the quotes are required):

```
"the" "tab.\*"
```

This searches for the word _the_ followed by a word starting with _tab_, such as _table_. As you can see, regular expressions can be used to build token queries.
Equivalent to the above query is:

```
[word="the"] [word="tab.\*"]
```

In addition to using regular expressions to express single-token restrictions, a similar notation can be used to express restrictions on sequences of tokens. For example:

```
"no.\*"{2,}
```

This query finds two or more successive words starting with _no_, for example _no nonsense_. You can also use the regular expression operators such as `*`, `+` and `?` to build multi-token regular expressions:

```
"in" "the"? "great" "la.\*"
```

If your corpus is tagged with `lemma` (head word) and part of speech, you can search for these features as well:

```
[lemma="be"] [lemma="stay"]
```

This find forms of these verbs occurring together, e.g. _is staying_.

```
[pos="a.\*"]+ "man"
```

This finds the word _man_ with one or more adjectives applied to it.

For much more about BCQL, see the [Corpus Query Language](/guide/query-language/) documentation.

## QueryTool reference

### CorpusQL examples

Find words starting with "sta":

```
"sta.\*"
```

Find "man" preceded by at least 2 adjectives:

```
[type="a.\*"]{2,} "man"
```

Find "stad" and "dorp" with one word in between:

```
"stad" [] "dorp"
```

Find "stad" and "dorp" with 2-10 words in between:

```
"stad" []{2,10} "dorp"
```

Find all words:

```
[]
```

Find all bigrams:

```
[] []
[]{2}
```

"de" at the start of a named entity:

```
<ne\> "de"
```

"poorter" at the end of a named entity:

```
"poorter" </ne\>
```

Named entities containing "de":

```
<ne/\> containing "de"
```

"de" within a named entity:

```
"de" within <ne/\>
```

All named entities:

```
<ne/\>
```

All persons:

```
<ne type="per"/\>
```

Person names containing "van":

```
<ne type="per"/\> containing "van"
```

Locations starting with "de":

```
<ne type="loc" /\> containing <ne\> "de"
```

### Other commands

Grouping

```
group match
group match lemma
group match pos
```

Sorting

```
sort left
sort right lemma
```

Paging through results:

```
n(ext)
p(revious)
```

Change context size (number of words around hit):

```
context 3
context 10
```

Show document title in KWIC view:

```
doctitle on
doctitle off
```

Case/diacritics-sensitivity:

```
sensitive on
sensitive off
```

Filter on metadata (uses Lucene query syntax):

```
filter title.level1:"courant"
filter author.level1:"jansen"
filter author.level1:"sterkenburg" author.level2:"sterkenburg"
filter (Filter weer leegmaken)
```

Show index structure:

```
struct
structure
```

### Commandline editing

Commandline editing is available if the [JLine](http://jline.sourceforge.net/) JAR is found on the classpath.
