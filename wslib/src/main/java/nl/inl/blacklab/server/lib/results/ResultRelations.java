package nl.inl.blacklab.server.lib.results;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStats;
import nl.inl.blacklab.server.lib.requests.RequestRelations;

public class ResultRelations {

    boolean separateSpans;

    Map<String, RelationsStats.ClassStats> classesMap;

    Collection<String> relClasses;

    public ResultRelations(RequestRelations request) {
        this.separateSpans = request.separateSpans();
        classesMap = request.index().getRelationsStats(request.annotatedField(), request.limitValues()).getClasses();
        boolean allClasses = request.relClasses().isEmpty() || request.relClasses().equals("*");
        relClasses = request.onlySpans() ?
                Set.of(RelationUtil.CLASS_INLINE_TAG) :
                (allClasses ?
                        classesMap.keySet() :
                        new HashSet<>(Arrays.asList(request.relClasses().split(",")))
                );
    }

    public boolean isSeparateSpans() {
        return separateSpans;
    }

    public Map<String, RelationsStats.ClassStats> getClassesMap() {
        return classesMap;
    }

    public Collection<String> getRelClasses() {
        return relClasses;
    }
}
