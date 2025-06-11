import{_ as t,c as a,o as n,al as o}from"./chunks/framework.B3j7FRen.js";const m=JSON.parse('{"title":"Custom DocIndexer","description":"","frontmatter":{},"headers":[],"relativePath":"development/customization/docindexer.md","filePath":"development/040_customization/010_docindexer.md","lastUpdated":1749639304000}'),r={name:"development/customization/docindexer.md"};function d(i,e,s,l,u,c){return n(),a("div",null,e[0]||(e[0]=[o(`<h1 id="custom-docindexer" tabindex="-1">Custom DocIndexer <a class="header-anchor" href="#custom-docindexer" aria-label="Permalink to &quot;Custom DocIndexer&quot;">​</a></h1> <p>In most cases, you won&#39;t need to write Java code to add support for your input format. See <a href="/guide/index-your-data/simple-example.html">How to configure indexing</a> to learn how.</p> <p>In rare cases, you may want to implement your own DocIndexer. This page provides a simple tutorial for getting started with that.</p> <p>If you have text in a format that isn&#39;t supported by BlackLab yet, you will have to create a DocIndexer class to support the format. You can have a look at the DocIndexer classes supplied with BlackLab (see the nl.inl.blacklab.indexers package), but here we&#39;ll build one from the ground up, step by step.</p> <p>It&#39;s important to note that we assume you have an XML format that is tagged per word. That is, in the main content of your documents, every word should have its own XML tag. If your format is not like that, it&#39;s still possible to index it using BlackLab, but the process will be a little bit different. Please <a href="/guide/about.html#contact-us">contact us</a> and we&#39;d be happy to help.</p> <p>For this example, we&#39;ll build a simple TEI DocIndexer. We&#39;ll keep it a little bit simpler than DocIndexerTei that&#39;s already included in BlackLab, but all the basic features will be in there.</p> <p>Let&#39;s get started. The easiest way to add support for an indexer is to derive from DocIndexerXmlHandlers. This base class will take care of XML parsing for you and will call any element handlers you set at the appropriate times.</p> <p>Here&#39;s the first version of our TEI indexer:</p> <pre><code>public class DocIndexerTei extends DocIndexerXmlHandlers {
	public DocIndexerTei(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);
		DocumentElementHandler documentElementHandler = new DocumentElementHandler();
		addHandler(&quot;TEI&quot;, documentElementHandler);
		addHandler(&quot;TEI.2&quot;, documentElementHandler);
	}
}
</code></pre> <p>We create a DocumentElementHandler (an inner class defined in DocIndexerXmlHandlers) and add it as a handler for the &lt;TEI&amp;gt; and &lt;TEI.2&amp;gt; elements. Why two different elements? Because we want to support both TEI P4 (which uses &lt;TEI.2&amp;gt;) and P5 (which uses &lt;TEI&amp;gt;). The handler will get triggered every time one of these elements is found.</p> <p>Note that, if we want, we can customize the handler. We&#39;ll see an example of this later. But for this document element handler, we don&#39;t need any customization. The default DocumentElementHandler will make sure BlackLab knows where our documents start and end and get added to the corpus in the correct way. It will also automatically add any attributes of the element as metadata fields, but the TEI document elements don&#39;t have attributes, so that doesn&#39;t apply here.</p> <p>Let&#39;s say you TEI files are part of speech tagged and lemmatized, and you want to add these annotations to your corpus as well. To do so, you will need to add these lines at the top of your constructor, just after calling the superclass constructor:</p> <pre><code>// Add some extra annotations
final AnnotationWriter annotLemma = addAnnotation(&quot;lemma&quot;);
final AnnotationWriter annotPartOfSpeech = addAnnotation(&quot;pos&quot;);
</code></pre> <p>Because we will also be working with the two default annotations that every indexer gets, word and punct, we also need to store a reference to those:</p> <pre><code>// Get handles to the default annotations (the main one &amp; punct)
final AnnotationWriter annotMain = mainAnnotation();
final AnnotationWriter annotPunct = punctAnnotation();
</code></pre> <p>The main annotation (named &quot;word&quot;) generally contains the word forms of the text. The punct annotation is used to store the characters between the words: punctuation and whitespace. These two annotations together can be used to generate snippets of context when needed.</p> <p>Before we create the handler for the word tags, let&#39;s create another one for the body tag. We only want to index word tags that occur in a body tag, and we will refer back to this handler to see when we&#39;re inside a body tag. Place this line at the end of the constructor:</p> <pre><code>final ElementHandler body = addHandler(&quot;body&quot;, new ElementHandler());
</code></pre> <p>Now it&#39;s time to add a handler for word tags:</p> <pre><code>// Word elements: index as main contents
addHandler(&quot;w&quot;, new WordHandlerBase() {
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) {
		if (!body.insideElement())
			return;
		super.startElement(uri, localName, qName, attributes);

		// Determine lemma and part of speech from the attributes
		String lemma = attributes.getValue(&quot;lemma&quot;);
		if (lemma == null)
			lemma = &quot;&quot;;
		annotLemma.addValue(lemma);
		String pos = attributes.getValue(&quot;type&quot;);
		if (pos == null)
			pos = &quot;?&quot;;
		annotPartOfSpeech.addValue(pos);

		// Add punctuation
		annotPunct.addValue(StringUtil.normalizeWhitespace(consumeCharacterContent()));
	}

	@Override
	public void endElement(String uri, String localName, String qName) {
		if (!body.insideElement())
			return;
		super.endElement(uri, localName, qName);
		annotMain.addValue(consumeCharacterContent());
	}
});
</code></pre> <p>Here we see an example of customizing a default handler. The default handler is called WordHandlerBase, and it takes care of storing character positions (needed if we want to highlight hits in the original XML) and reporting progress, but nothing else. You are responsible for indexing the different annotations (the defaults word and punct, plus the we you added ourselves, lemma and pos). We use an anonymous inner class and override the startElement() and endElement() methods.</p> <p>At the top of those methods, notice how we use the body handler we added earlier: we check if we&#39;re inside a body element, and return if not, even before calling the superclass method. This makes sure any &lt;w/&gt; tags outside &lt;body/&gt; tags are skipped.</p> <p>The values for lemma and part of speech are taken from the element attributes and added to the annotations we created earlier; simple enough. For the main annotation (&quot;word&quot;, the actual word forms) and the &quot;punct&quot; annotation (punctuation and whitespace between words), we use the consumeCharacterContent() method. All character content in the XML is collected, and calling consumeCharacterContent() returns the context collected since the last call, and clears the buffer again. At the start of each word, we consume the character content and add it to the punct annotation; at the end of each word, we do the same again and add it to the main annotation (&quot;word&quot;).</p> <p>If we also want to capture sentence tags, so we can search for sentences containing a word for example, we can add this handler:</p> <pre><code>// Sentence tags: index as tags in the content
addHandler(&quot;s&quot;, new InlineTagHandler() {
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) {
		if (body.insideElement())
			super.startElement(uri, localName, qName, attributes);
	}

	@Override
	public void endElement(String uri, String localName, String qName) {
		if (body.insideElement())
			super.endElement(uri, localName, qName);
	}
});
</code></pre> <p>We only customize this handler because we want to make sure we don&#39;t capture sentence tags outside body elements.</p> <p>We&#39;re almost done, but there&#39;s one subtle thing to take care of. What happens to the bit of punctuation after the last word? The way we have our indexer now, it would never get added to the corpus. We should customize the body handler a little bit to take care of that:</p> <pre><code>	final ElementHandler body = addHandler(&quot;body&quot;, new ElementHandler() {
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			consumeCharacterContent(); // clear it to capture punctuation and words
		}

		@Override
		public void endElement(String uri, String localName, String qName) {

			// Before ending the document, add the final bit of punctuation.
			annotPunct.addValue(StringUtil.normalizeWhitespace(consumeCharacterContent()));

			super.endElement(uri, localName, qName);
		}
	});
});
</code></pre> <p>Note how we&#39;ve also overridden the startElement() method in order to clean the character content buffer at the start of the body element. If we didn&#39;t do that, we might get some junk at the first position of the punct annotation.</p> <p>That&#39;s all there is to it, really. Well, we haven&#39;t covered capturing metadata, mostly because TEI doesn&#39;t have a clear standard for how metadata is represented. But indexing your particular type of metadata is easy. There&#39;s a few helper classes: MetadataElementHandler assumes the matched element name is the name of your metadata field and the character content is the value. MetadataAttributesHandler stores all the attributes from the matched element as metadata fields. MetadataNameValueAttributeHandler assumes the matched element has a name attribute and a value attribute (the attribute names can be specified in the constructor) and stores those as metadata fields.</p> <p>If you need something fancy for metadata, have a look at the DocIndexers in BlackLab and the implementation of the helper classes mentioned above. It&#39;s not hard to make a version that will work for you.</p> <p>That concludes this simple tutorial. If you have a question, please <a href="/guide/about.html#contact-us">contact us</a>.</p>`,63)]))}const p=t(r,[["render",d]]);export{m as __pageData,p as default};
