package nl.inl.blacklab.search.results.hits.fetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockAnnotatedField;
import nl.inl.blacklab.mocks.MockSpans;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;

public class TestHitPublisherCatchup {

    private static class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public synchronized void execute(Runnable task) {
            tasks.add(task);
        }

        void runNext() {
            Runnable task;
            synchronized (this) {
                task = tasks.remove();
            }
            task.run();
        }

        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private static class RecordingSubscriber implements HitSubscriber {
        long received;

        @Override public void start(LeafReaderContext lrc, Hits results) { }
        @Override public boolean needsMoreHits() { return true; }
        @Override public void counted(long hits, int docs) { }
        @Override public void hits(LeafReaderContext lrc, Hits batch, long start, long end, int docs, long offset) {
            received += end - start;
        }
        @Override public void flush(LeafReaderContext lrc, long published) { }
        @Override public void done(LeafReaderContext lrc) { }
        @Override public void error(LeafReaderContext lrc, Throwable exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    public void spansLateSubscriberReceivesTheCompleteRetainedPrefix() throws IOException {
        int n = 102;
        int[] docs = new int[n];
        int[] starts = new int[n];
        int[] ends = new int[n];
        for (int i = 0; i < n; i++) {
            docs[i] = i;
            ends[i] = 1;
        }
        BLSpanWeight weight = mock(BLSpanWeight.class);
        when(weight.getSpans(any(), any())).thenReturn(new MockSpans(docs, starts, ends));
        LeafReaderContext lrc = mock(LeafReaderContext.class);
        when(lrc.reader()).thenReturn(mock(LeafReader.class));
        MockAnnotatedField field = new MockAnnotatedField();
        ManualExecutor executor = new ManualExecutor();
        HitPublisherSpans publisher = new HitPublisherSpans(lrc, weight,
                new HitQueryContext(field.index(), null, field), executor,
                new ResultsStatsPassive(), new ResultsStatsPassive(), true);

        RecordingSubscriber early = new RecordingSubscriber() {
            @Override public boolean needsMoreHits() { return received < 100; }
        };
        publisher.subscribe(early);
        executor.runNext();
        assertEquals(100, early.received);

        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        executor.runNext();

        assertEquals(102, late.received);
    }

    @Test
    public void filterLateSubscriberReceivesTheCompleteRetainedPrefix() {
        int n = 102;
        Hits.HitsContext context = new Hits.HitsContext(new MockAnnotatedField());
        HitPublisher source = mock(HitPublisher.class);
        when(source.context()).thenReturn(context);
        HitFilter filter = mock(HitFilter.class);
        when(filter.forSegment(any(), any(), any())).thenReturn(filter);
        when(filter.accept(any(Long.class))).thenReturn(true);
        HitPublisherFilter publisher = new HitPublisherFilter(source, filter);
        var captor = org.mockito.ArgumentCaptor.forClass(HitSubscriber.class);
        org.mockito.Mockito.verify(source).subscribe(captor.capture());
        HitSubscriber sourceSubscriber = captor.getValue();

        RecordingSubscriber early = new RecordingSubscriber();
        publisher.subscribe(early);
        HitsMutable first = hits(context, 0, 100);
        sourceSubscriber.hits(null, first, 0, 100, 100, 0);
        assertEquals(100, early.received);

        RecordingSubscriber late = new RecordingSubscriber();
        publisher.subscribe(late);
        HitsMutable second = hits(context, 100, n);
        sourceSubscriber.hits(null, second, 0, 2, 2, 100);

        assertEquals(102, late.received);
    }

    private static HitsMutable hits(Hits.HitsContext context, int start, int end) {
        HitsMutable hits = HitsMutable.create(context, end - start, false, false);
        for (int doc = start; doc < end; doc++)
            hits.add(doc, 0, 1, null);
        return hits;
    }
}
