package nl.inl.blacklab.queryParser.corpusql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;

public class TestParser {
    private TestParser() {
    }

    public static void main(String[] args) throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("CorpusQL> ");
            System.out.flush();
            String expr = stdin.readLine();
            if (expr.length() == 0) {
                System.out.println("EXIT");
                break;
            }
            try {
                TextPattern result = CorpusQueryLanguageParser.parse(expr, "word");
                System.out.println("Result: " + result + "\n");
            } catch (InvalidQuery e) {
                e.printStackTrace(System.err);
            }
            System.out.flush();
            System.err.flush();
        }
    }
}
