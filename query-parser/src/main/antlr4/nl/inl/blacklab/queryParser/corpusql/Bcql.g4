/*

BlackLab Corpus Query Language (BCQL) parser definition.

ANTLR 4 is used to generate Java code from this parser definition.

*/

grammar Bcql;

options { caseInsensitive = true; }

// Lexer Rules
//----------------------------

// Skip whitespace
WHITESPACE: [ \t\r\n]+ -> skip ;

// Comments are 'special tokens': they are not reported, but are available
// for use by the next reported token
SINGLE_LINE_COMMENT: '#' ~[\r\n]* -> channel(HIDDEN);
MULTI_LINE_COMMENT: '/*' .*? '*/' -> channel(HIDDEN);

// Reserved words
WITHIN          : 'within';
CONTAINING      : 'containing';
OVERLAP         : 'overlap';
IN              : 'in';
TRUE            : 'true';
FALSE           : 'false';
DEFAULT_VALUE   : '_';

// Relation and alignment operators
// valid are e.g. --> -root-> -.*-> -a-b-> (and same preceded by ^ or !)

// disambiguated for query editor autocompletion purposes
fragment DEP_NAME : (~[\- '"\]] ((~[\- '"\]] | '-' ~[>]))*);
fragment ALIGNMENT_NAME : (~[= '"\]] ((~[= '"\]] | '=' ~[>]))*);
fragment DEP_TARGET: ([a-z_\-0-9])*;
fragment ALIGNMENT_TARGET: DEP_TARGET;

ROOT_DEP_OP : '^-' DEP_NAME? '->' DEP_TARGET;
DEP_OP : '!'? '-' DEP_NAME? '->' DEP_TARGET;
ALIGNMENT_OP : '=' ALIGNMENT_NAME? '=>' ALIGNMENT_TARGET '?'?;

LOOKAHEAD_OP: '?' ('<')? ('=' | '!');

QUOTED_STRING   : 'l'? '"' (~["\\] | '\\' . )* '"';
SINGLE_QUOTED_STRING: 'l'? '\'' (~['\\] | '\\' . )* '\'';

NAME            : [a-z_] [a-z_\-0-9]*;
INTEGER         : '-'? [0-9]+;
SETTINGS_OP     : '@' NAME '=' NAME (',' NAME '=' NAME)*;

// we need to match anything we want to use in syntax highlighting
// and we also can't group them together, because then we can't distinguish them in the parser
// string literals in the parser (e.g. '=') work as long as they exactly match the token
// so there's no need to replace all of them with the token name.
LBRACKET        : '[';
RBRACKET        : ']';
EQUALS          : '=';
NOT_EQUALS      : '!=';
GREATER_THAN    : '>';
LESS_THAN       : '<';
SLASH           : '/';
GREATER_THAN_OR_EQUAL: '>=';
LESS_THAN_OR_EQUAL: '<=';
AND             : '&';
OR              : '|';
NOT             : '!';
COLON           : ':';
STAR            : '*';
PLUS            : '+';
MINUS           : '-';
QUESTION        : '?';
COMMA           : ',';
SEMICOLON       : ';';


// Grammar rules
//----------------------------

// A BCQL query
query: settingsQuery EOF;

// Optional settings operator(s)
// (currently undocumented feature to set e.g. default relation class)
settingsQuery: SETTINGS_OP* constrainedQuery;

// Optional filter constraint(s)
constrainedQuery: containingWithinQuery ('::' constraint)*;

// Optional within/containing/overlap
containingWithinQuery:
    relationQuery (NOT? containingWithinOperator containingWithinQuery)?;

containingWithinOperator: WITHIN | CONTAINING | OVERLAP;

// Optional (root) relation/alignments query
relationQuery:
    booleanQuery (childRelation (';' childRelation)*)? |
    rootRelationType;

childRelation: (captureLabel ':')? relationType relationQuery;

relationType: DEP_OP | ALIGNMENT_OP;

rootRelationType: (captureLabel ':')? ROOT_DEP_OP relationQuery;

// Clause(s) optionally combined with boolean operators (& or |)
booleanQuery: sequence (booleanOperator sequence)*;

booleanOperator: '&' | '|' | '->';

// A sequence, i.e. query parts that need to follow one another while matching
sequence: captureQuery+;

// Optional capture label(s)
captureQuery: (captureLabel ':')* sequencePartNoCapture;

// A tag, a single position clause, a function call or a parenthesized subquery
// (with optional lookahead operator, e.g. ?= or ?<!)
// optionally followed by a repetition operator (*, +, ?, or {n,m})
// OR a negated sequence part
sequencePartNoCapture:
    (
        tag |
        position |
        queryFunctionCall |
        '(' LOOKAHEAD_OP? constrainedQuery ')'
    ) repetitionAmount* |

    NOT sequencePartNoCapture;

// A tag, e.g. <word>, <lemma="run">, </sentence>, etc.
tag:
    '<' '/'? (tagName|quotedString) attribute* '/'? '>';

attribute: attributeName '=' constraintValue;

// A single position (token) clause, e.g. [word="cat" & pos="N"]
position:
    '[' constraint? ']' |
    positionWord |  // clause matching the default annotation, e.g. just "cat" without [ ]
    DEFAULT_VALUE;  // _, the "default value", in this case meaning []*
                    // used to keep  relation queries looking clean e.g. _ -nsubj-> _

positionWord: quotedString;

// Call to a query function from a constraint expression
queryFunctionCall: functionName '(' commaSeparatedParamList ')';

commaSeparatedParamList: functionParam (',' functionParam)*;

// You can either pass a query or a simple value as a parameter to a function.
// Note that a double quoted string can be both; this is dealt with
// by checking the declared parameter type of the function.
functionParam: constrainedQuery | constraintValue;

// All the possible repetition operators for a sequence part
repetitionAmount: '*' | '+' | '?' | '{' INTEGER (',' INTEGER?)? '}';

// Simple constraint(s) optionally combined with boolean operators (&, |, or ->)
constraint: simpleConstraint (booleanOperator simpleConstraint)*;

// Constraint value(s) optionally combined with comparison operators (=, !=, >, <, >=, <=)
simpleConstraint: arithmeticConstraint (comparisonOperator arithmeticConstraint)*;

comparisonOperator:
    EQUALS |
    NOT_EQUALS |
    GREATER_THAN |
    LESS_THAN |
    GREATER_THAN_OR_EQUAL |
    LESS_THAN_OR_EQUAL;

// Constraint value(s) optionally combined with arithmetic operators (+, -)
arithmeticConstraint: constraintValue (arithmeticOperator constraintValue)*;

arithmeticOperator: PLUS | MINUS;

// A function call, property selection (e.g. A.lemma), negation or simple constraint value
constraintValue:
    simpleConstraintValue ( '(' commaSeparatedParamListConstraint ')' | '.' propertyName )? |
    NOT constraintValue;

commaSeparatedParamListConstraint:
    constraintValue ( ',' constraintValue )*;

simpleConstraintValue:
    quotedString |
    booleanValue |
    INTEGER |
    inIntegerRange |
    captureLabel |
    MINUS constraint |
    '(' constraint ')';

quotedString: QUOTED_STRING | SINGLE_QUOTED_STRING;

booleanValue: TRUE | FALSE;

// e.g. <s happy=in[5,7] /> to match sentences with attribute happy 5, 6 or 7.
inIntegerRange: IN '[' INTEGER ',' INTEGER ']';

// Some disambiguation between different kind of keys and values, so we can autocomplete better.
// (we only have access to the current token/rule, so items with distinct value possibilities need distinct names).

captureLabel: NAME;
tagName: NAME;
attributeName: NAME;
functionName: NAME;
propertyName: NAME;
