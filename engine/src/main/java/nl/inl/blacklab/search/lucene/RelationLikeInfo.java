package nl.inl.blacklab.search.lucene;

/** A MatchInfo that is either a relation or a list of relations
 *  (which we sometimes want to treat like a single relation, i.e. taking
 *   the min/max from all of the relations in the list)
 */
public interface RelationLikeInfo {
    boolean isRoot();

    int getSourceStart();

    int getSourceEnd();

    int getTargetStart();

    int getTargetEnd();

    String getField();

    String getTargetField();

    boolean isCrossFieldRelation();
}
