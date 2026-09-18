package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.SourceRangeEncoding;

/** Reads exact token source ranges from a constant-term auxiliary vector. */
final class SourceRangeReader {

    private static final BytesRef TERM = new BytesRef(SourceRangeEncoding.TERM);

    record DocumentInfo(int flags, int realTokenCount, Document storedFields) {}

    record SourceWindow(int start, int end) {}

    private SourceRangeReader() {
    }

    static void characterOffsets(BlackLabIndex index, int docId, AnnotatedField field,
            int[] startsOfWords, int[] endsOfWords, boolean fillInDefaultsIfNotFound) {
        String rangeField = AnnotatedFieldNameUtil.sourceRangesField(field.name());
        DocumentInfo info = documentInfo(index, docId, field, null);
        characterOffsets(index.reader(), docId, rangeField, info.realTokenCount(), startsOfWords, endsOfWords,
                fillInDefaultsIfNotFound);
    }

    static DocumentInfo documentInfo(BlackLabIndex index, int docId, AnnotatedField field,
            String additionalStoredField) {
        String rangeField = AnnotatedFieldNameUtil.sourceRangesField(field.name());
        String statusField = AnnotatedFieldNameUtil.sourceStatusField(index.mainAnnotatedField().name());
        String lengthField = field.tokenLengthField();
        try {
            Set<String> storedFieldNames = additionalStoredField == null ? Set.of(statusField, lengthField) :
                    Set.of(statusField, lengthField, additionalStoredField);
            Document document = index.reader().storedFields().document(docId, storedFieldNames);
            int flags = requiredInt(document, statusField, rangeField, docId);
            int knownFlags = SourceRangeEncoding.DOC_FLAG_XML |
                    SourceRangeEncoding.DOC_FLAG_SOURCE_RANGE_VECTORS;
            if ((flags & ~knownFlags) != 0 ||
                    (flags & SourceRangeEncoding.DOC_FLAG_SOURCE_RANGE_VECTORS) == 0)
                throw corrupt(rangeField, docId, "invalid document source status " + flags);
            int storedTokenCount = requiredInt(document, lengthField, rangeField, docId);
            if (storedTokenCount < BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN)
                throw corrupt(rangeField, docId, "invalid stored token count " + storedTokenCount);
            return new DocumentInfo(flags,
                    storedTokenCount - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN, document);
        } catch (IOException e) {
            throw new InvalidIndex("Error reading source-range metadata for document " + docId, e);
        }
    }

    static SourceWindow sourceWindow(IndexReader reader, int docId, String rangeField, int realTokenCount,
            int start, int end) {
        if (start < 0 || start >= end || end > realTokenCount)
            throw new IllegalArgumentException("Invalid token range " + start + ".." + end);
        int[] bounds = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        scanPrefix(reader, docId, rangeField, realTokenCount, end - 1, (position, sourceStart, sourceEnd) -> {
            if (position >= start) {
                bounds[0] = Math.min(bounds[0], sourceStart);
                bounds[1] = Math.max(bounds[1], sourceEnd);
            }
        });
        return new SourceWindow(bounds[0], bounds[1]);
    }

    static void characterOffsets(IndexReader reader, int docId, String rangeField, int realTokenCount,
            int[] startsOfWords, int[] endsOfWords, boolean fillInDefaultsIfNotFound) {
        long totalLong = (long) startsOfWords.length + endsOfWords.length;
        if (totalLong > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Too many source-range positions requested");
        int total = (int) totalLong;
        if (total == 0)
            return;
        if (realTokenCount < 0)
            throw corrupt(rangeField, docId, "negative real token count");
        if (fillInDefaultsIfNotFound && realTokenCount == 0) {
            Arrays.fill(startsOfWords, 0);
            Arrays.fill(endsOfWords, 0);
            return;
        }

        int[] requested = new int[total];
        System.arraycopy(startsOfWords, 0, requested, 0, startsOfWords.length);
        System.arraycopy(endsOfWords, 0, requested, startsOfWords.length, endsOfWords.length);
        // Concordance context may run past the document, and empty hits address either edge.
        // Clamp only the lookup positions: replacement still distinguishes starts from ends.
        if (fillInDefaultsIfNotFound) {
            for (int i = 0; i < requested.length; i++)
                requested[i] = Math.max(0, Math.min(realTokenCount - 1, requested[i]));
        }
        Arrays.sort(requested);
        int uniqueCount = 0;
        for (int position: requested) {
            if (position < 0 || position >= realTokenCount)
                throw corrupt(rangeField, docId, "requested token position out of bounds: " + position);
            if (uniqueCount == 0 || position != requested[uniqueCount - 1])
                requested[uniqueCount++] = position;
        }

        int[] sourceStarts = new int[uniqueCount];
        int[] sourceEnds = new int[uniqueCount];
        readRequestedPrefix(reader, docId, rangeField, realTokenCount, requested, uniqueCount,
                sourceStarts, sourceEnds);
        replacePositions(startsOfWords, requested, uniqueCount, sourceStarts, sourceEnds, realTokenCount, true);
        replacePositions(endsOfWords, requested, uniqueCount, sourceStarts, sourceEnds, realTokenCount, false);
    }

    private static void readRequestedPrefix(IndexReader reader, int docId, String rangeField, int realTokenCount,
            int[] requested, int uniqueCount, int[] sourceStarts, int[] sourceEnds) {
        int[] requestedIndex = { 0 };
        scanPrefix(reader, docId, rangeField, realTokenCount, requested[uniqueCount - 1],
                (position, sourceStart, sourceEnd) -> {
                    if (position == requested[requestedIndex[0]]) {
                        sourceStarts[requestedIndex[0]] = sourceStart;
                        sourceEnds[requestedIndex[0]] = sourceEnd;
                        requestedIndex[0]++;
                    }
                });
    }

    static void scanPrefix(IndexReader reader, int docId, String rangeField, int realTokenCount,
            int maxPosition, RangeHandler handler) {
        if (maxPosition < 0 || maxPosition >= realTokenCount)
            throw corrupt(rangeField, docId, "requested token position out of bounds: " + maxPosition);
        try {
            Terms terms = reader.termVectors().get(docId, rangeField);
            if (terms == null)
                throw corrupt(rangeField, docId, "missing source-range vector");
            if (!terms.hasPositions() || !terms.hasPayloads() || terms.hasOffsets())
                throw corrupt(rangeField, docId, "invalid source-range vector layout");

            TermsEnum termsEnum = terms.iterator();
            BytesRef term = termsEnum.next();
            if (!TERM.equals(term))
                throw corrupt(rangeField, docId, "missing constant term " + SourceRangeEncoding.TERM);
            PostingsEnum postings = termsEnum.postings(null, PostingsEnum.PAYLOADS);
            if (postings.nextDoc() != 0 || postings.freq() != realTokenCount)
                throw corrupt(rangeField, docId, "source-range frequency does not match real token count");

            for (int expectedPosition = 0; expectedPosition <= maxPosition; expectedPosition++) {
                int position = postings.nextPosition();
                if (position != expectedPosition)
                    throw corrupt(rangeField, docId, "noncontiguous source-range position " + position);
                BytesRef payload = postings.getPayload();
                if (payload == null || payload.length != SourceRangeEncoding.PAYLOAD_LENGTH)
                    throw corrupt(rangeField, docId, "source-range payload is not eight bytes");
                int sourceStart = readInt(payload.bytes, payload.offset);
                int sourceEnd = readInt(payload.bytes, payload.offset + Integer.BYTES);
                if (sourceStart < 0 || sourceEnd < sourceStart)
                    throw corrupt(rangeField, docId, "invalid source range " + sourceStart + ".." + sourceEnd);
                handler.range(expectedPosition, sourceStart, sourceEnd);
            }
            if (termsEnum.next() != null)
                throw corrupt(rangeField, docId, "source-range vector contains more than one term");
        } catch (IOException e) {
            throw new InvalidIndex("Error reading source ranges from " + rangeField + " in document " + docId, e);
        }
    }

    @FunctionalInterface
    interface RangeHandler {
        void range(int position, int sourceStart, int sourceEnd);
    }

    private static void replacePositions(int[] positions, int[] requested, int uniqueCount,
            int[] sourceStarts, int[] sourceEnds, int realTokenCount, boolean starts) {
        for (int i = 0; i < positions.length; i++) {
            int position = positions[i];
            int requestedIndex = Arrays.binarySearch(requested, 0, uniqueCount,
                    Math.max(0, Math.min(realTokenCount - 1, position)));
            positions[i] = position < 0 || position < realTokenCount && starts ?
                    sourceStarts[requestedIndex] : sourceEnds[requestedIndex];
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24 |
                (bytes[offset + 1] & 0xff) << 16 |
                (bytes[offset + 2] & 0xff) << 8 |
                bytes[offset + 3] & 0xff;
    }

    private static int requiredInt(Document document, String fieldName, String rangeField, int docId) {
        IndexableField[] fields = document.getFields(fieldName);
        if (fields.length != 1 || fields[0].numericValue() == null)
            throw corrupt(rangeField, docId, "missing or duplicate numeric field " + fieldName);
        Number value = fields[0].numericValue();
        long longValue = value.longValue();
        if (!(value instanceof Integer || value instanceof Long) || longValue < Integer.MIN_VALUE ||
                longValue > Integer.MAX_VALUE)
            throw corrupt(rangeField, docId, "invalid numeric field " + fieldName);
        return (int) longValue;
    }

    private static InvalidIndex corrupt(String field, int docId, String detail) {
        return new InvalidIndex("Corrupt source ranges in " + field + " for document " + docId + ": " + detail);
    }
}
