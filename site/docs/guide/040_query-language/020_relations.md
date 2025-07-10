# Relations querying

::: tip Supported from v4.0
Indexing and searching relations is supported from BlackLab 4.0.
:::

Relations show how (groups of) words are related to one another. One of the most common types of relations is the dependency relation, which shows grammatical dependency between words.

If your corpus contains relations, you can query those as well. One advantage of this style of querying is that it's much easier to find nonadjacent words related to one another, or two related words regardless of what order they occur in.

See [Indexing relations](/guide/index-your-data/relations.md).

Querying relations is essentially done by building a partial tree of relations constraints. BlackLab will try and find this structure in the corpus. Relations queries can be combined with "regular" token-level queries as well.

::: tip Treebank systems

BlackLab supports limited relations querying, but is not as powerful as a full
treebank system, which is primarily designed for this style of search. Links to some treebank systems can be found [here](https://github.com/instituutnederlandsetaal/BlackLab/blob/dev/doc/technical/design/design-relations-queries.md#research-into-treebank-systems). For BlackLab's relations querying limitations, see [below](#limitation-descendant-search).

:::

## An example dependency tree

Let's use an example to illustrate the various querying options. Here's a simple dependency tree for the phrase _I have a fluffy cat_:

```
      |
     have
    /    \
 (subj)   (obj)
 /          \
I            cat
           /   |
        (det)(amod)
        /      |
       a     fluffy 
```

## Finding specific relation types

We might want to find object relations in our data. We can do this as follows:

```
_ -obj-> _
```

This will find _have a fluffy cat_ (the span of text covering the two ends of the relation), with a match info group named for the relation type (_obj_) containing the relation details between _have_ and _cat_.

The two `_` marks in the query simply means we only care about the relation type, not the source or target of the relation. If we specifically want to find instances where _cat_ is the object of the sentence, we can use this instead:

```
_ -obj-> "cat"
```

So you can see that the token-based queries described previously are still useful here.

::: details Can I use [] instead of _ ?

As explained above, `_` in a relation expression means "any source or target". You might be tempted to use `[]` instead, especially if you know your relations always have a single-token source and target. This works just fine, but it's a bit slower (it has to double-check that source and target are actually of length 1), so we recommend sticking with `_`.

(the actual equivalent of `_` here is `[]*` (zero or more tokens with no restrictions), but that makes for less readable queries)

:::

## A note on terminology

For dependency relations, linguists call the left side the _head_ of the relation and the right side the _dependent_. However, because dependency relations aren't the only class of relation, and because the term _head_ can be a bit confusing (there's also the "head of an arrow", but that's the other end!), we will use _source_ and _target_.

When talking about tree structures, we will also use _parent_ and _child_.

| Context                   | Terms                |
|---------------------------|----------------------|
| Dependency relations      | `head --> dependent` |
| Relations in BlackLab     | `source --> target`  |
| Searching tree structures | `parent --> child`   |

## Finding relation types using regular expressions

We can specify the relation type as a regular expression as well. To find both subject and object relations, we could use:

```
_ -subj|obj-> _
```

or:

```
_ -.*bj-> _
```

If you find it clearer, you can use parentheses around the regular expression:

```
_ -(subj|obj)-> _
```

With our example tree, the above queries will find all subject relations and all object relations. Each hit will have one relation in the match info. To find multiple relations per hit, read on.

::: details Relation classes

When indexing relations in BlackLab, you assign them a _class_, a short string indicating what family of relations it belongs to. For example, you could assign the class string `dep` to dependency relations. An `obj` relation would become `dep::obj`.

To simplify things, `rel` is the default relation class in BlackLab. If you index relations without a class, they will automatically get this class. Similarly, when searching, if you don't specify a class, `rel::` will be prepended to the relation type. So if you're not indexing different classes of relations, you can just ignore the classes.

:::

## Root relations

A dependency tree has a single root relation. A root relation is special relation that only has a target and no source. Its relation type is usually just called _root_. In our example, the root points to the word _have_.

You can find root relations with a special unary operator:

```
^--> _
```

This will find all root relations. The details for the root relation will be returned in the match info.

Of course you can place constraints on the target of the root relation as well:

```
^--> "have"
```

This will only find root relations pointing to the word _have_.

## Finding two relations with the same source

What if we want to find the subject and object relations of a sentence, both linked to the same source (the verb in the sentence)? We can do that using a semicolon to separate the two _target constraints_ (or _child constraints_):

```
_ -subj-> _ ;
  -obj-> _
```

As you can see, the source or parent is specified only once at the beginning. Then you may specify one or more target constraints (a relation type plus target, e.g. `-subj-> _`), separated by semicolons.

The above query will find hits covering the words involved in both relations, with details for the two relations in the match info of each hit. In our example, it would find the entire sentence _I have a fluffy cat_.

::: details Target constraint uniqueness

Note that when matching multiple relations with the same source this way, BlackLab will enforce that they are unique. That is, two target constraints will only match two different relations.

:::

## Negative child constraints

You may want to have negative constraints, such as making sure that _dog_ is not the object of the sentence. This can be done by prefixing the relation operator with `!`:

```
_  -subj-> _ ;
  !-obj-> "dog"
```

Note that this is different from :

```
_  -subj-> _ ;
   -obj-> [word != "dog"]
```

The second query requires an object relation where the target is a word other than _dog_; that is, the object relation must exist. By contrast, in the first case, we only require that there exists no object relation with the target _dog_, so this might match sentences without an object as well as sentences with an object that is not _dog_.

## Searching over multiple levels in the tree

What if we want to query over multiple levels of the tree? For example, we want to find sentences where the target of the `subj` relation is the source of an `amod` relation pointing to _fluffy_, such as in our example tree.

```
_ -subj-> _ -amod-> "fluffy"
```

We can combine the techniques as well, for example if we also want to find the object of the sentence like before:

```
_ -subj-> (_ -amod-> _) ;
  -obj-> _
```

As you can see, the value of the expression `(_ -amod-> _)` is actually the _source_ of the `amod` relation, so we can easily use it as the target of the `subj` relation.

The `-..->` operator is right-associative (as you can see from the first example), but we do need parentheses here, or the parent of the `-obj->` relation would be ambiguous.

## Limitation: descendant search

One current limitation compared to dedicated treebank systems is the lack
of support for finding descendants that are not direct children.

For example, if we want to look for sentences with the verb _have_ and the word _fluffy_ somewhere as an adjectival modifier in that sentence, we can't query something like this:

```
^--> "have" -->> -amod-> "fluffy"   # DOES NOT WORK
```

Instead, we have to know how many nodes are between _have_ and _fluffy_, e.g. this does work:

```
^--> "have" --> _ -amod-> "fluffy"
```

Supporting arbitrary descendant search with decent performance is a challenge that we may try to tackle in the future.

For now, you might be able to work around this limitation using a hybrid between token-based and relations querying, e.g.:

```
(<s/> containing (^--> "have")) containing (_ -amod-> "fluffy")
```

## Advanced relations querying features

Most users won't need this, but they might come in handy in some cases.

### Controlling the resulting span

As shown in the previous section, relation expressions return the source of the matching relation by default. But what if you want a different part of the relation?

For example, if we want to find targets of the _amod_ relation, we can do this:

```
rspan(_ -amod-> _, "target")
```

If we want the entire span covering both the source and the target (and anything in between):

```
rspan(_ -amod-> _, "full")
```

Note that _full_ is the default value for the second parameter, so this will work too:

```
rspan(_ -amod-> _)
```

`rspan` supports another option: _all_ will return a span covering all of the relations matched by your query.

```
rspan(_ -subj-> (_ -amod-> _) ; -obj-> _, "all")
```

Because this is pretty useful when searching relations, there's an easy way to apply this `rspan` operation: just add a parameter `adjusthits=true` to your BlackLab Server URL. Note that if your query already starts with a call to `rspan`, `adjusthits=true` won't do anything.

### Capturing all relations in a sentence

If you want to capture all relations in the sentence containing your match, use:

```
"elephant" within rcapture(<s/>)
```

What actually happens here is that all relations in the matched clause are returned in the match info.

You can pass a second parameter with the match info name for the list of captured relations (defaults to _captured_rels_):

```
"elephant" within rcapture(<s/>, "relations")
```

If you only want to capture certain relations, you specify a third parameter that is a regular expression filter on the relation type. For example, to only capture relations in the `fam` class, use:

```
"elephant" within rcapture(<s/>, "relations", "fam::.*")
```

### Cross-field relations

It is possible to have a corpus with multiple annotated fields, with relations that point from a position or span in one field to another. Annotated fields should be named e.g. `contents__original` and `contents__corrected` for this to work, and a relation from the first to the second field could be found like this:

```
"mistpyed" -->corrected "mistyped"
```

As you can see, the target version is appended to the relation operator. The source version is determined by the main annotated field searched (the `field` parameter for BlackLab Server; will default to the main annotated field, which is the first one you defined in your indexing configuration).

Cross-field relations are used to enable parallel corpora, which we'll discuss [next](parallel.md).
