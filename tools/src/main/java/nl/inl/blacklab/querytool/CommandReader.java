package nl.inl.blacklab.querytool;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;

import nl.inl.blacklab.exceptions.BlackLabException;

/** Read QueryTool commands from a Reader.
 *
 * With optional JLine support (if on classpath)
 */
class CommandReader {

    private final BufferedReader in;

    private final Output output;

    /**
     * If JLine is available, this holds the ConsoleReader object
     */
    private Object jlineConsoleReader;

    /**
     * If JLine is available, this holds the readLine() method
     */
    private Method jlineReadLineMethod;

    /**
     * Did we check if JLine is available?
     */
    private boolean jlineChecked = false;

    /** Was the last command explicitly silenced? (batch mode) */
    private boolean silenced;

    public CommandReader(BufferedReader in, Output output) {
        this.in = in;
        this.output = output;
    }

    public String readCommand(String prompt) {
        try {
            if (!output.isBatchMode() && jlineConsoleReader == null && !jlineChecked) {
                jlineChecked = true;
                try {
                    Class<?> c = Class.forName("jline.ConsoleReader");
                    jlineConsoleReader = c.getConstructor().newInstance();

                    // Disable bell
                    c.getMethod("setBellEnabled", boolean.class).invoke(jlineConsoleReader, false);

                    // Fetch and store the readLine method
                    jlineReadLineMethod = c.getMethod("readLine", String.class);

                    output.line("Command line editing enabled.");
                } catch (ClassNotFoundException e) {
                    // Can't init JLine; too bad, fall back to stdin
                    output.line("Command line editing not available; to enable, place jline jar in classpath.");
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Could not init JLine console reader", e);
                }
            }

            String cmd;
            if (jlineConsoleReader != null) {
                try {
                    cmd = (String) jlineReadLineMethod.invoke(jlineConsoleReader, prompt);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Could not invoke JLine ConsoleReader.readLine()", e);
                }
            } else {
                if (!output.isBatchMode())
                    output.noNewLine(prompt);
                output.flush();
                cmd = in.readLine();
            }
            return outputCommandIfNotSilenced(cmd);
        } catch (IOException e1) {
            throw BlackLabException.wrapRuntime(e1);
        }
    }

    String outputCommandIfNotSilenced(String cmd) {
        if (cmd == null)
            cmd = "";
        silenced = false;
        if (cmd.startsWith("-")) {
            // Silent, don't output stats
            silenced = true;
            cmd = cmd.substring(1).trim();
        }
        if (!silenced)
            output.command(cmd); // (show command depending on mode))
        return cmd;
    }

    public boolean lastCommandWasSilenced() {
        return silenced;
    }
}
