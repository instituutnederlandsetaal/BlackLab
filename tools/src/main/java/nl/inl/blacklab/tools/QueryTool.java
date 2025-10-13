package nl.inl.blacklab.tools;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.querytool.QueryToolImpl;

/**
 * Simple command-line querying tool for BlackLab indices.
 */
public class QueryTool {
    private QueryTool() {
    }

    /**
     * The main program.
     *
     * @param args commandline arguments
     * @throws ErrorOpeningIndex if index could not be opened
     */
    public static void main(String[] args) throws ErrorOpeningIndex {
        QueryToolImpl.queryToolMain(args);
    }
}
