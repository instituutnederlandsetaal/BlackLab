package nl.inl.blacklab.search.results.stats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.docs.DocResults;

/**
 * Result of a facets search.
 *
 * Faceting counts number of documents per facet value.
 */
public class Facets implements SearchResult {
    
    private final List<DocProperty> facets;
    
    private final Map<DocProperty, DocGroups> counts;
    
    private int resultObjects = 0;

    public Facets(DocResults source, List<DocProperty> facets) {
        this.facets = facets;
        counts = new HashMap<>();
        for (DocProperty facetBy : facets) {
            DocGroups groups = source.group(facetBy, 0);
            counts.put(facetBy, groups);
            resultObjects += groups.size();
        }
    }

    public List<DocProperty> facets() {
        return facets;
    }

    public Map<DocProperty, DocGroups> countsPerFacet() {
        return counts;
    }

    @Override
    public long numberOfResultObjects() {
        return resultObjects;
    }

}
