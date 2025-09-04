package nl.inl.blacklab.codec.tokens;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

/**
 * Tokens codec that uses run-length encoding.
 *
 * Stored as follows:
 * - a 2-byte short value that specifies the decoded block size in number of tokens;
 *   this is always set to 100 tokens for now.
 * - a 4-byte int value that specifies the number of blocks.
 * - for each block, a 4-byte int value that specifies the relative offset
 *   from the start of the blocks data where the block ENDS.
 *   The first block always starts at offset 0.
 *
 * Next follow the blocks with the actual tokens. Some tokens are stored preceded by a a count:
 * a value N <= -2 indicates that the next token index is repeated -N times.
 * Any value N >= -1 is a token index. A token index that is not preceded by a count
 * is a single occurrence of that token index. So aside from the block overhead, this codec
 * should always use less space than storing one value per token.
 * (NOTE: -1 is a valid token index that means "no token value was recorded at this position")
 */
public class TokensCodecRunLengthEncoded implements TokensCodec {

    private static final TokensCodec withByte = new TokensCodecRunLengthEncoded(TokenValueType.BYTE);
    private static final TokensCodec withShort = new TokensCodecRunLengthEncoded(TokenValueType.SHORT);
    private static final TokensCodec withThreeBytes = new TokensCodecRunLengthEncoded(TokenValueType.THREE_BYTES);
    private static final TokensCodec withInt = new TokensCodecRunLengthEncoded(TokenValueType.INT);

    public static TokensCodec get(TokenValueType tokenValueType) {
        return switch (tokenValueType) {
            case BYTE -> withByte;
            case SHORT -> withShort;
            case THREE_BYTES -> withThreeBytes;
            case INT -> withInt;
        };
    }

    /** Block size. Static for now, but recorded, so can be changed later. */
    private short decodedBlockSizeTokens = 100;

    private final TokenValueType tokenType;

    public TokensCodecRunLengthEncoded(TokenValueType tokenType) {
        this.tokenType = tokenType;
    }

    @Override
    public TokensCodecType codecType() {
        return TokensCodecType.RUN_LENGTH_ENCODING;
    }

    @Override
    public TokenValueType valueType() {
        return tokenType;
    }

    @Override
    public byte parameter() {
        return tokenType.code;
    }

    public int[][] readSnippets(IndexInput tokensFile, long docTokensOffset, int[] starts, int[] ends)
            throws IOException {
        Doc doc = new Doc(tokensFile, docTokensOffset);
        int n = starts.length;
        if (n != ends.length)
            throw new IllegalArgumentException("start and end must be of equal length");
        int[][] snippets = new int[n][];
        for (int i = 0; i < n; i++) {
            snippets[i] = new int[ends[i] - starts[i]];
            doc.readSnippet(starts[i], snippets[i]);
        }
        return snippets;
    }

    @Override
    public void readSnippet(IndexInput tokensFile, long docTokensOffset, int startPosition, int[] snippet)
            throws IOException {
        Doc doc = new Doc(tokensFile, docTokensOffset);
        doc.readSnippet(startPosition, snippet);
    }

    public void setDecodedBlockSize(short i) {
        this.decodedBlockSizeTokens = i;
    }

    /** State for a document we're getting snippets from */
    private class Doc {

        private final short decodedBlockSizeTokens;

        private final int numberOfBlocks;

        private final long blockIndexOffset;

        private final IndexInput tokensFile;

        private final long blockDataStart;

        /** Cache of blocks (partially or fully decoded) in this doc, by block number */
        Map<Integer, Block> blocks = new HashMap<>();

        public Doc(IndexInput tokensFile, long docTokensOffset) throws IOException {
            this.tokensFile = tokensFile;
            tokensFile.seek(docTokensOffset);
            decodedBlockSizeTokens = tokensFile.readShort();
            numberOfBlocks = tokensFile.readInt();
            blockIndexOffset = tokensFile.getFilePointer();
            blockDataStart = blockIndexOffset + (long) numberOfBlocks * Integer.BYTES;
        }

        public void readSnippet(int startPosition, int[] snippet) throws IOException {
            int[] decodedBlock = null;
            int blockNumber = (startPosition / decodedBlockSizeTokens) - 1; // -1 so we read the first block below
            int indexInBlock = (startPosition % decodedBlockSizeTokens) + decodedBlockSizeTokens; // will be corrected below
            int snippetIndex = 0;
            while (snippetIndex < snippet.length) {
                // Make sure we have the right block decoded
                if (decodedBlock == null) {
                    blockNumber++;
                    if (blockNumber >= numberOfBlocks)
                        throw new IOException("Trying to read past end of document");
                    Block block = blocks.get(blockNumber);
                    if (block == null) {
                        block = new Block(this, blockNumber);
                        blocks.put(blockNumber, block);
                    }
                    indexInBlock -= decodedBlockSizeTokens;
                    int howManyMoreCharsNeeded = snippet.length - snippetIndex;
                    decodedBlock = block.decodeUpTo(indexInBlock + howManyMoreCharsNeeded);
                }

                // Copy from decoded block to snippet
                if (indexInBlock > decodedBlock.length)
                    throw new IOException("Error reading tokens: index in block " + indexInBlock + " > decoded block size " + decodedBlock.length);
                snippet[snippetIndex] = decodedBlock[indexInBlock];
                indexInBlock++;
                if (indexInBlock == decodedBlockSizeTokens) {
                    decodedBlock = null;
                }
                snippetIndex++;
            }
        }
    }

    /** A (partially decoded) block read from the index. */
    private class Block {

        Doc doc;

        private final int blockStartOffset;

        private final int encodedLength;

        /** Next byte in encoded block to decode */
        private int encodedIndex;

        /** The (partially) decoded block */
        private final int[] decodedBlock;

        /** Next position in decodedBlock to write */
        private int decodedIndex;

        public Block(Doc doc, int blockNumber) throws IOException {
            this.doc = doc;
            decodedBlock = new int[doc.decodedBlockSizeTokens];
            decodedIndex = 0;

            // Determine where the block starts and ends
            if (blockNumber > 0) {
                // Block starts where the previous one ended
                doc.tokensFile.seek(doc.blockIndexOffset + (long) (blockNumber - 1) * Integer.BYTES);
                blockStartOffset = doc.tokensFile.readInt();
            } else {
                // First block starts at offset 0
                blockStartOffset = 0;
                doc.tokensFile.seek(doc.blockIndexOffset);
            }
            int blockEndOffset = doc.tokensFile.readInt();
            encodedLength = blockEndOffset - blockStartOffset;
            encodedIndex = 0;
        }

        private int[] decodeUpTo(int stopAtPosition) throws IOException {
            // Read the encoded block
            doc.tokensFile.seek(doc.blockDataStart + blockStartOffset + encodedIndex);

            // Wrap encodedBlock in ByteBuffer
            int bytesPerValue = tokenType.sizeBytes();
            while (encodedIndex < encodedLength && decodedIndex < stopAtPosition) {
                int value = tokenType.read(doc.tokensFile);
                encodedIndex += bytesPerValue;
                if (value <= -2) {
                    // Run length
                    int runLength = -value;
                    int token = tokenType.read(doc.tokensFile);
                    encodedIndex += bytesPerValue;
                    for (int i = 0; i < runLength; i++) {
                        decodedBlock[decodedIndex] = token;
                        decodedIndex++;
                    }
                } else {
                    // Single value
                    decodedBlock[decodedIndex] = value;
                    decodedIndex++;
                }
            }
            return decodedBlock;
        }

    }

    @Override
    public void writeTokens(int[] tokensInDoc, IndexOutput outTokensFile) throws IOException {
        outTokensFile.writeShort(decodedBlockSizeTokens);

        // Encode blocks, write block index and finally the encoded blocks
        int[][] blocks = determineBlocks(tokensInDoc);
        outTokensFile.writeInt(blocks.length);
        int currentBlockEndOffset = 0;
        for (int[] block : blocks) {
            currentBlockEndOffset += block.length * tokenType.sizeBytes();
            outTokensFile.writeInt(currentBlockEndOffset);
        }
        for (int[] block : blocks) {
            for (int value : block) {
                tokenType.write(value, outTokensFile);
            }
        }
    }

    private int[][] determineBlocks(int[] tokensInDoc) {
        int numBlocks = (tokensInDoc.length + decodedBlockSizeTokens - 1) / decodedBlockSizeTokens;
        int[][] blocks = new int[numBlocks][];
        for (int block = 0; block < numBlocks; block++) {
            int[] buffer = new int[decodedBlockSizeTokens * 2]; // (worst case, we'll truncate it later)
            int tokenStart = block * decodedBlockSizeTokens;
            int tokenEnd = Math.min(tokenStart + decodedBlockSizeTokens, tokensInDoc.length);
            int last = -2; // -2 means "no last token"
            int currentRunLength = 0;
            int indexInBlock = 0;
            for (int i = tokenStart; i < tokenEnd; i++) {
                int token = tokensInDoc[i];
                if (last == token && currentRunLength < -tokenType.minValue()) {
                    // Same token (and maximum run length not yet reached)
                    currentRunLength++;
                } else {
                    // Different token; write this "run"
                    if (last != -2)
                        indexInBlock = writeRun(buffer, indexInBlock, last, currentRunLength);
                    currentRunLength = 1;
                    last = token;
                }
            }
            // Final run
            if (last != -2)
                indexInBlock = writeRun(buffer, indexInBlock, last, currentRunLength);
            // Truncate buffer to actual size
            blocks[block] = new int[indexInBlock];
            System.arraycopy(buffer, 0, blocks[block], 0, indexInBlock);
        }
        return blocks;
    }

    private static int writeRun(int[] block, int indexInBlock, int token, int runLength) {
        if (runLength > 1) {
            // write run length
            block[indexInBlock] = -runLength;
            indexInBlock++;
        }
        block[indexInBlock] = token;
        indexInBlock++;
        return indexInBlock;
    }
}
