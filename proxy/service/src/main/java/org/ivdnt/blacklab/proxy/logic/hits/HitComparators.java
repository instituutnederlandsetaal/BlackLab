package org.ivdnt.blacklab.proxy.logic.hits;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.ivdnt.blacklab.proxy.helper.Util;
import org.ivdnt.blacklab.proxy.representation.Hit;
import org.ivdnt.blacklab.proxy.representation.HitMin;
import org.ivdnt.blacklab.proxy.representation.MetadataValues;

import nl.inl.blacklab.util.PropertySerializeUtil;

class HitComparators {

    // FIXME: use correct collator
    final static Comparator<HitMin> baseComparator = Comparator.naturalOrder();

    public static Comparator<HitMin> deserializeMin(String hitProp) {
        return (a, b) -> {
            if (a.nodeId == b.nodeId) {
                // Hits from the same node are already in the correct order.
                return Long.compare(a.indexOnNode, b.indexOnNode);
            }
            return baseComparator.compare(a, b);
        };
    }

    public static Comparator<Hit> deserialize(String hitProp) {
        if (hitProp.isEmpty())
            return null;
        if (PropertySerializeUtil.isMultiple(hitProp)) {
            boolean reverse = false;
            if (hitProp.startsWith("-(") && hitProp.endsWith(")")) {
                reverse = true;
                hitProp = hitProp.substring(2, hitProp.length() - 1);
            }
            Comparator<Hit> result = Arrays.stream(PropertySerializeUtil.splitMultiple(hitProp))
                    .map(HitComparators::deserialize)
                    .reduce(Comparator::thenComparing)
                    .orElseThrow();
            if (reverse)
                result = result.reversed();
            return result;
        }

        List<String> parts = PropertySerializeUtil.splitPartsList(hitProp);
        String type = parts.get(0).toLowerCase();
        boolean reverse = false;
        if (!type.isEmpty() && type.charAt(0) == '-') {
            reverse = true;
            type = type.substring(1);
        }
        List<String> infos = parts.subList(1, parts.size());
        Comparator<Hit> cmp;
        switch(type) {
        case "hitposition": cmp = HIT_POSITION; break;
        case "decade": cmp = docFieldDecade(infos.get(0)); break;
        case "field": cmp = docField(infos.get(0)); break;
        case "doc": case "docid": cmp = DOC_PID; break;
        default:
            // Context property. Find annotation and sensitivity.
            String annotation = infos.isEmpty() ? "" : infos.get(0);
            if (annotation.isEmpty())
                throw new UnsupportedOperationException("Specify annotation for sort/group prop!");
            boolean sensitive = infos.size() <= 1 || infos.get(1).equals("s");
            switch (type) {
            case "before":
            case "left":
                cmp = hitTextBefore(annotation, sensitive, false);
                break;
            case "wordleft":
                cmp = hitTextBefore(annotation, sensitive, true);
                break;
            case "hit":
                cmp = hitMatchedText(annotation, sensitive);
                break;
            case "after":
            case "right":
                cmp = hitTextAfter(annotation, sensitive, false);
                break;
            case "wordright":
                cmp = hitTextAfter(annotation, sensitive, true);
                break;
            default:
                throw new UnsupportedOperationException("Hit property not supported: " + type);
            }
        }
        return reverse ? cmp.reversed() : cmp;
    }

    private static final Comparator<Hit> DOC_PID = Comparator.comparing(a -> a.docPid);

    private static final Comparator<Hit> HIT_POSITION = Comparator.comparingLong(a -> a.start);

    private static Comparator<Hit> hitMatchedText(String annotation, boolean sensitive) {
        return (a, b) -> a.match.compareTo(b.match, annotation, sensitive, false, false);
    }

    private static Comparator<Hit> hitTextBefore(String annotation, boolean sensitive, boolean stopAfterOne) {
        return (a, b) -> a.left.compareTo(b.left, annotation, sensitive, true, stopAfterOne);
    }

    private static Comparator<Hit> hitTextAfter(String annotation, boolean sensitive, boolean stopAfterOne) {
        return (a, b) -> a.right.compareTo(b.right, annotation, sensitive, false, stopAfterOne);
    }

    /** String compare by metadata field */
    private static Comparator<Hit> docField(String field) {
        return (a, b) -> {
            MetadataValues va = a.docInfo.get(field);
            MetadataValues vb = b.docInfo.get(field);
            String sa = va == null ? "" : va.getValue().get(0);
            String sb = vb == null ? "" : vb.getValue().get(0);
            return Util.DEFAULT_COLLATOR.compare(sa, sb);
        };
    }

    /** Compare by decade for metadata field containing year */
    private static Comparator<Hit> docFieldDecade(String field) {
        return (a, b) -> {
            int da = Integer.parseInt(a.docInfo.get(field).getValue().get(0)) / 10;
            int db = Integer.parseInt(b.docInfo.get(field).getValue().get(0)) / 10;
            return Integer.compare(da, db);
        };
    }
}
