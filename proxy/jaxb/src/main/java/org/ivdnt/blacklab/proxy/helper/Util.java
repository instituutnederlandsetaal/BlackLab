package org.ivdnt.blacklab.proxy.helper;

import java.util.Locale;

import com.ibm.icu.text.Collator;

public class Util {

    public static final Collator DEFAULT_COLLATOR;

    public static final Collator DEFAULT_COLLATOR_INSENSITIVE;

    static {
        DEFAULT_COLLATOR = Collator.getInstance(new Locale("nl", "NL"));
        DEFAULT_COLLATOR.setStrength(Collator.TERTIARY);
        DEFAULT_COLLATOR.freeze();

        DEFAULT_COLLATOR_INSENSITIVE = Collator.getInstance(new Locale("nl", "NL"));
        DEFAULT_COLLATOR_INSENSITIVE.setStrength(Collator.PRIMARY);
        DEFAULT_COLLATOR_INSENSITIVE.freeze();
    }
}
