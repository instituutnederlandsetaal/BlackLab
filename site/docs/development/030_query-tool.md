# QueryTool

## What is it?

QueryTool is a simple command-driven search tool that provides a demonstration of the querying features of BlackLab. It allows you to search an index using different query languages:

- [BlackLab Corpus Query Language](/guide/corpus-query-language.html) (BCQL), aimed at finding linguistic patterns in large text corpora. A dialect of the query language developed at the University of Stuttgard for the IMS Corpus Workbench (CWB) and is used in other corpus search systems as well, notably the Lexicom Sketch Engine.
- [Lucene Query Language](https://lucene.apache.org/core/8_0_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#Overview), the query parser supplied with Lucene. A familiar syntax for including/excluding terms, similar to how many search engines operate.
- Rudimentary support for [SRU CQL](http://zing.z3950.org/cql/intro.html) (Contextual Query Language), a query language used by libraries around the world.

Note that BCQL is by far the best-supported query language.

## Starting the QueryTool

You can start QueryTool to search your own data set by running it from the commandline. To learn how to quickly index and search a corpus, see [Getting Started](/guide/getting-started.md).

## Paging, sorting and grouping

As a first query, type "de" (the) and press Enter. The first twenty hits for this query are shown. To page through the results, use the commands “next” and “previous” (or their one-letter abbreviations). You can also change the number of hits displayed per page by using the “pagesize” command followed by a number.

You can sort the hits using the command “sort <criterium\>”. The criterium can be “match”, “left” or “right”. “match” sorts by matched text, “left” sorts by left context (the text to the left of the matched text), and “right” sorts by right context. You can also specify an annotation to sort on, e.g. word, lemma, or pos. If you don't specify this, hits will be sorted by word.

You can group the hits using “group <criterium\>”. Valid values are again “match”, “left” or “right”. Here, “left” and “right” group on the single word occurring to the left or right of the matched text. Just like sort, you can optionally specify an annotation (word, lemma, pos) to group on. Word is the default.

Once you group hits, you enter group mode. The groups are displayed in columns: group number, group size and group identity. If there are many groups, you can page through the groups using the same command as for hits. You can also sort the groups by “identity” or “size”.

To examine the hits in a group, enter “group n”, where n is the group number displayed at the beginning of the line (the second number is the group size). To leave group mode and go back to showing all hits, enter “hits”. To get back to group mode, enter “groups”.

## BlackLab Corpus Query Language (BCQL)

The demo starts out in BlackLab Corpus Query Language (BCQL) mode, which is the most powerful of the supported languages. This query language expresses queries as sequences of token queries. It is therefore mainly useful to find specific types of phrases in a larger text.

An example of a simple query (note that the quotes are required):

	"de" "sta.\*"

This searches for the word “de” followed by a word starting with “sta”. As you can see, regular expressions can be used to build token queries.

Equivalent to the above query is:

	[word="de"] [word="sta.\*"]

In addition to using regular expressions to express single-token restrictions, a similar notation can be used to express restrictions on sequences of tokens. For example:

	"der.\*"{2,}

This query finds two or more successive words starting with "der". You can also use the regular expression operators such as \*, + and ? to build multi-token regular expressions:

	"in" "de"? "gang" "g.\*"

If your corpus is tagged with headword and part of speech (the supplied test set is), you can search for these features as well:

	[hw="zijn"] [hw="blijven"]

This find forms of these verbs (“to be” and “to stay”) occurring together.

	[pos="a.\*"]+ "man"

This finds the word "man" with one or more adjectives applied to it.

## QueryTool reference

### CorpusQL examples

Find words starting with "sta":

	"sta.\*"

Find "man" preceded by at least 2 adjectives:

	[type="a.\*"]{2,} "man"

Find "stad" and "dorp" with one word in between:

	"stad" [] "dorp"

Find "stad" and "dorp" with 2-10 words in between:

	"stad" []{2,10} "dorp"

Find all words:

	[]

Find all bigrams:

	[] []
	[]{2}

"de" at the start of a named entity:

	<ne\> "de"

"poorter" at the end of a named entity:

	"poorter" </ne\>

Named entities containing "de":

	<ne/\> containing "de"

"de" within a named entity:

	"de" within <ne/\>

All named entities:

	<ne/\>

All persons:

	<ne type="per"/\>

Person names containing "van":

	<ne type="per"/\> containing "van"

Locations starting with "de":

	<ne type="loc" /\> containing <ne\> "de"

### Other commands

Grouping

	group match
	group match lemma
	group match pos

Sorting

	sort left
	sort right lemma

Paging through results:

	n(ext)
	p(revious)

Change context size (number of words around hit):

	context 3
	context 10

Show document title in KWIC view:

	doctitle on
	doctitle off

Case/diacritics-sensitivity:

	sensitive on
	sensitive off

Filter on metadata (uses Lucene query syntax):

	filter title.level1:"courant"
	filter author.level1:"jansen"
	filter author.level1:"sterkenburg" author.level2:"sterkenburg"
	filter (Filter weer leegmaken)

Show index structure:

	struct
	structure

### Commandline editing

Commandline editing is available if the [JLine](http://jline.sourceforge.net/) JAR is found on the classpath.
