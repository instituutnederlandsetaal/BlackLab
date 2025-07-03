package nl.inl.blacklab.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * Takes care of serializing/deserializing Hit/DocProperties and -Values with
 * proper escaping.
 */
public final class PropertySerializeUtil {

    private static final String PART_SEPARATOR = ":";

    private static final String PART_SEPARATOR_ESC_REGEX = ":";

    private static final String MULTIPLE_SEPARATOR = ",";

    private static final String MULTIPLE_SEPARATOR_ESC_REGEX = ",";

    private PropertySerializeUtil() {
    }

    /** Escape dollar, comma and colon */
    private static String escapePart(String part) {
        return part.replace("$", "$DL").replace(",", "$CM").replace(":", "$CL");
    }

    /** Unescape dollar, comma and colon */
    private static String unescapePart(String partEscaped) {
        return partEscaped.replace("$CL", ":").replace("$CM", ",").replace("$DL", "$");
    }

    public static String combineParts(String... parts) {
        return Arrays.stream(parts).map(PropertySerializeUtil::escapePart).collect(Collectors.joining(PART_SEPARATOR));
    }

    public static String combineParts(List<String> parts) {
        return combineParts(parts.toArray(new String[0]));
    }

    public static List<String> splitPartsList(String partsCombined) {
        return Arrays.stream(partsCombined.split(PART_SEPARATOR_ESC_REGEX, -1))
                .map(PropertySerializeUtil::unescapePart)
                .collect(Collectors.toList());
    }

    public static String combineMultiple(String... values) {
        return StringUtils.join(values, MULTIPLE_SEPARATOR);
    }

    public static String[] splitMultiple(String valueCombined) {
        return valueCombined.split(MULTIPLE_SEPARATOR_ESC_REGEX, -1);
    }

    public static boolean isMultiple(String serialized) {
        return serialized.contains(PropertySerializeUtil.MULTIPLE_SEPARATOR);
    }

    public interface SerializableProperty {
        String serialize();
    }

    public static String serializeMultiple(boolean reverse, List<? extends SerializableProperty> properties) {
        String[] values = new String[properties.size()];
        for (int i = 0; i < properties.size(); i++) {
            values[i] = properties.get(i).serialize();
        }
        return (reverse ? "-(" : "") + combineMultiple(values) + (reverse ? ")" : "");
    }
}
