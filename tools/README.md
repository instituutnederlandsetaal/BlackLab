# BlackLab tools

Commandline tools such as `IndexTool` and `QueryTool`, as well as some test/debug utilities.

## IndexTool

See [Indexing with BlackLab](https://blacklab.ivdnt.org/indexing-with-blacklab.html)

## Query Tool

See [Using the Query Tool](https://blacklab.ivdnt.org/query-tool.html)

## FrequencyTool

A tool to make frequency lists over the whole corpus.

WIP.

Example config file:

```yaml
---

# The annotated field to make frequency lists for
annotatedField: contents

# The frequency lists we want to make
frequencyLists:

  # Lemma frequency per year
  - name: lemmas-per-year
    # What (combination of) annotation to calculate frequencies for
    annotations:
      - lemma
    # What metadata fields to also group by
    metadataFields:
      - witnessYear_from
```
