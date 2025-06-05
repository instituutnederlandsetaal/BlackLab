# Blog

*A technical look into the BlackLab corpus retrieval engine. Or: the exciting search for very specific needles in large, annotated haystacks of text.*

3 - What BlackLab brings to the Lucene table
--------------------------------------------

Lucene is a great text search library: it's fast, has a lot of useful features and is well-documented. Of course, it doesn't do everything we want out of the box, or we wouldn't have developed BlackLab. Fortunately, Lucene is free software and is relatively easy to extend with your own functionality. So, this time around, let's take a look at some of the ways in which we've added functionality to Lucene for BlackLab. That means we're going to get a little more technical.

**Word Properties**

An important feature we wanted to provide with BlackLab was the ability to store and search for multiple properties (also called tags or annotations) associated with words. A common example of this would be to store the headword and part-of-speech of each word, but it could be any extra information about a word, like how high it scores on your "sumptuous words to casually use in a blog post" list (now I can cross 'sumptuous' off mine).

Lucene can index multiple values for a word position in a single field, but you would have to differentiate the types of values with a prefix or some such. Storing type information in string prefixes makes me feel dirty and unworthy as a programmer, so I decided instead to use a separate field per property and make sure the position information on each field is synchronized so we can combine them in queries. It takes a little bit of convincing to stop Lucene complaining that you're mixing fields in this way, but that wasn't too hard. We call the aggregate of fields containing properties about your words an "annotated field", and you can query such a field directly in BlackLab, for example using Corpus Query Language:

	[hw = 'feed' & pos = 'NOU']

**Regular Expression Queries**

The second key feature on our wish list was the ability to search for queries with a regular expression structure at the word level. So for example, we want to search for an article ("a", "an" or "the"), followed by two or more adjectives, followed by a form of the headword "cat". Examples of hits for that query would be "the sleepy long-haired white cats" and "an inquisitive black cat". We could denote this query in Corpus Query Language as follows:

	[pos = "ART"] [pos = "ADJ"]{2,} [hw = "cat"]

The first thing to realize is that we need SpanQueries for this purpose. Regular Queries can't really deal with positional information aside from PhraseQuery, which is too limited for us. But while many SpanQuery classes are included in Lucene, we were missing a few. For this functionality, we needed a "repetition query" (for parts of the query that may occur multiple times in direct succession) and a true "sequence query" (to find a number of subquery hits directly adjacent to one another; SpanNearQuery seems to come close, but turns out not to quite fit our purposes).

So, I implemented SpanQueryRepetition and SpanQuerySequence for this purpose. This involved some sorting and uniquifying (another one off the list!) trickery to make sure we generated all possible sequences, didn't include doubles, and didn't violate Lucene's convention that SpanQuery results are ordered by position.

**Match All Operator**

A third interesting challenge was how to support "match all" operators. For example:

	[]{2} "apple"

This searches for apple preceded by two arbitrary words. The match all operator can be used as a prefix, infix or postfix operator, and we wanted generic support for all these situations. This was solved by creating a class SpanQueryExpansion, which expands hits from another query by a number of tokens to the left or right. Again, a bit of craftiness was required to maintain proper sort order and prevent doubles.

There's plenty of stuff we haven't covered yet, such as how BlackLab enables fast sorting and grouping on matched text or the words around it, but that's a subject for another post. I'm also working on several other improvements, like a generic way to index and search for XML tags in the text, so you can search for words at the beginning of a sentence, or search for tagged named entities (like the names of people or places). The basic functionality for this is in BlackLab now, and should mature over the next few months.

Hopefully, now you've got a better idea of how BlackLab builds on the many features Lucene already provides to enable new ways of searching for patterns in annotated texts. As always, questions, suggestions and code contributions are more than welcome. See the [README](https://github.com/instituutnederlandsetaal/BlackLab#readme "https://github.com/instituutnederlandsetaal/BlackLab#readme") for contact information.

2 - BlackLab's niche
--------------------

Projects are like animals. Some are predatory, and consume all available resources. Others are more like gentle herbivores: slowly but surely getting the job done.

As the name suggests, BlackLab is a friendly, inquisitive companion who is great at search and retrieval. Unlike it's namesake, however, it does not stick its wet nose in your face at 5:30 AM. Although I guess we could add that feature if enough people request it.

**Evolution**

Like animals, projects evolve from what was around before, they find a niche for themselves, and they either thrive or go extinct.

BlackLab evolved out of our need for a better way to search through large bodies of text. For the IMPACT project, we needed to show the advances made in digitizing and searching in historical texts. Once we had the basics, it made sense to develop it further and use it to build interfaces on our other corpora.

Wasn't BlackLab's niche already occupied by other projects? No, we don't think so. Of course there are other corpus retrieval engines. Some of those we can't afford, some don't really fit with our chosen technologies, some aren't as full-featured as we needed. And importantly, most aren't based on Apache Lucene.

**Symbiosis**

BlackLab lives in symbiosis with Lucene: it relies on it and adds a lot of useful functionality to it. Building on top of an active open source project like Lucene is great, because it saves us a lot of work, and ensures that progress for Lucene equals progress for BlackLab.

For example, recently Lucene 4.0 came out, and it appears to speed up several important operations: regular expression matching and fuzzy search. So when we update BlackLab to work with Lucene 4.0, many searches will likely be faster.

**Ecosystem**

That, for me, defines BlackLab's niche in the ecosystem of text search. It's fully written in Java and has a mature, actively developed open source project as its basis. It's a great position to start from and allows us to add advanced features to BlackLab without ever having to worry about the low-level side of things.

We believe this niche needed filling, so we went ahead and gave it a shot. We invite you to [try it out](https://github.com/instituutnederlandsetaal/BlackLab/wiki "https://github.com/instituutnederlandsetaal/BlackLab/wiki"). See for yourself if this is just a short-lived evolutionary oddity, or a viable new species. If you have any questions or comments, let us know. Together we can make BlackLab thrive!

1 - Introducing BlackLab
------------------------

A pet peeve of mine are project websites that don't describe the project, but instead launch into a detailed account of some bugfix, some obscure planned feature, or what the author had for dessert at a recent conference. So, I should probably start by explaining what BlackLab is, who would use it and why, and how you get started using it.

**What?**

The first part is easy: BlackLab is a corpus retrieval engine written in Java and based on [Apache Lucene](http://lucene.apache.org/core/ "http://lucene.apache.org/core/"). Lucene is a text search library, which allows you to quickly find occurrences of words in a text. A corpus retrieval engine allows you to do more: you can search for particular word patterns in a text, similar to the way regular expressions allow you to search for patterns at the individual character level. In addition to just searching for words, you can even search for word classes, for example all adjectives, or all forms of the word 'see'.

**Who and why?**

Who would want to do any of this, and why? Well, all sorts of people, for all sorts of reasons. But it is particularly useful to researchers, who are often looking for specific turns of phrase, or specific linguistic constructions. A historian might be trying to find all occurrences of a name that may have many different variations in word boundaries and spelling. A lexicographer might be looking for all adjectives applied to a certain noun. And so on.

**How?**

How do you build an application that uses BlackLab to accomplish this? Well, that's a bit too complex to discuss exhaustively here, but I can certainly explain the basics. First you need your data in a structured format, preferably XML. Some good options (supported by BlackLab) are TEI, FoLiA, PageXML or Alto. Then you have to instruct BlackLab to index your data into a new form that it can process efficiently. Finally, you build an application that runs queries on this indexed data. This might be a web application, web service or mobile app; it really doesn't matter.

So, we've cleared the first hurdle: you know what BlackLab is and why you might be interested in using it. In the next few posts, I'd like to expand on this basic information, explaining why and how we developed BlackLab at the INL, how we're using it at the moment, and what our future plans are. Oh, and in case anyone's wondering: I had the [Crêpe Suzette](http://en.wikipedia.org/wiki/Cr%C3%AApe_Suzette), and it was wonderful.
