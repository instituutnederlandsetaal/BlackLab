package nl.inl.blacklab.indexers.config.saxon;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.BitSet;
import java.util.Objects;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
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
import org.codehaus.stax2.XMLStreamReader2;

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
 * For START_ELEMENT events, Woodstox reports the offset at the exact position of the opening '&lt;'. Its StAX2
 * location information also reports the position immediately after an END_ELEMENT event, which is the exclusive
 * source end needed here.
 * </pre>
 */
public class SaxonDocumentWithElementOffsets {
    @FunctionalInterface
    interface StaxEventCallback {
        int apply(int value, Location context, XMLStreamReader2 reader) throws XMLStreamException;
    }

    /** Start offsets of elements, indexed by element index. */
    private LongList elementStartOffsets = new LongArrayList();

    /** End offsets of elements, indexed by element index. */
    private LongList elementEndOffsets = new LongArrayList();

    /** Stack of indices into elementStartOffsets/elementEndOffsets for currently open elements. */
    private IntList openElementStack;

    /** Greatest reliable descendant end seen for each open element. */
    private LongList openElementMaxEnd;

    /** Contains the starting [line, col] of elements mapped to their index in elementStartOffsets/elementEndOffsets. */
    private final Long2IntMap elementLocationToIndex = new Long2IntOpenHashMap();

    /** Elements whose parser locations describe literal markup in the main source. */
    private final BitSet materialElements = new BitSet();

    /** The system ID of the document entity, captured from its necessarily literal root element. */
    private String documentSystemId;

    /** Last accepted source start; literal start tags occur in strictly increasing order. */
    private long previousMaterialStart = -1;

    private final TreeInfo document;

    public SaxonDocumentWithElementOffsets(Reader source, Configuration configuration) throws XMLStreamException, XPathException, IOException {
        elementLocationToIndex.defaultReturnValue(-1);
        openElementStack = new IntArrayList();
        openElementMaxEnd = new LongArrayList();
        StAXSource staxSource = wrapStaxSourceAndAttachCallbackOnElementEncountered(source, this::handleEvent);

        this.document = configuration.buildDocumentTree(staxSource);

        // cleanup - only close the stream reader since we created a StAXSource with XMLStreamReader (not XMLEventReader)
        staxSource.getXMLStreamReader().close();
        source.close();
        openElementStack = null;
        openElementMaxEnd = null;
    }


    public TreeInfo getDocument() {
        return document;
    }

    /** Return the inclusive start offset of the element in the document. */
    public long getElementStartCharOffset(NodeInfo node) {
        return elementStartOffsets.getLong(requireElementOrdinal(node));
    }
    /** Return the exclusive end offset of the element in the document. */
    public long getElementEndCharOffset(NodeInfo node) {
        return elementEndOffsets.getLong(requireElementOrdinal(node));
    }

    /** Return this parsed tree's stable start-tag ordinal, or -1 if this is not one of its material elements. */
    public int getElementOrdinal(NodeInfo node) {
        if (node == null || node.getNodeKind() != net.sf.saxon.type.Type.ELEMENT || node.getTreeInfo() != document)
            return -1;
        int ordinal = elementLocationToIndex.get(encodeElementLocation(node));
        return ordinal >= 0 && materialElements.get(ordinal) ? ordinal : -1;
    }

    private int requireElementOrdinal(NodeInfo node) {
        int ordinal = getElementOrdinal(node);
        if (ordinal < 0)
            throw new IllegalArgumentException("Node is not a material element from this parsed XML tree");
        return ordinal;
    }


    /// ========
    /// Tracking logic
    /// ===========

    private int handleEvent(int evt, Location loc, XMLStreamReader2 reader) throws XMLStreamException {
        if (evt == XMLStreamReader.START_ELEMENT)
            this.trackElementStart(loc, reader.getLocationInfo().getEndingCharOffset());
        else if (evt == XMLStreamReader.END_ELEMENT)
            this.trackElementEnd(reader.getLocationInfo().getEndingCharOffset());
        return evt;
    }

    private void trackElementStart(Location loc, long startTagEndPosition) {
        // Woodstox reports the offset at the exact position of the opening '<'
        long startPosition = loc.getCharacterOffset();
        long encodedElementLocation = this.encodeElementLocation(loc.getLineNumber(), loc.getColumnNumber());

        int index = elementStartOffsets.size();
        elementStartOffsets.add(startPosition);
        elementEndOffsets.add(-1L); // placeholder, will be filled in trackElementEnd

        if (documentSystemId == null)
            documentSystemId = loc.getSystemId();
        boolean parentIsMaterial = openElementStack.isEmpty()
                || materialElements.get(openElementStack.getInt(openElementStack.size() - 1));
        boolean isMaterial = parentIsMaterial
                && Objects.equals(documentSystemId, loc.getSystemId())
                && startPosition >= 0
                && startTagEndPosition > startPosition
                && startPosition > previousMaterialStart;
        if (isMaterial) {
            materialElements.set(index);
            previousMaterialStart = startPosition;
        }

        this.openElementStack.add(index);
        this.openElementMaxEnd.add(-1L);
        int previousIndex = this.elementLocationToIndex.putIfAbsent(encodedElementLocation, index);
        if (previousIndex >= 0)
            this.elementLocationToIndex.put(encodedElementLocation, -1);
    }

    private void trackElementEnd(long endPosition) {
        int index = this.openElementStack.removeInt(this.openElementStack.size() - 1);
        long maxDescendantEnd = this.openElementMaxEnd.removeLong(this.openElementMaxEnd.size() - 1);
        elementEndOffsets.set(index, endPosition);
        if (materialElements.get(index)
                && (endPosition <= elementStartOffsets.getLong(index) || endPosition < maxDescendantEnd)) {
            materialElements.clear(index, elementStartOffsets.size());
        } else if (materialElements.get(index) && !openElementMaxEnd.isEmpty()) {
            int parentStackIndex = openElementMaxEnd.size() - 1;
            openElementMaxEnd.set(parentStackIndex,
                    Math.max(openElementMaxEnd.getLong(parentStackIndex), endPosition));
        }
    }

    private long encodeElementLocation(NodeInfo node) {
        return encodeElementLocation(node.getLineNumber(), node.getColumnNumber());
    }

    private long encodeElementLocation(long line, long col) {
        return (line << 32) | col;
    }

    /// ========
    /// Setup logic/wrappers to enable tracking
    /// ========

    private static StAXSource wrapStaxSourceAndAttachCallbackOnElementEncountered(Reader source, StaxEventCallback handler) throws XMLStreamException {
        XMLStreamReader2 streamReaderImpl = createXmlStreamReader(source);
        XMLStreamReader wrapper = new StreamReaderDelegate(streamReaderImpl) {
            @Override
            public int next() throws XMLStreamException {
                int event = super.next();
                return handler.apply(event, this.getLocation(), streamReaderImpl);
            }
            @Override
            public int nextTag() throws XMLStreamException {
                int event = super.nextTag();
                return handler.apply(event, this.getLocation(), streamReaderImpl);
            }
        };
        return new StAXSource(wrapper);
    }

    private static XMLStreamReader2 createXmlStreamReader(Reader source) throws XMLStreamException {
        // Use Woodstox explicitly - the default JDK StAX implementation has bugs
        // in character offset reporting when elements are directly nested without
        // whitespace between them (e.g., <parent><child>).
        XMLInputFactory fac = new WstxInputFactory();

        // Read external entities from file
        fac.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.TRUE);
        XMLResolver dummyResolver = (publicID, systemID, baseURI, namespace) -> {
            if (systemID != null) {
                  try {
                      return new ByteArrayInputStream(new FileInputStream(systemID).readAllBytes());
                  } catch (IOException e) {
                      throw new RuntimeException(e);
                  }
              }
              return null; // Let the parser handle it normally
        };
        fac.setProperty(XMLInputFactory.RESOLVER, dummyResolver);

        return (XMLStreamReader2) fac.createXMLStreamReader(new StreamSource(source, "file:///unknown"));
    }
}
