package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;

/**
 * Applies grouping to the results in a DocResults object.
 */
public class DocGroups extends ResultsList<DocGroup> implements ResultGroups, Iterable<DocGroup> {

    /**
     * Construct a DocGroups from a list of groups.
     * 
     * @param queryInfo query info
     * @param groups list of groups to wrap
     * @param groupBy what the documents were grouped by
     * @param sampleParameters sample parameters if this is a sample, null otherwise
     * @param windowStats window stats if this is a window, null otherwise
     * @return document groups
     */
    public static DocGroups fromList(QueryInfo queryInfo, List<DocGroup> groups, DocProperty groupBy, SampleParameters sampleParameters, WindowStats windowStats) {
        return new DocGroups(queryInfo, groups, groupBy, sampleParameters, windowStats);
    }

    /**
     * The groups.
     * Note that we keep the groups both in the ResultsList.results object for
     * the ordering and access by index as well as in this map to access by group
     * identity. Ideally this wouldn't be necessary, but we need direct access to
     * the ordering for e.g. paging.
     */
    private final Map<PropertyValue, DocGroup> groups = new HashMap<>();

    /** Maximum number of groups (limited by number of entries allowed in a HashMap) */
    public static final int MAX_NUMBER_OF_GROUPS = Constants.JAVA_MAX_HASHMAP_SIZE;

    private final ResultsStatsSaved stats;

    private long largestGroupSize = 0;

    private long totalResults = 0;
    
    private long resultObjects = 0;

    private final DocProperty groupBy;
    
    private final WindowStats windowStats;
    
    private final SampleParameters sampleParameters;

    protected DocGroups(QueryInfo queryInfo, List<DocGroup> groups, DocProperty groupBy, SampleParameters sampleParameters, WindowStats windowStats) {
        super(queryInfo);

        if (groups.size() > MAX_NUMBER_OF_GROUPS)
            throw new UnsupportedOperationException("Cannot handle more than " + MAX_NUMBER_OF_GROUPS + " groups");

        this.groupBy = groupBy;
        this.windowStats = windowStats;
        this.sampleParameters = sampleParameters;
        for (DocGroup group: groups) {
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            totalResults += group.size();
            resultObjects += group.numberOfStoredHits() + 1;
            results.add(group);
            this.groups.put(group.identity(), group);
        }
        stats = new ResultsStatsSaved(results.size(), results.size(), MaxStats.NOT_EXCEEDED);
    }

    @Override
    public ResultsStats resultsStats() {
        return stats;
    }

    public WindowStats windowStats() {
        return windowStats;
    }

    public SampleParameters sampleParameters() {
        return sampleParameters;
    }

    public DocGroup get(PropertyValue groupId) {
        return groups.get(groupId);
    }
    
    public DocGroups sort(DocGroupProperty sortProp) {
        ensureResultsRead(-1);
        List<DocGroup> sorted = new ArrayList<>(this.results);
        sorted.sort(sortProp);
        return new DocGroups(
            this.queryInfo(), 
            sorted,
            this.groupBy, 
            null,
            null
       );
    }

    public DocGroups filter(DocGroupProperty property, PropertyValue value) {
        return new DocGroups(
            this.queryInfo(), 
            this.results.stream().filter(group -> property.get(group).equals(value)).toList(),
            this.groupBy, 
            null,
            null
       );
    }

    @Override
    public long largestGroupSize() {
        return largestGroupSize;
    }

    @Override
    public long sumOfGroupSizes() {
        return totalResults;
    }

    @Override
    public DocProperty groupCriteria() {
        return groupBy;
    }
    
    public DocGroups window(long first, long number) {
        List<DocGroup> resultsWindow = doWindow(this, first, number);
        boolean hasNext = resultsStats().waitUntil().processedAtLeast(first + resultsWindow.size() + 1);
        WindowStats windowStats = new WindowStats(hasNext, first, number, resultsWindow.size());
        return DocGroups.fromList(queryInfo(), resultsWindow, groupBy, null, windowStats);
    }

    @Override
    protected void ensureResultsRead(long number) {
        // NOP
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters 
     * @return the sample
     */
    public DocGroups sample(SampleParameters sampleParameters) {
        return DocGroups.fromList(queryInfo(), doSample(this, sampleParameters), groupCriteria(), sampleParameters, null);
    }
    
    @Override
    public long numberOfResultObjects() {
        return resultObjects;
    }

    @Override
    public String toString() {
        return "DocGroups{" +
                "totalResults=" + totalResults +
                ", groupBy=" + groupBy +
                ", windowStats=" + windowStats +
                ", sampleParameters=" + sampleParameters +
                ", largestGroupSize=" + largestGroupSize +
                '}';
    }
}
