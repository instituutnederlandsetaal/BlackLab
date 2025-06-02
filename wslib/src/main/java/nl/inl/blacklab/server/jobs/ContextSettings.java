package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.results.ContextSize;

public record ContextSettings(ContextSize size, ConcordanceType concType) {}
