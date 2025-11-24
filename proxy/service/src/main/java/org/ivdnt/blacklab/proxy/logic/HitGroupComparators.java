package org.ivdnt.blacklab.proxy.logic;

import java.util.Comparator;

import org.ivdnt.blacklab.proxy.helper.Util;
import org.ivdnt.blacklab.proxy.representation.HitOrDocGroup;

import nl.inl.util.CollationKeyCache;

public class HitGroupComparators {

    private static final Comparator<HitOrDocGroup> CMP_IDENTITY = (a, b) -> CollationKeyCache.compare(Util.DEFAULT_COLLATOR, a.identity, b.identity);

    private static final Comparator<HitOrDocGroup> CMP_SIZE = (a, b) -> Long.compare(b.size, a.size);

    public static Comparator<HitOrDocGroup> deserialize(String sort) {
        if (sort.isEmpty())
            return null;
        boolean reversed = sort.charAt(0) == '-';
        if (reversed)
            sort = sort.substring(1);

        Comparator<HitOrDocGroup> cmp = sort.equals("identity") ? CMP_IDENTITY : CMP_SIZE;
        return reversed ? cmp.reversed() : cmp;
    }
}
