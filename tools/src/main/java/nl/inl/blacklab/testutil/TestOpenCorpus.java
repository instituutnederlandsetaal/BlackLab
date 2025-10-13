package nl.inl.blacklab.testutil;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Level;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.LogUtil;

/** Simple test program to monitor opening a BlackLab corpus */
public class TestOpenCorpus {

    static final AtomicBoolean terminate = new AtomicBoolean();

    private TestOpenCorpus() {
    }

    public static void main(String[] args) throws Exception {
        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that
        LogUtil.setupBasicLoggingConfig(Level.DEBUG);

        // Make sure we catch Ctrl+C and terminate gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutdown hook running");
            terminate.set(true);
        }));

        File indexDir = new File(args[0]);
        try (BlackLabIndex index = BlackLab.implicitInstance().open(indexDir)) {
            // Wait until terminated by user (Ctrl+C to terminate)
            int i = 0;
            while (!terminate.get()) {
                // Avoid busy waiting
                Thread.sleep(1000);

                // Show that we're still running
                i++;
                if (i % 30 == 0)
                    System.err.println(String.format("%3d s...", i));
            }
            System.err.println("outside loop");
        }
    }
}
