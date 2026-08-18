package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A document fragment with its metadata.
 *
 * @param span     start and end position of the fragment
 * @param metadata metadata for this fragment
 */
record Fragment(Span span, Map<String, Collection<String>> metadata) implements Comparable<Fragment> {

    /**
     * A position where a fragment starts or ends
     *
     * @param pos      milestone position in the document
     * @param isStart  true if this milestone is the start of a fragment, false if it's the end
     * @param fragment the associated fragment
     */
    private record Milestone(int pos, boolean isStart, Fragment fragment) implements Comparable<Milestone> {
        @Override
        public int compareTo(Milestone other) {
            int cmp = Integer.compare(this.pos, other.pos);
            if (cmp == 0) {
                // Ends are sorted before starts (i.e. adjoining fragments don't overlap)
                cmp = Boolean.compare(this.isStart, other.isStart);
            }
            return cmp;
        }
    }

    /**
     * Chop fragments into list of non-overlapping fragments.
     * <p>
     * Given an unordered list of potentially overlapping fragments, return a list of potentially chopped,
     * non-overlapping fragments in document order. Also combines metadata from document and fragments to
     * produce the final metadata for each fragment.
     *
     * @param fragments        the list of fragments to chop
     * @param documentMetadata document-level metadata to apply to all fragments (before any overrides)
     * @param docLengthTokens   the length of the document in tokens (used to create a fragment after the last fragment)
     * @return a list of non-overlapping fragments in document order, with combined metadata
     */
    public static List<Fragment> chopOverlappingFragments(List<Fragment> fragments,
            Map<String, Collection<String>> documentMetadata, int docLengthTokens) {
        // Get a sorted list of milestones
        List<Milestone> milestones = new ArrayList<>(fragments.size() * 2);
        for (Fragment fragment: fragments) {
            milestones.add(new Milestone(fragment.span.start(), true, fragment));
            milestones.add(new Milestone(fragment.span.end(), false, fragment));
        }
        Collections.sort(milestones);

        // Now iterate over the milestones, chopping into non-overlapping new fragments.
        // TODO: create fragment before first and after last fragment
        List<Fragment> choppedFrags = new ArrayList<>();
        Set<Fragment> openFragments = new LinkedHashSet<>();
        if (!documentMetadata.isEmpty()) {
            // Make sure document-level metadata is applied before any metadata from actual fragments
            openFragments.add(new Fragment(Span.between(0, 0), documentMetadata));
        }
        int pos = 0;
        for (Milestone milestone: milestones) {
            if (!openFragments.isEmpty() && milestone.pos > pos) {
                // Add fragment from last milestone to this milestone, with combined metadata from all open fragments
                // (note that this will create a fragment before the first fragment if the first milestone is not at
                //  position 0 and there was document-level metadata)
                choppedFrags.add(new Fragment(Span.between(pos, milestone.pos), metadataFrom(openFragments)));
            }
            // Update openFragments according to the milestone type
            if (milestone.isStart())
                openFragments.add(milestone.fragment());
            else
                openFragments.remove(milestone.fragment());
            pos = milestone.pos;
        }
        if (!documentMetadata.isEmpty() && docLengthTokens > pos) {
            // Add final fragment after last fragment, with only the document-level metadata
            choppedFrags.add(new Fragment(Span.between(pos, docLengthTokens), documentMetadata));
        }
        return choppedFrags;
    }

    /**
     * Determine the effective metadata from an ordered set of metadata overrides.
     * <p>
     * Applies metadata from the set of fragments, in order, where later metadata values override earlier ones.
     *
     * @param openFragments the set of fragments whose metadata to apply
     * @return the effective metadata after applying all overrides
     */
    private static Map<String, Collection<String>> metadataFrom(Set<Fragment> openFragments) {
        Map<String, Collection<String>> result = new HashMap<>();
        for (Fragment fragment: openFragments) {
            result.putAll(fragment.metadata());
        }
        return result;
    }

    /**
     * Sort fragments by start position first, then endposition
     */
    @Override
    public int compareTo(Fragment other) {
        int cmp = Integer.compare(this.span().start(), other.span().start());
        return cmp == 0 ? Integer.compare(this.span().end(), other.span().end()) : cmp;
    }

    public boolean contains(Fragment fragment) {
        return fragment.span.start() >= span().start() && fragment.span.end() <= span().end();
    }
}
