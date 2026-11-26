--- 
title: "Introduction"
order: -1
---

# What is BlackLab?

BlackLab is a corpus search engine built on top of [Apache Lucene](http://lucene.apache.org/). It supports token-based querying and querying (dependency) relations.

::: details <b>What is a corpus search engine?</b>
A corpus search engine allows you to search through large bodies of annotated text. Each word can have a number of annotations such as headword, part of speech, etc. Spans of text may be annotated, and there may be (dependency) relations between (groups of) words. You can search all of these, looking for specific patterns.

For example, the word *chickens* would be tagged with the headword *chicken* and the part of speech *(plural) noun*.

An example of a query could be: find adjectives occurring before the headword *chicken*. This might find matches like "small chicken" or "black spotted chickens". Of course, much more complex queries can be crafted as well.
 
You may also have annotations on spans (groups of words); for example, named entities like *Albert Einstein* or *The Eiffel Tower*. Other tags could include paragraphs and sentences. You can incorporate all these annotations in your queries as well.
 
Even if your corpus does not include annotations, you can still benefit from other features that a corpus engine provides, such as sorting hits by the word before the hit, or grouping on the matched text.
:::

BlackLab was designed primarily for linguists, but is also used for other purposes, like historical research and knowledge extraction; anyone who wants to find patterns in text.

You can use it from any programming language using the REST API.

BlackLab was developed at the [Dutch Language Institute](https://ivdnt.org). It is free and open source software (Apache License 2.0).


::: tip BlackLab present and future

BlackLab 5.0.1 has a new [plugin](/development/customization/) system, [collocations support](/server/rest-api/search/collocations), metadata on [fragments](/guide/index-your-data/metadata#metadata-on-fragments) of documents, and many other improvements. See the [changelog](/development/changelog) for more details.

BlackLab 4.1.1 has [dependency relations](/guide/query-language/relations) and [parallel corpora](/guide/query-language/parallel).

BlackLab 6.0.0 will release in December and use Java 21 and Lucene 10.

:::

## Features

BlackLab's features:
- **Index annotated data**: flexibly handles different input formats with any kind of annotations (e.g. headword/part-of-speech, named entities, etc.)
- **Search for complex patterns** using the powerful [query language BCQL](query-language/) (see below)
- **Group and sort** result sets on many criteria, such as the text preceding the match.
- **Highlight** hits in a document and keyword-in-context (KWIC) view of hits.
- **Fast and scalable**: supports searching corpora with billions of tokens
- **Easy to use**, well-documented REST API
- **Highly customizable using plugins**. Add your own data preprocessors, query functions, etc.
- **Mature and actively developed** since 2010

Examples of search features:
- **Token-based querying**<br>To find _dog_ or _cat_ followed within 10 tokens by one or more adjectives and a word starting with _friend_:<br>`[lemma="dog|cat"] []{1,10} [pos="ADJ"]+ "friend.*"`
- **Search within spans**<br>To find named entities such as  _Sir Barkington the Third_:<br>`<ne/> containing "bark.*"`
- **Find collocations**<br>To find nouns that occur close to _paw_ within a sentence:<br>`meet_within([pos=NOUN], [lemma="paw"], <s/>, -3, 3)`
- **Search (dependency or other) relations**<br>To find nominal subjects for the verb _pet_<br>`[lemma="pet" & pos="VERB"] -nsubj-> [pos="NOUN"]`
- **Search parallel corpora**, such as different languages or historical versions.<br>To find Dutch translations for _good dog_ and _bad dog_<br>`"good|bad" "dog" ==>nl _`
- **Capture parts of matches**<br>Capture the noun after _canine_ or _feline_ as A<br>`"canine|feline" A:[pos="NOUN"] within <s/>`


## Try it out

To see BlackLab and BlackLab Frontend in action, have a look at either of these:

- [Brieven als Buit](https://brievenalsbuit.ivdnt.org/) ("Letters as Loot"), where you can search a collection of historical letters to and from sailors from the 17th to the 19th century
- [Corpus Gysseling](https://corpusgysseling.ivdnt.org/), a small corpus of historic Dutch (1200-1300)

With a [free CLARIN account](https://idm.clarin.eu/unitygw/pub#!registration-CLARIN%20Identity%20Registration) account, you can also check out:

- [Corpus Hedendaags Nederlands](https://chn.ivdnt.org/)
- [OpenSonar](https://opensonar.ivdnt.org/)

(others can be found in the [Who uses BlackLab?](who-uses-blacklab) section)

Here are a few searches you can try (use the _Extended_ tab for these):

- **Lemma: _koe_** Finds all forms of the word "koe" (cow)<br/>
  Other words to try: _wet_ (law), _zien_ (to see), _groot_ (large)
- **Part of speech: _NOU-C_** Find all common nouns<br/>
  Other values to try: _VRB_ (verbs), _ADJ_ (adjectives)
- **Word: _hoe*_** Find words starting with "hoe"

To learn how to get BlackLab up and running yourself, move on to [Getting Started](./getting-started).
