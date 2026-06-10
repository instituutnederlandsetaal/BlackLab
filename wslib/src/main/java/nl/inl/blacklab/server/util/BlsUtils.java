package nl.inl.blacklab.server.util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.TokenMgrError;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.FileUtil;
import nl.inl.util.LuceneUtil;

/**
 * Various utility methods for parsing filters and patterns, and other stuff
 * used in BLS.
 */
public class BlsUtils {
    private static final Logger logger = LogManager.getLogger(BlsUtils.class);

    private BlsUtils() {
    }

    public static Query parseFilter(BlackLabIndex index, String filter,
            String filterLang) throws BlsException {
        return BlsUtils.parseFilter(index, filter, filterLang, false);
    }

    public static Query parseFilter(BlackLabIndex index, String filter,
            String filterLang, boolean required) throws BlsException {
        if (filter == null || filter.isEmpty()) {
            if (required)
                throw new BadRequest("NO_FILTER_GIVEN",
                        "Document filter required. Please specify 'filter' parameter.");
            return null; // not required
        }

        Analyzer analyzer = index.analyzer();
        if (filterLang.equals("luceneql")) {
            try {
                return LuceneUtil.parseLuceneQuery(index, filter, analyzer, "");
            } catch (ParseException | TokenMgrError e) {
                throw new BadRequest("FILTER_SYNTAX_ERROR",
                        "Error parsing LuceneQL filter query: "
                                + e.getMessage());
            }
        } else if (filterLang.equals("contextql")) {
            try {
                CompleteQuery q = ContextualQueryLanguageParser.parse(index, PluginParams.NONE, filter);
                return q.filter();
            } catch (InvalidQuery e) {
                throw new BadRequest("FILTER_SYNTAX_ERROR",
                        "Error parsing ContextQL filter query: "
                                + e.getMessage());
            }
        }

        throw new BadRequest("UNKNOWN_FILTER_LANG",
                "Unknown filter language '" + filterLang
                        + "'. Supported: luceneql, contextql.");
    }

    public static TextPattern parsePatt(BlackLabIndex index, String pattern, String language) throws BlsException {
        if (pattern == null || pattern.isEmpty()) {
                throw new BadRequest("NO_PATTERN_GIVEN",
                        "Text search pattern required. Please specify 'patt' parameter.");
        }

        if (language.equals("default")) {
            // Try to parse as CorpusQL. If that fails, try JSON.
            try {
                BLQueryParser parser = index.getQueryParser("bcql");
                return parser.parse(pattern).pattern();
            } catch (InvalidQuery e1) {
                try {
                    BLQueryParser parser = index.getQueryParser("json-bql");
                    return parser.parse(pattern).pattern();
                } catch (InvalidQuery e2) {
                    throw BadRequest.pattSyntaxError(e1);
                }
            }
        } else if (language.equals("json")) {
            try {
                BLQueryParser parser = index.getQueryParser("json-bql");
                return parser.parse(pattern).pattern();
            } catch (InvalidQuery e) {
                throw new BadRequest("PATT_SYNTAX_ERROR",
                        "Unable to parse JSON pattern: " + e.getMessage());
            }
        } else if (language.matches("bcql|corpusql")) {
            try {
                BLQueryParser parser = index.getQueryParser("bcql");
                return parser.parse(pattern).pattern();
            } catch (InvalidQuery e) {
                throw BadRequest.pattSyntaxError(e);
            }
        } else if (language.equals("contextql")) {
            try {
                BLQueryParser parser = index.getQueryParser("contextql");
                return parser.parse(pattern).pattern();
            } catch (InvalidQuery e) {
                throw new BadRequest("PATT_SYNTAX_ERROR",
                        "Syntax error in ContextQL pattern: " + e.getMessage());
            }
        }

        throw new BadRequest("UNKNOWN_PATT_LANG",
                "Unknown pattern language '" + language
                        + "'. Supported: corpusql, contextql, luceneql.");
    }

    /**
     * Delete an entire tree with files, subdirectories, etc., but only if it is
     * contained within an allowed parent directory.
     *
     * @param root the directory tree to delete
     * @param allowedParent allowed parent directory; if null, no containment check is performed
     */
    public static void delTree(File root, File allowedParent) {
        if (root == null)
            throw new IllegalArgumentException("Root directory is null");
        if (!root.isDirectory())
            throw new IllegalArgumentException("Not a directory: " + root);
        if (allowedParent != null && !FileUtil.isWithinDirectory(root, allowedParent))
            throw new IllegalArgumentException("Refusing to delete directory outside allowed parent: " + root);
        File[] files = root.listFiles();
        if (files != null) {
            for (File f: files) {
                if (f.isDirectory())
                    delTree(f, allowedParent);
                else
                    try {
                        Files.delete(f.toPath());
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
            }
        }
        if (!root.delete())
            logger.error("Unable to delete directory: {}", root);
    }

    /**
     * A file filter that returns readable directories only; used for scanning
     * collections dirs
     */
    public static final FileFilter readableDirFilter = f -> f.isDirectory() && f.canRead();

    /**
     * Convert a number of seconds to a M:SS string.
     *
     * @param sec number of seconds
     * @return a string of the form M:SS, e.g. 1s, 5m or 12m34s
     */
    public static String describeIntervalSec(int sec) {
        int min = sec / 60;
        sec = sec % 60;
        if (min == 0)
            return sec + "s";
        if (sec == 0)
            return min + "m";
        return String.format("%dm%02ds", min, sec);
    }

    public static boolean wildcardIpMatches(String wildcardIpExpr, String ip) {
        if (wildcardIpExpr.contains("*") || wildcardIpExpr.contains("?")) {
            wildcardIpExpr = wildcardIpExpr.replaceAll("\\.", "\\\\.");
            wildcardIpExpr = wildcardIpExpr.replaceAll("\\*", ".*");
            wildcardIpExpr = wildcardIpExpr.replaceAll("\\?", ".");
            return ip.matches(wildcardIpExpr);
        }
        return wildcardIpExpr.equals(ip);
    }

    public static boolean wildcardIpsContain(List<String> addresses, String ip) {
        return addresses.stream().anyMatch(adr -> wildcardIpMatches(adr, ip));
    }
}
