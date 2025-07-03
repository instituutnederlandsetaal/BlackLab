package nl.inl.blacklab.indexers.config.process;

import nl.inl.blacklab.index.DocIndexer;

/** Undocumented operation chatFormatAgeToMonths, specific to the CHAT format...
 *
 * Parse a value like "1;10.30" => 1 year, 10 months, 30 days => round to 23 months.
 *
 * (does anyone use this? likely not, DocIndexerChat doesn't)
 */
public class ProcessingStepChatAge extends ProcessingStep {

    public ProcessingStepChatAge() {
    }

    @Override
    public String performSingle(String value, DocIndexer docIndexer) {
        String[] parts = value.split("[;.]", 3);
        int years = 0;
        int months = 0;
        int days = 0;
        try {
            years = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            // ignore
        }
        try {
            months = parts.length <= 1 ? 0 : Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            // ignore
        }
        try {
            days = parts.length <= 2 ? 0 : Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            // ignore
        }
        return Integer.toString(years * 12 + months + (days > 14 ? 1 : 0) );
    }

    @Override
    public boolean canProduceMultipleValues() {
        return false;
    }

    @Override
    public String toString() {
        return "chatFormatAgeToMonths()";
    }

}
