package nl.inl.blacklab.codec.tokens;

import java.io.IOException;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

public interface TokensCodec {

    static TokensCodec fromHeader(IndexInput tokensIndex) throws IOException {
        TokensCodecType codec = TokensCodecType.fromCode(tokensIndex.readByte());
        byte parameter = tokensIndex.readByte();
        return fromType(codec, parameter);
    }

    static TokensCodec choose(int[] tokensInDoc) {
        int max = 0, min = 0;
        boolean allTheSame = tokensInDoc.length > 0; // if no tokens, then not all the same.
        int last = -1;
        int sizeWithRunLengthEncoding = 0; // number of values needed for RLE
        int currentRunLength = 0;
        for (int token: tokensInDoc) {
            max = Math.max(max, token);
            min = Math.min(min, token);
            allTheSame = allTheSame && (last == -1 || last == token);
            if (last == token) {
                currentRunLength++;
            } else {
                sizeWithRunLengthEncoding++; // value
                if (currentRunLength > 1) {
                    sizeWithRunLengthEncoding++; // run length
                }
                currentRunLength = 1;
            }
            last = token;
            if ((min < Short.MIN_VALUE || max > Short.MAX_VALUE) && !allTheSame) // stop if already at worst case (int per token + not all the same).
                break;
        }

        // Determine codec:
        // - if all the same, use that (1 value total)
        // - if run-length encoding saves more than 50%, use that
        //   (RLE can be slower, so we want a significant gain)
        //   (also, we encode in blocks, so the gain will be less than calculated here)
        // - otherwise, value per token
        //(sizeWithRunLengthEncoding < tokensInDoc.length * 2 ? TokensCodecType.RUN_LENGTH_ENCODING : TokensCodecType.VALUE_PER_TOKEN);
        TokensCodecType codec = allTheSame ? TokensCodecType.ALL_TOKENS_THE_SAME : TokensCodecType.VALUE_PER_TOKEN;

        // determine parameter byte for codec.
        byte codecParameter = 0;
        switch (codec) {
            case ALL_TOKENS_THE_SAME:
                break;
            case VALUE_PER_TOKEN, RUN_LENGTH_ENCODING: {
                codecParameter = TokenValueType.choose(min, max).code;
                break;
            }
        }
        return fromType(codec, codecParameter);
    }

    static TokensCodec fromType(TokensCodecType codec, byte parameter) {
        return switch (codec) {
            case VALUE_PER_TOKEN -> TokensCodecValuePerToken.get(TokenValueType.fromCode(parameter));
            case ALL_TOKENS_THE_SAME -> TokensCodecAllTheSame.INSTANCE;
            case RUN_LENGTH_ENCODING -> TokensCodecRunLengthEncoded.get(TokenValueType.fromCode(parameter));
        };
    }

    void readSnippet(IndexInput tokensFile, long fileOffset, int startPosition, int[] snippet)
            throws IOException;

    void writeTokens(int[] tokensInDoc, IndexOutput outTokensFile) throws IOException;

    TokensCodecType codecType();

    byte parameter();

    default void writeHeader(IndexOutput outTokensIndexFile) throws IOException {
        outTokensIndexFile.writeByte(codecType().code);
        outTokensIndexFile.writeByte(parameter());
    }

    TokenValueType valueType();
}
