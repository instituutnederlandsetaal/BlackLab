package nl.inl.blacklab.forwardindex;

import net.jcip.annotations.ThreadSafe;

/** Keeps a list of unique terms and their sort positions. */
@ThreadSafe
public interface Terms extends TermsSegment {

    /**
     * We have a snippet with segment-specific term ids; convert it to global term ids.
     *
     * Note that with external forward index, there is no such thing as segment-specific term ids,
     * there's only global term ids. So in this case, this method should just return the input.
     *
     * @param ord segment these snippets came from
     * @param segmentResults snippets with segment-specific term ids
     * @return segments with global term ids
     */
    int[] segmentIdsToGlobalIds(int ord, int[] segmentResults);

}
