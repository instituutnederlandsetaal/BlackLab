package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Make sure we can tell primary from secondary values using the payload.
 *
 * A primary value is the first value indexed at a token position
 * (e.g. tokenIncrement greater than zero, except for the first token
 * which is by definition the first value),
 *
 * This ensures that we can identify which value to store in the forward index.
 *
 * See PayloadUtils for how the encoding works. We only change the payload
 * if we absolutely have to; if you only have primary values, very few
 * payloads will have to be changed.
 *
 * When using the payloads later, you must know whether or not this annotation
 * passed through this filter or not, in order to be able to remove the
 * indicator if it's there.
 */
public class AddIsPrimaryValueToPayloadFilter extends TokenFilter {

    private final PositionIncrementAttribute posIncAtt;

    private final PayloadAttribute payloadAtt;

    private boolean first;

    /**
     * @param input the token stream to desensitize
     */
    public AddIsPrimaryValueToPayloadFilter(TokenStream input) {
        super(input);
        posIncAtt = addAttribute(PositionIncrementAttribute.class);
        payloadAtt = addAttribute(PayloadAttribute.class);
        first = true;
    }

    @Override
    public final boolean incrementToken() throws IOException {
        // clear previous payload.
        // If the token has a payload of its own, it will be set during input.incrementToken()
        // If we don't do this, and the input tokenStream doesn't contain payloads, we will continuously receive/reuse the previous payload we set
        payloadAtt.setPayload(null);
        if (input.incrementToken()) {
            // Is this a primary value (the first at this position) or a secondary one (any subsequent value
            // at the same position)?
            boolean isPrimary = first || posIncAtt.getPositionIncrement() > 0;
            payloadAtt.setPayload(PayloadUtils.addIsPrimary(isPrimary, payloadAtt.getPayload()));
            first = false;
            return true;
        }
        first = false;
        return false;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AddIsPrimaryValueToPayloadFilter that))
            return false;
        if (!super.equals(o))
            return false;
        return first == that.first && posIncAtt.equals(that.posIncAtt) && payloadAtt.equals(that.payloadAtt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), posIncAtt, payloadAtt, first);
    }
}
