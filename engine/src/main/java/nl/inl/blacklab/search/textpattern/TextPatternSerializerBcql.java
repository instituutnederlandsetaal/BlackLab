package nl.inl.blacklab.search.textpattern;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanFilter;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueIntRange;
import nl.inl.blacklab.search.matchfilter.ConstraintValueString;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.TextPatternStruct;
import nl.inl.util.StringUtil;

/**
 * Serialize a TextPattern (back) to a BCQL query.
 */
public class TextPatternSerializerBcql {

    private TextPatternSerializerBcql() {
    }

    public static String serialize(TextPatternStruct pattern) {
        StringBuilder b = new StringBuilder();
        serialize(pattern, b, false);
        return b.toString();
    }

    public static void serialize(TextPatternStruct pattern, StringBuilder b) {
        serialize(pattern, b, false);
    }

    public static void serialize(TextPatternStruct pattern, StringBuilder b,
            boolean insideTokenBrackets) {
        NodeSerializer nodeSerializer = cqlSerializers.get(pattern.getClass());
        if (nodeSerializer == null)
            throw new UnsupportedOperationException("Cannot serialize " + pattern.getClass().getSimpleName() + " to CQL");
        BracketType bt = bracketType(Integer.MAX_VALUE, pattern, insideTokenBrackets);
        b.append(bt.start);
        if (bt == BracketType.SQUARE_BRACKETS)
            insideTokenBrackets = true;
        nodeSerializer.serialize(pattern, b, insideTokenBrackets);
        b.append(bt.end);
    }

    private static void handleRegexOrTerm(TextPatternTerm tp, StringBuilder b, boolean negate) {
        String className = tp.getClass().getSimpleName();
        boolean isRegexPattern = tp instanceof TextPatternRegex;
        String annotation = tp.getAnnotation();
        if (negate && annotation == null)
            throw new UnsupportedOperationException("Cannot serialize negated " + className + " without annotation to CQL");
        MatchSensitivity sensitivity = tp.getSensitivity();
        if (sensitivity != null)
            throw new UnsupportedOperationException("Cannot serialize " + className + " with sensitivity to CQL");
        if (annotation != null)
            b.append(annotation).append(negate ? "!" : "").append("=");
        // Regular regex or literal, e.g. [word="the"]
        String value = tp.getValue();
        if (!isRegexPattern) {
            // We're looking for an exact value, which may include regex characters.
            value = StringUtil.escapeLuceneRegexCharacters(value);
        }
        serializeToQuotedString(b, value);
    }

    interface NodeSerializer {
        void serialize(TextPatternStruct pattern, StringBuilder b,
                boolean insideTokenBrackets);
    }

    private static final Map<Class<? extends TextPatternStruct>, NodeSerializer> cqlSerializers = new LinkedHashMap<>();

    /** Use double quotes for CQL */
    private static final String USE_QUOTE = "\"";

    static {
        // For each node type, add a CQL serializer to the map.

        // AND
        cqlSerializers.put(TextPatternAnd.class, (pattern, b, insideTokenBrackets) -> {
            ((NodeSerializerBrackets) (brackets) -> {
                TextPatternAnd tp = (TextPatternAnd) pattern;
                infix(b, brackets, " & ", tp.getClauses(), tp.getPrecedence());
            }).serialize(insideTokenBrackets);
        });

        // ANYTOKEN
        cqlSerializers.put(TextPatternAnyToken.class, (pattern1, b1, insideTokenBrackets) -> {
            TextPatternAnyToken tp = (TextPatternAnyToken) pattern1;
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternAnyToken inside brackets to CQL");
            b1.append("[]").append(repetitionOperator(tp.getMin(), tp.getMax()));
        });

        // CAPTURE
        cqlSerializers.put(TextPatternCaptureGroup.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize capture inside brackets to CQL");
            TextPatternCaptureGroup tp = (TextPatternCaptureGroup) pattern;
            b.append(tp.getCaptureName()).append(":");
            BracketType bt = bracketType(tp.getPrecedence(), tp.getClause(), insideTokenBrackets);
            b.append(bt.start);
            if (bt == BracketType.SQUARE_BRACKETS)
                insideTokenBrackets = true; // don't add token brackets inside square brackets
            serialize(tp.getClause(), b, insideTokenBrackets);
            b.append(bt.end);
        });

        // CONSTRAINED
        cqlSerializers.put(TextPatternConstrained.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternConstrained inside brackets to CQL");
            TextPatternConstrained tp = (TextPatternConstrained) pattern;
            infix(b, insideTokenBrackets, " :: ", List.of(tp.getClause(), tp.getConstraint()), tp.getPrecedence());
        });

        // DEFAULT VALUE
        cqlSerializers.put(TextPatternDefaultValue.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternDefaultValue inside brackets to CQL");
            b.append("_");
        });

        // NOT
        cqlSerializers.put(TextPatternNot.class, (pattern, b, insideTokenBrackets) -> {
            ((NodeSerializerBrackets) (brackets) -> {
                TextPatternNot tp = (TextPatternNot) pattern;
                if (tp.getClause() instanceof TextPatternTerm tpt && brackets) {
                    handleRegexOrTerm(tpt, b, true);
                } else {
                    b.append("!");
                    BracketType bt = bracketType(tp.getPrecedence(), tp.getClause(), brackets);
                    if (bt == BracketType.SQUARE_BRACKETS)
                        brackets = true; // don't add token brackets inside square brackets
                    b.append(bt.start);
                    serialize(tp.getClause(), b, brackets);
                    b.append(bt.end);
                }
            }).serialize(insideTokenBrackets);
        });

        // OR
        cqlSerializers.put(TextPatternOr.class, (pattern, b, insideTokenBrackets) -> {
            ((NodeSerializerBrackets) (brackets) -> {
                TextPatternOr tp = (TextPatternOr) pattern;
                infix(b, brackets, " | ", tp.getClauses(), tp.getPrecedence());
            }).serialize(insideTokenBrackets);
        });

        // POSFILTER
        cqlSerializers.put(TextPatternPositionFilter.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternPositionFilter inside brackets to CQL");
            TextPatternPositionFilter tp = (TextPatternPositionFilter) pattern;
            boolean supportedOp = tp.getOperation() == SpanFilter.WITHIN ||
                    tp.getOperation() == SpanFilter.CONTAINING;
            if (tp.isInvert() || !supportedOp)
                throw new IllegalArgumentException(
                        "Cannot serialize to CorpusQL: posfilter with invert " + tp.isInvert() +
                                ", operation " + tp.getOperation() +
                                " (only supports uninverted within/containing))");
            infix(b, insideTokenBrackets, " " + tp.getOperation() + " ",
                    List.of(tp.getProducer(), tp.getFilter()), tp.getPrecedence());
        });

        // OVERLAPPING
        cqlSerializers.put(TextPatternOverlapping.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternOverlapping inside brackets to CQL");
            TextPatternOverlapping tp = (TextPatternOverlapping) pattern;
            boolean supportedOp = tp.getOperation().equalsIgnoreCase("overlap");
            if (!supportedOp)
                throw new IllegalArgumentException(
                        "Cannot serialize to CorpusQL: TextPatternOverlapping with operation " + tp.getOperation());
            infix(b, insideTokenBrackets, " " + tp.getOperation().toLowerCase() + " ",
                    List.of(tp.getLeft(), tp.getRight()), tp.getPrecedence());
        });

        // QUERYFUNCTION
        cqlSerializers.put(TextPatternFunctionCall.class, (pattern, b, insideTokenBrackets) -> {
            TextPatternFunctionCall tp = (TextPatternFunctionCall) pattern;
            b.append(tp.getFunctionName()).append("(");
            boolean first = true;
            for (TextPattern arg: tp.getArgs()) {
                if (!first)
                    b.append(", ");
                first = false;
                serialize(arg, b, insideTokenBrackets);
            }
            b.append(")");
        });


        // Relation match (parent + children)
        cqlSerializers.put(TextPatternRelationMatch.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternRelationMatch inside brackets to CQL");
            TextPatternRelationMatch tp = (TextPatternRelationMatch) pattern;
            if (tp.getParent() != null)
                serialize(tp.getParent(), b, insideTokenBrackets);
            boolean first = true;
            for (RelationTarget child: tp.getChildren()) {
                if (!first)
                    b.append(" ;");
                first = false;
                serialize(child, b, insideTokenBrackets);
            }
        });

        // Relation target (child)
        cqlSerializers.put(RelationTarget.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternRelationTarget inside brackets to CQL");
            RelationTarget tp = (RelationTarget) pattern;
            String optCapture = tp.getCaptureAs().isEmpty() ? "" : tp.getCaptureAs() + ":";
            RelationOperatorInfo operatorInfo = tp.getOperatorInfo();
            String typeRegex = operatorInfo.getTypeRegex();
            String optRegex = typeRegex.matches("\\.[*+]") ? "" : typeRegex;
            boolean isRoot = operatorInfo.getDirection() == SpanQueryRelations.Direction.ROOT;
            if (isRoot && tp.getSpanMode() != RelationInfo.SpanMode.TARGET)
                throw new IllegalArgumentException("Root relation must have span mode target (has no source)");
            String optOperatorPrefix = isRoot ? "^" : (operatorInfo.isNegate() ? "!" : "");
            String opChar = operatorInfo.isAlignment() ? "=" : "-";
            String optTargetVersion = operatorInfo.getTargetVersion() == null ? "" : operatorInfo.getTargetVersion();
            b.append(isRoot ? "" : " ").append(optCapture).append(optOperatorPrefix).append(opChar).append(optRegex)
                    .append(opChar).append(">").append(optTargetVersion).append(operatorInfo.isOptionalMatch() ? "?" : "")
                    .append(" ");
            BracketType bt = bracketType(tp.getPrecedence(), tp.getTarget(), insideTokenBrackets);
            b.append(bt.start);
            if (bt == BracketType.SQUARE_BRACKETS)
                insideTokenBrackets = true; // don't add token brackets inside square brackets
            serialize(tp.getTarget(), b, insideTokenBrackets);
            b.append(bt.end);
        });

        // REPETITION
        cqlSerializers.put(TextPatternRepetition.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternRepetition inside brackets to CQL");
            TextPatternRepetition tp = (TextPatternRepetition) pattern;
            BracketType bt = bracketType(tp.getPrecedence(), tp.getClause(), insideTokenBrackets);
            b.append(bt.start);
            if (bt == BracketType.SQUARE_BRACKETS)
                insideTokenBrackets = true; // don't add token brackets inside square brackets
            serialize(tp.getClause(), b, insideTokenBrackets);
            b.append(bt.end);
            b.append(repetitionOperator(tp.getMin(), tp.getMax()));
        });

        // SEQUENCE
        cqlSerializers.put(TextPatternSequence.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternSequence inside brackets to CQL");
            infix(b, insideTokenBrackets, " ", ((TextPatternSequence)pattern).getClauses(), pattern.getPrecedence());
        });

        // LOOKAHEAD/BEHIND
        cqlSerializers.put(TextPatternLook.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternLookahead inside brackets to CQL");
            TextPatternLook tp = (TextPatternLook) pattern;
            b.append("(");
            b.append(lookaheadOperator(tp.isLookBehind(), tp.isNegate()));
            b.append(" ");
            serialize(tp.getClause(), b, insideTokenBrackets);
            b.append(")");
        });

        // Settings
        cqlSerializers.put(TextPatternSettings.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternSettings inside brackets to CQL");
            b.append("@");
            TextPatternSettings tp = (TextPatternSettings) pattern;
            b.append(tp.getSettings().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","))).append(" ");
            BracketType bt = bracketType(tp.getPrecedence(), tp.getClause(), insideTokenBrackets);
            b.append(bt.start);
            if (bt == BracketType.SQUARE_BRACKETS)
                insideTokenBrackets = true; // don't add token brackets inside square brackets
            serialize(tp.getClause(), b, false);
            b.append(bt.end);
        });

        // TAGS
        cqlSerializers.put(TextPatternTags.class, (pattern, b, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternTags inside brackets to CQL");
            TextPatternTags tp = (TextPatternTags) pattern;
            String optAttr = tp.getAttributes().isEmpty() ? "" : " " + serializeAttributes(tp.getAttributes());
            String optCapture = tp.getCaptureAs().isEmpty() ? "" : tp.getCaptureAs() + ":";
            TextPatternTags.Adjust adjust = tp.getAdjust();
            String slashBefore = adjust == TextPatternTags.Adjust.TRAILING_EDGE ? "/" : "";
            String slashAfter = adjust == TextPatternTags.Adjust.FULL_TAG ? "/" : "";
            b.append(optCapture).append("<").append(slashBefore);
            String tagName = tp.getElementNameRegex();
            if (StringUtil.containsRegexCharacters(tagName)) {
                // Put in double quotes to signify it's a regex, and escape double quotes if needed
                serializeToQuotedString(b, tagName);
            } else {
                // No quotes needed
                b.append(tagName);
            }
            b.append(optAttr).append(slashAfter).append(">");
        });

        // REGEX
        cqlSerializers.put(TextPatternRegex.class, (pattern, b, insideTokenBrackets) -> {
            ((NodeSerializerBrackets) (brackets) -> {
                handleRegexOrTerm((TextPatternRegex)pattern, b, false);
            }).serialize(insideTokenBrackets);
        });

        // TERM
        cqlSerializers.put(TextPatternTerm.class, (pattern, b, insideTokenBrackets) -> {
            ((NodeSerializerBrackets) (brackets) -> {
                handleRegexOrTerm((TextPatternTerm)pattern, b, false);
            }).serialize(insideTokenBrackets);
        });

        // TextPattern compare
        cqlSerializers.put(TextPatternCompare.class, (pattern, b, insideTokenBrackets) -> {
            TextPatternCompare tp = (TextPatternCompare) pattern;
            if (tp.isEqualsDefaultAnnotation()) {
                // Special case: a top-level string in BCQL is comparing with the default annotation
                // (i.e. "cow" means [word="cow"])
                String value = ((ConstraintValueString) ((TextPatternValue) tp.getRightClause()).getValue()).getValue();
                handleRegexOrTerm((TextPatternTerm)TextPattern.regex(value), b, false);
            } else {
                if (tp.getLeftClause() instanceof TextPatternDefaultValue)
                    throw new UnsupportedOperationException("TextPatternCompare with default annotation is only allowed with = and a string value");
                ((NodeSerializerBrackets) (brackets) -> {
                    infix(b, insideTokenBrackets, " " + tp.getOperator() + " ",
                            List.of(tp.getLeftClause(), tp.getRightClause()), tp.getPrecedence());
                }).serialize(insideTokenBrackets);
            }
        });

        // TextPattern implication
        cqlSerializers.put(TextPatternImplication.class, (pattern, b, insideTokenBrackets) -> {
            ((NodeSerializerBrackets) (brackets) -> {
                TextPatternImplication tp = (TextPatternImplication) pattern;
                infix(b, insideTokenBrackets, " -> ", List.of(tp.getAntecedent(), tp.getConsequent()),
                        tp.getPrecedence());
            }).serialize(insideTokenBrackets);
        });

        // TextPattern value
        cqlSerializers.put(TextPatternValue.class, (pattern, b, insideTokenBrackets) -> {
            TextPatternValue tp = (TextPatternValue) pattern;
            serializeConstraintValue(b, tp.getValue());
        });

        // TextPattern token annotation
        cqlSerializers.put(TextPatternPropertySelect.class, (pattern, b, insideTokenBrackets) -> {
            TextPatternPropertySelect tp = (TextPatternPropertySelect) pattern;
            infix(b, insideTokenBrackets, ".", List.of(tp.getLabel(), tp.getAnnotation()), tp.getPrecedence());
        });
    }

    enum BracketType {
        NONE("", ""),
        PARENTHESES("(", ")"),
        SQUARE_BRACKETS("[", "]");

        String start, end;

        BracketType(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }

    private static BracketType bracketType(int parentPrecedence, TextPatternStruct child, boolean insideTokenBrackets) {
        if (child.isBracketQuery() && !insideTokenBrackets)
            return BracketType.SQUARE_BRACKETS;
        boolean childIsDefaultAnnotCompare = child instanceof TextPatternCompare compare &&
                compare.isEqualsDefaultAnnotation() || (child instanceof TextPatternTerm tpt && tpt.annotation == null);
        if (child.getPrecedence() != 0 && child.getPrecedence() >= parentPrecedence && !childIsDefaultAnnotCompare)
            return BracketType.PARENTHESES;
        return BracketType.NONE;
    }

    // Longer serializers below

    interface NodeSerializerBrackets {
        void serialize(boolean insideTokenBrackets);
    }

    private static String lookaheadOperator(boolean lookBehind, boolean negate) {
        return "?" + (lookBehind ? "<" : "") + (negate ? "!" : "=");
    }

    private static String repetitionOperator(int min, int max) {
        if (min ==1 && max == 1) {
            return "";
        } else if (min == 0 && max == TextPattern.MAX_UNLIMITED) {
            return "*";
        } else if (min == 1 && max == TextPattern.MAX_UNLIMITED) {
            return "+";
        } else if (min == 0 && max == 1) {
            return "?";
        } else if (max == TextPattern.MAX_UNLIMITED) {
            return "{" + min + ",}";
        } else {
            return "{" + min + "," + max + "}";
        }
    }

    private static void serializeConstraintValue(StringBuilder b, ConstraintValue cv) {
        if (cv instanceof ConstraintValueString s)
            serializeToQuotedString(b, s.getValue());
        else if (cv instanceof ConstraintValueSymbol cvs)
            b.append(cvs.getValue());
        else if (cv instanceof ConstraintValueIntRange cvir)
            b.append("in[").append(cvir.getMin()).append(",").append(cvir.getMax()).append("]");
        else
            b.append(cv.getValue().toString());
    }

    private static void serializeToQuotedString(StringBuilder b, String value) {
        b.append(USE_QUOTE).append(StringUtil.escapeQuoteForBcql(value, USE_QUOTE)).append(USE_QUOTE);
    }

    private static String serializeAttributes(Map<String, TextPattern> attr) {
        return attr.entrySet().stream()
                .map(e -> {
                    return e.getKey() + "=" + serialize(e.getValue());
                })
                .collect(Collectors.joining(" "));
    }

    private static void infix(StringBuilder b, boolean insideTokenBrackets, String operator,
            List<? extends TextPatternStruct> clauses, int precedence) {
        boolean first = true;
        boolean isConstrainOperator = operator.matches("\\s*::\\s*");
        for (TextPatternStruct clause: clauses) {
            if (!first)
                b.append(operator);

            // never add [brackets] to the constraint on the right side of ::
            boolean isConstraint = isConstrainOperator && !first;
            boolean br = insideTokenBrackets;
            if (isConstraint)
                br = true; // don't add token brackets in constraints

            BracketType bt = bracketType(precedence, clause, br);
            b.append(bt.start);
            if (bt == BracketType.SQUARE_BRACKETS)
                br = true; // don't add token brackets inside square brackets
            serialize(clause, b, br);
            b.append(bt.end);
            first = false;
        }
    }
}
