# BlackLab query optimization

This is an overview of how BlackLab rewrites queries to speed them up. The goal of this overview is to rethink how we rewrite queries, to apply optimizations more consistently and make it easier to add new optimizations.

Things to consider:
- some optimizations recognize trivial cases such as a sequence of one; others recognize specific optimizable cases such as two identical clauses next to each other; others yet focus on creating longer sequences or larger AND operations, because those can often be executed more efficiently than multiple smaller ones (i.e. because one clause has fewer matches, allowing us to quickly skip through the other clauses with more matches).
- some optimizations only work if you add a startAdjust/endAdjust to a clause (e.g. lift a capture past a sequence of fixed length, where only part of the sequence is captured, so something like `A cap:B C -> adjusted-cap(A B C)` (where `adjusted-cap` makes sure we only actually capture `B`). This is also done with position filter, for example. 
- some optimizations should be done before others to get the most benefit, although it is not always clear which order is best. An example is flattening sequences, which should be done before trying to combine adjacent clauses for maximum benefit.
- certain optimizations create new opportunities for other optimizations, so we need to keep rewriting until we can't optimize any further

## Where is it done?

Query rewriting is done in several places at the moment. Ideally it would be done in fewer places.

- `TextPattern.translate()` (just a few trivial things, can probably be removed)
- `SpanQuerySequence.optimize()` (should be able to eliminate this as `rewrite()` does everything as well?)
- `BLSpanQuery.rewrite()` (bulk of the optimizations) including `ClauseCombiner` (called from `SpanQuerySequence.rewrite()`)
- `BLSpanQuery.getSpans()` (decides which Spans classes to use, although not really a rewrite, more a decision how to efficiently perform an operation)


## Rules

(Reason "simplify" makes queries more readable, but also easier to further optimize)

| Location                     | Name                    | Rule                                                                            | Reason                | Change?            |
|------------------------------|-------------------------|---------------------------------------------------------------------------------|-----------------------|--------------------|
| TP Regex/Wildcard            | SimplestMatcher         | `REGEX(WORD) -> TERM(WORD)` (or prefix/wildcard, if possible)                   | faster to resolve     | also done in query |
| TP Repetition/Sequence       | TrivialRep/Seq          | `CLAUSE{1,1} -> CLAUSE` / `SEQ(CLAUSE) -> CLAUSE`                               | simplify              | also done in query |
| Q Sequence optimize,rewrite  | Flatten                 | `SEQ(A, SEQ(B, C)) -> SEQ(A, B, C)`                                             | longer seq (fast/opt) |                    |
| Q Sequence optimize,rewrite  | MatchingTags            | `SEQ(<s>, A, B, </s>) -> WITHIN(SEQ(A, B), <s/>)`                               | faster to resolve     |                    |
| Q Sequence optimize,rewrite  | CCRepetition            | `A A -> A{2}` / `A{a,b} A{c,d} -> A{a+c,b+d}`                                   | faster to resolve     |                    |
| Q Sequence optimize,rewrite  | CCInternalizeNeighbour  | (clause gobbles up one of its neighbours if possible)                           | longer seq (fast/opt) |                    |
| Q Sequence optimize,rewrite  | CCInt. Capture          | `A cap:B -> cap(adjusted):(A B)` (if `A` fixed-length)                          | longer seq (fast/opt) |                    |
| Q Sequence optimize,rewrite  | CCInt. Expansion1       | `A EXP(B, RIGHT, ...) -> EXP(A B, RIGHT, ...)`                                  | longer seq (fast/opt) |                    |
| Q Sequence optimize,rewrite  | CCInt. Expansion2       | `[]{a,b} EXP(A, LEFT, c, d) -> EXP(A, LEFT, a+c, b+d)`                          | faster to resolve     |                    |
| Q Sequence optimize,rewrite  | CCInt. PosFilter        | `A POSFILTER(B, ...) -> POSFILTER(A B, ...)` (if A fixed-length; edge adjusted) | longer seq (fast/opt) |                    |
| Q Sequence optimize,rewrite  | CCAnyExpansion          | `[]{m,n} A --> EXPAND(A, LEFT, m, n)` (or to right of course)                   | faster to resolve     |                    |
| Q Sequence optimize,rewrite  | CCAnyCombine            | `[]{a,b} []{c,d} --> []{a+c,b+d}`                                               | simplify              |                    |
| Q Sequence optimize,rewrite  | CCNot (Containing)      | `!A B -> NOTCONTAINING(EXPAND(B, LEFT, 1), A, ...)` (A 1 token, B constant len) | faster to resolve     |                    |
| Q Sequence optimize,rewrite  | CCNFA / CCForwardIndex  | `A B -> FISEQ(A, B)` (one is anchor, other is resolved with FI)                 | faster to resolve     |                    |
| Q Sequence rewrite           | EmptyClauseAlts         | (after other operations) generate OR-alternatives for clauses matching empty    | correctness           |                    |
| Q Repetition rewrite         | TrivialRep              | `A{1,1} -> A`                                                                   | simplify / faster     |                    |
| Q Repetition rewrite         | NestedAnyToken          | `([]{1,1}){m,n} -> []{m,n}` / `([]{m,m}){n,n} -> []{m*n,m*n}`                   | simplify / faster     |                    |
| Q Repetition rewrite         | NotTokenToNotContaining | `(!A){m,n} -> NOTCONTAINING([]{m,n}, A))`                                       | faster to resolve     |                    |
| Q Repetition rewrite         | NestedRepetition1       | `(A{0,}){0,} -> A{0,}`                                                          | simplify              |                    |
| Q Repetition rewrite         | NestedRepetition2       | `(A{0,1}){0,1} -> A{0,1}` / `(A{a,a}){b,b} -> A{a*b,a*b}`                       | simplify              |                    |
| Q AndNot rewrite             | LiftCaptures            | `A & cap:B -> cap:(A & B)`                                                      | simplify / enable opt |                    |
| Q AndNot rewrite             | Flatten                 | `AND(A, AND(B, !C) -> AND(A, B, !C)`                                            | larger AND (fast/opt) |                    |
| Q AndNot rewrite             | InvertNegativeOnly      | `AND(A, !(!B)) -> AND(A, B)`                                                    | faster to resolve     |                    |
| Q AndNot rewrite             | AllNegativeToOr         | `AND(!A, !B) -> !OR(A, B)`                                                      | simplify / enable opt |                    |
| Q AndNot rewrite             | RedundantNGramsClauses  | `AND(A, B, []*) -> AND(A, B)`                                                   | faster to resolve     |                    |
| Q AndNot rewrite             | NGramsToLengthFilter    | `AND(A, B, []{m,n}) -> LENFILTER(AND(A, B), m, n)`                              | faster to resolve     |                    |
| Q AndNot rewrite             | OnlyNGrams              | `AND([]{a,b}, []{c,d}) -> []{max(a,c),min(b,d)}`                                | simplify/ faster      |                    |
| Q AndNot rewrite             | TrivialAnd              | `AND(A) -> A`                                                                   | simplify              |                    |
| Q AndNot rewrite             | NotClausesToNotMatches  | `AND(A, B, !C) -> NOTMATCHES(AND(A, B), C))`                                    | faster to resolve     |                    |
| Q Not rewrite                | InvertInvertableClause  | `!!A -> A` (actually any clause that's ok to invert)                            | simplify / faster     |                    |
| Q PositionFilter rewrite     | FilterNGrams            | `POSFILTER([]{m,n}, A) -> NGRAMFILTER(m, n, A)`                                 | faster to resolve     |                    |
| Q RelationSpanAdjust rewrite | NestedRelations         | `rspan(rel(...)) -> rel(...)` (pointless to immediately change span mode)       | simplify              |                    |
| Q RelationSpanAdjust rewrite | NestedSpanAdjust        | `rspan(rspan(...)) -> rspan()` (pointless to immediately change span mode)      | simplify              |                    |

(`SpansFilterByHitLength` could perhaps be lifted up similar to how we lift captures with AND and SEQ; probably not a huge gain though. Other "filter" clauses are not liftable for various reasons, such as changing start/end positions, being dependent on match info of their clause, or providing guarantees such as uniqueness)
