package nl.inl.blacklab.search.results.hits.fetch;

import nl.inl.blacklab.search.results.hits.Hits;

/** An object that publishes hits to its subscribers.
 *
 * Subscribers will receive all hits, even when they subscribe after some (or all)
 * hits have already been produced.
 */
public interface HitPublisher {

    /** Subscribe to this publisher.
     * <p>
     * The publisher will immediately send all hits it has produced so far to the new
     * subscriber, and will later send any new hits as they are produced.
     */
    void subscribe(HitSubscriber subscriber);

    /** Make sure that this publisher is actively producing hits for its subscribers.
     * <p>
     * When hits are produced, subscribers can indicate that they don't need any more
     * hits at the moment. If all of a publisher's subscribers indicate this, this will
     * stop the fetching thread.
     * <p>
     * If that happened, this method starts another fetching thread. If the fetching
     * thread is already running, this does nothing.
     */
    void activate();

    /** Context for our hit: field, match info definitions and segment they came from (or null if global) */
    Hits.HitsContext context();

    /** Wait for all hits to be fetched and return an efficient Hits interface to them. */
    Hits getStatic();
}
