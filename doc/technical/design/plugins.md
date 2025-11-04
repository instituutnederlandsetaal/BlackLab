# Plugins in BlackLab

## Possible future types

```

// Inspect the file contents to see if we actually want to index it
// (e.g. "skip files with fewer than X words") 
FileFilter {
  include(FileReference)
}

// Highlight origin document content 
DocumentHighlighter {
    public String highlight(String partialContent, List<HitCharSpan> hits, int offset);
}

MetricsProviderType {
    MetricsProvider get(Map<> config)
}
RequestInstrumentationProvider {
    String requestId(HttpServletRequest)
}

ResultsCache {
    ...?
}

```
