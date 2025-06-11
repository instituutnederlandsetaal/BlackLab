# BlackLab newsletter

**NOTE:** if you want to receive an email alert for this very, very occasional newsletter, [drop us a line](/guide/about.md#contact-us).

<hr/>

## August 2016
It's been almost three years since the last BlackLab newsletter, so I wasn't kidding when I promised it would be low-volume. BlackLab has made a lot of progress since then, and I thought I should get the word out.

Oh, and I wanted to focus-test a new slogan: *BlackLab, the corpus search engine that must be Lu-cene to be believed*.

No? No. Back to the drawing board, I guess.

* <a href="#history">It's been six years already</a>
* <a href="#whitelab">Polished to a sheen: WhiteLab 2.0 interface</a>
* <a href="#collab">ColLab: Open Source for the win!</a>
* <a href="#features">Take it to the maxx(x): Features</a>
* <a href="#testing">Proof of the Black pudding: Testing</a>
* <a href="#speed">Cutting corners: Speed</a>

<a id="history"></a>

### It's been six years already

Yes, we started BlackLab in 2010. First there was just a Java library ([BlackLab Core](https://github.com/instituutnederlandsetaal/BlackLab/tree/main/core)), but we've since added a REST webservice ([BlackLab Server](https://github.com/instituutnederlandsetaal/BlackLab/tree/main/server)) so you can easily access BlackLab from any programming language. Both projects are now mature, and there's a [project website](https://blacklab.ivdnt.org/) with an overview, documentation and examples, and BlackLab is available from the Maven Central Repository, the industry-standard source for Java libraries.

BlackLab's future looks bright. Version 1.5.0 just came out (see the [change log](/development/changelog)). Have a look at our [roadmap](/guide/future-plans) to get an idea of where it's going next; support for distributed search and integration with Solr and/or ElasticSearch are high on the list. I'd love to hear from you what you would like to see.

<a id="whitelab"></a>

### Polished to a sheen: WhiteLab 2.0 interface

If you're looking for a beautiful, feature-rich user interface for BlackLab, stop looking and check out [WhiteLab 2.0](https://github.com/Taalmonsters/WhiteLab2.0)!

Version 1.0 has already proven itself in the OpenSonar, OpenChechen and VIVA Korpusportaal projects (links below). This new version is even better. It features an improved query builder and some cool visualisations. A neo4j backend is also being developed.

WhiteLab was developed as part of CLARIN/CLARIAH, for Universiteit Tilburg and INL, by Matje van de Camp of [Taalmonsters](http://www.taalmonsters.nl/). 

<a id="collab"></a>

### ColLab: Open Source for the win!

For the past 4 years, BlackLab has been available on GitHub, and it's been used and improved by many different people. The 13 pull requests 14 issues there represent just a fraction of the questions, suggestions and praise we've received via email.

Users of BlackLab include:

* INL (of course, we use it for [all](http://brievenalsbuit.inl.nl/zeebrieven/page/search) [our](http://gysseling.corpus.taalbanknederlands.inl.nl/gysseling/page/search) [corpora](http://chn.inl.nl/))
* Northwestern University ([MorphAdorner](https://bitbucket.org/pibburns/corpusindexer), a tool for morphological annotation)
* University of Tilburg ([OpenSonar](http://opensonar.inl.nl), a Dutch corpus)
* Allen Institute for Artificial Intelligence ([IKE](https://www.overleaf.com/articles/machine-teaching-for-information-extraction/hxxcqzwmpvdf/viewer.pdf), a knowledge extraction tool)
* Meertens Institute ([Fesli](http://yago.meertens.knaw.nl/apache/Fesli/), a [bilingual] children's language corpus)
* Radboud Universiteit (Basilex, a corpus of text written for children, [OpenChechen](http://corpus-studio-web.cttnww-meertens.vm.surfsara.nl/openchechen/), a Chechen corpus).
* [VIVA](http://viva-afrikaans.org/), Virtuele Instituut Vir Afrikaans (Korpusportaal, an Afrikaans corpus (paid access only))

If you're using BlackLab in a project not listed here, let me know, and I'll mention it on the project site!

<a id="features"></a>

### Take it to the maxx(x): Features
BlackLab allows you to index and search on as many annotation layers as you want, including hierarchical structures like XML. Most users keep it to around 5 layers or so, but we've heard of people going well beyond 20, and BlackLab handles it just fine.

A new addition is random hit sampling. Just let BlackLab select 1% of your results at random. Useful when you don't want to wade through millions of individual results, but you do want to get a sense of what you might find if you did.

BlackLab supports a large and growing [subset of the Corpus Query Language](https://blacklab.ivdnt.org/guide/query-language/) and you can sort and group your results on different properties, for example "the second word to the left of the matched text".

You can even capture groups of words in the matched text (just like you can with regular expressions), allowing you to analyse the structure of each of your matches in more detail. The IKE knowledge extraction tool developed by the Allen Institute (see above) uses this feature. You could also implement features like the [Sketch Engine](https://www.sketchengine.co.uk/)'s Word Sketch feature with this.

<a id="testing"></a>

### Proof of the Black pudding: Testing
For scientific research, it's very important that BlackLab matches *every* occurrence of your query pattern, and getting it just right for every possible query is difficult, to say the least. That's why we run automatic tests with long lists of queries, from simple to very complex, on real corpora. We compare different versions of BlackLab and make sure  nothing breaks. We'll be using this system to test the correctness and performance of other engines as well. The more data, the better.

In addition to those real-world search tests, BlackLab has many "unit tests", that test small parts of the system in isolation. We also run the BlackLab source code through static code analyzers to help us find even more (potential) bugs. Many improvements have already been made thanks to these useful tools.

<a id="speed"></a>

### Cutting corners: Speed
With some queries, how you approach it greatly influences how long it takes to find matches. Sometimes, rewriting a query to a variant that does the same can speed it up by orders of magnitude. Now, I could teach you, the user, how to perform this arcane magic, but I'll do you one better: BlackLab does it automatically for you.

Examples of queries that are rewritten to much faster alternatives include: queries with negative clauses ([word != "the"]), queries with "containing" or "within" clauses (&lt;s/&gt; containing "in" "the") and XML element queries in general (&lt;ne type="pers" /&gt;).

With these new optimizations, BlackLab seems to be roughly comparable in performance to the Corpus WorkBench, with each system beating the other on some queries (as shown in a paper to be published in the book "CLARIN in the Low Countries"). We have every intention of pulling ahead, though. :-)


Thanks for reading! Questions and feedback are very welcome ([how to contact us](/guide/about.md#contact-us)).
Until the next newsletter! (I'll try not to wait another three years...)


Jan Niestadt.

<hr/>

## September 2013

Hi everyone! More and more people are (thinking of) using BlackLab, and I wanted to keep you all up to date. Hence this newsletter with a quick overview of the latest BlackLab developments.

Letters as Loot: a glimpse into the past
----------------------------------------

The INL has put a new BlackLab-based application online: "Letters as Loot"!

Here you can search a corpus consisting of approximately 40,000 Dutch letters from the 17th-19th centuries, sent home by sailors to keep in touch with their loved ones. A fascinating collection and priceless material for historical linguists. [Read more about the project](http://brievenalsbuit.inl.nl/zeebrieven/page/project "http://brievenalsbuit.inl.nl/zeebrieven/page/project") or [start searching the letters](http://brievenalsbuit.inl.nl/zeebrieven/page/search "http://brievenalsbuit.inl.nl/zeebrieven/page/search") (a few suggested lemma searches: "schip" (ship), "zee" (sea), "lief" (sweet)).

Large snippets: more context, please
------------------------------------

KWIC (keyword in context) views are nice, but they don’t show a lot of the document, so it can be tricky to understand the context of a hit. It would be nice if users could click on a hit to see a bit more context around it. Say you want to show them 50 words before and after the hit when they click on it. You can easily do this now using Hits.getConcordance(Hit, int). This retrieves a single concordance with a larger context size. See it in action [here](http://brievenalsbuit.inl.nl/zeebrieven/page/search "http://brievenalsbuit.inl.nl/zeebrieven/page/search"): search for lemma 'schip' (ship) and click on one of the hits for more context.

Contextual Query Language: search like a librarian
--------------------------------------------------

We've had a version of this running for a while, but now it has been integrated into BlackLab: support for the Contextual Query Language. This language is used by many libraries and other institutions across the globe. Support in BlackLab is basic but functional. Have a look at it (try it out in the [QueryTool](http://gysseling.corpus.taalbanknederlands.inl.nl/QueryTool/ "http://gysseling.corpus.taalbanknederlands.inl.nl/QueryTool/"), for example) and see if it suits your needs. If not, let me know (see below) and we can work out how to improve it together.

Input formats: TEI, FoLiA, Alto or add your own?
------------------------------------------------

We've also had code for indexing formats like TEI and FoLiA, but it wasn't yet part of BlackLab. Now this code has been restructured and added to the project. Both TEI P4 and P5 are supported. Please note that these indexers are relatively simple, so if you want to make use of the more advanced features of these formats, those may not be supported yet.

However, adding or improving support for input formats has become much easier. Before, you had to really dive into the inner workings of BlackLab to fully grasp how to index a new file format. Now, you just have to write a few simple handlers for element types and you’re good to go. Default handlers are supplied and you can look at the supplied indexers to see how to achieve specific things. The [introductory guide](/guide/index-your-data/simple-example.md) to writing an indexer should also be helpful. If you have any questions, contact me (see below).

OpenSoNaR & WhiteLab
--------------------

The recently approved OpenSoNaR project will use BlackLab to provide end users with a search interface on the SoNaR-500 reference corpus of contemporary written Dutch. The front-end will be called WhiteLab and will eventually be open sourced. OpenSoNaR is a project of the University of Tilburg and INL. More information about OpenSoNaR can be found in the [CLARIN Annual Report 2012](http://www.clarin.nl/sites/default/files/CLARIN%20NL%20Annual%20Report%202012%20130429.pdf "http://www.clarin.nl/sites/default/files/CLARIN%20NL%20Annual%20Report%202012%20130429.pdf").

Performance: search in a snap
-----------------------------

I'm happy to report that BlackLab performance has improved in several areas.

Large BlackLab indices used to take a long time to open. This has been much improved. In addition, there’s now an option to automatically “warm up” the forward indices (i.e. prime the disk cache) in a background thread on startup. Enable this by calling Searcher.setAutoWarmForwardIndices(true); before constructing a Searcher object.

Generating concordances (hits in context, for showing KWIC (keyword in context) views) has become much faster, because we generate them from the forward indices now instead of the content store. For this to work, we’ve added a new forward index called “punct” containing whitespace and punctuation – all characters that occur between two words. So if you want the improved speed, you should recreate your index to make sure you have this new forward index available. BlackLab automatically switches over to the fast way of making concordances when it detects this forward index.

Several types of queries (notably, phrase searches) have been sped up. Significant improvements were achieved by remembering properties of the resultsets of subqueries, such as whether or not the results are all of the same length. We use these extra properties to determine when certain operations (such as sorting) can be skipped.

IndexTool: add and update documents
-----------------------------------

Before, BlackLab included a test program with each of the included indexers. This was messy and didn’t allow for a lot of customization. Now, we’ve included a single, generic indexing tool for indexing data: the IndexTool. [Here's](/guide/index-your-data/create-an-index.md#indextool "Building and testing BlackLab") how to use it. You can also pass it "--help" to see more information. For example, it is possible to pass parameters to indexer classes to customize how you want to index your documents.

We’re working on making the BlackLab index fully updateable. It is already possible to delete documents and then add new versions of them, effectively updating the documents. The forward index is smart about reusing free space. (The content store may still get fragmented if you do a lot of deletions, but this will be improved in the future.) All this means that you don't have to reindex your whole dataset every time a few documents change.

QueryTool on the web
--------------------

The QueryTool is a useful debugging aid. It lets you try out BlackLab and test your index easily (if you’re comfortable using a command-driven interface, that is). Now, there’s a web-based version of it included in BlackLab (in the examples folder), so you don’t have to log in to a server if you just want to check something quickly. It’s also an interesting example of (one way of) building a BlackLab web application. In the future, we’ll add some more examples that you can base your own applications on. You can try out the web-based version [here](http://gysseling.corpus.taalbanknederlands.inl.nl/QueryTool/ "http://gysseling.corpus.taalbanknederlands.inl.nl/QueryTool/").

Past, present and future
------------------------

Want to know about every recent improvement in BlackLab? Check out the [changelog](/development/changelog) page. Or, if you'd rather look to the future, the [roadmap](/guide/future-plans) gives a good overview of where we're headed.

If you have any questions about BlackLab, or suggestions of course, don't hesitate to [contact us](/guide/about#contact-us). Also, I will be at eLex 2013 in Tallinn in October, so if you're going there as well, we can meet to talk shop!
