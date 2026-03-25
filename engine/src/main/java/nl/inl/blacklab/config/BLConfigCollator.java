package nl.inl.blacklab.config;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;

import nl.inl.blacklab.exceptions.InvalidConfiguration;

public class BLConfigCollator {
    String language = "en";
    
    String country = "";
    
    String variant = "";

    private Locale locale = null;

    private Collator collator = null;

    @SuppressWarnings("unused")
    public synchronized void setLanguage(String language) {
        this.language = language;
        collator = null;
        locale = null;
    }

    @SuppressWarnings("unused")
    public synchronized void setCountry(String country) {
        this.country = country;
        collator = null;
        locale = null;
    }

    @SuppressWarnings("unused")
    public synchronized void setVariant(String variant) {
        this.variant = variant;
        collator = null;
        locale = null;
    }

    public synchronized String getCountry() {
        return country;
    }

    public synchronized String getVariant() {
        return variant;
    }

    public synchronized String getLanguage() {
        return language;
    }

    public synchronized Collator get() {
        if (collator == null) {
            if (StringUtils.isEmpty(language))
                throw new InvalidConfiguration(
                        "If you wish to customize the collator, you must at least set collator.language in blacklab-server.yaml.");
            collator = Collator.getInstance(getLocale());
            if (collator instanceof RuleBasedCollator r) {
                try {
                    // Make sure we ignore punctuation
                    r.setAlternateHandlingShifted(true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            collator.freeze(); // make it thread-safe
        }
        return collator;
    }

    public synchronized Locale getLocale() {
        if (locale == null)
            locale = new Locale(country, language, variant);
        return locale;
    }
}
