package nl.inl.blacklab.search.textpattern;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;

/**
 * Describes a relation operator, e.g. "-nmod->" or "==>".
 *
 * Include the relation type filter, relation direction (forward/backward, usually both;
 * direction also determines if it's a root relation operator), and whether it's a negation.
 *
 * For parallel corpora, it also includes source and target version (for parallel corpora, e.g. source
 * might be 'en' and target might be 'nl' to search from English to Dutch), and whether it's an
 * alignment operator ("find all relations between matches of these two queries") or a regular relation
 * operator ("find one matching relation").
 */
public class RelationOperatorInfo {

    /**
     * Relation or alignment operator, with optional source/target version and relation type regex.
     * Examples: --> ; -nmod-> ; nl==>de
     */
    private static final Pattern PATT_RELATION_OPERATOR = Pattern.compile(
            "^[-=](.*)[-=]>([a-zA-Z0-9_-]*)$");

    /**
     * Create an info struct from the operator string.
     *
     * We look at whether it's a root relation operator, a negated relation operator,
     * an alignment operator, the relation type regex and target version.
     *
     * Get the relation type and target version regexes from the operator.
     * NOTE: if the operator started with ! or ^, this character must have been removed already!
     * If no type was specified, the type will be ".*" (any relation type). If no target version was specified,
     * the target version will be the empty string (any target version or no target version).
     *
     * @return relation type and target version (intepret both as regexes)
     */
    public static RelationOperatorInfo fromOperator(String op) {
        // If relation operator ends with ?, hits on the left are included even if
        // there's no match on the right. Right now only available for alignment operators (parallel corpora).
        boolean optionalMatch = op.endsWith("?");
        if (optionalMatch)
            op = op.substring(0, op.length() - 1);

        // Root operator?
        // (this determines the relation directions we allow; direction is usually both (i.e. forward and backward),
        //  but root relations have a special "direction" because they have no source, so using that we ensure we'll
        //  only find root relations)
        boolean isRoot = op.charAt(0) == '^';
        if (isRoot)
            op = op.substring(1);
        SpanQueryRelations.Direction direction = isRoot ? SpanQueryRelations.Direction.ROOT :
                SpanQueryRelations.Direction.BOTH_DIRECTIONS;

        // Alignment operator? E.g. ==> instead of -->
        // (difference: ==> captures all relations between (part of) source and target spans, e.g. for parallel corpora;
        //              --> captures a single relation from source to target)
        boolean isAlignmentOperator = op.contains("=>");
        if (isRoot && isAlignmentOperator)
            throw new InvalidQuery("Root relation operator cannot be an alignment operator");

        // Negated?
        // (i.e. no child exists conforming to this filter)
        boolean negate = false;
        if (op.charAt(0) == '!') {
            if (isRoot)
                throw new InvalidQuery("Root relation operator cannot be negated");
            negate = true;
            op = op.substring(1);
        }

        // Now find relation type filter regex, as well as (optional) source and target version
        // (used for parallel corpora)
        Matcher matcher = PATT_RELATION_OPERATOR.matcher(op);
        if (!matcher.matches())
            throw new InvalidQuery("Invalid relation operator: " + op);
        String typeRegex = matcher.group(1);
        String targetVersion = matcher.group(2);
        if (StringUtils.isEmpty(typeRegex))
            typeRegex = RelationUtil.ANY_TYPE_REGEX; // any relation type

        return new RelationOperatorInfo(typeRegex, direction, targetVersion, negate,
                isAlignmentOperator, optionalMatch);
    }

    /** Relation type regex. */
    private final String typeRegex;

    /** How to filter relations by direction (forward/backward/both/root). */
    private final SpanQueryRelations.Direction direction;

    /** Relation target regex */
    private final String targetVersion;

    /** Is this a negated relation operator? E.g. !-nmod-> */
    private final boolean negate;

    /** Is this an alignment operator? E.g. ==>de ("find all alignment relations between spans on left and right",
     *  used for parallel corpora) */
    private final boolean isAlignmentOperator;

    /** Are hits on the left included even if there's no match on the right?
     *  Right now, this is only available for alignment operators (parallel corpora). */
    private final boolean optionalMatch;

    public RelationOperatorInfo(String typeRegex, SpanQueryRelations.Direction direction,
            String targetVersion, boolean negate,
            boolean isAlignmentOperator, boolean optionalMatch) {
        this.typeRegex = typeRegex;
        this.direction = direction;
        this.targetVersion = targetVersion == null || targetVersion.isEmpty() ? null : targetVersion;
        this.negate = negate;
        this.isAlignmentOperator = isAlignmentOperator;
        this.optionalMatch = optionalMatch;

        if (isAlignmentOperator && negate)
            throw new InvalidQuery("Alignment operator cannot be negated");
        if (optionalMatch && !isAlignmentOperator)
            throw new InvalidQuery("Optional match operator can only be used with alignment operators");
    }

    public String getTypeRegex() {
        return typeRegex;
    }

    public SpanQueryRelations.Direction getDirection() {
        return direction;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public boolean isNegate() {
        return negate;
    }

    public Boolean isAlignment() {
        return isAlignmentOperator;
    }

    public boolean isOptionalMatch() {
        return optionalMatch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RelationOperatorInfo that = (RelationOperatorInfo) o;
        return negate == that.negate && isAlignmentOperator == that.isAlignmentOperator
                && optionalMatch == that.optionalMatch && Objects.equals(typeRegex, that.typeRegex)
                && direction == that.direction && Objects.equals(targetVersion, that.targetVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeRegex, direction, targetVersion, negate, isAlignmentOperator, optionalMatch);
    }

    /**
     * Get the full relation type regex, optionally including the target version.
     *
     * @return the full relation type regex
     */
    public String getFullTypeRegex(QueryExecutionContext context) {
        // Make sure our type regex has a relation class
        String regex = RelationUtil.optPrependDefaultClass(typeRegex, context);
        if (targetVersion == null || targetVersion.isEmpty())
            return regex;
        String relationClass = RelationUtil.classFromFullType(regex);
        String relationType = RelationUtil.typeFromFullType(regex);
        // A target version was set. Target version must be added to or replaced in type regex.
        // Replace or add target version in relation class
        relationClass = AnnotatedFieldNameUtil.changeParallelFieldVersion(relationClass, targetVersion);
        return RelationUtil.fullTypeRegex(relationClass, relationType);
    }
}
