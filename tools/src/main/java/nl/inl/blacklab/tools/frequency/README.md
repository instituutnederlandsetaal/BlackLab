Calculate term frequencies over annotation(s) and metadata field(s).

Usage:

    FrequencyTool [--compress] INDEX_DIR CONFIG_FILE [OUTPUT_DIR]

- `--compress`:   compress output files (using lz4)
- `INDEX_DIR`:    index to generate frequency lists for
- `CONFIG_FILE`:  YAML file specifying what frequency lists to generate
- `OUTPUT_DIR`:   where to write output files (defaults to current dir)

Example config file:

```yaml
---

# The annotated field to make frequency lists for
annotatedField: contents

# The frequency lists we want to make
frequencyLists:

  # word frequencies over the entire corpus (defaults to 1-grams)
  - annotations:
      - word

  # lemma bigram frequencies per year
  - annotations:
      - lemma
    metadataFields:
      - year
    ngramSize: 2

  # lemma+pos+word trigram frequencies per year+medium+language
  - annotations:
      - lemma
      - pos
      - word
    metadataFields:
      - year
      - medium
      - language
    ngramSize: 3
```

Part of output TSV file for word+medium (annotations, metadata fields, frequency):

```
apple    forum      1234
pear     newspaper  2345
orange   book       3456
```
