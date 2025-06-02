package nl.inl.blacklab.index;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX parser with the ability to attach "SAX-path hooks" to process specific
 * elements/attributes in the XML.
 */
public class HookableSaxHandler extends DefaultHandler {

    /**
     * Describes a path in an XML document that can be evaluated while doing SAX
     * parsing, and keeps track of the current matching state(s).
     */
    public static class SaxPathExpressionChecker {
        /** Keeps track of a (potential) match of this expression. */
        class ExprMatcher {
            /** How many elements of the current path have we matched? */
            private int succesfulMatches = 0;

            /** How many unmatched elements deeper than the matched path are we? */
            private int failedMatches = 0;

            /**
             * For relative paths: how many elements have we skipped to find the first part?
             */
            private int elementsSkipped = 0;

            public void startElement(String elementName) {
                if (failedMatches > 0) {
                    // Already unmatched. Just increment the level.
                    failedMatches++;
                } else {
                    // No failed matches yet. Does this one match?
                    if (succesfulMatches < elementNames.size()) {
                        String elementToMatch = elementNames.get(succesfulMatches);
                        if (elementToMatch.equals(elementName) || elementToMatch.equals("*")) {
                            // We have a match! Record it.
                            succesfulMatches++;
                        } else {
                            // Mismatch.
                            failedMatches++;
                        }
                        return;
                    }

                    // No, no match. Are we still in the skipping phase for a relative path?
                    if (isRelativePath && succesfulMatches == 0)
                        elementsSkipped++; // Yes, skip one more
                    else
                        failedMatches++; // No, record failed match
                }
            }

            /**
             * A close tag was found; go back up one level.
             *
             * @return false if we're at level 0 now, true otherwise
             */
            public boolean endElement() {
                if (failedMatches > 0) {
                    // Unmatched; decrement the unmatched level, so it may become matched again
                    failedMatches--;
                } else {
                    if (succesfulMatches > 0) {
                        // Just decrement the matched level.
                        succesfulMatches--;
                    } else {
                        // We're in the skipping phase for a relative path.
                        elementsSkipped--;
                    }
                }
                return failedMatches > 0 || succesfulMatches > 0 || elementsSkipped > 0;
            }

            /**
             * Check if the current element matched the path expression.
             * 
             * @return true iff the current element matched
             */
            public boolean currentElementMatched() {
                return succesfulMatches == elementNames.size() && failedMatches == 0
                /*&& attributeName == null*/;
            }

            /**
             * Check if one of our ancestors matched the path expression.
             *
             * @return true iff one of our ancestors matched
             */
            public boolean ancestorOrSelfMatched() {
                return succesfulMatches == elementNames.size() /*&& attributeName == null*/;
            }

        }

        /** Is this a non-absolute path, i.e. starting with "//" instead of "/"? */
        boolean isRelativePath;

        /** The element names to match */
        List<String> elementNames;

        /** Current matching status */
        private final List<ExprMatcher> matchers = new ArrayList<>();

        /** Current parsing depth */
        private int depth = 0;

        /**
         * Constructs the object from the string representation (xpath subset).
         *
         * @param expr the expression
         */
        public SaxPathExpressionChecker(String expr) {
            super();

            // See if it's a relative or absolute path
            if (expr.length() > 0 && expr.charAt(0) != '/') {
                // Doesn't start with "/": relative path
                isRelativePath = true;
            } else {
                if (expr.length() > 1 && expr.charAt(1) == '/') {
                    // Starts with "//": relative path
                    isRelativePath = true;
                    expr = expr.substring(2);
                } else {
                    // Starts with "/": absolute path
                    expr = expr.substring(1);
                }
            }

            // Split into parts
            String[] parts = expr.split("/");
            int numberOfElementParts = parts.length;
            if (parts[numberOfElementParts - 1].length() == 0) {
                throw new IllegalArgumentException("Double slash in simple-xpath expression");
            }
            if (parts[numberOfElementParts - 1].charAt(0) == '@') {
                throw new IllegalArgumentException("Cannot match on attribute");
            }
            elementNames = new ArrayList<>();
            for (String part : parts) {
                if (part.length() == 0) {
                    throw new IllegalArgumentException("Double slash in simple-xpath expression");
                }
                if (part.charAt(0) == '@') {
                    throw new IllegalArgumentException("Cannot match on attribute");
                }
                elementNames.add(part);
            }
        }

        @Override
        public String toString() {
            return (isRelativePath ? "" : "/") + StringUtils.join(elementNames, "/");
        }

        public void startElement(String localName) {
            // Should we start a new matcher here?
            if ((isRelativePath || depth == 0) && (elementNames.isEmpty() || localName.equals(elementNames.get(0)))) {
                // Yes, start a new matcher
                matchers.add(new ExprMatcher());
            }

            // Call each of the matchers to signal the start of this element
            for (ExprMatcher m : matchers) {
                m.startElement(localName);
            }

            depth++;
        }

        public void endElement() {
            depth--;

            // Call each of the matchers to signal the end of this element
            Set<ExprMatcher> toRemove = new HashSet<>();
            for (ExprMatcher m : matchers) {
                if (!m.endElement())
                    toRemove.add(m); // This matcher is done
            }
            for (ExprMatcher m : toRemove) {
                matchers.remove(m);
            }
        }

        public boolean currentElementMatches() {
            for (ExprMatcher m : matchers) {
                if (m.currentElementMatched())
                    return true;
            }
            return false;
        }

        public boolean ancestorOrSelfMatched() {
            for (ExprMatcher m : matchers) {
                if (m.ancestorOrSelfMatched())
                    return true;
            }
            return false;
        }

    }

    /**
     * Represents a handler for when a SAX path condition matches
     */
    public static class ElementHandler {
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            // To be implemented by child class
        }

        public void endElement(String uri, String localName, String qName) {
            // To be implemented by child class
        }

        public void characters(char[] ch, int start, int length) {
            // To be implemented by child class
        }

        boolean insideElement = false;

        public boolean insideElement() {
            return insideElement;
        }

        public void setInsideElement(boolean b) {
            insideElement = b;
        }
    }

    /**
     * A SAX parser hook handler that captures the element's character content for
     * processing in the endElement() method.
     */
    public static class ContentCapturingHandler extends ElementHandler {

        private final StringBuilder elementContent = new StringBuilder();

        public String getElementContent() {
            return elementContent.toString();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            elementContent.setLength(0);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            elementContent.append(ch, start, length);
        }

    }

    /**
     * Evaluates the condition and calls the handler if if matches
     *
     * @param expression                The path to check for
     * @param handler                   The handler for this hook
     * @param callHandlerForDescendants Whether or not to call the handler for all descendants of the matched element
     */
    private record SaxParserHook(SaxPathExpressionChecker expression, ElementHandler handler,
                                 boolean callHandlerForDescendants) {
        /**
         * Constructs the object.
         *
         * @param expression                the expression to check for
         * @param handler                   the handler to call for matches
         * @param callHandlerForDescendants whether or not to call handler for all
         *                                  descendants of a matched element
         */
        private SaxParserHook {
        }

        /**
         * Should we call the handler for our current place in the document?
         *
         * @return true iff we should
         */
        private boolean shouldCallHandler() {
            if (callHandlerForDescendants)
                return expression.ancestorOrSelfMatched();
            return expression.currentElementMatches();
        }

        /**
         * Open tag: pass on to expression checked, and call handler if the element
         * matches or any of the attributes match.
         *
         * @param uri        namespace uri
         * @param localName  element local name
         * @param qName      element qualified name
         * @param attributes element attributes
         */
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            expression.startElement(localName);
            if (shouldCallHandler()) {
                handler.setInsideElement(true);
                handler.startElement(uri, localName, qName, attributes);
            }
        }

        /**
         * Close tag: call handler if the element matched, and pass the tag on to the
         * expression checker.
         *
         * @param uri       namespace uri
         * @param localName element local name
         * @param qName     element qualified name
         */
        public void endElement(String uri, String localName, String qName) {
            if (shouldCallHandler()) {
                handler.endElement(uri, localName, qName);
                handler.setInsideElement(false);
            }
            expression.endElement();
        }

        /**
         * Character content: call handler if the expression is currently matched.
         *
         * @param ch     character buffer
         * @param start  start of content in buffer
         * @param length length of content in buffer
         */
        public void characters(char[] ch, int start, int length) {
            if (shouldCallHandler())
                handler.characters(ch, start, length);
        }

        @Override
        public String toString() {
            return expression.toString();
        }
    }

    /** The list of hooks into our parser */
    private final List<SaxParserHook> hooks = new ArrayList<>();

    /** To keep track of the position within the document */
    protected Locator locator;

    /**
     * Add a hook to the parser.
     *
     * @param condition when to invoke the handler
     * @param handler what to do when the condition matches
     * @param callHandlerForAllDescendants whether or not to call the handler for
     *            all descendants of the matched element
     */
    private void addHook(SaxPathExpressionChecker condition, ElementHandler handler,
            boolean callHandlerForAllDescendants) {
        hooks.add(new SaxParserHook(condition, handler, callHandlerForAllDescendants));
    }

    /**
     * Add a hook to the parser.
     *
     * @param condition when to invoke the handler (xpath subset expression)
     * @param handler what to do when the condition matches
     * @param callHandlerForAllDescendants whether or not to call the handler for
     *            all descendants of the matched element
     */
    public void addHook(String condition, ElementHandler handler, boolean callHandlerForAllDescendants) {
        addHook(new SaxPathExpressionChecker(condition), handler, callHandlerForAllDescendants);
    }

    /**
     * Add a hook to the parser.
     *
     * The hook is called only for the matched element itself, not all its
     * descendants.
     *
     * @param condition when to invoke the handler (xpath subset expression)
     * @param handler what to do when the condition matches
     */
    public void addHook(String condition, ElementHandler handler) {
        addHook(condition, handler, false);
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    /**
     * Describe current parsing position
     * 
     * @return the description
     */
    public String describePosition() {
        return "line " + locator.getLineNumber() + ", position " + locator.getColumnNumber();
    }

    /**
     * Called when character content (not a tag) is encountered in the XML.
     */
    @Override
    public void characters(char[] ch, int start, int length) {
        for (SaxParserHook hook : hooks) {
            hook.characters(ch, start, length);
        }
    }

    /**
     * Called when an end tag (element close tag) is encountered in the XML.
     */
    @Override
    public void endElement(String uri, String localName, String qName) {
        for (SaxParserHook hook : hooks) {
            hook.endElement(uri, localName, qName);
        }
    }

    /**
     * Called when an start tag (element open tag) is encountered in the XML.
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        for (SaxParserHook hook : hooks) {
            hook.startElement(uri, localName, qName, attributes);
        }
    }

}
