package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Definitions of match information (captured groups) for a query.
 *
 * NOTE: shared between multiple Spans that might run in parallel!
 * For this reason, many methods are synchronized. Some methods are marked
 * as dangerous (i.e. rely on knowing "all" match info defs), but we've verified
 * that these are only called at a time when this is known.
 */
public class MatchInfoDefs {
    public static final MatchInfoDefs EMPTY = new MatchInfoDefs() {
        @Override
        MatchInfo.Def addNew(String name, MatchInfo.Type type, String field, String targetField) {
            throw new UnsupportedOperationException("Cannot add to DUMMY MatchInfoDefs");
        }
    };

    /** Match info names for our query we know about so far, in index order.
     *
     * New ones can be added while fetching hits (because different index segments can trigger different parts of the
     * query), but once added, they won't change.
     */
    private final List<MatchInfo.Def> defs = new ArrayList<>();

    public MatchInfoDefs() {
    }

    public MatchInfoDefs(List<MatchInfo.Def> defs) {
        this.defs.addAll(defs);
    }

    synchronized MatchInfo.Def addNew(String name, MatchInfo.Type type, String field, String targetField) {
        MatchInfo.Def e = new MatchInfo.Def(defs.size(), name, type, field, targetField);
        defs.add(e);
        return e;
    }

    public synchronized MatchInfo.Def get(int i) {
        return defs.get(i);
    }

    public synchronized MatchInfo.Def register(String name, MatchInfo.Type type, String field, String targetField) {
        Optional<MatchInfo.Def> mi = defs.stream()
                .filter(mid -> mid.getName().equals(name))
                .findFirst();
        if (mi.isPresent()) {
            mi.get().updateType(type); // update type (e.g. if group is referred to before we know its type)
            return mi.get(); // already registered, reuse
        }
        assert field != null;
        return addNew(name, type, field, targetField);
    }

    // Dangerous, can change! But only used when hits have all been processed?
    public synchronized List<MatchInfo.Def> currentListFiltered(Predicate<MatchInfo.Def> filter) {
        return defs.stream()
                .filter(filter)
                .collect(Collectors.toList());
    }

    /**
     * Get the number of match info definitions we have.
     *
     * NOTE: this can change because of multithreading! However, the code takes this into account, e.g. by
     * assuming that MatchInfo[] arrays can have different sizes, based on the match infos we knew about at the
     * time.
     *
     * @return
     */
    public synchronized int currentSize() {
        return defs.size();
    }

    /**
     * Are any of the captures of type INLINE_TAG or RELATION?
     * If yes, getRelationInfo() can return non-null values, and we must
     * e.g. store these in SpansInBuckets.
     *
     * NOTE: this can change because of multithreading! But it is only used in Spans,
     *       where it needs to be correct for current segment only, which is guaranteed.
     *
     * @return true if any of the captures are of type INLINE_TAG or RELATION
     */
    public synchronized boolean currentlyHasRelationCaptures() {
        return defs.stream().anyMatch(
                mid -> mid.getType() == MatchInfo.Type.INLINE_TAG || mid.getType() == MatchInfo.Type.RELATION);
    }

    /**
     * Get the match infos definitions.
     *
     * NOTE: this can change because of multithreading! But only used when producing hits response, so "list so far"
     *       is okay for that response.
     *
     * @return the list of match infos
     */
    public synchronized List<MatchInfo.Def> currentList() {
        return Collections.unmodifiableList(defs);
    }

    /**
     * Get the index of a match info definition by name.
     *
     * NOTE: this can change because of multithreading! But only used in HitProperty classes, when grouping/sorting,
     *       so all hits have been processed at that time.
     *
     * @param name name of the match info definition
     * @return index of the match info definition, or -1 if not found
     */
    public int indexOf(String name) {
        return currentListFiltered(def -> def.getName().equals(name)).stream()
                .map(MatchInfo.Def::getIndex)
                .findFirst().orElse(-1);
    }
}
