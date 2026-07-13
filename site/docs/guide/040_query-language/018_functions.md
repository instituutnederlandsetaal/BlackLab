# Functions

<!-- @include: ../../_from_v5.md -->

BCQL supports a number of useful functions. Some functions produce queries as output, which you can use as part of a larger query; others produce values that you can pass to other functions.

Some of these functions exist in some form in other dialects of Corpus Query Language.

Functions to do with relations search are described [there](./relations.html).

## Query functions

Most functions are used in constraints (after `::`), but some can be used in the regular query part (before `::`).

### cspan: adjust to capture

<!-- @include: ../../_from_v5.md -->

Occasionally, you might not want your hits to span your whole query, but you're just interested in one part of it. You can use the `cspan()` function to adjust the hit:

```
# Find adjectives with tree
cspan(A:[pos="ADJ"]+ "tree", "A")
```

The hits will be anything captured as `A`, so just the adjectives in this case.

(of course, the same result can often be achieved using a lookahead. In this case, you could use `[pos="ADJ"]+ (?= "tree")` to get the same hits)


### meet: nearby words

<!-- @include: ../../_from_v5.md -->

You can use either the `meet` or the `meet_within` function to filter matches based on the presence of other words nearby.

For example, to find all occurrences of the word _cat_ that are within 5 tokens of the word _fluffy_ (before or after), you can use:

```
meet("cat", "fluffy", -5, 5)
```

To find occurrences of the phrase _black dog_ with the word _good_ occurring up to 10 tokens before it, use:

```
meet("black" "dog", "good", -10, -1)
```

To find occurrences of the word _fish_ with the phrase _in water_ occurring between 2 and 5 tokens after it (i.e. 1-4 tokens between _fish_ and _in_):

```
meet("fish", "in" "water", 2, 5)
```

To find occurrences of `platypus` where `weird` does NOT occur within 5 tokens:

```
meet("platypus", !"weird", -5, 5)
```

::: details How `meet` works

The `meet` function was inspired by the function with the same name in the [Sketch Engine](https://www.sketchengine.eu/documentation/cql-meet-union/).

Note that the `meet` function is just syntactic sugar for a "regular" BCQL query. For example, `meet("fish", "in" "water", 2, 5)` is equivalent to:

```
"fish" (?= []{1,4} "in" "water" )
```

(read as: find those occurrences of _fish_ that are followed by 1-4 tokens, followed by the phrase _in water_)

Different calls to `meet` and `meet_within` are rewritten to different queries, trying to choose the optimal variant for the situation.

:::


### meet_within: meet within boundary

<!-- @include: ../../_from_v5.md -->

You may only want to find words that occur in the same sentence. This is what `meet_within` is for. To find _cat_ and _fluffy_ in the same sentence:

```
meet_within("cat", "fluffy", <s/>)
```

To also require that they must occur within 5 words of each other:

```
meet_within("cat", "fluffy", <s/>, -5, 5)
```

To find _cat_ provided that the sentence does not have _fluffy_ within 5 words of it:

```
meet_within("cat", !"fluffy", <s/>, -5, 5)
```

### union: combine matches

<!-- @include: ../../_from_v5.md -->

The `union` function allows you to combine matches from a list of queries into a single result set. For example, to find all occurrences of either _fluffy cat_ or _good dog_, use:

```
union("fluffy" "cat", "good" "dog")
```

This is equivalent to the following query using the OR operator (`|`):

```
"fluffy" "cat" | "good" "dog"
```

The function is provided for those familiar with it from other corpus query languages.





## Constraints on captures

These are used in constraints (after `::`), to operate on parts captured by the query. 

### start: start of capture

Within constraints, use `start(A)` to get the starting token position of capture `A`.

If `A` captured the first token of the document, `start(A)` would return `0`.


### end: end of capture

Within constraints, use `end(A)` to get the ending token position of capture `A`. Note that this is always the first token position AFTER the capture.

The first token in a document has token position 0, so if `A` captured the first two words of the document, `end(A)` would return `2` (the third token).


### gap: gap between captures

<!-- @include: ../../_from_v5.md -->

To determine the gap between two captures:

```
gap(A, B, directional=false)
```

If `A` and `B` overlap or are contiguous, this will return 0.

Otherwise, if the optional argument `directional` is `false` (the default) it will determine the gap between A and B as a non-negative number. (To be precise, it calculates either `end(B) - start(A)` or `end(A) - start(B)`, whichever is positive)

If `directional` is set to `true`, the gap will be negative if `A` comes after `B`.


## Working with types

<!-- @include: ../../_from_v5.md -->

BCQL supports several basic types such as string, integer, boolean and list. These functions create or convert to specific types.

### abs: absolute value

<!-- @include: ../../_from_v5.md -->

Calculates the absolute value of a number:

```
abs(-5)   # result 5
```

### list: list of values

<!-- @include: ../../_from_v5.md -->

Some functions take a list of values as input. You can create such a list using the `list` function. For example, to pass multiple clauses to the `union` function, you could use:

```
union(list("cat", "dog"))
```

Note that you won't need `list()` to pass the function's final (or in this, only) parameter: anytime the final parameter to a function is of type list, you can simply pass a variable number of arguments and they will automatically be combined into a list. For example:

```
union("one", "two", "three")  # same as union(list("one", "two", "three"))
```

### str: interpret as string

<!-- @include: ../../_from_v5.md -->

A quoted string in BCQL can be interpreted as either a string or a token query, depending on the context. For example, `"duck"` might mean `[word="duck"]` (a query), or it might just mean the simple string value. When building a list of strings, BlackLab doesn't know which one you mean, so you need to explicitly tell it. For example, to create a list of strings _cat_ and _dog_, use:

```
list(str("cat"), str("dog"))
```

note that this is different from

```
list("cat", "dog")
```

which will create a list of token queries.


### symbol: interpret as symbol

<!-- @include: ../../_from_v5.md -->

To specify a symbol (e.g. an annotation) using a string:

```
[ symbol("word") = "cow" ]
```

is equivalent to

```
[ word = "cow" ]
```

For now, this mostly exists to prove that functions can return symbols, but it might prove useful in rare cases in the future. 
