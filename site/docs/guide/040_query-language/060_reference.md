# BCQL reference

## Query structure

A basic query consists of a sequence of tokens. There is no sequence operator; tokens are simply written one after the other.

Sequences of tokens can be grouped using parentheses `( )` or combined using the union operator `|` or intersection operator `&`.

Tokens or parenthesized groups can be repeated using the repetition postfix operators `*`, `+`, `?`, `{n}`, and `{n,m}`. They can be captured using the capture operator `:`, e.g.

```
A:("very" | "extremely") "good"
```

Capture constraints can be applied to the whole query or a parenthesized part using the `::` operator:

```
QUERY :: CAPTURE_CONSTRAINT
```

## Token structure

Tokens are defined using token brackets `[ ]`. Inside the brackets, you can specify constraints on the token's annotations, or use tag attribute expressions to match tags with specific attributes.

If you only need a constraint 

## Capture constraint structure
