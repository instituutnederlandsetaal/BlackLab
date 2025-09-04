package nl.inl.blacklab.codec.tokens;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

public class TokensCodecAllTheSame implements TokensCodec {

    public static final TokensCodec INSTANCE = new TokensCodecAllTheSame();

    private TokensCodecAllTheSame() {}

    @Override
    public TokensCodecType codecType() {
        return TokensCodecType.ALL_TOKENS_THE_SAME;
    }

    @Override
    public TokenValueType valueType() {
        return TokenValueType.INT;
    }

    @Override
    public byte parameter() {
        return 0;
    }

    @Override
    public void readSnippet(IndexInput tokensFile, long docTokensOffset, int startPosition, int[] snippet)
            throws IOException {
        tokensFile.seek(docTokensOffset);
        int value = tokensFile.readInt();
        Arrays.fill(snippet, value);
    }

    @Override
    public void writeTokens(int[] tokensInDoc, IndexOutput outTokensFile) throws IOException {
        outTokensFile.writeInt(tokensInDoc[0]);
    }
}
