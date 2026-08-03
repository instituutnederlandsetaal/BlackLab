package nl.inl.util;

import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockTimer implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(BlockTimer.class); 
    
    private static final long RUNNING = -1;
    
    private volatile long start;
    private volatile long end;
    private final TimerGroup group;
    private Thread ownThread;
    
    private BlockTimer(TimerGroup group) {
        this.group = group;
    }
    public Long runtime() {
        return (this.end - this.start);
    }

    public BlockTimer child(String message) {
        return this.group.child(message);
    }

    public static BlockTimer create(boolean log, String message) {
        return new TimerGroup(log, message, null).get();
    }

    public static BlockTimer create(String message) {
        return create(true, message);
    }
    
    private void start() {
        if (this.end == RUNNING) throw new IllegalStateException("Timer already running");
        
        this.ownThread = Thread.currentThread();
        this.start = System.nanoTime();
        this.end = RUNNING;
    }
    
    @Override
    public void close() {
        this.end = System.nanoTime();
        this.group.put(this, ownThread);
        this.ownThread = null;
    }
    
    private boolean isClosed() {
        return this.end != RUNNING;
    }

    /**
     * TimerGroup represents the results of all instances of a timer with the same message
     * e.g.
     * <pre>
     * try (BlockTimer top = Timer.create("top")) {
     *     for (int i  = 0; i < 10; ++i) {
     *         try (BlockTimer loop = top.child("loop") {
     *             somethingDifficult();
     *         }
     *     }
     * }
     * </pre>
     *
     * All 10 instances of child "loop" will register their results to the same TimerGroup.
     * When the topmost TimerGroup finishes, the results are printed.
     * Children may be added/created from any thread. The "self" time reported is always in regard to the thread the root timer was created in.
     * */
    private static class TimerGroup {
        private final boolean log;
        private final String message;
        private long invocations = 0;
        private long running = 0;
        private long runtime = 0;
        private final TimerGroup parent;
        private final Thread rootThread;
        private long timeInRootThread = 0;
        
        private final Stack<BlockTimer> instances = new Stack<>();
        private final ConcurrentHashMap<String, TimerGroup> children = new ConcurrentHashMap<>();

        
        private TimerGroup(boolean log, String message, TimerGroup parent) {
            this.log = log;
            this.message = message;
            this.parent = parent;
            this.rootThread = parent != null ? parent.rootThread : Thread.currentThread();
        }
        
        private synchronized BlockTimer get() {
            BlockTimer t = instances.isEmpty() ? new BlockTimer(this) : instances.pop();
            ++this.running;
            t.start();
            return t;
        }
        
        private synchronized void put(BlockTimer timer, Thread timerThread) {
            if (!timer.isClosed()) throw new IllegalStateException("Cannot return running timer");
            this.instances.push(timer);
            ++this.invocations;
            --this.running;
            this.runtime += timer.runtime();
            if (this.running < 0) throw new IllegalStateException("Negative running timers!");
            if (timerThread == rootThread)
                timeInRootThread += timer.runtime();
            checkDone();
        }
        
        private BlockTimer child(String message) {
            return this.children.computeIfAbsent(message, __ -> new TimerGroup(log, message, this)).get();
        }

        private synchronized void checkDone() {
            if (this.running == 0 && this.children.values().stream().allMatch(c -> c.running == 0)) {
                if (this.parent == null) this.print(0, 0);
                else this.parent.checkDone();
            }
        }

        private long totalDescendantTimeInRootThread() {
            return this.children.reduceValuesToLong(1000, t -> t.timeInRootThread, 0, Long::sum);
        }

        private String print(int lead, int messageLength) {

            final String[] labels = {"total", "av.", "self", "av. self"};

            long timeInRootThreadWithoutChildren = this.timeInRootThread - this.totalDescendantTimeInRootThread();

            final Long[] values = {
                runtime / 1_000_000,
                (runtime / invocations) / 1_000_000,
                timeInRootThreadWithoutChildren / 1_000_000,
                (timeInRootThreadWithoutChildren / invocations) / 1_000_000
            };
            
            final String fmt = "% 6dms %s ";
            StringBuilder msg = new StringBuilder();

            msg.append("\n").append(StringUtils.repeat(' ', lead * 4))
                    .append(StringUtils.rightPad(message, messageLength));
            for (int i = 0; i < labels.length; ++i) msg.append(String.format(fmt, values[i], labels[i]));
            msg.append(String.format("% 6dx ", invocations));
            
            int longestMessage = this.children.values().stream().mapToInt(t -> t.message.length()).reduce(0, Math::max);
            for (TimerGroup c : this.children.values()) msg.append(c.print(lead+1, longestMessage));
            
            if (this.parent != null) {
                return msg.toString();
            } else {
                if (log)
                    logger.debug(msg.toString());
                return null;
            }      
        }
    }
}
