# Custom DocIndexer

In most cases, you won't need to write Java code to add support for your input format. See [How to configure indexing](/guide/how-to-configure-indexing.md) to learn how. 
For more information about indexing in general, see [Indexing with BlackLab](/guide/indexing-with-blacklab.md).

In rare cases, you may want to implement your own DocIndexer. This page provides a simple tutorial for getting started with that.

If you have text in a format that isn't supported by BlackLab yet, you will have to create a DocIndexer class to support the format. You can have a look at the DocIndexer classes supplied with BlackLab (see the nl.inl.blacklab.indexers package), but here we'll build one from the ground up, step by step.

It's important to note that we assume you have an XML format that is tagged per word. That is, in the main content of your documents, every word should have its own XML tag. If your format is not like that, it's still possible to index it using BlackLab, but the process will be a little bit different. Please [contact us](/guide/about.md#contact-us) and we'd be happy to help.

For this example, we'll build a simple TEI DocIndexer. We'll keep it a little bit simpler than DocIndexerTei that's already included in BlackLab, but all the basic features will be in there.

Let's get started. The easiest way to add support for an indexer is to derive from DocIndexerXmlHandlers. This base class will take care of XML parsing for you and will call any element handlers you set at the appropriate times.

Here's the first version of our TEI indexer:

	public class DocIndexerTei extends DocIndexerXmlHandlers {
		public DocIndexerTei(Indexer indexer, String fileName, Reader reader) {
			super(indexer, fileName, reader);
			DocumentElementHandler documentElementHandler = new DocumentElementHandler();
			addHandler("TEI", documentElementHandler);
			addHandler("TEI.2", documentElementHandler);
		}
	}

We create a DocumentElementHandler (an inner class defined in DocIndexerXmlHandlers) and add it as a handler for the &lt;TEI\&gt; and &lt;TEI.2\&gt; elements. Why two different elements? Because we want to support both TEI P4 (which uses &lt;TEI.2\&gt;) and P5 (which uses &lt;TEI\&gt;). The handler will get triggered every time one of these elements is found.

Note that, if we want, we can customize the handler. We'll see an example of this later. But for this document element handler, we don't need any customization. The default DocumentElementHandler will make sure BlackLab knows where our documents start and end and get added to the index in the correct way. It will also automatically add any attributes of the element as metadata fields, but the TEI document elements don't have attributes, so that doesn't apply here.

Let's say you TEI files are part of speech tagged and lemmatized, and you want to add these annotations to your index as well. To do so, you will need to add these lines at the top of your constructor, just after calling the superclass constructor:

	// Add some extra annotations
	final AnnotationWriter annotLemma = addAnnotation("lemma");
	final AnnotationWriter annotPartOfSpeech = addAnnotation("pos");

Because we will also be working with the two default annotations that every indexer gets, word and punct, we also need to store a reference to those:

	// Get handles to the default annotations (the main one & punct)
	final AnnotationWriter annotMain = mainAnnotation();
	final AnnotationWriter annotPunct = punctAnnotation();

The main annotation (named "word") generally contains the word forms of the text. The punct annotation is used to store the characters between the words: punctuation and whitespace. These two annotations together can be used to generate snippets of context when needed.

Before we create the handler for the word tags, let's create another one for the body tag. We only want to index word tags that occur in a body tag, and we will refer back to this handler to see when we're inside a body tag. Place this line at the end of the constructor:

	final ElementHandler body = addHandler("body", new ElementHandler());

Now it's time to add a handler for word tags:

	// Word elements: index as main contents
	addHandler("w", new WordHandlerBase() {
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) {
			if (!body.insideElement())
				return;
			super.startElement(uri, localName, qName, attributes);

			// Determine lemma and part of speech from the attributes
			String lemma = attributes.getValue("lemma");
			if (lemma == null)
				lemma = "";
			annotLemma.addValue(lemma);
			String pos = attributes.getValue("type");
			if (pos == null)
				pos = "?";
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

Here we see an example of customizing a default handler. The default handler is called WordHandlerBase, and it takes care of storing character positions (needed if we want to highlight hits in the original XML) and reporting progress, but nothing else. You are responsible for indexing the different annotations (the defaults word and punct, plus the we you added ourselves, lemma and pos). We use an anonymous inner class and override the startElement() and endElement() methods.

At the top of those methods, notice how we use the body handler we added earlier: we check if we're inside a body element, and return if not, even before calling the superclass method. This makes sure any &lt;w/&gt; tags outside &lt;body/&gt; tags are skipped.

The values for lemma and part of speech are taken from the element attributes and added to the annotations we created earlier; simple enough. For the main annotation ("word", the actual word forms) and the "punct" annotation (punctuation and whitespace between words), we use the consumeCharacterContent() method. All character content in the XML is collected, and calling consumeCharacterContent() returns the context collected since the last call, and clears the buffer again. At the start of each word, we consume the character content and add it to the punct annotation; at the end of each word, we do the same again and add it to the main annotation ("word").

If we also want to capture sentence tags, so we can search for sentences containing a word for example, we can add this handler:

	// Sentence tags: index as tags in the content
	addHandler("s", new InlineTagHandler() {
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

We only customize this handler because we want to make sure we don't capture sentence tags outside body elements.

We're almost done, but there's one subtle thing to take care of. What happens to the bit of punctuation after the last word? The way we have our indexer now, it would never get added to the index. We should customize the body handler a little bit to take care of that:

		final ElementHandler body = addHandler("body", new ElementHandler() {
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

Note how we've also overridden the startElement() method in order to clean the character content buffer at the start of the body element. If we didn't do that, we might get some junk at the first position of the punct annotation.

That's all there is to it, really. Well, we haven't covered capturing metadata, mostly because TEI doesn't have a clear standard for how metadata is represented. But indexing your particular type of metadata is easy. There's a few helper classes: MetadataElementHandler assumes the matched element name is the name of your metadata field and the character content is the value. MetadataAttributesHandler stores all the attributes from the matched element as metadata fields. MetadataNameValueAttributeHandler assumes the matched element has a name attribute and a value attribute (the attribute names can be specified in the constructor) and stores those as metadata fields.

If you need something fancy for metadata, have a look at the DocIndexers in BlackLab and the implementation of the helper classes mentioned above. It's not hard to make a version that will work for you.

That concludes this simple tutorial. If you have a question, please [contact us](/guide/about.md#contact-us).
