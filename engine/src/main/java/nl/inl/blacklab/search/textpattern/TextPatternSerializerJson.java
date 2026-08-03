package nl.inl.blacklab.search.textpattern;

import static nl.inl.blacklab.search.textpattern.TextPattern.MAX_UNLIMITED;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanFilter;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueIntRange;
import nl.inl.blacklab.search.matchfilter.ConstraintValueSymbol;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.blacklab.search.matchfilter.TextPatternStruct;
import nl.inl.util.ObjectSerializationWriter;

/**
 * A Jackson serializer for TextPattern.
 * <p>
 * Used to convert TextPattern to a JSON structure.
 */
public class TextPatternSerializerJson extends JsonSerializer<TextPatternStruct> {

    // Node types
    public static final String NT_AND = "and";
    public static final String NT_ANYTOKEN = "anytoken";
    public static final String NT_CAPTURE = "capture";
    public static final String NT_COMPARE = "compare";
    public static final String NT_CONSTRAINED = "constrained";
    public static final String NT_DEFVAL = "defval";
    public static final String NT_LOOK = "look";
    public static final String NT_EXPANSION = "expansion";
    public static final String NT_FILTERNGRAMS = "filterngrams";
    public static final String NT_FIXEDSPAN = "fixedspan";
    public static final String NT_FUZZY = "fuzzy";
    public static final String NT_IMPLICATION = "implication";
    public static final String NT_INT_RANGE = "intrange";
    public static final String NT_NOT = "not";
    public static final String NT_OR = "or";
    public static final String NT_POSFILTER = "posfilter";
    public static final String NT_OVERLAPPING = "overlapping";
    public static final String NT_PREFIX = "prefix";
    public static final String NT_CALL_FUNC = "callfunc";
    public static final String NT_REGEX = "regex";
    public static final String NT_RELATION_MATCH = "relmatch";
    public static final String NT_RELATION_TARGET = "reltarget";
    public static final String NT_REPEAT = "repeat";
    public static final String NT_SENSITIVITY = "sensitivity";
    public static final String NT_SEQUENCE = "sequence";
    public static final String NT_SETTINGS = "settings";
    public static final String NT_TAGS = "tags";
    public static final String NT_TERM = "term";
    public static final String NT_PROP_SELECT = "prop-selector";
    public static final String NT_VALUE_STRING = "string";
    public static final String NT_VALUE_BOOLEAN = "boolean";
    public static final String NT_VALUE_INTEGER = "integer";
    public static final String NT_VALUE_INT_RANGE = "int-range";
    public static final String NT_VALUE_SYMBOL = "symbol";
    public static final String NT_VALUE_UNDEFINED = "undefined";
    public static final String NT_WILDCARD = "wildcard";

    @Override
    public void serialize(TextPatternStruct pattern, JsonGenerator gen, SerializerProvider serializerProvider) {
        serialize(pattern, (type, args) -> {
            try {
                gen.writeStartObject();
                {
                    gen.writeStringField("bcqlFragment", TextPatternSerializerBcql.serialize(pattern));
                    gen.writeStringField("type", type);
                    Map<String, Object> map = ObjectSerializationWriter.mapFromArgs(args);
                    for (Map.Entry<String, Object> e: map.entrySet()) {
                        Object value = e.getValue();
                        if (value != null) {
                            gen.writeFieldName(e.getKey());
                            serializerProvider.defaultSerializeValue(value, gen);
                        }
                    }
                }
                gen.writeEndObject();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    interface NodeSerializer {
        void serialize(TextPatternStruct pattern, ObjectSerializationWriter writer);
    }

    private static final Map<Class<? extends TextPatternStruct>, NodeSerializer> jsonSerializers = new LinkedHashMap<>();

    // Keys used in serialization
    private static final String KEY_ADJUST = "adjust";
    private static final String KEY_ADJUST_LEADING = "adjustLeading";
    private static final String KEY_ADJUST_TRAILING = "adjustTrailing";
    private static final String KEY_ALIGNMENT = "alignment";
    private static final String KEY_ANNOTATION = "annotation";
    private static final String KEY_ARGS = "args";
    public static final String KEY_ATTRIBUTES = "attributes";
    private static final String KEY_CAPTURE = "capture"; // capture, tags
    private static final String KEY_CHILDREN = "children";
    private static final String KEY_CLAUSE = "clause";
    private static final String KEY_CLAUSES = "clauses";
    private static final String KEY_CONSTRAINT = "constraint";
    private static final String KEY_DIRECTION = "direction";
    private static final String KEY_END = "end";
    private static final String KEY_FILTER = "filter";
    private static final String KEY_INVERT = "invert";
    public static final String KEY_MAX = "max"; // (same)
    public static final String KEY_MIN = "min"; // repeat, ngrams, anytoken
    private static final String KEY_NAME = "name"; // annotation, function
    private static final String KEY_NEGATE = "negate";
    private static final String KEY_OPERATION = "operation"; // posfilter, ngrams
    private static final String KEY_OPTIONAL = "optional"; // relation target
    private static final String KEY_PARENT = "parent";
    private static final String KEY_PRODUCER = "producer";
    private static final String KEY_REL_SPAN_MODE = "spanMode";
    private static final String KEY_REL_TYPE = "relType";
    private static final String KEY_SENSITIVITY = "sensitivity";
    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_START = "start";
    private static final String KEY_TARGET_VERSION = "targetVersion";
    private static final String KEY_WHERE = "where";
    private static final String KEY_VALUE = "value"; // term, regex, etc.

    static {
        // For each node type, add a CQL serializer to the map.

        // AND
        jsonSerializers.put(TextPatternAnd.class, (pattern, writer) -> {
            writer.write(NT_AND, KEY_CLAUSES, ((TextPatternAnd)pattern).getClauses());
        });

        // Anytoken
        jsonSerializers.put(TextPatternAnyToken.class, (pattern, writer) -> {
            TextPatternAnyToken tp = (TextPatternAnyToken) pattern;
            writer.write(NT_ANYTOKEN, KEY_MIN, tp.getMin(), KEY_MAX, nullIfUnlimited(tp.getMax()));
        });

        // Capture
        jsonSerializers.put(TextPatternCaptureGroup.class, (pattern, writer) -> {
            TextPatternCaptureGroup tp = (TextPatternCaptureGroup) pattern;
            writer.write(NT_CAPTURE, KEY_CLAUSE, tp.getClause(), KEY_CAPTURE, tp.getCaptureName());
        });

        // Constrained
        jsonSerializers.put(TextPatternConstrained.class, (pattern, writer) -> {
            TextPatternConstrained tp = (TextPatternConstrained) pattern;
            writer.write(NT_CONSTRAINED, KEY_CLAUSE, tp.getClause(), KEY_CONSTRAINT, tp.getConstraint());
        });

        // Default value
        jsonSerializers.put(TextPatternDefaultValue.class, (pattern, writer) -> {
            writer.write(NT_DEFVAL);
        });

        // Lookahead/lookbehind
        jsonSerializers.put(TextPatternLook.class, (pattern, writer) -> {
            TextPatternLook tp = (TextPatternLook) pattern;
            writer.write(NT_LOOK,
                KEY_WHERE, tp.isLookBehind() ? "behind" : "ahead",
                    KEY_NEGATE, tp.isNegate(),
                    KEY_CLAUSE, tp.getClause());
        });

        // Not
        jsonSerializers.put(TextPatternNot.class, (pattern, writer) -> {
            writer.write(NT_NOT, KEY_CLAUSE, ((TextPatternNot) pattern).getClause());
        });

        // Or
        jsonSerializers.put(TextPatternOr.class, (pattern, writer) -> {
            writer.write(NT_OR, KEY_CLAUSES, ((TextPatternOr) pattern).getClauses());
        });

        // PositionFilter
        jsonSerializers.put(TextPatternPositionFilter.class, (pattern, writer) -> {
            TextPatternPositionFilter tp = (TextPatternPositionFilter) pattern;
            writer.write(NT_POSFILTER,
                    KEY_PRODUCER, tp.getProducer(),
                    KEY_FILTER, tp.getFilter(),
                    KEY_OPERATION, tp.getOperation().toString(),
                    KEY_INVERT, nullIf(tp.isInvert(), false));
        });

        // Overlapping
        jsonSerializers.put(TextPatternOverlapping.class, (pattern, writer) -> {
            TextPatternOverlapping tp = (TextPatternOverlapping) pattern;
            writer.write(NT_OVERLAPPING,
                    KEY_CLAUSES, Arrays.asList(tp.getLeft(), tp.getRight()),
                    KEY_OPERATION, tp.getOperation());
        });

        // QueryFunction
        jsonSerializers.put(TextPatternFunctionCall.class, (pattern, writer) -> {
            TextPatternFunctionCall tp = (TextPatternFunctionCall) pattern;
            writer.write(NT_CALL_FUNC,KEY_NAME, tp.getFunctionName(), KEY_ARGS, tp.getArgs());
        });

        // Regex
        jsonSerializers.put(TextPatternRegex.class, (pattern, writer) -> {
            TextPatternRegex tp = (TextPatternRegex) pattern;
            writer.write(NT_REGEX,
                    KEY_VALUE, tp.getValue(),
                    KEY_ANNOTATION, tp.getAnnotation(),    // (omitted if null)
                    KEY_SENSITIVITY, sensitivity(tp.getSensitivity())); // (omitted if null)
        });

        // Relation match
        jsonSerializers.put(TextPatternRelationMatch.class, (pattern, writer) -> {
            TextPatternRelationMatch tp = (TextPatternRelationMatch) pattern;
            writer.write(NT_RELATION_MATCH,
                    KEY_PARENT, tp.getParent(),
                    KEY_CHILDREN, tp.getChildren());
        });

        // Relation target
        jsonSerializers.put(RelationTarget.class, (pattern, writer) -> {
            RelationTarget tp = (RelationTarget) pattern;
            RelationOperatorInfo operatorInfo = tp.getOperatorInfo();
            writer.write(NT_RELATION_TARGET,
                    KEY_REL_TYPE, operatorInfo.getTypeRegex(),
                    KEY_CLAUSE, tp.getTarget(),
                    KEY_NEGATE, nullIf(operatorInfo.isNegate(), false),
                    KEY_REL_SPAN_MODE, nullIf(tp.getSpanMode().getCode(), "source"),
                    KEY_DIRECTION, nullIf(operatorInfo.getDirection().getCode(), "both"),
                    KEY_CAPTURE, nullIfEmpty(tp.getCaptureAs()),
                    KEY_TARGET_VERSION, nullIfEmpty(operatorInfo.getTargetVersion()),
                    KEY_ALIGNMENT, nullIf(operatorInfo.isAlignment(), false),
                    KEY_OPTIONAL, nullIf(operatorInfo.isOptionalMatch(), false));
        });

        // Repetition
        jsonSerializers.put(TextPatternRepetition.class, (pattern, writer) -> {
            TextPatternRepetition tp = (TextPatternRepetition) pattern;
            writer.write(NT_REPEAT,
                    KEY_CLAUSE, tp.getClause(),
                    KEY_MIN, tp.getMin(),
                    KEY_MAX, nullIfUnlimited(tp.getMax()));
        });

        // Sequence
        jsonSerializers.put(TextPatternSequence.class, (pattern, writer) -> {
            writer.write(NT_SEQUENCE, KEY_CLAUSES, ((TextPatternSequence) pattern).getClauses());
        });

        // Settings
        jsonSerializers.put(TextPatternSettings.class, (pattern, writer) -> {
            writer.write(NT_SETTINGS,
                    KEY_CLAUSE, ((TextPatternSettings) pattern).getClause(),
                    KEY_SETTINGS, ((TextPatternSettings) pattern).getSettings());
        });

        // Tags
        jsonSerializers.put(TextPatternTags.class, (pattern, writer) -> {
            TextPatternTags tp = (TextPatternTags) pattern;
            writer.write(NT_TAGS,
                    KEY_NAME, tp.getElementNameRegex(),
                    KEY_ATTRIBUTES, nullIfEmpty(tp.getAttributes()),
                    KEY_ADJUST, nullIf(tp.getAdjust().toString(), "full_tag"),
                    KEY_CAPTURE, nullIfEmpty(tp.getCaptureAs()));
        });

        // Term
        jsonSerializers.put(TextPatternTerm.class, (pattern, writer) -> {
            TextPatternTerm tp = (TextPatternTerm) pattern;
            writer.write(NT_TERM,
                    KEY_VALUE, tp.getValue(),
                    KEY_ANNOTATION, tp.getAnnotation(),    // (omitted if null)
                    KEY_SENSITIVITY, sensitivity(tp.getSensitivity())); // (omitted if null)
        });

        // TextPatternCompare
        jsonSerializers.put(TextPatternCompare.class, (pattern, writer) -> {
            TextPatternCompare tp = (TextPatternCompare) pattern;
            writer.write(NT_COMPARE,
                    KEY_CLAUSES, List.of(tp.getLeftClause(), tp.getRightClause()),
                    KEY_OPERATION, tp.getOperator().toString());
        });

        // TextPatternImplication
        jsonSerializers.put(TextPatternImplication.class, (pattern, writer) -> {
            TextPatternImplication tp = (TextPatternImplication) pattern;
            writer.write(NT_IMPLICATION, KEY_CLAUSES, tp.getClauses());
        });

        // TextPatternValue
        jsonSerializers.put(TextPatternValue.class, (pattern, writer) -> {
            TextPatternValue tp = (TextPatternValue) pattern;
            ConstraintValue cv = tp.getValue();
            switch (cv.getType()) {
            case STRING -> writer.write(NT_VALUE_STRING, KEY_VALUE, cv.getValue());
            case BOOLEAN -> writer.write(NT_VALUE_BOOLEAN, KEY_VALUE, cv.getValue());
            case INTEGER -> writer.write(NT_VALUE_INTEGER, KEY_VALUE, cv.getValue());
            case INT_RANGE -> {
                ConstraintValueIntRange cvir = (ConstraintValueIntRange) cv;
                writer.write(NT_VALUE_INT_RANGE, KEY_MIN, cvir.getMin(), KEY_MAX, cvir.getMax());
            }
            case SYMBOL -> writer.write(NT_VALUE_SYMBOL, KEY_VALUE, ((ConstraintValueSymbol)cv).getValue());
            case UNDEFINED -> writer.write(NT_VALUE_UNDEFINED);
            default -> throw new UnsupportedOperationException(
                    "Cannot serialize ConstraintValue of type: " + cv.getClass().getName());
            }
        });

        // TextPatternTokenAnnotation
        jsonSerializers.put(TextPatternPropertySelect.class, (pattern, writer) -> {
            TextPatternPropertySelect tp = (TextPatternPropertySelect) pattern;
            writer.write(NT_PROP_SELECT,
                    KEY_CAPTURE, tp.getLabel(),
                    KEY_ANNOTATION, tp.getAnnotation());
        });
    }

    private static String sensitivity(MatchSensitivity sensitivity) {
        if (sensitivity == null)
            return null;
        return sensitivity.luceneFieldSuffix();
    }

    private static String nullIfEmpty(String str) {
        return str == null || str.isEmpty() ? null : str;
    }

    private static <K,V> Map<K, V> nullIfEmpty(Map<K, V> attributes) {
        return attributes.isEmpty() ? null : attributes;
    }

    private static <T> T nullIf(T max, T value) {
        return max.equals(value) ? null : max;
    }

    private static Integer nullIfUnlimited(int max) {
        return max == MAX_UNLIMITED ? null : max;
    }

    public static void serialize(TextPatternStruct pattern, ObjectSerializationWriter writer) {
        NodeSerializer serializer = jsonSerializers.get(pattern.getClass());
        if (serializer == null)
            throw new UnsupportedOperationException("Unable to serialize TextPattern type: " + pattern.getClass().getName());
        serializer.serialize(pattern, writer);
    }

    public static TextPatternStruct deserialize(String nodeType, Map<String, Object> args) {
        switch (nodeType) {
        case NT_AND:
            return new TextPatternAnd((List<TextPattern>) args.get(KEY_CLAUSES));
        case NT_ANYTOKEN:
            return new TextPatternAnyToken((int)args.get(KEY_MIN), (int)args.getOrDefault(KEY_MAX, MAX_UNLIMITED));
        case NT_CAPTURE:
            return new TextPatternCaptureGroup(
                    (TextPattern) args.get(KEY_CLAUSE),
                    (String) args.get(KEY_CAPTURE));
        case NT_COMPARE: {
            List<TextPattern> cl = (List<TextPattern>) args.get(KEY_CLAUSES);
            return new TextPatternCompare(
                    cl.get(0),
                    cl.get(1),
                    MatchFilterCompare.Operator.fromSymbol((String) args.get(KEY_OPERATION))
            );
        }
        case NT_CONSTRAINED:
            return new TextPatternConstrained(
                    (TextPattern) args.get(KEY_CLAUSE),
                    (TextPattern) args.get(KEY_CONSTRAINT));
        case NT_DEFVAL:
            return TextPatternDefaultValue.get();
        case NT_LOOK:
            return new TextPatternLook(
                    (TextPattern) args.get(KEY_CLAUSE),
                    !((boolean) args.getOrDefault(KEY_WHERE, "ahead").equals("behind")),
                    (boolean)args.getOrDefault(KEY_NEGATE, false));
        case NT_FUZZY:
            throw new UnsupportedOperationException("Cannot deserialize deprecated TextPatternFuzzy");
        case NT_IMPLICATION: {
            List<TextPattern> cl = (List<TextPattern>) args.get(KEY_CLAUSES);
            return new TextPatternImplication(cl.get(0), cl.get(1));
        }
        case NT_NOT:
            return new TextPatternNot((TextPattern) args.get(KEY_CLAUSE));
        case NT_OR:
            return new TextPatternOr((List<TextPattern>) args.get(KEY_CLAUSES));
        case NT_POSFILTER:
            return new TextPatternPositionFilter(
                    (TextPattern) args.get(KEY_PRODUCER),
                    (TextPattern) args.get(KEY_FILTER),
                    SpanFilter.fromStringValue((String)args.get(KEY_OPERATION)),
                    (boolean) args.getOrDefault(KEY_INVERT, false));
        case NT_OVERLAPPING: {
            List<TextPattern> clauses = (List<TextPattern>)args.get(KEY_CLAUSES);
            return new TextPatternOverlapping(
                    clauses.get(0),
                    clauses.get(1),
                    (String) args.get(KEY_OPERATION)
            );
        }
        case NT_CALL_FUNC:
            return new TextPatternFunctionCall(
                    (String) args.get(KEY_NAME),
                    (List<TextPattern>) args.get(KEY_ARGS));
        case NT_REGEX:
            return TextPattern.regex(
                    (String) args.get(KEY_VALUE),
                    (String) args.get(KEY_ANNOTATION),
                    optArgSensitivity(args));
        case NT_RELATION_MATCH:
            return new TextPatternRelationMatch(
                    (TextPattern) args.get(KEY_PARENT),
                    (List<RelationTarget>) args.get(KEY_CHILDREN));
        case NT_RELATION_TARGET:
            RelationOperatorInfo relOpInfo = new RelationOperatorInfo(
                    (String) args.get(KEY_REL_TYPE),
                    SpanQueryRelations.Direction.fromCode((String)args.getOrDefault(KEY_DIRECTION, "both")),
                    (String) args.get(KEY_TARGET_VERSION),
                    (boolean) args.getOrDefault(KEY_NEGATE, false),
                    (boolean) args.getOrDefault(KEY_ALIGNMENT, false),
                    (boolean) args.getOrDefault(KEY_OPTIONAL, false));
            return new RelationTarget(
                    relOpInfo,
                    (TextPattern) args.get(KEY_CLAUSE),
                    RelationInfo.SpanMode.fromCode((String)args.getOrDefault(KEY_REL_SPAN_MODE, "source")),
                    (String) args.get(KEY_CAPTURE));
        case NT_REPEAT:
            return TextPatternRepetition.get(
                    (TextPattern) args.get(KEY_CLAUSE),
                    (int) args.get(KEY_MIN),
                    (int) args.getOrDefault(KEY_MAX, MAX_UNLIMITED));
        case NT_SENSITIVITY:
            throw new UnsupportedOperationException("Cannot deserialize deprecated TextPatternSensitive");
        case NT_SEQUENCE:
            return new TextPatternSequence((List<TextPattern>) args.get(KEY_CLAUSES));
        case NT_TAGS:
            return new TextPatternTags(
                    (String) args.get(KEY_NAME),
                     (Map<String, TextPattern>)args.get(KEY_ATTRIBUTES),
                    optArgAdjust(args),
                    (String) args.get(KEY_CAPTURE));
        case NT_TERM:
            return new TextPatternTerm(
                    (String) args.get(KEY_VALUE),
                    (String) args.get(KEY_ANNOTATION),
                    optArgSensitivity(args));
        case NT_VALUE_STRING:
            return TextPatternValue.fromObject(args.get(KEY_VALUE).toString());
        case NT_VALUE_BOOLEAN:
            return TextPatternValue.fromObject(args.get(KEY_VALUE));
        case NT_VALUE_INTEGER:
            return TextPatternValue.fromObject(args.get(KEY_VALUE));
        case NT_VALUE_INT_RANGE:
            return new TextPatternValue(new ConstraintValueIntRange(
                    (int) args.get(KEY_MIN),
                    (int) args.get(KEY_MAX)));
        case NT_VALUE_SYMBOL:
            return new TextPatternValue(ConstraintValue.symbol((String) args.get(KEY_VALUE)));
        case NT_VALUE_UNDEFINED:
            return new TextPatternValue(ConstraintValue.undefined());
        case NT_WILDCARD:
            throw new UnsupportedOperationException("Cannot deserialize deprecated TextPatternWildcard");
        case NT_PROP_SELECT:
            return new TextPatternPropertySelect((TextPattern) args.get(KEY_CAPTURE),
                    (TextPattern) args.get(KEY_ANNOTATION));
        default:
            throw new UnsupportedOperationException("Unable to deserialize TextPattern type: " + nodeType);
        }
    }

    private static TextPatternTags.Adjust optArgAdjust(Map<String, Object> args) {
        String adjust = (String) args.get(KEY_ADJUST);
        return adjust == null ? null : TextPatternTags.Adjust.fromString(adjust);
    }

    private static MatchSensitivity optArgSensitivity(Map<String, Object> args) {
        String sensitivity = (String) args.get(KEY_SENSITIVITY);
        return sensitivity == null ? null : MatchSensitivity.fromLuceneFieldSuffix(sensitivity);
    }
}
