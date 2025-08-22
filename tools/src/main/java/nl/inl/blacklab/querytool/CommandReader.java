package nl.inl.blacklab.querytool;

import java.io.BufferedReader;
import java.io.IOException;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import nl.inl.blacklab.exceptions.BlackLabException;

/** Read QueryTool commands from a Reader.
 *
 * With optional JLine support (if on classpath)
 */
class CommandReader {

    private BufferedReader in;

    private LineReader jlineReader;

    private final Output output;

    /** Was the last command explicitly silenced? (batch mode) */
    private boolean silenced;

    public CommandReader(BufferedReader in, Output output) {
        this.output = output;
        try {
            if (!output.isBatchMode()) {
                Terminal jlineTerminal = TerminalBuilder.builder().build();
                this.jlineReader = LineReaderBuilder.builder()
                        .terminal(jlineTerminal)
                        .build();
            } else {
                this.in = in;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String readCommand(String prompt) {
        try {
            String cmd;
            if (jlineReader != null)
                cmd = jlineReader.readLine(prompt);
            else {
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
