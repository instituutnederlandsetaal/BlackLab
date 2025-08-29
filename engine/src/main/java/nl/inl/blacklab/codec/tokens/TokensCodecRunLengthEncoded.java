package nl.inl.blacklab.codec.tokens;

import java.io.IOException;

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
    final short DECODED_BLOCK_SIZE_TOKENS = 100;

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

    @Override
    public void readSnippet(IndexInput tokensFile, long fileOffset, int startPosition, int[] snippet)
            throws IOException {
        tokensFile.seek(fileOffset);
        short blockSize = tokensFile.readShort();
        int numberOfBlocks = tokensFile.readInt();
        long blockIndexOffset = tokensFile.getFilePointer();
        long blockDataStart = blockIndexOffset + (long) numberOfBlocks * Integer.BYTES;
        int[] decoded = null;
        int blockNumber = (startPosition / blockSize) - 1; // -1 so we read the first block below
        int indexInBlock = (startPosition % blockSize) + DECODED_BLOCK_SIZE_TOKENS; // will be corrected below
        int snippetIndex = 0;
        while (snippetIndex < snippet.length) {
            // Make sure we have the right block decoded
            if (decoded == null) {
                blockNumber++;
                if (blockNumber >= numberOfBlocks)
                    throw new IOException("Trying to read past end of document");
                decoded = decodeBlock(tokensFile, blockNumber, blockIndexOffset, blockDataStart);
                indexInBlock -= DECODED_BLOCK_SIZE_TOKENS;
            }

            // Copy from decoded block to snippet
            if (indexInBlock > decoded.length)
                throw new IOException("Error reading tokens: index in block " + indexInBlock + " > decoded block size " + decoded.length);
            snippet[snippetIndex] = decoded[indexInBlock];
            indexInBlock++;
            if (indexInBlock == DECODED_BLOCK_SIZE_TOKENS) {
                decoded = null;
                indexInBlock = -1;
            }
            snippetIndex++;
        }
    }

    private int[] decodeBlock(IndexInput tokensFile, int blockNumber, long blockIndexOffset, long blockDataStart)
            throws IOException {
        int blockStartOffset = 0;
        if (blockNumber > 0) {
            tokensFile.seek(blockIndexOffset + (long) (blockNumber - 1) * Integer.BYTES);
            blockStartOffset = tokensFile.readInt();
        }
        int blockEndOffset = tokensFile.readInt();
        tokensFile.seek(blockDataStart + blockStartOffset);
        int[] decoded = new int[DECODED_BLOCK_SIZE_TOKENS];
        int decodedIndex = 0;
        while (tokensFile.getFilePointer() < blockDataStart + blockEndOffset) {
            int value = tokenType.read(tokensFile);
            if (value <= -2) {
                // Run length
                int runLength = -value;
                int token = tokenType.read(tokensFile);
                for (int i = 0; i < runLength; i++) {
                    decoded[decodedIndex] = token;
                    decodedIndex++;
                }
            } else {
                // Single value
                decoded[decodedIndex] = value;
                decodedIndex++;
            }
        }
        if (decodedIndex > DECODED_BLOCK_SIZE_TOKENS) {
            throw new IOException("Error decoding tokens: expected at most " + DECODED_BLOCK_SIZE_TOKENS + " tokens, got " + decodedIndex);
        }
        return decoded;
    }

    @Override
    public void writeTokens(int[] tokensInDoc, IndexOutput outTokensFile) throws IOException {
        outTokensFile.writeShort(DECODED_BLOCK_SIZE_TOKENS);

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
        int numBlocks = (tokensInDoc.length + DECODED_BLOCK_SIZE_TOKENS - 1) / DECODED_BLOCK_SIZE_TOKENS;
        int[][] blocks = new int[numBlocks][];
        for (int block = 0; block < numBlocks; block++) {
            int[] buffer = new int[DECODED_BLOCK_SIZE_TOKENS * 2]; // (worst case, we'll truncate it later)
            int tokenStart = block * DECODED_BLOCK_SIZE_TOKENS;
            int tokenEnd = Math.min(tokenStart + DECODED_BLOCK_SIZE_TOKENS, tokensInDoc.length);
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
