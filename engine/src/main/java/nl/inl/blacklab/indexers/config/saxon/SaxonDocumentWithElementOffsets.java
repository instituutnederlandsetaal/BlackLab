package nl.inl.blacklab.indexers.config.saxon;

import java.io.IOException;
import java.io.Reader;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import com.ctc.wstx.stax.WstxInputFactory;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;

/**
 * <pre>
 * To correctly implement the content store, we need to track character offsets for certain elements in the XML.
 * By default, Saxon does not provide this information, only line and col numbers.
 *
 * Since Saxon has its DOM builder separated from the XML Parser implementation, we can pick a parser that reports
 * character offsets, then insert ourselves in between the parser -> saxon pipeline, and track the character offsets
 * of element start/end positions that way.
 *
 * We use Woodstox as the StAX parser because it reports accurate character offsets in its Location object.
 * (The default JDK StAX implementation has bugs in offset reporting, and working around these proved troublesome.)
 *
 * For START_ELEMENT events, Woodstox reports the offset at the exact position of the opening '&lt;'.
 * For END_ELEMENT events, Woodstox reports the offset at the '&lt;' of the closing tag, so we still need to
 * track '&gt;' positions in the document to find the actual end position.
 * </pre>
 */
public class SaxonDocumentWithElementOffsets {
    @FunctionalInterface
    interface StaxEventCallback {
        int apply(int value, Location context);
    }

    /** Positions of all '>' characters in the document (position AFTER the '>').
     *  We store the position after so it can be used as an exclusive end offset directly. */
    private LongList closeBracketPositions = new LongArrayList();

    /** Index of the next close bracket to consider. Since END_ELEMENT events come in
     *  document order (offsets always increase), we can skip already-processed brackets. */
    private int nextCloseBracketIndex = 0;

    /** Start offsets of elements, indexed by element index. */
    private LongList elementStartOffsets = new LongArrayList();

    /** End offsets of elements, indexed by element index. */
    private LongList elementEndOffsets = new LongArrayList();

    /** Stack of indices into elementStartOffsets/elementEndOffsets for currently open elements. */
    private IntList openElementStack;

    /** Contains the starting [line, col] of elements mapped to their index in elementStartOffsets/elementEndOffsets. */
    private final Long2IntMap elementLocationToIndex = new Long2IntOpenHashMap();

    private final TreeInfo document;

    public SaxonDocumentWithElementOffsets(Reader source, Configuration configuration) throws XMLStreamException, XPathException, IOException {
        openElementStack = new IntArrayList();
        source = wrapReaderAndTrackCloseBrackets(source, closeBracketPositions::add);
        StAXSource staxSource = wrapStaxSourceAndAttachCallbackOnElementEncountered(source, this::handleEvent);

        this.document = configuration.buildDocumentTree(staxSource);

        // cleanup - only close the stream reader since we created a StAXSource with XMLStreamReader (not XMLEventReader)
        staxSource.getXMLStreamReader().close();
        source.close();
        openElementStack = null;
        closeBracketPositions = null;
    }


    public TreeInfo getDocument() {
        return document;
    }

    /** Return the inclusive start offset of the element in the document. */
    public long getElementStartCharOffset(NodeInfo node) {
        int index = elementLocationToIndex.get(encodeElementLocation(node));
        return elementStartOffsets.getLong(index);
    }
    /** Return the exclusive end offset of the element in the document. */
    public long getElementEndCharOffset(NodeInfo node) {
        int index = elementLocationToIndex.get(encodeElementLocation(node));
        return elementEndOffsets.getLong(index);
    }


    /// ========
    /// Tracking logic
    /// ===========

    private int handleEvent(int evt, Location loc) {
        if (evt == XMLStreamReader.START_ELEMENT)
            this.trackElementStart(loc);
        else if (evt == XMLStreamReader.END_ELEMENT)
            this.trackElementEnd(loc);
        return evt;
    }

    private void trackElementStart(Location loc) {
        // Woodstox reports the offset at the exact position of the opening '<'
        long startPosition = loc.getCharacterOffset();
        long encodedElementLocation = this.encodeElementLocation(loc.getLineNumber(), loc.getColumnNumber());

        int index = elementStartOffsets.size();
        elementStartOffsets.add(startPosition);
        elementEndOffsets.add(-1L); // placeholder, will be filled in trackElementEnd

        this.openElementStack.add(index);
        this.elementLocationToIndex.put(encodedElementLocation, index);
    }

    private void trackElementEnd(Location loc) {
        int index = this.openElementStack.removeInt(this.openElementStack.size() - 1);
        // closeBracketPositions stores positions AFTER the '>', so we can use them as exclusive end offsets directly.
        // Woodstox reports END_ELEMENT offset at the '<' of the closing tag, so we need to find
        // the next '>' after that position.
        long endPosition = findNearestCloseBracketAfter(loc.getCharacterOffset());
        elementEndOffsets.set(index, endPosition);
    }

    private long encodeElementLocation(NodeInfo node) {
        return encodeElementLocation(node.getLineNumber(), node.getColumnNumber());
    }

    private long encodeElementLocation(long line, long col) {
        return (line << 32) | col;
    }

    /**
     * Find the first '>' position after the given offset.
     * Since END_ELEMENT events come in document order (offsets always increase),
     * we track our position and skip already-processed brackets for O(1) amortized lookup.
     */
    private long findNearestCloseBracketAfter(long charOffset) {
        // Skip brackets that are at or before the current offset
        while (nextCloseBracketIndex < closeBracketPositions.size()) {
            long position = closeBracketPositions.getLong(nextCloseBracketIndex);
            if (position > charOffset) {
                return position;
            }
            nextCloseBracketIndex++;
        }
        throw new IllegalStateException("No close bracket found after the given character offset: " + charOffset);
    }



    /// ========
    /// Setup logic/wrappers to enable tracking
    /// ========


    private static Reader wrapReaderAndTrackCloseBrackets(Reader source, java.util.function.LongConsumer trackCloseBracket) {
        return new Reader() {
            long charsRead = 0;

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                int n = source.read(cbuf, off, len);
                if (n > 0) {
                    for (int i = off; i < off + n; ++i) {
                        if (cbuf[i] == '>') {
                            // store position AFTER '>'
                            trackCloseBracket.accept(charsRead + i - off + 1);
                        }
                    }
                    charsRead += n;
                }
                return n;
            }

            @Override
            public int read() throws IOException {
                int ch = source.read();
                if (ch == '>') {
                    trackCloseBracket.accept(charsRead + 1); // store position AFTER '>'
                }
                if (ch != -1)
                    charsRead += 1;
                return ch;
            }

            @Override
            public void close() throws IOException {
                source.close();
            }
        };
    }

    private static StAXSource wrapStaxSourceAndAttachCallbackOnElementEncountered(Reader source, StaxEventCallback handler) throws XMLStreamException {
        XMLStreamReader streamReaderImpl = createXmlStreamReader(source);
        XMLStreamReader wrapper = new StreamReaderDelegate(streamReaderImpl) {
            @Override
            public int next() throws XMLStreamException {
                return handler.apply(super.next(), this.getLocation());
            }
            @Override
            public int nextTag() throws XMLStreamException {
                return handler.apply(super.nextTag(), this.getLocation());
            }
        };
        return new StAXSource(wrapper);
    }

    private static XMLStreamReader createXmlStreamReader(Reader source) throws XMLStreamException {
        // Use Woodstox explicitly - the default JDK StAX implementation has bugs
        // in character offset reporting when elements are directly nested without
        // whitespace between them (e.g., <parent><child>).
        XMLInputFactory fac = new WstxInputFactory();
        // Disable loading of external entities: documents are often read from ZIP archives
        // or other sources where relative SYSTEM paths cannot be resolved.
        fac.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        // Provide a placeholder system ID via StreamSource so that external entity
        // declarations in a DOCTYPE (e.g. <!ENTITY % foo SYSTEM "foo.xml">) get a
        // non-null base URL context (EntityDecl.mContext). Without this, Saxon's
        // StaxBridge.getUnparsedEntities() throws a NullPointerException when it calls
        // EntityDecl.getBaseURI() on those declarations.
        return fac.createXMLStreamReader(new StreamSource(source, "file:///unknown"));
    }
}
