package nl.inl.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class XPathUtil {
    /*
     * NOTE: leaves start as-is, but strips ./ if needed (//a/b will work)
     * handles nulls
     */
    public static String joinXpath(String a, String b) {

        // 	a 			-> a
        //	/a			-> a
        // 	./a			-> a
        // 	//a 		-> //a
        // 	.//a		-> //a

        // 	b			-> /b
        // 	/b			-> /b
        // 	./b			-> /b
        // 	//b			-> //b
        // .//b			-> //b

        // return a+/+b

        // strip any leading . (since it's implicit)
        a = normalizeXpath(a);
        b = normalizeXpath(b);

        // split and explode into cartesian product...
        // a|b c -> a/c | b/c
        // because a/(b|c) is not valid xpath
        String[] asplit = StringUtils.split(a, "|");
        String[] bsplit = StringUtils.split(b, "|");
        List<String> result = new ArrayList<>();
        if (asplit != null && asplit.length > 0) {
            for (String _a: asplit) {
                if (bsplit != null && bsplit.length > 0) {
                    for (String _b: bsplit) {
                        if (_b.startsWith("/"))
                            result.add(StringUtils.join(_a, _b));
                        else
                            result.add(StringUtils.join(new String[] { _a, _b }, '/'));
                    }
                } else {
                    result.add(_a);
                }
            }
        } else if (bsplit != null && bsplit.length > 0) {
            Collections.addAll(result, bsplit);
        }

        String joined = StringUtils.join(result, "|");
        if (joined.isEmpty())
            return ".";

        return joined;
    }

    // Strip leading and trailing (./)
    // leading // is preserved
    // normalizeXpath(null) -> null
    public static String normalizeXpath(String xpath) {
        xpath = StringUtils.stripStart(xpath, ".");
        if (!StringUtils.startsWith(xpath, "//"))
            xpath = StringUtils.stripStart(xpath, "/");

        xpath = StringUtils.stripEnd(xpath, "./");
        return xpath;
    }

    public static String joinXpath(String... strings) {
        // accumulate over all strings
        String result = null;
        for (String s: strings)
            result = joinXpath(result, s);

        return result;
    }
}
