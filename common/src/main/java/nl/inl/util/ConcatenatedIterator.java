package nl.inl.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Concatenated iterator that will iterate over a list of iterators as if there's just one.
 */
public class ConcatenatedIterator<T> implements Iterator<T> {

    /** Main iterator over the sub-iterators. */
    private final Iterator<Iterator<T>> iterator;

    /** Current sub-iterator.  */
    private Iterator<T> subIterator;

    /** Next item we'll return, or null if no more items */
    private T lookahead;

    public <S> ConcatenatedIterator(Iterator<S> parentIterator, Function<S, Iterator<T>> toSubIterator) {
        this(new Iterator<>() {
            @Override
            public boolean hasNext() {
                return parentIterator.hasNext();
            }

            @Override
            public Iterator<T> next() {
                S parentItem = parentIterator.next();
                return toSubIterator.apply(parentItem);
            }
        });
    }

    public ConcatenatedIterator(Iterator<Iterator<T>> parentIterator) {
        iterator = parentIterator;
        lookAhead();
    }

    private void lookAhead() {
        while (true) {
            if (subIterator != null && subIterator.hasNext()) {
                // We're still iterating over the current sub-iterator
                lookahead = subIterator.next();
                break;
            } else {
                // We need the next sub-iterator
                if (iterator.hasNext()) {
                    subIterator = iterator.next();
                } else {
                    // No more sub-iterators; we're done
                    lookahead = null;
                    subIterator = null;
                    break;
                }
            }
        }
    }

    @Override
    public boolean hasNext() {
        return lookahead != null;
    }

    @Override
    public T next() {
        if (lookahead == null)
            throw new NoSuchElementException("No more files");
        T result = lookahead;
        lookAhead();
        return result;
    }
}
