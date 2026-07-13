package nl.inl.blacklab.queryParser.corpusql;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanFilter;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnd;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.search.textpattern.TextPatternCaptureGroup;
import nl.inl.blacklab.search.textpattern.TextPatternCompare;
import nl.inl.blacklab.search.textpattern.TextPatternConstrained;
import nl.inl.blacklab.search.textpattern.TextPatternDefaultValue;
import nl.inl.blacklab.search.textpattern.TextPatternFunctionCall;
import nl.inl.blacklab.search.textpattern.TextPatternImplication;
import nl.inl.blacklab.search.textpattern.TextPatternLook;
import nl.inl.blacklab.search.textpattern.TextPatternNot;
import nl.inl.blacklab.search.textpattern.TextPatternOr;
import nl.inl.blacklab.search.textpattern.TextPatternOverlapping;
import nl.inl.blacklab.search.textpattern.TextPatternPositionFilter;
import nl.inl.blacklab.search.textpattern.TextPatternPropertySelect;
import nl.inl.blacklab.search.textpattern.TextPatternRepetition;
import nl.inl.blacklab.search.textpattern.TextPatternSequence;
import nl.inl.blacklab.search.textpattern.TextPatternSettings;
import nl.inl.blacklab.search.textpattern.TextPatternTags;
import nl.inl.blacklab.search.textpattern.TextPatternValue;
import nl.inl.util.StringUtil;

/**
 * Visitor that translates a parse tree from the BcqlParser
 * into a TextPattern.
 */
public class BcqlAstVisitor extends BcqlBaseVisitor<TextPattern> {

    /**
     * If we encounter a quoted string, should we translate that to a query on the default annotion,
     * or to a simple string value?
     */
    boolean quotedStringIsQuery = true;

    @Override
    public TextPattern visitQuery(BcqlParser.QueryContext ctx) {
        return visit(ctx.settingsQuery());
    }

    @Override
    public TextPattern visitSettingsQuery(BcqlParser.SettingsQueryContext ctx) {
        TextPattern result = visit(ctx.constrainedQuery());
        List<TerminalNode> ops = ctx.SETTINGS_OP();
        if (!ops.isEmpty())
            result = new TextPatternSettings(getSettingsMap(ops), result);
        return result;
    }

    private static @NonNull Map<String, String> getSettingsMap(List<TerminalNode> ops) {
        Map<String, String> settingsMap = new LinkedHashMap<String, String>();
        for (TerminalNode op: ops) {
            // Parse settings operator (of the form "@a=b,c=d,e=f") into a map
            String keyValuesString = op.getText().substring(1);
            for (String keyValue: keyValuesString.split(",")) {
                String[] keyAndValue = keyValue.split("=");
                if (keyAndValue.length != 2)
                    throw new IllegalArgumentException("Invalid settings string: " + keyValuesString);
                settingsMap.put(keyAndValue[0], keyAndValue[1]);
            }
        }
        return settingsMap;
    }

    @Override
    public TextPattern visitConstrainedQuery(BcqlParser.ConstrainedQueryContext ctx) {
        boolean prev = quotedStringIsQuery;
        try {
            quotedStringIsQuery = true;
            TextPattern result = visit(ctx.containingWithinQuery());
            if (!ctx.constraint().isEmpty()) {
                quotedStringIsQuery = false; // strings after :: are not queries
                for (BcqlParser.ConstraintContext constraintCtx: ctx.constraint()) {
                    TextPattern constraint = visit(constraintCtx);
                    result = new TextPatternConstrained(result, constraint);
                }
            }
            return result;
        } finally {
            quotedStringIsQuery = prev;
        }
    }

    @Override
    public TextPattern visitConstraint(BcqlParser.ConstraintContext ctx) {
        List<BcqlParser.SimpleConstraintContext> simpleConstraints = ctx.simpleConstraint();
        TextPattern result = visit(simpleConstraints.get(0));
        if (simpleConstraints.size() != ctx.booleanOperator().size() + 1)
            throw new IllegalArgumentException("Number of simple constraints must be one more than number of boolean operators");
        if (!ctx.booleanOperator().isEmpty()) {
            for (int i = 0; i < ctx.booleanOperator().size(); i++) {
                String op = ctx.booleanOperator(i).getText();
                TextPattern rightClause = visit(simpleConstraints.get(i + 1));
                result = switch (op) {
                    case "&" -> new TextPatternAnd(result, rightClause);
                    case "|" -> new TextPatternOr(result, rightClause);
                    case "->" -> new TextPatternImplication(result, rightClause);
                    default -> throw new IllegalArgumentException("Invalid constraint: " + op);
                };
            }
        }
        return result;
    }

    @Override
    public TextPattern visitSimpleConstraint(BcqlParser.SimpleConstraintContext ctx) {
        List<BcqlParser.ConstraintValueContext> cvs = ctx.constraintValue();
        TextPattern result = visit(cvs.get(0));
        if (cvs.size() != ctx.comparisonOperator().size() + 1)
            throw new IllegalArgumentException("Number of constraint values must be one more than number of comparison operators");
        for (int i = 1; i < cvs.size(); i++) {
            String op = ctx.comparisonOperator(i - 1).getText();
            TextPattern clause2 = visit(cvs.get(i));
            result = new TextPatternCompare(result, clause2, MatchFilterCompare.Operator.fromSymbol(op));
        }
        return result;
    }

    @Override
    public TextPattern visitConstraintValue(BcqlParser.ConstraintValueContext ctx) {
        TextPattern result;
        if (ctx.NOT() != null) {
            // Negation, e.g. !(word = "example")
            TextPattern clause = visit(ctx.constraintValue());
            result = new TextPatternNot(clause);
        } else {
            result = visit(ctx.simpleConstraintValue());
            if (ctx.commaSeparatedParamListConstraint() != null) {
                // Function call, e.g. some_function([word="example"], true)
                List<TextPattern> params = commaSeparatedParamListConstraint(ctx.commaSeparatedParamListConstraint());
                if (!(result instanceof TextPatternValue tpv))
                    throw new IllegalArgumentException("Function call needs a function name");
                if (!(tpv.getValue() instanceof ConstraintValueSymbol cvs))
                    throw new IllegalArgumentException("Function call needs a function name");
                result = new TextPatternFunctionCall(cvs.getValue(), params);
            } else if (ctx.propertyName() != null) {
                // Property select, e.g. A.lemma
                TextPatternValue propName = new TextPatternValue(ConstraintValue.symbol(ctx.propertyName().getText()));
                result = new TextPatternPropertySelect(result, propName);
            }
        }
        return result;
    }

    private List<TextPattern> commaSeparatedParamListConstraint(
            BcqlParser.CommaSeparatedParamListConstraintContext ctx) {
        List<BcqlParser.ConstraintValueContext> values = ctx.constraintValue();
        return values.stream().map(this::visit).toList();
    }

    private List<TextPattern> commaSeparatedParamList(
            BcqlParser.CommaSeparatedParamListContext ctx) {
        return ctx.functionParam().stream().map(this::visit).toList();
    }

    @Override
    public TextPattern visitSimpleConstraintValue(BcqlParser.SimpleConstraintValueContext ctx) {
        if (ctx.quotedString() != null) {
            return visit(ctx.quotedString());
        } else if (ctx.booleanValue() != null) {
            return new TextPatternValue(ConstraintValue.get(Boolean.parseBoolean(ctx.booleanValue().getText())));
        } else if (ctx.INTEGER() != null) {
            return new TextPatternValue(ConstraintValue.get(Integer.parseInt(ctx.INTEGER().getText())));
        } else if (ctx.inIntegerRange() != null) {
            return visit(ctx.inIntegerRange());
        } else if (ctx.captureLabel() != null) {
            return visit(ctx.captureLabel());
        } else if (ctx.constraint() != null) {
            return visit(ctx.constraint());
        } else {
            throw new IllegalArgumentException("Unexpected token type: " + ctx.getStart().getType());
        }
    }

    @Override
    public TextPattern visitInIntegerRange(BcqlParser.InIntegerRangeContext ctx) {
        List<Integer> range = ctx.INTEGER().stream().map(s -> Integer.parseInt(s.getText())).toList();
        if (range.size() != 2)
            throw new IllegalArgumentException("Invalid integer range: " + ctx.getText());
        return new TextPatternValue(ConstraintValue.get(range.get(0), range.get(1)));
    }

    @Override
    public TextPattern visitContainingWithinQuery(BcqlParser.ContainingWithinQueryContext ctx) {
        TextPattern result = visit(ctx.relationQuery());
        if (ctx.containingWithinQuery() != null) {
            boolean invert = ctx.NOT() != null;
            String op = ctx.containingWithinOperator().getText();
            TextPattern clause2 = visit(ctx.containingWithinQuery());
            result = switch (op) {
                case "overlap" -> new TextPatternOverlapping(result, clause2, op);
                case "within", "containing" -> {
                    SpanFilter operator = op.equals("within") ?
                            SpanFilter.WITHIN :
                            SpanFilter.CONTAINING;
                    yield new TextPatternPositionFilter(result, clause2, operator, invert);
                }
                default -> throw new IllegalArgumentException("Invalid containingWithin operator: " + op);
            };
        }
        return result;
    }

    @Override
    public TextPattern visitRelationQuery(BcqlParser.RelationQueryContext ctx) {
        if (ctx.rootRelationType() != null) {
            return visit(ctx.rootRelationType());
        }
        TextPattern result = visit(ctx.booleanQuery());
        if (!ctx.childRelation().isEmpty()) {
            List<BcqlQueryLanguageParser.ChildRelationStruct> childRelations = ctx.childRelation().stream()
                    .map(this::childRelation).toList();
            result = BcqlQueryLanguageParser.relationQuery(result, childRelations);
        }
        return result;
    }

    BcqlQueryLanguageParser.ChildRelationStruct childRelation(BcqlParser.ChildRelationContext ctx) {
        String captureAs = ctx.captureLabel() == null ? null : ctx.captureLabel().getText();
        RelationOperatorInfo relationType = RelationOperatorInfo.fromOperator(ctx.relationType().getText());
        TextPattern childQuery = visit(ctx.relationQuery());
        return new BcqlQueryLanguageParser.ChildRelationStruct(relationType, childQuery, captureAs);
    }

    @Override
    public TextPattern visitRootRelationType(BcqlParser.RootRelationTypeContext ctx) {
        String captureAs = ctx.captureLabel() == null ? null : ctx.captureLabel().getText();
        RelationOperatorInfo relationType = RelationOperatorInfo.fromOperator(ctx.ROOT_DEP_OP().getText());
        TextPattern childQuery = visit(ctx.relationQuery());
        return BcqlQueryLanguageParser.rootRelationQuery(
                new BcqlQueryLanguageParser.ChildRelationStruct(relationType, childQuery, captureAs));
    }

    @Override
    public TextPattern visitTag(BcqlParser.TagContext ctx) {
        boolean prev = quotedStringIsQuery;
        try {
            quotedStringIsQuery = false; // inside < > a quoted string is always a value, never a query
            return new TextPatternTags(tagNameRegex(ctx), tagAttributes(ctx), tagAdjust(ctx), "");
        } finally {
            quotedStringIsQuery = prev;
        }
    }

    private static TextPatternTags.@NonNull Adjust tagAdjust(BcqlParser.TagContext ctx) {
        List<TerminalNode> slashes = ctx.SLASH();
        boolean endTagSlash = false;
        boolean selfCloseSlash = false;
        if (slashes.size() == 1) {
            var slash = slashes.get(0).getSymbol();
            ParserRuleContext tagNameContext = ctx.tagName();
            if (tagNameContext == null)
                tagNameContext = ctx.quotedString();
            if (slash.getTokenIndex() < tagNameContext.getStart().getTokenIndex()) {
                // this was the opening slash: </tag>
                endTagSlash = true;
            } else {
                // this was the self-closing slash: <tag/>
                selfCloseSlash = true;
            }
        } else if (slashes.size() == 2) {
            throw new IllegalArgumentException("Too many slashes in tag: " + ctx.getText());
        }
        TextPatternTags.Adjust adjust;
        if (selfCloseSlash)
            adjust = TextPatternTags.Adjust.FULL_TAG;
        else
            adjust = TextPatternTags.Adjust.LEADING_EDGE;
        if (endTagSlash)
            adjust = TextPatternTags.Adjust.TRAILING_EDGE;
        return adjust;
    }

    private static String tagNameRegex(BcqlParser.TagContext ctx) {
        String tag;
        if (ctx.tagName() != null) {
            // Normal tag name: interpret as literal (so i.e. '.' is just a dot, not 'any character')
            tag = StringUtil.escapeLuceneRegexCharacters(ctx.tagName().getText());
        } else {
            // Quoted tag name: interpret as regex
            tag = BcqlQueryLanguageParser.getRegexFromQuotedString(ctx.quotedString().getText());
        }
        return tag;
    }

    private @NonNull Map<String, TextPattern> tagAttributes(BcqlParser.TagContext ctx) {
        Map<String, TextPattern> attr = new HashMap<>();
        ctx.attribute().forEach(attCtx -> {
            attr.put(attCtx.attributeName().getText(), visit(attCtx.constraintValue()));
        });
        return attr;
    }

    @Override
    public TextPattern visitBooleanQuery(BcqlParser.BooleanQueryContext ctx) {
        List<BcqlParser.SequenceContext> sequence = ctx.sequence();
        TextPattern result = visit(sequence.get(0));
        if (sequence.size() != ctx.booleanOperator().size() + 1)
            throw new IllegalArgumentException("Number of sequences must be one more than number of boolean operators");
        for (int i = 0; i < ctx.booleanOperator().size(); i++) {
            TextPattern clause2 = visit(sequence.get(i + 1));
            String op = ctx.booleanOperator(i).getText();
            result = switch (op) {
                case "&" -> new TextPatternAnd(result, clause2);
                case "|" -> new TextPatternOr(result, clause2);
                case "->" -> throw new UnsupportedOperationException(
                        "Implication operator not supported at the sequence level");
                default -> throw new IllegalArgumentException("Invalid boolean operator: " + op);
            };
        }
        return result;
    }

    @Override
    public TextPattern visitSequence(BcqlParser.SequenceContext ctx) {
        List<TextPattern> clauses = ctx.captureQuery().stream().map(this::visit).toList();
        if (clauses.size() > 1)
            return new TextPatternSequence(clauses);
        return clauses.get(0);
    }

    @Override
    public TextPattern visitCaptureQuery(BcqlParser.CaptureQueryContext ctx) {
        TextPattern result = visit(ctx.sequencePartNoCapture());
        for (int i = ctx.captureLabel().size() - 1; i >= 0; i--) {
            String captureAs = ctx.captureLabel(i).getText();
            result = TextPatternCaptureGroup.get(result, captureAs);
        }
        return result;
    }

    @Override
    public TextPattern visitSequencePartNoCapture(BcqlParser.SequencePartNoCaptureContext ctx) {
        if (ctx.NOT() != null)
            return new TextPatternNot(visit(ctx.sequencePartNoCapture()));
        TextPattern result;
        if (ctx.tag() != null) {
            result = visit(ctx.tag());
        } else if (ctx.position() != null) {
            result = visit(ctx.position());
        } else if (ctx.constrainedQuery() != null) {
            // For lookahead/lookbehind, we need zero-length matches of either the leading or trailing edge.
            result = visit(ctx.constrainedQuery());
            if (ctx.LOOKAHEAD_OP() != null) {
                String op = ctx.LOOKAHEAD_OP().getText();
                boolean behind = op.contains("<");
                boolean negative = op.contains("!");
                result = new TextPatternLook(result, behind, negative);
            }
        } else if (ctx.queryFunctionCall() != null) {
            result = visit(ctx.queryFunctionCall());
        } else
            throw new IllegalArgumentException("Invalid sequence part no capture: " + ctx.getText());

        // Optional repetition
        for (BcqlParser.RepetitionAmountContext repCtx: ctx.repetitionAmount()) {
            int[] rep = repetitionAmount(repCtx);
            if (rep[0] < 0 || rep[1] < 0)
                throw new IllegalArgumentException("Repetition amounts must be non-negative");
            if (result instanceof TextPatternAnyToken any) {
                result = any.repeat(rep[0], rep[1]);
            } else {
                result = TextPatternRepetition.get(result, rep[0], rep[1]);
            }
        }

        return result;
    }

    private int[] repetitionAmount(BcqlParser.RepetitionAmountContext rep) {
        if (rep.STAR() != null) {
            return new int[] { 0, BLSpanQuery.MAX_UNLIMITED };
        } else if (rep.PLUS() != null) {
            return new int[] { 1, BLSpanQuery.MAX_UNLIMITED };
        } else if (rep.QUESTION() != null) {
            return new int[] { 0, 1 };
        } else if (!rep.INTEGER().isEmpty()) {
            // Explicit min/max, e.g. {2} (exactly 2) or {2,5} (two to five) or {2,} (at least two)
            int min = Integer.parseInt(rep.INTEGER(0).getText());
            int defMax = rep.COMMA() == null ? min : BLSpanQuery.MAX_UNLIMITED;
            int max = rep.INTEGER().size() > 1 ? Integer.parseInt(rep.INTEGER(1).getText()) : defMax;
            return new int[] { min, max };
        } else
            throw new IllegalArgumentException("Invalid repetition amount: " + rep.getText());
    }

    @Override
    public TextPattern visitQueryFunctionCall(BcqlParser.QueryFunctionCallContext ctx) {
        String name = ctx.functionName().getText();
        List<TextPattern> params = commaSeparatedParamList(ctx.commaSeparatedParamList());
        return new TextPatternFunctionCall(name, params);
    }

    @Override
    public TextPattern visitFunctionParam(BcqlParser.FunctionParamContext ctx) {
        if (ctx.constrainedQuery() != null)
            return visit(ctx.constrainedQuery());
        else if (ctx.constraintValue() != null) {
            boolean prev = quotedStringIsQuery;
            try {
                quotedStringIsQuery = false; // in constraint value, a quoted string is always a value, never a query
                return visit(ctx.constraintValue());
            } finally {
                quotedStringIsQuery = prev;
            }
        } else
            throw new IllegalArgumentException("Invalid function parameter: " + ctx.getText());
    }

    @Override
    public TextPattern visitCaptureLabel(BcqlParser.CaptureLabelContext ctx) {
        return new TextPatternValue(ConstraintValue.symbol(ctx.getText()));
    }

    @Override
    public TextPattern visitPosition(BcqlParser.PositionContext ctx) {
        if (ctx.DEFAULT_VALUE() != null) {
            return TextPatternDefaultValue.get();
        } else if (ctx.constraint() != null) {
            // Inside [ ], a quoted string is always a value, never a query.
            boolean prev = quotedStringIsQuery;
            try {
                quotedStringIsQuery = false;
                return visit(ctx.constraint());
            } finally {
                quotedStringIsQuery = prev;
            }
        } else if (ctx.positionWord() != null) {
            return visit(ctx.positionWord());
        } else {
            return new TextPatternAnyToken(1, 1);
        }
    }

    @Override
    public TextPattern visitPositionWord(BcqlParser.PositionWordContext ctx) {
        return visit(ctx.quotedString());
    }

    @Override
    public TextPattern visitQuotedString(BcqlParser.QuotedStringContext ctx) {
        String unescaped = BcqlQueryLanguageParser.getRegexFromQuotedString(ctx.getText());
        TextPattern result = new TextPatternValue(ConstraintValue.get(unescaped));
        if (quotedStringIsQuery)
            result = new TextPatternCompare(TextPatternDefaultValue.get(), result, MatchFilterCompare.Operator.EQUAL);
        return result;
    }
}
