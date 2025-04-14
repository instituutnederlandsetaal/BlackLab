# Relations between words and word groups

(largely completed; see GitHub issue [#405](https://github.com/instituutnederlandsetaal/BlackLab/issues/405))

## Goal

We want to index (and search for) relations between two words, such as the dependency relation "verb X has object Y". In this case, we have two words, X and Y, and the `object` relation pointing from X to Y:

    X  -object->  Y

Example:

             Y <-o- X 
    Small   man   bites   large   dog.

For dependency relations, the most common case is that a word can point to multiple other words, but can only be pointed to by one other word. We don't want to limit the relations primitive to just dependency relations though, so our implementation will be able to handle any set of relations.

### Special relations

For dependency relations, there's a special relation called the `root` relation, which has a target (dep) but not a source (head). We will need to record this special case as an option in the payload.


### Terminology

For dependency relations, linguists call the start of the arrow X the _head_ of the relation and the end of the arrow Y the _dependent_.

However, because we don't want to limit the relations primitive to only dependency relations, and because the term _head_ can be confusing (there's also the "head of an arrow", but that's the other end), we will use _source_ for X and _target_ for Y in BlackLab.
 
Of course, in addition to the CQL extension functions described below, we could also include syntax specifically suited for working with dependency relations, which would use the common terminology of _head_ and _dependent_.

### Relations beteween groups of words

Obviously it would also be very useful to be able to index relations between groups of words. For example, in the example above, the object of the sentence is not just "man" but actually "Small man".

Our implementation should allow us to store relations between groups of words as well, although full support for this might not be in the initial version.

We might even want to support discontinuous groups of words at some point, but the initial version does not do this.


## Document format and test data

The [CoNLL-U format](https://universaldependencies.org/format.html) is commonly used to encode texts with these kinds of dependency relations. We should support this format. We can use the [LassySmall](https://github.com/UniversalDependencies/UD_Dutch-LassySmall) corpus for testing.

This [CoNNL-U viewer](https://universaldependencies.org/conllu_viewer.html) is convenient for visualizing data in this format.

Eventually we'll look at adding support for TEI with dependency relations as well.

## How to index relations

Indexing relations will mean changes to the index format. We will implement these changes in the integrated index format only; the classic external index format will not support them. We will simply change the integrated index format, so older integrated indexes will no longer be readable. This is okay as long as the integrated index format is still experimental.

### Unification with spans

A good way to index relations is similar to how we index spans such as `<s/>`. Spans are indexed at the starting token, with the end position stored in the payload.

We could index these in the same annotation spans are already indexed in (historically called `starttag`, but we will rename it to `_relation`). We'd have to be careful to make sure both can easily be distinguished from one another.

Better yet, we should generalize the notion of an "inline tag" such as `<s/>` we have right now to be indexed as a relation as well, i.e.:

      X -starts_sentence_ending_at-> Y
    Small    man    bites   large   dog.

> **NOTE:** should there be a mechanism to exclude certain inline tag attributes (e.g. id) from being indexed in the term string? See below.

### Where to index relations

We could index relations at the source or target position X (with the term we index representing the type of relation, i.e. `object`) and store the position of the opposite position (target/source) Y in the payload. Which token we should choose to store a relation depends on which direction is more commonly queried, as having to decode the payload to find the "other end" of the relation takes slightly longer. We may even choose to index at both positions, meaning we would also need to store the direction of the relation. Though we should first test if any of this actually makes a difference in practice.

### Groups of words

To support relations between groups of words, we could of course add two length values to the payload as well. The first one would indicate the length of group X (the token the relation is indexed at) and the second one the length of group Y (the token indicated in the payload).

### Optimizations

We should use relative positions and variable-length integers to store information in the payload, as this could save significant amounts of disk space and potentially be faster (because more dense storage makes better use of the disk cache).

We should also consider only storing certain values if they differ from the common case (e.g. if we're not dealing with word groups, don't store two 1s for the length).

## How to implement relations search

Obviously, we'd need a query to find a relation, such as:

1. find all `object` relations
2. find `object` relations where the source lemma equals `bite`
3. find all relations where the source lemma equals `bite`
4. find `object` relations where the target lemma equals `man`

Of course, we also want to run more complex queries that combine several relations. There's two common ways to combine relations: two relations involving the same word, and two transitive relations. Examples:

- Find verbs where "man" is object and "dog" is subject. In other words, we're looking for a word that is the source for two specific relations. Visually:     `man <-o- X -s-> dog `
- Find subjects where its verb is "bite" and that verb has "man" as its object. In other words, we're looking for a word that is the target for a word that is itself the source for another relation. Visually: `man <-o- bite -s-> X`

In the first case, we can search for both relations, and find matches between them based on their source positions.

In the second case, we find matches for both relations, keeping matches from the second relation where its source exactly matches the target of the first relation, then finally find the targets of those filtered matches.

### Combining two relations into a larger span.

In certain cases we may want to combine two relations into a single larger span, while retaining the source or target we matched them on. After doing this, the source or target need not be the first and last word of the span, so these positions need to be kept track of separately.

A simple way to implement this operation is to return a combined span that has the source and target of the first relation. The source/target from the second relation that was not matched on is lost, but users could still capture those in a group if necessary.

For the low-level syntax, capturing would be up to the user, but for the specific dependency relations syntax, we could automatically capture relevant parts.

### Longer sources and targets

If we generalize the above to sources/targets that are groups of words, the operations become slightly more complicated, because source and target are both spans in their own right, but the principles remain the same.

### Looking for different words with the same relation

If we want to find e.g. two different adjectives applied to a word, we'll need a way to ensure that two matches are different. We could use "global capture constraints" for this. One issue is that CQL generally only allows these at the top-level of the query (hence the word _global_), but it seems that this is not a fundamental limitation. A query such as `(A:[] "que" B:[] :: A.lemma = B.lemma) [lemma="willen"]` should be valid, as long as the constraint only references groups captured within the parentheses.

These "local constraints" combined with the ability to check if two groups are (not) equal, e.g. something like `A@start > B@start` to ensure `A` occurs after `B`, should be enough to implement this.

### Building up a tree fragment while matching?

Ideally, we'd like to build a tree structure of relations that can be included with the match.

This would mean joining relations into a tree structure each time we combine two relations.  

Dependency relations are never cyclic, but because we want our relations support to be generic and future-proof, we should ensure cycles don't cause problems.

### Finding descendants

In addition to finding specific relations and combining them, maybe we want to find any "descendants" starting from a specific word. Getting this to perform well would probably be challenging though. 


## CQL syntax

### Dependency relations syntax

We'll provide a syntax specifically designed to easily query dependency relations (the likely most common use case for now).

For the more generic "building block" functions that these queries will rewrite to internally, see the next section below.

The dependency relations syntax could perhaps be toggleable, so it can be disabled if desired. However, if it does not interfere with other CQL queries, we can probably just enable it by default.

#### Dependency relation operator

The dependency relation operator is an n-ary operator to match one or more dependency relations between a parent and several children. It also allows you to exclude certain relations, i.e. ensure that these don't exist.

```
parent 
   -deprel1-> child1;
   -deprel2-> child2;
  !-deprel3-> child3; ...
```

`deprel` is interpreted as a regular expression. If you omit `deprel` from the operator (i.e. `-->`), it will match any relation type. A different way to write this is `-.*->`.

Note that the NOT version of the operator is `!-deprel->`; the `!` here is part of the
operator symbol just like with `!=`, not a separate operator. Adding a NOT clause means "there exists no relation of this type to this target".

The above operator expression is equivalent to the following functional query style:

```
rmatch(parent, 
   rel(deprel1, child1),
   rel(deprel2, child2),
  !rel(deprel3, child3), ...)
```

Similarly

```
parent -deprel1-> child -deprel2-> grandchild
```

will be equivalent to

```
rmatch(parent, 
    rel(deprel1, child, 
        rel(deprel2, grandchild)))
```
    
You can find root relations using this unary form of the operator:

```
^--> child
```

(you may specify a `deprel` in the operator here as well, although that will generally always be `root` for root relations)

The root relation operator will return the targets of these relations, as root relations have no source.

The source and target operands for the relation operators can be replaced by `_`, meaning "the default value" or "don't care". These will be replaced with `[]*` (any n-gram), which puts no restrictions on source or target.

Examples:

    # Find subjects that are nouns
    _ -nsubj-> [pos="NOUN"]

    # Find verbs with an object but no subject
    [pos="VERB"]    -obj-> _ ;
                 !-nsubj-> _

    # Match node with children of certain types)
    _  -nmod-> _ ;
        -det-> _ ;
     -advmod-> _

    # Find two different adjectives
    _ -amod-> _; -amod-> _

    # Match a series of descendants of certain types, starting with a root
    # (i.e. vertical paths in dependency trees)
    ^--> _ -nmod-> _ -case-> _

### Generic syntax for relations

We need syntax to incorporate relation searches into Corpus Query Language. For the same reasons as explained in [#396](https://github.com/instituutnederlandsetaal/BlackLab/issues/396), we'll use a simple function call style for now. This will correspond closely to how relations are indexed an will be useful
for any type of relations. We will provide specific syntax for relations types such as dependency relations.

**TODO:**

- exclude relations
- requirement that certain groups may not contain the same match (e.g. targets of two relations attached to the same source must be different, so you can e.g. find two different adjectives attached to a noun)

Below is a quick reference for these basic "building block" relations functions.

#### rel: find relations with certain properties

We can find a relation by type and target using:

    rel(reltype = ".*", target = []*, spanMode = 'source', captureAs = '', direction = 'both')

If `reltype` does not contain the substring `::`, it will be prefixed with the default relation class `dep::` (for dependency relations). So `.*` would match all dependency relations and `.*::.*` would match relations of all types. The default relation class could be made configurable, of course.

The default value for `target` matches all n-grams, although that query will obviously never actually be executed, we will just not filter on the child at all in that case.

Note that this returns the _source_ of the relation by default, as we've already matched on the target.

`spanMode` adjusts the resulting span and can take the values:
- `"source"` (span becomes the source of the relation)
- `"target"` (span becomes the target of the relation - this is the default value)
- `"full"` (span becomes the full span of the relation, including source and target)

By default `rel()` returns spans that match the _source_ of the relation. So e.g. `rel('nsubj')` will find words that have a subject, and `rel('nsubj', 'target')` will find the subjects themselves, and `rel('nsubj', 'full')` will find spans that include both the source and target.

If `captureAs` is set to a non-empty string, the relation will be captured as a group with that name. This is useful to avoid collisions if you're capturing the same relation type multiple times in a query. The same can be achieved in operator form with `_&nbsp;A:-det->&nbsp;_` to capture these relations as `A`.

`direction` can be set to `both` (default), `root` (only root relations), `forward` or `backward`.

Note that if try to find sources of root relations, no matches will be returned, as root relations don't have 
a source. OTOH, if you find targets of root relations first, then change the span mode to source, root relations will
not be thrown out but will return their targets. (**NOTE:** should this throw an error instead?)

**NOTE:** there is currently no way to filter _out_ root relations. Do we need this?

#### rspan: adjusting the span of a relation match

We can also change the spanMode of the spans returned by `rel()` according to what we need:

    rspan(relation_matches, spanMode = "full")

This will return the same relation matches, but with the span start and end set according to the value of `spanMode` (see above). The default is `full`, i.e. a span covering both the source and target of the relation.

**NOTE:** for `rspan`, there is a special extra spanMode `"all"`, that will return a span covering the sources and targets of _all_ relations matched.

### rmatch: match clauses (AND) and ensure unique relations

We can match a tree fragment of a tree (a parent and some of its children) using `rmatch`:

    rmatch(clause1, clause2, ! clause3, ...)

Often this will be used with the target of the parent relation as `clause1` and the source of child relations as `clause2`, `clause3`, etc. (some of them negated to indicate such children must not be present).

This operation is the same as a regular AND (NOT) operation with these clauses, but with the added requirement that a given relation may not be matched by more than one clause. (without the `rmatch` operation, you might need many capture constraints to enforce this).

#### rcapture: capture all relations occurring inside a span

We can capture all relations occurring inside a span using `rcapture`:

    rcapture(query, captureAs, relationType = '.*')

This is useful for e.g. finding all dependency relations in a sentence.

Parameters:
- `query` is the query we're operating on. `rcapture` does not affect the matches, other than adding a match info with the relations captured.
- `captureAs` is the name of the match info to store the captured relations in.
- `relationType` is a regular expression matching the relation types to capture. The default is `.*`, i.e. all relations of the default relation class (`dep` by default). See `rel()` for more details about matching relation types.

The relations are captured as a list-of-relations match info type.

#### rtype

Construct a relation type string that may include attributes

    rtype(class, type, attributes = {})

(not yet implemented)

#### Finding relations

Below are examples of how to use the `rel` function to find relations between words. Note `_` indicates the default value for a parameter. Parameters at the end may be omitted; their default values will also be used.

Find all `object` relations (spans will contain the source of the relation, i.e. the word that has an object):

    rel('dep::object')
    rel('object')       # "dep::" is automatically prepended
    rel('object', _, 'source')

All `object` relations (spans will contain the targets, i.e. the objects):

    rel('object', _, 'target')

All `object` relations where the target is a noun:

    rel('object', [pos = 'NOUN'], 'target')

Relations of type `object` where the source word is `bites`:

    'bites' & rel('object')

All relations where the source word equals `bites`:

    'bites' & rel()

Relations of type `object` where the target word equals `man`:

    'man' & rel('dep::object', _, 'target')

Find root relations:

    rel('root')

Find all dependency relations:

    rel('dep::.*')
    rel('.*')

Find all relations, not just dependency relations:

    rel('.*::.*')

Find sentences:

    <s/>
    rel(rtype('__tag', 's'), _, 'full')
    rel('__tag::s'), _, 'full')

Find sentences with happy sentiment:

    <s sentiment='happy' confidence='10' />
    rel(rtype('__tag', 's', list('sentiment', 'happy', 'confidence', '10')), _, 'full')
    rel('__tag::s\u0003\u0001confidence\u000210\u0003\u0001sentiment\u0002happy\u0003'), _, 'full')

#### Extract source or target

Find words that have 'man' as their object ("find relation source where target is 'man' and type is 'object'"):

    rel('object', 'man')

Find words that are the object for 'bites' ("find relation target where source is `bites` and type is `object`"):

    rspan('bites' & rel('object'), 'target')


#### Combining relation matches

We can combine relation matches using the standard CQL operators.

Some examples follow.

Find words that have both a subject and an object (i.e. relations have same source):

    rel('object') & rel('subject')
    rmatch(_, rel('object'), rel('subject'))

Find words that have `man` as their object and `dog` as their subject (i.e. find X in `'man' <-O- X -S-> 'dog'`):

    rel('object', 'man') & rel('subject', 'dog')
    rmatch(_, rel('object', 'man') & rel('subject', 'dog'))

Find words that are the subject of a word that has 'man' as its object (i.e. find X in `'man' <-O- ? -S-> X`):

    rspan(rel('subject') & rel('object', 'man'), 'target')

Note in the above that when combining two relations matches with `&`, a new relations match is created that stores the information for both relations. The third `rspan` parameter can be used to select the relation, but the default is the first of the two relations combined.


#### Examples Lassy Small

Find `case` and `nmod` relations with same source (returns that source):

    rel('case') & rel('nmod')
    rmatch(_, rel('case'), rel('nmod'))

Same, but return target for `case`:

    rspan(rel('case') & rel('nmod'), 'target')

Same, but return the full span for `case`:

    rspan(rel('case') & rel('nmod'), 'full')

Same, but return the full span covering both relations:

    rspan(rel('case') & rel('nmod'), 'all')

Match target of one relation to source of another (return span covering all matched relations):

    rel('nmod', rel('acl:relcl'), 'all')


**TODO:** rel() doesn't support spanMode 'all' yet, but should (matchTarget can have matched other relations)


#### Capturing parts

**TODO:** see if we can unify capture groups and relations (WIP)

Just like in other CQL queries, we can tag parts with a group name to capture them.

    V:(
        rspan(S:relt('subject') & 
        rspan(O:relt('object'), 'source') & 
        [pos='VERB']
    )

This would return spans including a verb and its subject and object, with the verb tagged as `V`, the subject as `S` and the object as `O`.


## Changes to regular inline tag indexing

### Encoding regular spans as relations

As mentioned earlier, we want to unify the spans we already supported in BlackLab (i.e. XML tags like `<s/>`) with the new relations. This means we need a way to encode regular spans plus their attributes as relations.

#### Single term

We will encode the tag name and all attribute values into a single indexed term, so we only need to store the payload once and cannot accidentally mix up attributes with those of other spans/relations (as is a problem in the classic external index format).

A potential downside of is that this could greatly increase the number of unique terms in the index, if there's multiple attributes and many different combinations of attribute values. This would consume more disk space and could slow down queries. While this doesn't seem to be a huge problem for most typical datasets, perhaps we could offer the option to exclude certain attributes from indexing.

Similarly, such attributes with many different values will slow down searching, even if we don't filter on any of the attributes. For this (common) case, we should index tags/relations with attributes twice: once with attributes and once without. We should take care to be able to distinguish between the two, e.g. using a prefix that indicates whether or not attributes are included. This also works for tags that have no attributes, as those can only ever be found by queries that don't filter on attributes.

#### Example encoding

For example, to encode a tag `<s sentiment="happy" confidence="10" />` into a single term we can index these two terms in the `_relation` attribute:

    __tag::s\u0003
    __tag::s\u0003\u0001confidence\u000210\u0003\u0001sentiment\u0002happy\u0003

For a tag where no attributes are indexed, e.g. `<b>blabla</b>`, the only term we index would be:

    __tag::b\u0003

So the "full relation type" in the first example is `__tag::s` (consisting of relation class `__tag` and relation type `s`), from which the tag name `s` can be decoded. We keep the tag name as part of the relation type so it's always at the start of the term, allowing us to use a fast prefix query. The full relation type is always followed by `\u0003` ("value end"). Note that the relation type does not need a prefix character because it is always the first part of the term.

If attributes are included in this term, they follow now, in alphabetical order. The alphabetical order is so we can construct an efficient regex to find multiple of them. Each attribute name is prefixed with `\u0001` ("name prefix"), and each attribute value prefixed with `\u0002` ("value prefix"). The term always ends with `\u0003` ("value end").

#### Source and target

Inline tags don't really have a source and target word, and using the first word of the sentence and the last word (or the first word of the next sentence) creates awkward situations when trying to determine the span start and end from the source and target.

Instead, we'll opt to store a 0-length source and target for the tag. This source will start and end at the first word of the tag, and the target will start and end at the first word after the tag. This way the calculations line up.

### rtype function

Because we have XML-style syntax for spans, we likely won't need it, but just in case, a utility function  `rtype` could be provided, so that:

    rtype('__tag', 's', list('sentiment', 'happy', 'confidence', '10'))

would return the regex:

    __tag::s\u0003.*\u0001confidence\u000210\u0003.*\u0001sentiment\u0002happy\u0003.*

Similarly, `rtype('__tag', 's')` would return the regex:

    __tag::s.*\u0003


### Better keep track of spans hierarchy?

> **NOTE:** this is beyond the scope of this task, but it's good to keep it in the back of our mind

Span nesting has never been completely accurately recorded in the index, because only the start and end tokens are stored, not the nesting level. If two spans have the same start and end, we can't tell which one is the parent of the other.

We could include the nesting hierarchy in the indexed term, e.g. `<b><i>dog</i></b>` would be indexed as `b_i`. In addition to giving us the correct nesting, it would also help to speed up queries like `("dog" within <i/>) within <b/>`, which would only have to search for `"dog" within <b_i />`

We also can't efficiently find an inline tag's ancestors or descendants; `within` and `contains` are relatively expensive, especially in large documents. We could (for certain relations?) store a unique relation id in the payload and have a separate index file where we can look up parent/child relations, so we can quickly walk the tree. This would help if we want to support complex XPath-like search queries.


## Specification of indexing relations

This is a complete specification of how relations will be indexed in BlackLab. This includes the new way of indexing spans as relations as well.

Relations (and spans) are indexed in the `_relation` annotation. A relation is encoded as a single term with a payload.

We have to choose where to index relations:

1. always at the source, or always at the target
2. always at the first position in the document (whether that's the source or target)

We'll start with the second option, which has the advantage that the associated spans don't need to be sorted to be in order of increasing start position. It is also the way inline tags are indexed right now, so no existing invariants and optimizations are messed up. This does mean that we will have to record whether the relation was indexed at the source or target.

### Term

The term indexed is a string of one of these forms:

    relClass::relType\u0001
    relClass::relType\u0001\u0003attr1\u0002value1\u0001\u0003attr2\u0002value2\u0001...

We call `relClass::relType` the _full relation type_. It consists of the relation class and the relation type. The relation class distinguishes between different types of relations, e.g. `__tag` for inline tags, `dep` for dependency relations, etc. The relation type is used to distinguish between different relations of the same class, e.g. `dep::subject` for subject relations, `dep::object` for object relations, `dep::nsubj` for nominal subject relations, etc.

There is always a `\u0003` ("value end") after the full relation type (such a closing character is useful to avoid unwanted prefix matches when using regexes).

If there are any attributes, they follow next. These are sorted alphabetically by name. An attribute is encoded as follows: a `\u0001`, the attribute name, a `\u0002`, the attribute value, and a `\u0003`. The surrounding special characters ensure we can match any part of the attribute's name or value without unwanted matches.

### Payload

The payload uses Lucene's `VInt` (for non-negative numbers) and `ZInt` (an implementation of [variable-length quantity (VLQ)](https://en.wikipedia.org/wiki/Variable-length_quantity)). We store a relative position for the target end to save space.

The payload for a relation consists of the following fields:

* This number is either `relTargetStart` or `relationId`, depending on the `flags` byte (see below). `relationId` is a unique id for this relation, which can be used to look up extra information, such as attributes, and maybe other information in the future. For `relTargetStart`, see below. Default value: `1`.
* `flags: byte`: If `0x02` is set, the relation only has a target (root relation). If `0x04` is set, use a default length of 1 for `sourceLength` and `targetLength`. If `0x08` is set, the first number in the payload is the `relationId`. The other bits are reserved for future use and must not be set. Default: `0`.
* If flag `0x08` was set, `flags` is followed by `relTargetStart: ZInt`: relative position of the (start of the) target end. Default: `1`. If flag `0x08` was not set, this number will not be written here (in that case, the first number of the payload is `relTargetStart`, see above).
* `sourceLength: VInt`: length of the source end of the relation. For a single word this would be 1; for a span of words, greater than one. For inline tags, it will be set to 0 (start and end tags are considered to be zero-length). Default: `0` (normally) or `1` (if flag `0x04` is set)
* `targetLength: VInt`: length of the target end of the relation. For a single word this would be 1; for a span of words, greater than one. For inline tags, this will be set to 0 (start and end tags are considered to be zero-length). Default: `0` (normally) or `1` (if flag `0x04` is set)

Fields may be omitted from the end if they have the default value. Therefore, an empty payload means `{ relTargetStart: 1, flags: 0, sourceLength: 0, targetLength: 0 }`.

As another example, the payload `0x81; 0x04` would mean `{ relTargetStart: 1, flags: 4, sourceLength: 1, targetLength: 1 }`. Explanation: `0x81` is the `VInt` encoding for `1` (the lower seven bits giving the number and the high bit set because this is the last byte of the number). The flag `0x04` is set, so the lengths default to `1` instead of `0`.


#### How to ensure we can look up attributes and other relation info (NOT YET IMPLEMENTED)

_(NOTE: this feature is planned but not yet implemented)_

Relations with attributes are indexed twice: once with the attributes as part of the term (so we can find instances with specific attribute values efficiently) and once without the attributes (so queries that don't filter on attributes are efficient even if there's a unique id attribute).

This causes problems when we want to group by an attribute value of a tag we matched, because we don't always have those available, depending on the query.

There could also be other things we want to look up about a relation in the future, like its ties to other relations: in the case of inline tags: parent, children, ancestors, descendants, etc. In the case of dependency relations: perhaps information about transitive connections to other relations, etc.

We want to enable this by adding a unique relation id to the payload that allows us to look up additional information in a separate file.

How this changes the relation encoding:
- we add a flag `0x10`; if set, a unique `relationId` is stored in the payload. It would be stored after `flags`. This ensures BlackLab remains compatible with older corpora; however, corpora encoded this way are not compatible with older versions of BlackLab (as they would not be able to correctly decode the payload).
- `relationId` is a unique number (for this annotated field), assigned when the relation is parsed and added, that can be used to look up information about the relation, such as its attributes, in separate index files.
- The index files with relation info is created while writing the indexed terms _with_ attributes to the segment (because that term contains all the information we need to create it, whereas the optimization terms obviously do not). Both versions of the term are added with the same `relationId`, of course. 

### Calculate Lucene span from relation term

When found using BlackLab, a relation has a source and target (which may be word groups or single words) as well as a "regular" span.

The span normally runs between source and target, but operations combining relations may enlarge the span so this no longer holds.

To calculate the Lucene span from a matched relation term's payload, first a few helper values:

    thisStart = position the relation was indexed at
    thisEnd = thisStart + thisLength
    otherStart = thisStart + relOtherStart
    otherEnd = otherStart + otherLength

So the Lucene span for a relation is:

    [thisStart, max(thisEnd, otherEnd) )

(although it seems unlikely that we want the source and target to overlap)

(note that Lucene spans are half-open, i.e. the end position is not included in the span)
