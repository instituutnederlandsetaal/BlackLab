# Token-based querying

BlackLab started out as purely a token-based corpus engine. This section shows BCQL's token-based features.

## Matching a token

With BCQL you can specify a pattern of tokens (i.e. words) you're looking for.

A simple such pattern is:

```
[word="man"]
```

This simply searches for all occurrences of the word _man_.

Each corpus has a default annotation; usually _word_. Using this fact, this query can be written even simpler:

```
"man"
```

NOTE: In BlackLab's CQL dialect, double and single quotes are interchangeable, but this is not true for all corpus engines.
We will use the more standard double quotes in our examples.

### Multiple annotations

If your corpus includes the per-word annotations _lemma_ (i.e. headword) and _pos_ (part-of-speech, i.e. noun, verb, etc.), you can query those as well. 

For example:

```
[lemma="search" & pos="noun"]
```

This query would match _search_ and _searches_ where used as a noun. (your data may use different part-of-speech tags, of course)

### Negation

You can use the "does not equal" operator (!=) to search for all words except nouns:

```
[pos != "noun"]
```

### Regular expressions

The strings between quotes can also contain "wildcards", of sorts. To be precise, they are [regular expressions](http://en.wikipedia.org/wiki/Regular_expression), which provide a flexible way of matching strings of text. For example, to find _man_ or _woman_ (in the default annotation _word_), use:

```
"(wo)?man"
```

And to find lemmas starting with _under_, use:

```
[lemma="under.*"]
```

Explaining regular expression syntax is beyond the scope of this document, but for a complete overview, see [regular-expressions.info](http://www.regular-expressions.info/) for a general overview, or [Lucene's regular expression syntax](https://www.elastic.co/guide/en/elasticsearch/reference/current/regexp-syntax.html) specifically, which has a few quirks.

::: details Escaping and literal strings
To find characters with special meaning in a regular expression, such as the period, you need to escape them with a backslash:

```
[lemma="etc\."]
```

Alternatively, you can use a "literal string" by prefixing the string with an `l`:

```
[lemma=l'etc.']
```

Note that some unexpected characters may be considered special regex characters, such as `<` and `>`. See the above link to Lucene's regex documentation for more details.
:::

### Matching any token

Sometimes you want to match any token, regardless of its value.

Of course, this is usually only useful in a larger query, as we will explore next. But we'll introduce the syntax here.

To match any token, use the match-all pattern, which is just a pair of empty square brackets:

```
[]
```

### Case- and diacritics sensitivity

BlackLab defaults to (case and diacritics) _insensitive_ search. That is, it ignores differences in upper- and lowercase, as well as diacritical marks (accented characters). So searching for `"panama"` will also find _Panama_.

To match a pattern sensitively, prefix it with `(?-i)`:

```
"(?-i)Panama"
```

## Sequences

### Simple sequences

You can search for sequences of words as well (i.e. phrase searches, but with many more possibilities). To search for the phrase _the tall man_, use this query:

```
"the" "tall" "man"
```

It might seem a bit clunky to separately quote each word, but this allows us the flexibility to specify exactly what kinds of words we're looking for.

For example, if you want to know all single adjectives used with man (not just _tall_), use this:

```
"an?|the" [pos="ADJ"] "man"
```

This would also match _a wise man_, _an important man_, _the foolish man_, etc.

If we don't care about the part of speech between the article and _man_, we can use the match-all pattern we showed before:

```
"an?|the" [] "man"
```

This way we might match something like _the cable man_ as well as _a wise man_.

### Repetitions

Really powerful token-based queries become possible when you use the regular expression operators on whole tokens as well. If we want to see not just single adjectives applied to _man_, but multiple as well:

```
[pos="ADJ"]+ "man"
```

This query matches _little green man_, for example. The plus sign after `[pos="ADJ"]` says that the preceding part should occur one or more times (similarly, `*` means "zero or more times", and `?` means "zero or once").

If you only want matches with exactly two or three adjectives, you can specify that too:

```
[pos="ADJ"]{2,3} "man"
```

Or, for two or more adjectives:

```
[pos="ADJ"]{2,} "man"
```

You can group sequences of tokens with parentheses and apply operators to the whole group as well. To search for a sequence of nouns, each optionally preceded by an article:

```
("an?|the"? [pos="NOU"])+
```

This would, for example, match the well-known palindrome _a man, a plan, a canal: Panama!_ (provided the punctuation marks were not indexed as separate tokens)

### Lookahead/lookbehind

::: tip Supported from v4.0
This feature will be supported from BlackLab 4.0 (and current development snapshots).
:::

Just like most regular expressions engines, BlackLab supports lookahead and lookbehind assertions. These match a position in the text but do not consume any tokens. They are useful for matching a token only if it is followed or preceded by other token(s).

For example, to find the word _cat_ only if it is followed by _in the hat_:

```
"cat" (?= "in" "the" "hat")
```

Similarly, to find the word _dog_, but only if it is preceded by _very good_:

```
(?<= "very" "good") "dog"
```

Negative lookahead is also supported. To only find _cat_ if it is not followed by _call_:

```
"cat" (?! "call")
```

And negative lookbehind:

```
(?<! "bad") "dog"
```

### Finding punctuation

(The following applies to corpora that index punctuation as the `punct` property of the next word, not to corpora that index punctuation as a separate token)

Often in BlackLab, the punctuation and spaces between words will be indexed as a property named `punct`. This property always contains the spaces and interpunction that occurs before the word where it is indexed.

Because of where it is indexed, it can be tricky to find specific punctuation _after_ a certain word. To find the word `dog` followed by a comma, you'd need to do something like this:

```
"dog" [punct=", *"]
```

Because spaces are also indexed with the `punct` annotation, you need to include them in the regex as well.

BlackLab supports _pseudo-annotations_ that can help with this. You can pretend that every corpus has a `punctBefore` and `punctAfter` annotation. So you can write the above query as:

```
[word="dog" & punctAfter=","]
```

Note that in special cases where more than one punctuation mark is indexed with a word, you may still need to tweak your regular expression. For example, if your input data contained the fragment "(white) dog, (black) cat", the above query would not work because the `punct` annotation for the word after `dog` would have the value `, (`. You'd have to use a more general regular expression:

```
[word="dog" & punctAfter=",.*"]
```

Note that `punctBefore` and `punctAfter` look like annotations when used in the query, but are not; they will not be in the results and you cannot group on them. You can group on the `punct` annotation they are based on, because that is actually a part of the index.


## Spans

Your input data may contains "spans": marked regions of text, such as paragraphs, sentences, named entities, etc. If your input data is XML these may be XML elements, but they may also be marked in other ways. Non-XML formats may also define spans.

Finding text in relation to these spans is done using an XML-like syntax, regardless of the exact input data format.

### Finding spans

If you want to find all the sentence spans in your data:

```
<s/>
```

Note that forward slash before the closing bracket. This way of referring to the span means "the whole span". Compare this to `<s>`, which means "the start of the span", and `</s>`, which means "the end of the span".

So to find only the starts of sentences, use:

```
<s>
```

This would find zero-length hits at the position before the first word. Similarly, `</s>` finds the ends of sentences. Not very useful, but we can combine these with other queries.

### Words at the start or end of a span

More useful might be to find the first word of each sentence:

```
<s> []
```

or sentences ending in _that_:

```
"that" </s>
```

(Note that this assumes the period at the end of the sentence is not indexed as a separate token - if it is, you would use `"that" '.' </s>` instead)

### Words inside a span

You can also search for words occurring inside a specific span. Say you've run named entity recognition on your data, so all names of people are tagged with the `person` span. To find the word _baker_ as part of a person's name, use:

```
"baker" within <person/>
```

The above query will just match the word _baker_ as part of a person's name. But you're likely more interested in the entire name that contains the word _baker_. So, to find those full names, use:

```
<person/> containing 'baker'
```

::: tip Using a regular expression for the span name
You can match multiple span types (e.g. both `<person/>` and `<location/>`) using a regular expression:

```
"baker" within <"person|location" />
```

To match all spans in the corpus, use:

```
<".+" />
```

:::

::: tip Capturing all overlapping spans
If you want to know all spans that overlap each of your hits (for example, the sentence, paragraph and chapter it occurs 
in, if you've indexed those as spans), pass `withspans=true` as a parameter.

Alternatively, you can adjust your query directly:

```
with-spans("baker")
```

or

```
with-spans("baker", <"person|location" />, "props")
```

The second example will capture a list of matching spans in the match info named `props`.

Only the first parameter for `with-spans` is required. The second parameter defaults to `<".+"/>` (all tags); the third defaults to `"with-spans"`.

:::

### Universal operators

As you might have guessed, you can use `within` and `containing` with any other query as well. For example:

```
([pos="ADJ"]+ containing "tall") "man"
```

will find adjectives applied to man, where one of those adjectives is _tall_.

## Captures

### Part of the match

Just like in regular expressions, it is possible to "capture" part of the match for your query as a named group. Everything you capture is returned with the hit in a response section called _match info_. You label each capture with a name.

Example:

```
"an?|the" A:[pos="ADJ"] "man"
```

The adjective part of the match will be captured in a group named _A_.

You can capture multiple words as well:

```
"an?|the" adjectives:[pos="ADJ"]+ "man"
```

This will capture the adjectives found for each match in a captured group named _adjectives_.

The capture name can also just be a number:

```
"an?|the" 1:[pos="ADJ"]+ "man"
```

<!--
::: details Compared to other corpus engines
CWB and Sketch Engine offer similar functionality, but instead of capturing part of the match, they label a single token.

BlackLab can capture a span of tokens of any length, capture relations and spans with all their details, and even capture lists of relations, such as all relations in a sentence (relations are described later in this document).
:::
-->

::: tip Spans are captured automatically
If your query involves spans like `<s/>`, it will automatically be captured under the span name (`s` in this case). You can override the capture name by specifying it in the query, e.g. `A:<s/>`.
:::

### Constraints

If you tag certain tokens with labels, you can also apply "capture constraints" (also known as "global constraints") 
on these tokens. This is a way of relating different tokens to one another, for example requiring that they correspond 
to the same word:

```
A:[] "by" B:[] :: A.word = B.word
```

This would match _day by day_, _step by step_, etc.

::: details Multiple-value annotations and constraints

Unfortunately, capture constraints can only access the first value indexed for an annotation. If you need this kind of
functionality in combination with multi-values constraints, you'll have to find a way around this limitation.

Some queries can be rewritten so they don't need a capture constraint. For example,
`A:[word="some"] B:[word="queries"] :: A.lemma="some" & B.lemma="query"` can also be written as
`A:[word="some" & lemma="some"] B:[word="queries" & lemma="query"]`, which does work with multiple annotation values.
But this is rare.

In other cases, you might be able to add extra annotations or use spans ("inline tags") to get around this limitation.

:::

### Constraint functions

You can also use a few special functions in capture constraints. For example, ensure that words occur in the right order:

```
(<s> containing A:"cat") containing B:"fluffy" :: start(B) < start(A)
```

Here we find sentences containing both _cat_ and _fluffy_ (in some order), but then require that _fluffy_ occurs before _cat_.

Of course this particular query would be better expressed as `<s/> containing "fluffy" []* "cat"`. As a general rule, 
capture constraints can be a bit slower, so only use them when you need to.

### Local capture constraints

Unlike most other corpus engines, BlackLab allows you to place capture constraints inside a parenthesized expression.
Be careful that the constraint only refers to labels that are captured inside the parentheses, though!

This is valid and would match _over and over again_:

```
(A:[] "and" B:[] :: A.word = B.word) "again"
```

This is NOT valid (may not produce an error, but the results are undefined):

```
A:[] ("and" B:[] :: A.word = B.word) "again"   # BAD
```


