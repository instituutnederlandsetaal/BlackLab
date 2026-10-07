package nl.inl.blacklab.search.results.hits.fetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.lucene.index.LeafReaderContext;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockAnnotatedField;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsAbstract;

public class TestPerPublisherGrouping {

    private static class RecordingSubscriber implements HitSubscriber {
        private Hits retained;
        private long flushed = -1;
        private int done;

        @Override public void start(LeafReaderContext lrc, Hits results) { retained = results; }
        @Override public boolean needsMoreHits() { return true; }
        @Override public void hits(LeafReaderContext lrc, Hits batch, long start, long end, int docs, long offset) { }
        @Override public void counted(long hits, int docs) { }
        @Override public void flush(LeafReaderContext lrc, long numPublished) { flushed = numPublished; }
        @Override public void done(LeafReaderContext lrc) { done++; }
        @Override public void error(LeafReaderContext lrc, Throwable exception) { }
    }

    @Test
    public void groupingPropagatesAssertionErrors() {
        AssertionError failure = new AssertionError("query assertion failed");
        HitPublisher publisher = mock(HitPublisher.class);
        org.mockito.Mockito.doAnswer(call -> {
            HitSubscriber subscriber = call.getArgument(0);
            subscriber.error(null, failure);
            return null;
        }).when(publisher).subscribe(any());
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> HitsAbstract.performPerPublisher(List.of(publisher), RecordingSubscriber::new, false));
        assertSame(failure, exception.getCause());
    }

    @Test
    public void groupingCompletesForStaticAndAsynchronousStreamingPublishers() {
        Hits retained = Hits.empty(new Hits.HitsContext(new MockAnnotatedField()));
        HitPublisher staticSource = mock(HitPublisher.class);
        when(staticSource.getStatic()).thenReturn(retained);
        RecordingSubscriber staticSubscriber = new RecordingSubscriber();
        HitsAbstract.performPerPublisher(List.of(staticSource), () -> staticSubscriber, true);
        assertEquals(1, staticSubscriber.done);

        HitPublisher streamingSource = mock(HitPublisher.class);
        RecordingSubscriber streamingSubscriber = new RecordingSubscriber();
        org.mockito.Mockito.doAnswer(call -> {
            HitSubscriber subscriber = call.getArgument(0);
            Thread callbackThread = new Thread(() -> {
                subscriber.start(null, retained);
                subscriber.flush(null, 0);
                subscriber.done(null);
            });
            callbackThread.setDaemon(true);
            callbackThread.start();
            return null;
        }).when(streamingSource).subscribe(any());
        HitsAbstract.performPerPublisher(List.of(streamingSource), () -> streamingSubscriber, false);
        assertSame(retained, streamingSubscriber.retained);
        assertEquals(0, streamingSubscriber.flushed);
        assertEquals(1, streamingSubscriber.done);
    }

    @Test(timeout = 5000)
    public void asynchronousStreamingErrorCallbackCannotStrandGrouping() {
        RuntimeException sourceFailure = new RuntimeException("source failed");
        RuntimeException callbackFailure = new RuntimeException("error callback failed");
        HitPublisher publisher = mock(HitPublisher.class);
        org.mockito.Mockito.doAnswer(call -> {
            HitSubscriber subscriber = call.getArgument(0);
            Thread callbackThread = new Thread(() -> subscriber.error(null, sourceFailure));
            callbackThread.setDaemon(true);
            callbackThread.start();
            return null;
        }).when(publisher).subscribe(any());
        RecordingSubscriber subscriber = new RecordingSubscriber() {
            @Override public void error(LeafReaderContext lrc, Throwable exception) { throw callbackFailure; }
        };
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> HitsAbstract.performPerPublisher(List.of(publisher), () -> subscriber, false));
        assertSame(sourceFailure, exception.getCause());
    }
}
