# Miscellaneous

## Operator precedence

This is the precedence of the different BCQL operators, from highest to lowest. The highest precedence operators "bind 
most tightly". See the examples below.

Inside token brackets `[ ]`:

| Operator | Description    | Associativity |
|----------|----------------|---------------|
| `!`      | logical not    | right-to-left |
| `=` `!=` | (not) equals   | left-to-right |
| `&` `\|` | logical and/or | left-to-right |

At the sequence level (i.e. outside token brackets):

| Operator                                     | Description                                   | Associativity |
|----------------------------------------------|-----------------------------------------------|---------------|
| `!`                                          | logical not                                   | right-to-left |
| `[ ]`                                        | token brackets                                | left-to-right |
| `( )`                                        | function call                                 | left-to-right |
| `*` `+` `?`<br>`{n}` `{n,m}`                 | repetition                                    | left-to-right |
| `:`                                          | capture                                       | right-to-left |
| `< />` `< >` `</ >`                          | span (start/end)                              | left-to-right |
| `[] []`                                      | sequence<br>(implied operator)                | left-to-right |
| `\|` `&`                                     | union/intersection                            | left-to-right |
| `--> [ ; --> ]`<br>`^-->`<br>`==> [ ; ==> ]` | child relations<br>root relation<br>alignment | right-to-left |
| `within` `containing`                        | position filter                               | right-to-left |
| `::`                                         | capture constraint                            | left-to-right |

NOTES:
- you can always use grouping parens `( )` (at either token or sequence level) to override this precedence.
- notice that `|` and `&` have the _same_ precedence; don't rely on `&` binding more tightly than `|` or vice versa, which you might be used to from other languages.

A few examples:

| Query                                           | Interpreted as                                                     |
|-------------------------------------------------|--------------------------------------------------------------------|
| `[word = "can" & pos != "verb"]`                | `[ (word = "can") & (pos != "verb") ]`                             |
| `[pos = "verb" \| pos = "noun" & word = "can"]` | `[ (pos = "verb" \| pos = "noun") & word = "can"]`                 |
| `A:"very"+`                                     | `A:("very"+)`                                                      |
| `A:_ --> B:_`                                   | `(A:_) --> (B:_)`                                                  |
| `_ -obj-> _ -amod-> _`                          | `_ -obj-> (_ -amod-> _)`                                           |
| `!"d.*" & ".e.*"`                               | `(!"d.*") & ".e.*"`, meaning <br>`[word != "d.*" & word = ".e.*"]` |
| `"cow" within <pasture/> containing "grass"`    | `"cow" within (<pasture/> containing "grass")`                     |


## CQL support, differences

For those who already know CQL/CQP from another corpus search engine such as IMS Corpus Workbench, here's a quick overview of the extent of BlackLab's support for this "lingua franca" corpus query language. If a feature we don't support yet is important to you, please let us know. If it's quick to add, we may be able to help you out.

### Supported features ###

BlackLab currently supports (arguably) most of the important features of Corpus Query Language:

* Matching on token annotations, using regular expressions and `=`, `!=`, `!`. Example: `[word="bank"]` (or just `"bank"`)
* Case/accent sensitive matching. Note that, unlike in CWB, case-INsensitive matching is the default. To explicitly match case-/accent-insensitively, use `"(?i)..."`. Example: `"(?-i)Mr\." "(?-i)Banks"`
* Combining criteria using `&`, `|` and `!`. Parentheses can also be used for grouping. Example: `[lemma="bank" & pos="V"]`
* Matchall pattern `[]` matches any token. Example: `"a" [] "day"`
* Regular expression operators `+`, `*`, `?`, `{n}`, `{n,m}` at the token level. Example: `[pos="ADJ"]+`
* Sequences of token constraints. Example: `[pos="ADJ"] "cow"`
* Operators `|`, `&` and parentheses can be used to build complex sequence queries. Example: `"happy" "dog" | "sad" "cat"`
* Querying with tag positions using e.g. `<s>` (start of sentence), `</s>` (end of sentence), `<s/>` (whole sentence) or `<s> ... </s>` (equivalent to `<s/> containing ...`). Example: `<s> "The" `. XML attribute values may be used as well, e.g. `<ne type="PERS"/>` ("named entities that are persons").
* Using `within` and `containing` operators to find hits inside another set of hits. Example: `"you" "are" within <s/>`
* Using an anchor to capture a token position. Example: `"big" A:[]`. Captured matches can be used in capture
  constraints (see next item) or processed separately later (using the Java interface; capture information is not yet returned by BlackLab Server). Note that BlackLab can actually capture entire groups of tokens as well, similarly to regular expression engines.
* Capture constraints, such as requiring two captures to contain the same word. Example: `"big" A:[] "or" "small" B:[] :: A.word = B.word`

See below for features not in this list that may be added soon, and let us know if you want a particular feature to be added.

### Differences from CWB ###

BlackLab's CQL syntax and behaviour differs in a few ways from CWBs, although they are mostly lesser-used features.

For now, here's what you should know:

* Case-insensitive search is the default in BlackLab, while CWB and Sketch Engine use case-sensitive search as the default. If you want to match a term case-sensitively, use `"(?-i).."` or `"(?c).."`.
* If you want to match a string literally, not as a regular expression, use backslash escaping (`"e\.g\."`) or a literal string (`l"e.g."`)
* BlackLab supports result set manipulation such as: sorting (including on specific context words), grouping/frequency distribution, subsets, sampling, setting context size, etc. However, these are supported through the REST and Java APIs, not through a command interface like in CWB. See [BlackLab Server overview](/server/overview.md)).
* Querying XML elements and attributes looks natural in BlackLab: `<s/>` means "sentences", `<s>` means "starts of sentences", `<s type="A">` means "sentence tags with a type attribute with value A". This natural syntax differs from CWBs in some places, however, particularly when matching XML attributes.
* In capture constraints (expressions occurring after `::`), only literal matching (no regex matching) is currently supported.
* To return whole sentences as the context of hits, pass `context=s` to BLS.
* The implication operator `->` is currently only supported in capture constraints (expressions after the `::` operator), not in a regular token constraints.
* We don't support the `@` anchor and corresponding `target` label; use a named anchor instead.
* backreferences to anchors only work in capture constraints, so this doesn't work: `A:[] [] [word = A.word]`. Instead, use something like: `A:[] [] B:[] :: A.word = B.word`.
* Instead of CWBs `intersection`, `union` and `difference` operators, BlackLab supports the `&`, `|` and `!` operators at the top-level of the query, e.g. `("double" [] & [] "trouble")` to match the intersection of these queries, i.e. 'double trouble' and `("happy" "dog" | "sad" "cat")` to match the union of 'happy dog' and 'sad cat'. Difference can be achieved by combining `!` and `&`, e.g. `("happy" [] & !([] "dog"))` to match 'happy' followed by anything except 'dog' (although this is better expressed as `"happy" [word != "dog"]`).
* Integer ranges are supported: `[pos="verb" & pos_confidence=in[50,100]]` or `<verse number=in[1,10]/>` (ranges are always inclusive)

### (Currently) unsupported ###

Some CWB features that are not (yet) supported in BlackLab:

* `lbound`, `rbound` functions to get the edge of a region. You can use `<s>` to get all starts-of-sentences or `</s>` to get all ends-of-sentences, however.
* `distance`, `distabs` functions and `match`, `matchend` anchor points (sometimes used in capture constraints).
* using an XML element name to mean 'token is contained within', like `[(pos = "N") & !np]` meaning "noun NOT inside in an `<np/>` tag".
* a number of less well-known features.

If people ask about missing features, we're happy to work with them to see if it could be added.
