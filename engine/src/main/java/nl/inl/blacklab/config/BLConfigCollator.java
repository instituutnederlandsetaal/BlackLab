package nl.inl.blacklab.config;

import java.text.Collator;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidConfiguration;

public class BLConfigCollator {
    String language = "en";
    
    String country = "";
    
    String variant = "";

    private Collator collator = null;

    @SuppressWarnings("unused")
    public synchronized void setLanguage(String language) {
        this.language = language;
        collator = null;
    }

    @SuppressWarnings("unused")
    public synchronized void setCountry(String country) {
        this.country = country;
        collator = null;
    }

    @SuppressWarnings("unused")
    public synchronized void setVariant(String variant) {
        this.variant = variant;
        collator = null;
    }

    public synchronized Collator get() {
        if (collator == null) {
            if (StringUtils.isEmpty(language))
                throw new InvalidConfiguration(
                        "If you wish to customize the collator, you must at least set collator.language in blacklab-server.yaml.");
            collator = Collator.getInstance(new Locale(language, country, variant));
        }
        return collator;
    }
}
