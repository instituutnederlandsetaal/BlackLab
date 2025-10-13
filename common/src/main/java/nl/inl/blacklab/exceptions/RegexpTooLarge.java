package nl.inl.blacklab.exceptions;

/** An overly broad query lead to a regexp that matches too many terms. */
public class RegexpTooLarge extends InvalidQuery {

    public RegexpTooLarge() {
        super("Regular expression too large.");
    }

}
