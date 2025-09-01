package nl.inl.blacklab.tools.frequency.config;

/**
 * Config for a frequency cutoff when generating the frequency list.
 *
 * @param annotation annotation name as in the blacklab index.
 * @param count      minimum number of occurrences to be included.
 */
public record CutoffConfig(String annotation, int count) {
}
