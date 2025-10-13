package nl.inl.blacklab.indexers.config.saxon;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Locator;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import net.sf.saxon.om.NodeInfo;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;

/** Tracks characters positions for each tag in an XML document while reading through it. */
public class CharPosTrackingReader extends Reader {

    /** Estimate of characters per open bracket, for initial sizing of list */
    private static final int AVERAGE_CHARS_PER_TAG_ESTIMATE = 20;

    /** Document we're reading */
    private final Reader reader;

    /** How many characters have been read from our reader. */
    private long charsRead;

    /** Next char to produce (if we've peeked ahead) */
    private int readAheadChar;

    /**
     * For each line, starting from 0, store the STARTING (cumulative) character position.
     * Index 0 therefore always has the value 0.
     * End-of-line characters are included in the count.
     * Used to translate line/column into character position.
     */
    private final LongList lineNumberToCharPos;

    /**
     * The positions of all open brackets &lt; in the document.
     * We use these to find the starting character position of a tag.
     */
    private final LongList openBracketPositions;

    /**
     * We need to be able to find the start char. position of a tag, but we only know the end position.
     * This iterator helps us find the corresponding start.
     */
    private final LongIterator tagPosIterator;

    /** The last open bracket position above iterator produced. */
    private long currentTagPos;

    private boolean tagPosNexted;

    /**
     * Connects recorded SAX positions (just after start and end tags) to the calculated
     * characters positions of those tags.
     */
    private final Map<Long, StartEndPos> startEndPosMap;

    /** Our element stack while parsing, for filling in the end tag character position when we encounter it. */
    private final Deque<StartEndPos> elStack = new ArrayDeque<>();

    public CharPosTrackingReader(Reader reader) {
        this.reader = reader;
        charsRead = 0;
        readAheadChar = 0; // we haven't peeked at next char yet

        openBracketPositions = new LongArrayList();
        tagPosIterator = openBracketPositions.longIterator();
        tagPosNexted = false;
        currentTagPos = -1;

        lineNumberToCharPos = new LongArrayList();
        lineNumberToCharPos.add(0); // first line always starts at character 0

        // The map where we keep track of the real start and end positions of tags (as opposed to what Saxon provides,
        // which is always the position just after the tag)
        startEndPosMap = new HashMap<>((int)(charsRead / AVERAGE_CHARS_PER_TAG_ESTIMATE));
    }

    /**
     * translation of recorded line and column number to character position in the document
     */
    private long charPosForLineAndCol(int lineNumber, int columnNumber) {
        if (lineNumber == 0 && columnNumber == -1) {
            // If you match the document node / , it doesn't have valid line/col...
            lineNumber = columnNumber = 1;
        }
        assert lineNumber >= 1 && columnNumber >= 1 : "Line and column numbers must be positive";

        Long charPosStartOfLine = lineNumberToCharPos.getLong(lineNumber - 1);
        // Note that we subtract 1 at the end because columnNumber is 1-based, like line number,
        // and we want a character position that is 0-based
        return charPosStartOfLine + columnNumber - 1;
    }

    public StartEndPos getNodeStartEnd(NodeInfo node) {
        return startEndPosMap.get(charPosForLineAndCol(node.getLineNumber(), node.getColumnNumber()));
    }

    /**
     * A new start tag was found while parsing.
     *
     * Note that this must be called linearly because we iterate through the tag positions we indexed earlier.
     *
     * @param qName tag name
     * @param locator locator for the line and col
     */
    public void addNextStartElement(String qName, Locator locator) {
        long end = charPosForLineAndCol(locator.getLineNumber(), locator.getColumnNumber());
        long begin = end;
        // Now find the character position of the start of this tag
        // (last < character before the end position)
        if (!tagPosNexted) {
            currentTagPos = tagPosIterator.nextLong();
            tagPosNexted = true;
        }
        while (currentTagPos < end) {
            begin = currentTagPos;
            tagPosNexted = tagPosIterator.hasNext();
            if (tagPosNexted)
                currentTagPos = tagPosIterator.nextLong();
            else
                break; // no more open brackets available, current one must be correct
        }

        // NOTE more testing needed for self closing tags
        if (begin == end) {
            throw new ErrorIndexingFile(String.format("No '<' found for %s at line %d, col %d, charpos %d",
                    qName, locator.getLineNumber(), locator.getColumnNumber(), end));
        }
        StartEndPos startEndPos = new StartEndPos(begin);
        elStack.push(startEndPos);
        startEndPosMap.put(end, startEndPos);
    }

    /**
     * A new end tag was found while parsing.
     *
     * Updates the end position of the corresponding start tag.
     *
     * @param locator locator for the line and col
     */
    public void addNextEndElement(Locator locator) {
        elStack.pop().setEndPos(charPosForLineAndCol(locator.getLineNumber(), locator.getColumnNumber()));
    }

    @Override
    public int read() throws IOException {
        int currentChar;
        if (readAheadChar != 0) {
            // We already read the next char. Consume it now.
            currentChar = readAheadChar;
            readAheadChar = 0;
        } else {
            // Read the next char.
            currentChar = reader.read();
            if (currentChar >= 0)
                charsRead++;
        }
        if (currentChar < 0)
            return currentChar; // end of document
        if (currentChar == '<') {
            // Keep track of open bracket positions, so we can find the start of a tag
            openBracketPositions.add(charsRead - 1);
        } else if (currentChar == '\r' || currentChar == '\n') {
            // Keep track of line ending positions (or next-line-start-positions if you prefer),
            // so we can convert from line+col (Locator) to character position
            if (currentChar == '\r') {
                readAheadChar = reader.read();
                if (readAheadChar >= 0) {
                    charsRead++;
                    if (readAheadChar == '\n') {
                        readAheadChar = 0; // windows-style line ending; also skip newline
                        currentChar = '\n'; // produce a single newline
                    }
                }
            }
            lineNumberToCharPos.add(charsRead); // store character position after EOL char(s)
        }
        return currentChar;
    }

    @Override
    public int read(char[] chars, int offset, int length) throws IOException {
        int charsRead = 0;
        while (charsRead < length) {
            int c = read();
            if (c < 0)
                return charsRead == 0 ? c : charsRead;
            chars[offset] = (char)c;
            offset++;
            charsRead++;
        }
        return charsRead;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public static class StartEndPos {
        private final long startPos;

        private long endPos;

        public StartEndPos(long startPos) {
            this.startPos = startPos;
        }

        public void setEndPos(long endPos) {
            this.endPos = endPos;
        }

        public long getStartPos() {
            return startPos;
        }

        public long getEndPos() {
            return endPos;
        }
    }
}
