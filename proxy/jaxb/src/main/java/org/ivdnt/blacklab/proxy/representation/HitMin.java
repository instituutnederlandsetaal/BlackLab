package org.ivdnt.blacklab.proxy.representation;

import java.util.Arrays;
import java.util.Objects;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import org.ivdnt.blacklab.proxy.helper.SortValueUtil;
import org.ivdnt.blacklab.proxy.helper.Util;

/** Hit with minimal information for aggregator */
@XmlAccessorType(XmlAccessType.FIELD)
public class HitMin implements Comparable<HitMin> {

    /** What node did we read this hit from? */
    public int nodeId;

    /** What was this hit's index in the node's results? (for requesting concordances, etc.) */
    public long indexOnNode;

    /** What's the document id on the node? (really only used to keep hits from same doc together) */
    public int docIdOnNode;

    /** What is/are the value(s) these hits are sorted by? (used for merging) */
    public String[] sortValues;

    // required for Jersey
    public HitMin() {}

    public HitMin(int nodeId, long indexOnNode, int docIdOnNode, String[] sortValues) {
        this.nodeId = nodeId;
        this.indexOnNode = indexOnNode;
        this.docIdOnNode = docIdOnNode;
        this.sortValues = sortValues;
    }

    @Override
    public int compareTo(HitMin other) {
        if (sortValues == null || other.sortValues == null) {
            // No sort values. Sort by nodeId + indexOnNode (shouldn't be used).
            if (nodeId == other.nodeId) {
                return Long.compare(indexOnNode, other.indexOnNode);
            }
            return Integer.compare(nodeId, other.nodeId);
        }
        // Note that this doesn't use the appropriate collator, so we probably cannot
        // use this compareTo but have to explicitly do it elsewhere.
        return SortValueUtil.compare(Util.DEFAULT_COLLATOR, sortValues, other.sortValues);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HitMin hitMin = (HitMin) o;
        return nodeId == hitMin.nodeId && indexOnNode == hitMin.indexOnNode && docIdOnNode == hitMin.docIdOnNode
                && Arrays.equals(sortValues, hitMin.sortValues);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(nodeId, indexOnNode, docIdOnNode);
        result = 31 * result + Arrays.hashCode(sortValues);
        return result;
    }

    @Override
    public String toString() {
        return "HitMin{" +
                "nodeId=" + nodeId +
                ", indexOnNode=" + indexOnNode +
                ", docIdOnNode=" + docIdOnNode +
                ", sortValues=" + Arrays.toString(sortValues) +
                '}';
    }

    /**
     * Combine node id and doc id to create unique document number
     * @return unique document id
     */
    public long uniqueDocId() {
        return (((long)nodeId) << 32) | (long)docIdOnNode;
    }
}
