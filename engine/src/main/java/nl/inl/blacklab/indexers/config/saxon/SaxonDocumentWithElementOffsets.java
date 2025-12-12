package nl.inl.blacklab.indexers.config.saxon;

import java.io.IOException;
import java.io.Reader;
import java.util.function.LongConsumer;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.transform.stax.StAXSource;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLongMutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import nl.inl.util.CountingReader;

/**
 * <pre>
 * To correctly implement the content store, we need to track character offsets for certain elements in the XML.
 * By default, Saxon does not provide this information, only line and col numbers.
 *
 * BUT, since Saxon has its DOM builder separated from the XML Parser implementation, we can pick a parser that reports character offsets,
 * then insert ourselves in between the parser -> saxon pipeline, and track the character offsets of element start/end positions that way.
 *
 * We use the StAX parser for this, as it reports character offsets in its Location object.
 * We simply wrap the two functions that report elements to Saxon (as that's the only things we're interested in), capturing the character offsets as we go along.
 *
 * There's one more difficulty however; we need the *exact* positions of the very first/last '<' and '>' characters of elements.
 * The StAX parser events actually occur slightly *after* the bracket in question (though the close bracket seems to be exact).
 * So to correct this, we also wrap the Reader we provide to the StAX parser, and track the positions of all '<' characters ourselves.
 * Then, when we get notified of a start element event, we look up the nearest preceding '<' position and use that as the element start offset.
 * This works, as luckily in valid XML the literal '<' character may not appear anywhere in element declarations except as the opening bracket.
 * This means the nearest preceding '<' is always the correct one.
 *
 * NOTE: we really do need to use the parser's reported position on events, we can't just use our reader's position, as the parser typically reads ahead,
 * so our reader's read head could be well past the current element being reported.
 *
 * </pre>
 */
public class SaxonDocumentWithElementOffsets {
	@FunctionalInterface
	interface StaxEventCallback { int apply(int value, Location context); }

	private LongArrayList bracketPositions = new LongArrayList();
	/** Contains the start/end pairs of currently open elements. */
	private ObjectList<LongLongPair> openElementStack = new ObjectArrayList<>();

	/** Contains the starting [line, col] of elements mapped to the [start offset, end offset] in the document. */
	private Long2ObjectMap<LongLongPair> elementLocationToBracketPositions = new Long2ObjectOpenHashMap<>();

	private TreeInfo document;

	public SaxonDocumentWithElementOffsets(Reader source, Configuration configuration) throws XMLStreamException, XPathException, IOException {
		source = wrapReaderAndAttachCallbackOnBracketEncountered(source, this.bracketPositions::add);
		StAXSource staxSource = wrapStaxSourceAndAttachCallbackOnElementEncountered(new CountingReader(source), this::handleEvent);

		this.document = configuration.buildDocumentTree(staxSource);

		// cleanup - only close the stream reader since we created a StAXSource with XMLStreamReader (not XMLEventReader)
		staxSource.getXMLStreamReader().close();
		source.close();
		this.openElementStack = null;
		this.bracketPositions = null;
	}

	
	public TreeInfo getDocument() {
		return document;
	}

	public long getElementStartCharOffset(NodeInfo node) {
		return elementLocationToBracketPositions.get(encodeElementLocation(node)).leftLong();
	}
	public long getElementEndCharOffset(NodeInfo node) {
		return elementLocationToBracketPositions.get(encodeElementLocation(node)).rightLong();
	}


	/// ========
	/// Tracking logic
	/// ===========
	
	private int handleEvent(int evt, Location loc) {
		if (evt == XMLStreamReader.START_ELEMENT) { this.trackElementStart(loc); }
		else if (evt == XMLStreamReader.END_ELEMENT) { this.trackElementEnd(loc); }
		return evt;
	}

	private void trackElementStart(Location loc) {
		long positionOfOpeningBracket = this.findNearestOpeningBracketBefore(loc.getCharacterOffset());
		long encodedElementLocation = this.encodeElementLocation(loc.getLineNumber(), loc.getColumnNumber());
		
		LongLongPair startEndPos = LongLongMutablePair.of(positionOfOpeningBracket, -1L);
		
		this.openElementStack.add(startEndPos);
		this.elementLocationToBracketPositions.put(encodedElementLocation, startEndPos);
	}

	private void trackElementEnd(Location loc) {
		LongLongPair startEndPos = this.openElementStack.remove(this.openElementStack.size() - 1);
		startEndPos.right(loc.getCharacterOffset());
	}

	private long encodeElementLocation(NodeInfo node) {
		return encodeElementLocation(node.getLineNumber(), node.getColumnNumber());
	}
	private long encodeElementLocation(long line, long col) {
		return (line << 32) | col;
	}
	private long findNearestOpeningBracketBefore(long charOffset) {
		for (int i = bracketPositions.size() - 1; i >= 0; i--) {
			long position = bracketPositions.getLong(i);
			if (position < charOffset) {
				return position;
			}
		}
		throw new IllegalStateException("No opening bracket found before the given character offset: " + charOffset);
	}



	/// ========
	/// Setup logic/wrappers to enable tracking
	/// ========
	

	private static Reader wrapReaderAndAttachCallbackOnBracketEncountered(Reader source, LongConsumer trackPosition) {
		return new Reader() {
			long charsRead = 0;

			@Override
			public int read(char[] cbuf, int off, int len) throws IOException {
				int n = source.read(cbuf, off, len);
				for (int i = off; i < off + n; ++i) {
					if (cbuf[i] == '<') { trackPosition.accept(charsRead + i - off); }
				}
				charsRead += n;
				return n;
			}

			public int read() throws IOException {
				int ch = source.read();
				if (ch == '<') { trackPosition.accept(charsRead); }
				if (ch != -1) { charsRead += 1; }
				return ch;
			}

			@Override
			public void close() throws IOException {
				source.close();
			}
		};
	}

	private static StAXSource wrapStaxSourceAndAttachCallbackOnElementEncountered(CountingReader source, StaxEventCallback handler) throws XMLStreamException {
		XMLStreamReader streamReaderImpl = createXmlStreamReader(source);
		XMLStreamReader wrapper = new StreamReaderDelegate(streamReaderImpl) {
			static class LocationWrapper implements Location {
				private final Location base;
				private final int maxOffset;
				public LocationWrapper(Location base, int maxOffset) { this.base = base; this.maxOffset = maxOffset; }
				@Override public int getLineNumber() { return base.getLineNumber(); }
				@Override public int getColumnNumber() { return base.getColumnNumber(); }
				@Override public int getCharacterOffset() { return Math.min(base.getCharacterOffset(), maxOffset); }
				@Override public String getPublicId() { return base.getPublicId(); }
				@Override public String getSystemId() { return base.getSystemId(); }
			};

			@Override
			public int next() throws XMLStreamException { return handler.apply(super.next(), new LocationWrapper(this.getLocation(), (int) source.getCharsRead())); }
			@Override
			public int nextTag() throws XMLStreamException { return handler.apply(super.nextTag(), new LocationWrapper(this.getLocation(), (int) source.getCharsRead())); }
		};
		return new StAXSource(wrapper);
	}

	private static XMLStreamReader createXmlStreamReader(Reader source) throws XMLStreamException {
		XMLInputFactory fac = XMLInputFactory.newDefaultFactory();
		
		return fac.createXMLStreamReader(source);
	}
}
