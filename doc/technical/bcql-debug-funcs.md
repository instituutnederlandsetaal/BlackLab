# BCQL debug functions

These functions can be useful when debugging BCQL queries. They allow you to create queries that might not be easily expressible in regular BCQL.

NOTE: BCQL functions currently only support query and string arguments. Wherever e.g. a number or boolean is expected, you must enclose it in quotes.

# `_adjust`

    _adjust(query, startAdjust, endAdjust)

will adjust the start and end of the hits from `query` by `startAdjust` and `endAdjust` tokens respectively. Negative numbers are allowed.

Example:

    _adjust('word' 'for' 'word', '1', '-1')

will adjust the hits from the query to only match the word _for_ between the two occurrences of _word_.

# `_edge`

    _edge(query, whichEdge)

will only return either the starts or ends of hits as 0-length hits.

For the trailing edge (ends of hits), `whichEdge` should be `trailing` or `t`. For the leading edge (starts of hits) it should be `leading` or `l`.

Example: if `query` matches a hit with start 3 and end 5, `_edge(query, 'trailing')` will return a hit with start and end both set to 5.

# `_fixed`

    _fixed(start, end)

# `_fimatch`

    _fimatch(query1, query2, fiClause)

will resolve a sequence query (`query1` followed by `query2`) using forward index matching. Set `fiClause` to `'0'` to match the first clause using the forward index (the default). Set it to `'1'` for the second clause.

Example:

    _fimatch('clear', 'water', '1')

will use the reverse index to find the word _clear_,
then use the forward index to verify that it is followed by the word _water_.

# `_ident`

    _ident(query)

will return the query unchanged.

# `_indoc`

    _indoc(query, docId)

will only return hits from `query` that occur in the document with id `docId`.

(note that this is Lucene's internal docId, not your corpus' persistent identifier field)

Example:

    _indoc('water', '0')

Find _water_ in the document with docId 0.

# `_lenfilter`

    _lenfilter(query, minLength, maxLength)

will filter `query` hits by length. `minLength` and `maxLength` are inclusive.

Example:

    _lenfilter(<s/>, '1', '3')

will find sentences with lengths between 1 and 3 tokens.
# `_posfilter`

    _posfilter(producer, filter, operation, invert)

will filter `producer` hits using the `filter` query. `operation` may be `matches`, `contains`, `within`, `starts_at`, `ends_at`, `containing_at_start`, or `containing_at_end`. If `invert` is set to `true`, the filter will be inverted.

Example:

    _posfilter([lemma='water'], [pos='NOU'], 'matches', 'false')

will return hits `[lemma='water]` that DON'T match a hit from `[pos='NOU']`.

