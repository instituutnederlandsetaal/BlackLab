# Processing values

It is often useful to do some simple processing on a value just before it's added to the index. This could be a simple search and replace, or combining two fields into one for easier searching, etc. Or you might want to map a whole collection of values to different values. Both are possible.

::: tip Processing steps, or everything in XPath?

When using `processor: saxon`, you can often achieve the same results using XPath expressions ([examples](xpath-examples.md)).

Just use what works best in your case. Of course, when indexing a non-XML format such as CSV, processing steps are the 
only option.

:::

To perform simple value mapping on a metadata field, use the `map` action in the `process` section:

```yaml
metadata:
  containerPath: metadata
  fields:
  - name: speciesGroup
    valuePath: species
    process:

    # Map (translate) values (key will be translated to corresponding value)
    # In this example: translate species to the group they belong to
    - action: map
      table:
        dog: mammals
        cat: mammals
        shark: fish
        herring: fish
        # etc.
```

`process` can be used to perform simple string processing on (standoff) (sub)annotations and metadata values.

For example, to process a metadata field value, simply add the `process` key with a list of actions to perform, like so:

```yaml
metadata:
  containerPath: metadata
  fields:
  - name: author
    valuePath: author
    
    # Do some processing on the contents of the author element before indexing
    process:
    
      # If empty, set a default value
    - action: ifempty
      value: "(unknown)"
                          
      # Normalize spaces
    - action: replace
      find: "\\s\\s+"
      replace: " "
```

These are all the available generic processing steps:

| Processing Step         | Parameters                          | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|-------------------------|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `replace`               | `find`<br>`replace`<br>[`keep`]     | Do a regex search for `find` and replace each match with `replace`. Group references may be used. An optional parameter `keep` can be set to `both` to keep both the original strings and the results after applying the replace operation.                                                                                                                                                                                                                                                    |
| `ifempty`               | `value` or `field`                  | If current value is the empty string, set its value to either the specified `value` or the value of the specified `field`. If you refer to a `field`, make sure it is defined before this field (fields are processed in order). (NOTE: this processing step was previously called `default`)                                                                                                                                                                                                  |
| `append`                | `value` or `field`<br>[`separator`] | Append the specified `value` or the value of the specified `field`, using a space as the separator character. You may also specify a different `separator` if you wish, including the empty string (`""`).                                                                                                                                                                                                                                                                                     |
| `split` | [`separator`]<br>[`keep`]           | Split the field's value on the given separator and keep only the part indicated by `keep` (0-based). If `keep` is omitted, keep the first part. If `separator` is omitted, use `;`. The separator is a regex, and to split on special characters, those should be escaped by using a double backslash (`\\`). `keep` also allows two special values: `all` to keep all splits (instead of only the one at an index), and `both` to keep both the unsplit value as well as all the split parts. |
| `strip`           | [`chars`]                           | Strip specified chars from beginning and end. If `chars` is omitted, use space.                                                                                                                                                                                                                                                                                                                                                                                                                |
| `map`             | `table`                             | Map values to other values. The table is a map from input to output values. If the input value is not in the table, it is left unchanged.                                                                                                                                                                                                                                                                                                                                                      |
| `sort`                  |                                     | Sort multiple values using the default collator. This may help to ensure that the first term (which is the one used for sorting and grouping) is more predictable.                                                                                                                                                                                                                                                                                                                             |
| `unique`                |                                     | Remove duplicate values from the field. You normally never need to do this as it is done automatically just before actually indexing the final terms.                                                                                                                                                                                                                                                                                                                                          |

These processing steps are more specific to certain data formats:

| Processing Step           | Parameters                                              | Description |
|---------------------------|---------------------------------------------------------|-------------|
| `parsePos`                | `fieldName`                                             | Parse common part of speech expressions of the form `A(b=c,d=e)` where A is the main part of speech (e.g. `N` for noun), and b=c is a part of speech feature such as number=plural, etc. If you don't specify field (or specify an underscore `_` for field), the main part of speech is extracted. If you specify a feature name (e.g. `number`), that feature is extracted. |
| `chatFormatAgeToMonths`   |                                                         | Convert age as reported in CHAT format to number of months. |
| `concatDate`              | `yearField`<br>`monthField`<br>`dayField`<br>`autofill` | Concatenate 3 separate date fields into one, substituting unknown months and days with the first or last possible value. The output format is YYYYMMDD. Numbers are padded with leading zeroes.<br>Requires 4 arguments:**<br>`yearField`: the metadata field containing the numeric year<br>`monthField`: the metadata field containing the numeric month (so "12" instead of "december" or "dec")<br>`dayField`: the metadata field containing the numeric day<br>`autofill`: `start` to autofill missing month and day to the first possible value (01), or `end` to autofill the last possible value (12 for months, last day of the month in that year for days - takes into account leap years).<br>This step requires that at least the year is known. If the year is not known, no output is generated. |
If you would like a new processing step to be added, please let us know.

