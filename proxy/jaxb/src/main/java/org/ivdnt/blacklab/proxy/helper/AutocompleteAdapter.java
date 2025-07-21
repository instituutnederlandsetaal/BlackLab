package org.ivdnt.blacklab.proxy.helper;

import java.util.ArrayList;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import org.ivdnt.blacklab.proxy.representation.AutocompleteResponse;

/**
 * Helps us to (de)serialize autocomplete response in XML.
 */
public class AutocompleteAdapter extends XmlAdapter<ArrayList<String>, AutocompleteResponse> {

    @Override
    public ArrayList<String> marshal(AutocompleteResponse m) {
        ArrayList<String> wrapper = new ArrayList<>();
        for (String term: m.terms) {
            wrapper.add(term);
        }
        return wrapper;
    }

    @Override
    public AutocompleteResponse unmarshal(ArrayList<String> terms) {
        AutocompleteResponse returnval = new AutocompleteResponse();
        returnval.terms = new ArrayList<>();
        for (String term: terms) {
            returnval.terms.add(term);
        }
        return returnval;
    }
}
