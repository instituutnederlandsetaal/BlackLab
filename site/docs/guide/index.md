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

BlackLab was designed primarily for linguists, but is also used for other purposes, like historical research and knowledge extraction.

It is available as a web service, allowing you to use it from any programming language.

BlackLab was developed at the [Dutch Language Institute](https://ivdnt.org). It is free and open source software (Apache License 2.0).


## Features

BlackLab's features include:

- **Index annotated text**, so you can search for specific headwords or parts of speech.
- **Easy to use**, well-documented REST API.
- **Fast and scalable**: search very large corpora in seconds.
- **Index your data** using a built-in format or by writing a configuration file.
- **Search for complex patterns** using the powerful [BlackLab Corpus Query Language](query-language/)
- **Search within spans** to e.g. find named entities containing _tower_ at the end of a sentence.
- **Search (dependency) relations**, to find specific (tree) structures in your text. **(NEW in v4)**
- **Search parallel corpora**, so you can find alignments between different languages or historical versions. **(NEW in v4)**
- **Capture** parts of matches.
- **Group and sort** result sets on many criteria, such as the text preceding the match.
- **Highlight** hits in a document and keyword-in-context (KWIC) view of hits.
- **Mature and actively developed** since 2010, with many [plans for the future](future-plans.md).


## Try it online

For a quick example of the BlackLab Frontend web application, have a look at either of these:

- [Brieven als Buit](https://brievenalsbuit.ivdnt.org/) ("Letters as Loot"), where you can search a collection of historical letters to and from sailors from the 17th to the 19th century
- [Corpus Gysseling](https://corpusgysseling.ivdnt.org/), a small corpus of historic Dutch (1200-1300)

With a [free CLARIN account](https://idm.clarin.eu/unitygw/pub#!registration-CLARIN%20Identity%20Registration) account, you can also check out:

- [Corpus Hedendaags Nederlands](https://chn.ivdnt.org/)
- [OpenSonar](https://opensonar.ivdnt.org/)

Here are a few searches you can try (click on the _Extended_ tab):

- **Lemma: "koe"** Finds all forms of the word "koe" (cow)<br/>
  Other words to try: "wet" (law), "zien" (to see), "groot" (large)
- **Part of speech: "NOU-C"** Find all common nouns<br/>
  Other values to try: "VRB\*" (verbs), "ADJ\*" (adjectives)
- **Word: "coe"** Find a specific historic spelling of "koe"

This is just a small sample of the capabilities of BlackLab.

If you're excited about the possibilities and want to get BlackLab up and running yourself, move on to [Getting Started](./getting-started).
