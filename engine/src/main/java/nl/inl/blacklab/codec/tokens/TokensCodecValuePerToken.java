package nl.inl.blacklab.codec.tokens;

import java.io.IOException;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

public class TokensCodecValuePerToken implements TokensCodec {

    private static TokensCodec withByte = new TokensCodecValuePerToken(TokenValueType.BYTE);
    private static TokensCodec withShort = new TokensCodecValuePerToken(TokenValueType.SHORT);
    private static TokensCodec withThreeBytes = new TokensCodecValuePerToken(TokenValueType.THREE_BYTES);
    private static TokensCodec withInt = new TokensCodecValuePerToken(TokenValueType.INT);

    public static TokensCodec get(TokenValueType tokenValueType) {
        return switch (tokenValueType) {
            case BYTE -> withByte;
            case SHORT -> withShort;
            case THREE_BYTES -> withThreeBytes;
            case INT -> withInt;
        };
    }

    private TokenValueType tokenType;

    public TokensCodecValuePerToken(TokenValueType tokenType) {
        this.tokenType = tokenType;
    }

    @Override
    public TokensCodecType codecType() {
        return TokensCodecType.VALUE_PER_TOKEN;
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
    public void readSnippet(IndexInput tokensFile, long docTokensOffset, int startPosition, int[] snippet)
            throws IOException {
        tokensFile.seek(docTokensOffset + startPosition * tokenType.sizeBytes());
        for (int j = 0; j < snippet.length; j++) {
            snippet[j] = tokenType.read(tokensFile);
        }
    }

    @Override
    public void writeTokens(int[] tokensInDoc, IndexOutput outTokensFile) throws IOException {
        for (int token: tokensInDoc) {
            tokenType.write(token, outTokensFile);
        }
    }
}
