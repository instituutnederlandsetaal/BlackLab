package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.SourceRangeReader.SourceWindow;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.SourceUnitCodec;

/** Selects complete XML containers in source order, or the whole field document. */
final class SourceUnitReader {

    private SourceUnitReader() {}

    static List<SourceWindow> sourceRanges(BlackLabIndex index, int docId, AnnotatedField field,
            int start, int end) {
        boolean fullRequest = start == -1 && end == -1;
        String unitField = AnnotatedFieldNameUtil.sourceUnitsField(field.name());
        SourceRangeReader.DocumentInfo info = SourceRangeReader.documentInfo(index, docId, field,
                fullRequest ? null : unitField);
        int tokenCount = info.realTokenCount();
        if (start < -1 || end < -1)
            throw new IllegalArgumentException("Invalid token range " + start + ".." + end);
        start = start == -1 ? 0 : start;
        end = end == -1 ? tokenCount : end;
        if (start > end || end > tokenCount)
            throw new IllegalArgumentException("Invalid token range " + start + ".." + end);
        if (fullRequest)
            return List.of(new SourceWindow(-1, -1));
        IndexableField[] storedUnits = info.storedFields().getFields(unitField);
        if (storedUnits.length > 1 || storedUnits.length == 1 && storedUnits[0].binaryValue() == null)
            throw corrupt(unitField, docId, "duplicate or non-binary source-unit field");
        if (storedUnits.length == 0)
            return List.of(new SourceWindow(-1, -1));
        BytesRef units = storedUnits[0].binaryValue();
        int unitCount = SourceUnitCodec.unitCount(units);
        if ((tokenCount == 0) != (unitCount == 0))
            throw corrupt(unitField, docId, "source-unit count does not match document emptiness");
        if (tokenCount == 0)
            return List.of(new SourceWindow(-1, -1));
        // An empty selection uses the following token, or the last token at the document end.
        start = Math.min(start, tokenCount - 1);
        end = Math.max(start, end - 1);
        int firstUnit = findUnit(units, unitCount, start);
        int lastUnit = findUnit(units, unitCount, end);
        if (firstUnit < 0 || lastUnit < firstUnit)
            throw corrupt(unitField, docId, "source units do not cover requested tokens");
        int tokenStart = firstUnit == 0 ? 0 : SourceUnitCodec.value(units, firstUnit - 1, 2);
        if (tokenStart < 0 || start < tokenStart)
            throw corrupt(unitField, docId, "source units do not contain requested tokens");
        List<SourceWindow> ranges = new ArrayList<>(lastUnit - firstUnit + 1);
        for (int unit = firstUnit; unit <= lastUnit; unit++) {
            int sourceStart = SourceUnitCodec.value(units, unit, 0);
            int sourceEnd = SourceUnitCodec.value(units, unit, 1);
            int tokenEnd = SourceUnitCodec.value(units, unit, 2);
            if (sourceStart < 0 || sourceEnd <= sourceStart || tokenEnd <= tokenStart || tokenEnd > tokenCount)
                throw corrupt(unitField, docId, "invalid source unit " + unit);
            ranges.add(new SourceWindow(sourceStart, sourceEnd));
            tokenStart = tokenEnd;
        }
        ranges.sort(Comparator.comparingInt(SourceWindow::start)
                .thenComparing(Comparator.comparingInt(SourceWindow::end).reversed()));
        int previousEnd = -1, kept = 0;
        for (SourceWindow range: ranges) {
            if (range.end() <= previousEnd)
                continue; // Already included in an enclosing container.
            if (range.start() < previousEnd)
                throw corrupt(unitField, docId, "crossing XML container ranges");
            ranges.set(kept++, range);
            previousEnd = range.end();
        }
        ranges.subList(kept, ranges.size()).clear();
        return ranges;
    }

    private static int findUnit(BytesRef units, int unitCount, int tokenPosition) {
        int low = 0, high = unitCount - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (tokenPosition >= SourceUnitCodec.value(units, middle, 2))
                low = middle + 1;
            else
                high = middle - 1;
        }
        return low < unitCount ? low : -1;
    }

    private static InvalidIndex corrupt(String field, int docId, String detail) {
        return new InvalidIndex("Corrupt source units in " + field + " for document " + docId + ": " + detail);
    }
}
