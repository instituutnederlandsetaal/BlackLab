package nl.inl.blacklab.search.textpattern;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueIntRange;
import nl.inl.blacklab.search.matchfilter.ConstraintValueString;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
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
        serialize(pattern, b, false, false);
        return b.toString();
    }

    public static void serialize(TextPatternStruct pattern, StringBuilder b) {
        serialize(pattern, b, false, false);
    }

    public static void serialize(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        NodeSerializer nodeSerializer = cqlSerializers.get(pattern.getClass());
        if (nodeSerializer == null)
            throw new UnsupportedOperationException("Cannot serialize " + pattern.getClass().getSimpleName() + " to CQL");
        nodeSerializer.serialize(pattern, b, parenthesizeIfNecessary, insideTokenBrackets);
    }

    private static void handleRegexOrTerm(TextPatternStruct pattern, StringBuilder b, boolean insideTokenBrackets,
            boolean negate) {
        String className = pattern.getClass().getSimpleName();
        boolean isRegexPattern = pattern instanceof TextPatternRegex;
        TextPatternTerm tp = (TextPatternTerm) pattern;
        String annotation = tp.getAnnotation();
        if (negate && annotation == null)
            throw new UnsupportedOperationException("Cannot serialize negated " + className + " without annotation to CQL");
        MatchSensitivity sensitivity = tp.getSensitivity();
        if (sensitivity != null)
            throw new UnsupportedOperationException("Cannot serialize " + className + " with sensitivity to CQL");
//        String optOpenBracket = insideTokenBrackets ? "" : "[";
//        String optCloseBracket = insideTokenBrackets ? "" : "]";
        if (annotation != null)
            b/*.append(optOpenBracket)*/.append(annotation).append(negate ? "!" : "").append("=");
        // Regular regex or literal, e.g. [word="the"]
        String value = tp.getValue();
        if (!isRegexPattern) {
            // We're looking for an exact value, which may include regex characters.
            value = StringUtil.escapeLuceneRegexCharacters(value);
        }
        serializeToQuotedString(b, value);
//        if (annotation != null)
//            b.append(optCloseBracket);
    }

    interface NodeSerializer {
        void serialize(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
                boolean insideTokenBrackets);
    }

    private static final Map<Class<? extends TextPatternStruct>, NodeSerializer> cqlSerializers = new LinkedHashMap<>();

    static {
        // For each node type, add a CQL serializer to the map.

        // AND
        cqlSerializers.put(TextPatternAnd.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
                    (parenthesize, brackets) -> {
                TextPatternAnd tp = (TextPatternAnd) pattern;
                infix(b, parenthesize, brackets, " & ", tp.getClauses());
            });
        });

        // ANYTOKEN
        cqlSerializers.put(TextPatternAnyToken.class, (pattern1, b1, parenthesizeIfNecessary, insideTokenBrackets) -> {
            TextPatternAnyToken tp = (TextPatternAnyToken) pattern1;
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternAnyToken inside brackets to CQL");
            b1.append("[]").append(repetitionOperator(tp.getMin(), tp.getMax()));
        });

        // CAPTURE
        cqlSerializers.put(TextPatternCaptureGroup.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize capture inside brackets to CQL");
            if (parenthesizeIfNecessary)
                b.append("(");
            TextPatternCaptureGroup tp = (TextPatternCaptureGroup) pattern;
            b.append(tp.getCaptureName()).append(":");
            serialize(tp.getClause(), b, true, insideTokenBrackets);
            if (parenthesizeIfNecessary)
                b.append(")");
        });

        // CONSTRAINED
        cqlSerializers.put(TextPatternConstrained.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternConstrained inside brackets to CQL");
            TextPatternConstrained tp = (TextPatternConstrained) pattern;
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " :: ", List.of(tp.getClause(), tp.getConstraint()));
        });

        // DEFAULT VALUE
        cqlSerializers.put(TextPatternDefaultValue.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternDefaultValue inside brackets to CQL");
            b.append("_");
        });

        // EXPANSION
        cqlSerializers.put(TextPatternExpansion.class, TextPatternSerializerBcql::serializeExpansion);

        // NOT
        cqlSerializers.put(TextPatternNot.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
            (parenthesize, brackets) -> {
                TextPatternNot tp = (TextPatternNot) pattern;
                if (tp.getClause() instanceof TextPatternTerm && brackets) {
                    handleRegexOrTerm(tp.getClause(), b, true, true);
                } else {
                    b.append("!");
                    serialize(tp.getClause(), b, true, brackets);
                }
            });
        });

        // OR
        cqlSerializers.put(TextPatternOr.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
                    (parenthesize, brackets) -> {
                TextPatternOr tp = (TextPatternOr) pattern;
                infix(b, parenthesize, brackets, " | ", tp.getClauses());
            });
        });

        // POSFILTER
        cqlSerializers.put(TextPatternPositionFilter.class, TextPatternSerializerBcql::serializePosFilter);

        // OVERLAPPING
        cqlSerializers.put(TextPatternOverlapping.class, TextPatternSerializerBcql::serializeOverlapping);

        // QUERYFUNCTION
        cqlSerializers.put(TextPatternFunctionCall.class, TextPatternSerializerBcql::serializeFuncCall);


        // Relation match (parent + children)
        cqlSerializers.put(TextPatternRelationMatch.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternRelationMatch inside brackets to CQL");
            TextPatternRelationMatch tp = (TextPatternRelationMatch) pattern;
            if (parenthesizeIfNecessary)
                b.append("(");
            if (tp.getParent() != null)
                serialize(tp.getParent(), b, true, insideTokenBrackets);
            boolean first = true;
            for (RelationTarget child: tp.getChildren()) {
                if (!first)
                    b.append(" ;");
                first = false;
                serialize(child, b, true, insideTokenBrackets);
            }
            if (parenthesizeIfNecessary)
                b.append(")");
        });

        // Relation target (child)
        cqlSerializers.put(RelationTarget.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
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
            serialize(tp.getTarget(), b, true, insideTokenBrackets);
        });

        // REPETITION
        cqlSerializers.put(TextPatternRepetition.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternRepetition inside brackets to CQL");
            TextPatternRepetition tp = (TextPatternRepetition) pattern;
            if (parenthesizeIfNecessary)
                b.append("(");
            serialize(tp.getClause(), b, true, insideTokenBrackets);
            b.append(repetitionOperator(tp.getMin(), tp.getMax()));
            if (parenthesizeIfNecessary)
                b.append(")");
        });

        // SEQUENCE
        cqlSerializers.put(TextPatternSequence.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternSequence inside brackets to CQL");
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " ", ((TextPatternSequence)pattern).getClauses());
        });

        // LOOKAHEAD/BEHIND
        cqlSerializers.put(TextPatternLook.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternLookahead inside brackets to CQL");
            TextPatternLook tp = (TextPatternLook) pattern;
            b.append("(");
            b.append(lookaheadOperator(tp.isLookBehind(), tp.isNegate()));
            b.append(" ");
            serialize(tp.getClause(), b, false, insideTokenBrackets);
            b.append(")");
        });

        // Settings
        cqlSerializers.put(TextPatternSettings.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternSettings inside brackets to CQL");
            b.append("@");
            TextPatternSettings tp = (TextPatternSettings) pattern;
            b.append(tp.getSettings().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","))).append(" ");
            serialize(tp.getClause(), b, true, false);
        });

        // TAGS
        cqlSerializers.put(TextPatternTags.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternTags inside brackets to CQL");
            TextPatternTags tp = (TextPatternTags) pattern;
            String optAttr = tp.getAttributes().isEmpty() ? "" : " " + serializeAttributes(tp.getAttributes());
            String optCapture = tp.getCaptureAs().isEmpty() ? "" : tp.getCaptureAs() + ":";
            TextPatternTags.Adjust adjust = tp.getAdjust();
            String slashBefore = adjust == TextPatternTags.Adjust.TRAILING_EDGE ? "/" : "";
            String slashAfter = adjust == TextPatternTags.Adjust.FULL_TAG ? "/" : "";
            b.append(optCapture).append("<").append(slashBefore).append(tp.getElementNameRegex()).append(optAttr).append(slashAfter).append(">");
        });

        // REGEX
        cqlSerializers.put(TextPatternRegex.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
                    (parenthesize, brackets) -> {
                handleRegexOrTerm(pattern, b, brackets, false);
            });
        });

        // TERM
        cqlSerializers.put(TextPatternTerm.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
                    (parenthesize, brackets) -> {
                        handleRegexOrTerm(pattern, b, brackets, false);
                    });
        });

        // TextPattern compare
        cqlSerializers.put(TextPatternCompare.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            TextPatternCompare tp = (TextPatternCompare) pattern;
            TextPattern left = tp.getLeftClause();
            if (left instanceof TextPatternDefaultValue) {
                // Special case: a top-level string in BCQL is comparing with the default annotation
                // (i.e. "cow" means [word="cow"])
                if (tp.operator == MatchFilterCompare.Operator.EQUAL && tp.getRightClause() instanceof TextPatternValue tpv &&
                        tpv.getValue() instanceof ConstraintValueString cvs) {
                    handleRegexOrTerm(new TextPatternRegex(cvs.getValue()), b, insideTokenBrackets, false);
                } else {
                    throw new UnsupportedOperationException("TextPatternCompare with default annotation is only allowed with = and a string value");
                }
            } else {
                serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
                        (parenthesize, brackets) -> {
                            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " " + tp.getOperator() + " ",
                                    List.of(tp.getLeftClause(), tp.getRightClause()));
                        });
            }
        });

        // TextPattern implication
        cqlSerializers.put(TextPatternImplication.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
                    (parenthesize, brackets) -> {
                        TextPatternImplication tp = (TextPatternImplication) pattern;
                        infix(b, parenthesizeIfNecessary, insideTokenBrackets, " -> ", List.of(tp.getAntecedent(), tp.getConsequent()));
                    });
        });

        // TextPattern value
        cqlSerializers.put(TextPatternValue.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            TextPatternValue tp = (TextPatternValue) pattern;
            serializeConstraintValue(b, tp.getValue());
        });

        // TextPattern token annotation
        cqlSerializers.put(TextPatternPropertySelect.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            TextPatternPropertySelect tp = (TextPatternPropertySelect) pattern;
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, ".", List.of(tp.getLabel(), tp.getAnnotation()));
        });
    }

    // Longer serializers below

    interface NodeSerializerBrackets {
        void serialize(boolean parenthesizeIfNecessary, boolean insideTokenBrackets);
    }

    private static void serializeOptBrackets(TextPatternStruct pattern, StringBuilder b,
            boolean parenthesizeIfNecessary, boolean insideTokenBrackets, NodeSerializerBrackets serializer) {
        if (pattern.isBracketQuery() && !insideTokenBrackets) {
            b.append("[");
            serializer.serialize(false, true);
            b.append("]");
        } else {
            serializer.serialize(parenthesizeIfNecessary, insideTokenBrackets);
        }
    }

    private static void serializePosFilter(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        if (insideTokenBrackets)
            throw new UnsupportedOperationException("Cannot serialize TextPatternPositionFilter inside brackets to CQL");
        TextPatternPositionFilter tp = (TextPatternPositionFilter) pattern;
        boolean supportedOp = tp.getOperation() == SpanQueryPositionFilter.Operation.WITHIN ||
                tp.getOperation() == SpanQueryPositionFilter.Operation.CONTAINING;
        if (tp.getAdjustLeading() != 0 || tp.getAdjustTrailing() != 0 || tp.isInvert() || !supportedOp)
            throw new IllegalArgumentException(
                    "Cannot serialize to CorpusQL: posfilter with adjustLeading " + tp.getAdjustLeading() +
                            ", adjustTrailing " + tp.getAdjustTrailing() + ", invert " + tp.isInvert() +
                            ", operation " + tp.getOperation() +
                            " (only supports unadjusted, uninverted within/containing))");
        infix(b, parenthesizeIfNecessary, insideTokenBrackets, " " + tp.getOperation() + " ",
                List.of(tp.getProducer(), tp.getFilter()));
    }

    private static void serializeOverlapping(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        if (insideTokenBrackets)
            throw new UnsupportedOperationException("Cannot serialize TextPatternOverlapping inside brackets to CQL");
        TextPatternOverlapping tp = (TextPatternOverlapping) pattern;
        boolean supportedOp = tp.getOperation().toUpperCase().equals("OVERLAP");
        if (!supportedOp)
            throw new IllegalArgumentException(
                    "Cannot serialize to CorpusQL: TextPatternOverlapping with operation " + tp.getOperation());
        infix(b, parenthesizeIfNecessary, insideTokenBrackets, " " + tp.getOperation().toLowerCase() + " ",
                List.of(tp.getLeft(), tp.getRight()));
    }

    private static void serializeFuncCall(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
//        if (insideTokenBrackets)
//            throw new UnsupportedOperationException("Cannot serialize TextPatternQueryFunction inside brackets to CQL");
        TextPatternFunctionCall tp = (TextPatternFunctionCall) pattern;
        b.append(tp.getName()).append("(");
        boolean first = true;
        for (Object arg: tp.getArgs()) {
            if (!first)
                b.append(", ");
            first = false;
            if (arg instanceof TextPattern) {
                serialize((TextPattern) arg, b, false, insideTokenBrackets);
            } else if (arg instanceof String) {
                serializeToQuotedString(b, (String) arg);
            } else if (arg instanceof Integer) {
                b.append((int) arg);
            } else {
                b.append(arg);
            }
        }
        b.append(")");
    }

    private static void serializeExpansion(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        if (insideTokenBrackets)
            throw new UnsupportedOperationException("Cannot serialize TextPatternExpansion inside brackets to CQL");
        TextPatternExpansion tp = (TextPatternExpansion) pattern;
        String any = "[]" + repetitionOperator(tp.getMin(), tp.getMax());
        StringBuilder cl = new StringBuilder();
        serialize(tp.getClause(), cl, true, insideTokenBrackets);
        List<CharSequence> strCl = tp.isExpandToLeft() ? List.of(any, cl) : List.of(cl, any);
        if (parenthesizeIfNecessary)
            b.append("(");
        b.append(StringUtils.join(strCl, " "));
        if (parenthesizeIfNecessary)
            b.append(")");
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

    /** Use double quotes for CQL */
    private static final String USE_QUOTE = "\"";

    private static StringBuilder serializeConstraintValue(StringBuilder b, ConstraintValue cv) {
        if (cv instanceof ConstraintValueString s)
            return serializeToQuotedString(b, s.getValue());
        else if (cv instanceof ConstraintValueSymbol cvs)
            return b.append(cvs.getValue());
        else if (cv instanceof ConstraintValueIntRange cvir)
            return b.append("in[").append(cvir.getMin()).append(",").append(cvir.getMax()).append("]");
        else
            return b.append(cv.getValue().toString());
    }

    private static StringBuilder serializeToQuotedString(StringBuilder b, String value) {
        return b.append(USE_QUOTE).append(StringUtil.escapeQuote(value, USE_QUOTE)).append(USE_QUOTE);
    }

    private static String serializeAttributes(Map<String, TextPattern> attr) {
        return attr.entrySet().stream()
                .map(e -> {
                    return e.getKey() + "=" + serialize(e.getValue());
                })
                .collect(Collectors.joining(" "));
    }

    private static void infix(StringBuilder b, boolean parenthesize, boolean insideTokenBrackets, String operator,
            List<? extends TextPatternStruct> clauses) {
        if (parenthesize)
            b.append("(");
        boolean first = true;
        boolean isConstrainOperator = operator.matches("\\s*::\\s*");
        for (TextPatternStruct clause: clauses) {
            if (!first)
                b.append(operator);

            // never add [brackets] to the constraint on the right side of ::
            boolean isConstraint = isConstrainOperator && !first;

            serialize(clause, b, true, insideTokenBrackets || isConstraint);
            first = false;
        }
        if (parenthesize)
            b.append(")");
    }
}
