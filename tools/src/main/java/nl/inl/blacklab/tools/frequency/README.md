# BlackLab FrequencyTool
Calculate term frequencies over annotation(s) and metadata field(s).

## Usage
    FrequencyTool INDEX_DIR CONFIG_FILE [OUTPUT_DIR]

- `INDEX_DIR`:    index to generate frequency lists for
- `CONFIG_FILE`:  YAML file specifying what frequency lists to generate
- `OUTPUT_DIR`:   where to write output files (defaults to current dir)

## Example config file
```yaml
runConfig:
    # The number of docs to process in parallel
    docsInParallel: 500_000

    # The number of groups (i.e. tsv rows) to collect before writing a chunk to disk.
    # Adjusting the value will affect memory usage. Lower it when running out of memory.
    groupsPerChunk: 10_000_000

    # The annotated field to make frequency lists for
    annotatedField: contents

    # Whether to compress output files (and intermediate chunk files)
    compressed: true

    # Whether to write in a format ideal for databases (ID-columns and ID-lookup-tables). See below for details.
    databaseFormat: true

# The frequency lists we want to make
frequencyLists:

    # word frequencies over the entire corpus (1-gram)
    # annotations is the only required field
    -   annotations:
            - word

    # word bigram frequencies per year
    -   name: "bigrams-per-year" # custom name
        annotations:
            - word
        ngramSize: 2 # bigrams
        metadata: # grouped by year
            -   name: year


    # word bigram frequencies per year, that occur at least 100 times
    -   name: "highly-frequent-bigrams-per-year"
        annotations:
            - word
        ngramSize: 2 # bigrams
        metadata: # grouped by year
            -   name: year
        cutoff: # frequency is measured over the entire corpus
            count: 100 # only include ngrams where ALL TOKENS(!) in the ngram occur AT LEAST(!) 100 times in the corpus
            annotation: word # based on the 'word' annotation

    # metadata settings example
    -   annotations:
            - word
        metadata:
            -   name: year
                required: true # discards documents where year is null or empty
            -   name: language
                nullValue: "Dutch" # Replace null or empty values with "Dutch"

    # grouped metadata example (only valid for databaseFormat: true)
    -   annotations:
            - word
        ngramSize: 2 # bigrams
        metadata:
            -   name: year
                required: true
            -   name: author
                nullValue: "Unknown"
                outputAsId: true # output author+language as a single ID-column, and output an extra ID-lookup-table (see below)
            -   name: language
                nullValue: "Dutch"
                outputAsId: true # output author+language as a single ID-column, and output an extra ID-lookup-table (see below)
```

## Example output
Example output TSV file for word+medium (`annotations, metadata fields, frequency`):
```
apple    forum      1234
pear     newspaper  2345
orange   book       3456
```


## Database format
With `databaseFormat: true`, the output will be in a database-friendly format, with a lookup table for the annotations. The following example of bigrams shows the difference between `true` and `false`:

### databaseFormat: false
bigrams.tsv (`annotations, frequency, medium`):
```
the apple    1234    forum
the book     2345    newspaper
```

### databaseFormat: true
bigrams.tsv (`annotation_id , frequency, medium`):
```
1    1234    forum
2    2345    newspaper
```

bigrams_annotations.tsv (`annotation_id, word_ids`):
```
1    {1,2}
2    {1,3}
```

bigrams_word.tsv (`word_id, word`)
```
1    the
2    apple
3    book
```
If you were to define a `lemma` annotation as well, you would get bigrams_lemma.tsv. Etc.

### outputAsId: true
If you were to set `outputAsId: true` for the `medium` metadata in the previous example, you would get:

bigrams.tsv (`annotation_id , frequency, metadata_id`):
```
1    1234    1
2    2345    2
```

bigrams_metadata.tsv (`metadata_id, metadata`)
```
1    forum
2    newspaper
```
