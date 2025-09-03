package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigInlineTag;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.util.XPathUtil;

public class XslGenerator {

    private XslGenerator() {
    }

    private static String applyTemplates(String selector) {
        selector = StringUtils.stripStart(selector, ".");

        return "<xsl:apply-templates select=\"" + selector + "\" />";
    }

    private static String beginTemplate(String match) {
        match = StringUtils.stripStart(match, ".");
        return "<xsl:template match=\"" + match + "\">";
    }

    private static final String endTemplate = "</xsl:template>";

    /**
     * Generate an xslt document that can transform documents into a basic html view
     * with some minor formatting for highlights.
     *
     * @param config format config
     * @return XSLT document
     * @throws NotFound if the config does not pertain to an XML-based format (tsv,
     *                  plaintext, etc)
     */
    public static String generateXsltFromConfig(ConfigInputFormat config) throws NotFound {
        if (!ConfigInputFormat.FileType.XML.equals(config.getFileType()))
            throw new NotFound("NOT_FOUND", "The format '" + config.getName()
                    + "' does not apply to XML-type documents, and cannot be converted to XSLT.");

        // We want an XSLT from this config.
        StringBuilder xslt = new StringBuilder();
        StringBuilder nameSpaces = new StringBuilder();
        StringBuilder excludeResultPrefixes = new StringBuilder();

        String optDefaultNameSpace = "";
        for (Map.Entry<String, String> e : config.getNamespaces().entrySet()) {
            if (e.getKey().isEmpty())
                optDefaultNameSpace = "xpath-default-namespace=\"" + e.getValue() + "\"";
            else {
                nameSpaces.append(" xmlns:" + e.getKey() + "=\"" + e.getValue() + "\"");
                excludeResultPrefixes.append(" " + e.getKey());
            }
        }
        xslt.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                        + optDefaultNameSpace + nameSpaces + " exclude-result-prefixes=\"" + excludeResultPrefixes
                        + "\">")
                .append("<xsl:output encoding=\"utf-8\" method=\"html\" omit-xml-declaration=\"yes\" />");

        // swallow everything not explicitly matched.
        xslt.append("<xsl:template match='text()' priority='-10' >")
        // enable these to show any removed text between a pair of brackets
//			.append("<xsl:text>[</xsl:text>")
//			.append("<xsl:value-of select='.'/>")
//			.append("<xsl:text>]</xsl:text>")
                .append(endTemplate);

        // transform inserted <hl> tags into spans
        // use local-name to sidestep namespaces (we can do this because the hl tags are inserted by blacklab, so we know the data in this case)
        xslt.append(beginTemplate("*[local-name(.)='hl']"))
                .append("<span class=\"hl\">")
                .append(applyTemplates("node()")) // we don't know what level we're at, so explicitly match everything else
                .append("</span>")
                .append(endTemplate);

        // generate the templates for all words
        // NOTE: 'annotation' here refers to a linguistic annotation - not an xml attribute/annotation!
        for (ConfigAnnotatedField f : config.getAnnotatedFields().values()) {
            if (f.getAnnotations().isEmpty())
                continue; // No annotations for this word at all? can't display anything...

            // By default attempt to display the annotations of the word named "lemma" and "word"
            // TODO: if BlackLab is supposed be able to generate this kind of XSLT, we should allow users to configure
            //   how this is done. Only start guessing if no configuration has been provided.
            ConfigAnnotation wordAnnot = f.getAnnotations().get("word");   // TODO: better use mainAnnotation() ?
            ConfigAnnotation lemmaAnnot = f.getAnnotations().get("lemma");

            // Since the annotation can be named anything there is no guarantee there is a "word" annotation
            // (it's just a reasonable default guess), so just attempt to display whatever is first in the list otherwise
            // This ought to be correct - see DocIndexerConfig::init(), it sets the main annotation as the first annotation in the list
            // As for the "lemma" annotation that's used to generate hover tooltips, it's just a reasonable guess
            if (wordAnnot == null)
                wordAnnot = f.getAnnotations().values().iterator().next();

            // Begin word template
            // TODO: take containerPath into account too (optional, goes between documentPath and wordPath)
            String wordBase = XPathUtil.joinXpath(config.getDocumentPath(), f.getContainerPath(), f.getWordsPath());
            xslt.append(beginTemplate(wordBase))
                    .append("<span class=\"word\">");

            // Extract lemma
            if (lemmaAnnot != null && lemmaAnnot != wordAnnot && lemmaAnnot.getValuePath() != null) {
                xslt.append("<xsl:attribute name=\"data-toggle\" select=\"'tooltip'\"/>");
                xslt.append("<xsl:attribute name=\"data-lemma\">")
                        .append("<xsl:value-of select='"
                                + XPathUtil.joinXpath(lemmaAnnot.getBasePath(), lemmaAnnot.getValuePath()).replace("'", "&apos;") + "'/>")
                        .append("</xsl:attribute>");
            }

            // extract word
            xslt.append("<xsl:value-of select=\""
                    + XPathUtil.joinXpath(wordAnnot.getBasePath(), wordAnnot.getValuePath()).replace("'", "&apos;") + "\"/>");

            // end word template
            xslt.append("</span>")
                .append("<xsl:text> </xsl:text>") // space between words.
                .append(endTemplate);

            // Generate rules for inline tags
            for (ConfigInlineTag inlineTag : f.getInlineTags()) {
                String inlineTagPath = XPathUtil.joinXpath(config.getDocumentPath(), f.getContainerPath(),
                        inlineTag.getPath());
                String cssClass;
                if (!inlineTag.getDisplayAs().isEmpty())
                    cssClass = inlineTag.getDisplayAs();
                else
                    cssClass = inlineTag.getPath().replaceAll("\\b\\w+:", "").replaceAll("\\W+", " ").trim()
                            .replace(" ", "-");
                xslt.append(beginTemplate(inlineTagPath))
                        .append("<span class=\"" + cssClass + "\">")
                        .append(applyTemplates("node()"))
                        .append("</span>")
                        .append(endTemplate);
            }

            /*
             * Output a warning when not a single word can be matched within the document.
             * Why do we do this? Well, xslt is very strict about namespaces.
             * When a default namespace is provided we set the xpath-default-namespace in the xslt
             * This means that EVERY SINGLE TEMPLATE will ONLY match nodes within that namespace (so only children of, or the node that declares xmlns='http://my.default.namespace/')
             * When a default namespace is provided, but some node within our generated xpath to the word element is not within that namespace,
             * nothing will be matched.
             *
             * An example:
             * <documentRoot>
             * 		<textContainer xmlns="my-fancy-namespace">
             * 			<word lemma="blah">blah</word>
             * 		</textContainer>
             * </documentRoot>
             *
             * Now in our config documentPath = "//documentRoot"
             *  and wordPath = ".//word" (relative to the documentPath)
             * The xpath to get the words is generated by concatenating the documentPath and wordPath into "//documentRoot//word"
             * Because documentRoot is not within the "my-fancy-namespace" that was declared as default namespace, nothing will ever be matched.
             *
             * To warn the user of this, we test if any words exist, and output a warning if we can't find any.
             *
             * Edit 05-jul-2018 (Koen)
             * --------------------
             * This comment is no longer strictly true, as we now strip namespaces from the source document altogether in some cases.
             * but it helps explain the reasoning behind stripping namespaces.
             */
            String namespaceWarningMessage = "No words have been found within this entire document. " +
                    "This usually happens when your document contains namespaces, but the format you used to index the document doesn't use any namespaces. "
                    +
                    "When the format doesn't use namespaces, blacklab will ignore them during indexing, but the xslt used to turn the document into an html snippet doesn't, "
                    +
                    "so the words can't be found. " +
                    "To fix this, you will need to add the namespaces in your format and use them in the xpaths. " +
                    "If your document contains a default namespace, you can declare it as an empty name (\"\": \"https://my-default-namespace.site/namespace\"). "
                    +
                    "This may also happen if the generated xslt is applied to a partial document, such as when using the wordstart and wordend parameters when requesting its contents from blacklab-server.";

            // Entry point (after optional namespace removal)
            xslt.append(
                    "<xsl:template match=\"/\" mode=\"pass2\">" +
                            "<xsl:choose>" +
                            "<xsl:when test=\"" + wordBase.replace("\"", "\"\"") + "\">" +
                            "<xsl:apply-templates/>" +
                            "</xsl:when>" +
                            "<xsl:otherwise>" +
                            "<xsl:text>" + namespaceWarningMessage + "</xsl:text>" +
                            "</xsl:otherwise>" +
                            "</xsl:choose>" +
                            "</xsl:template>");

            // Strip namespaces before delegating to entry
            // A preprocessing step that (optionally) strips namespaces from the document before passing it on to the template above.
            // (This happens when there are no namespaces declared - which in blacklab means to ignore all namespaces, this is impossible in xslt without this workaround)
            if (config.getNamespaces().isEmpty()) {
                xslt.append(beginTemplate("/"))
                        .append("<!-- Since no namespaces are used in the source document format, namespaces are stripped from the document before processing it -->")
                        .append("<xsl:variable name=\"withoutNamespaces\"><xsl:apply-templates select=\".\" mode=\"remove-namespaces\"/></xsl:variable>")
                        .append("<xsl:apply-templates select=\"$withoutNamespaces\" mode=\"pass2\"/>")
                        .append(endTemplate);
            } else {
                xslt.append(beginTemplate("/"))
                        .append("<xsl:apply-templates select=\".\" mode=\"pass2\"/>")
                        .append(endTemplate);
            }

            // templates to strip namespaces
            xslt.append(
                    "<xsl:template match=\"*\" mode=\"remove-namespaces\">" +
                            "<xsl:element name=\"{local-name()}\">" +
                            "<xsl:apply-templates select=\"@* | node()\" mode=\"remove-namespaces\"/>" +
                            "</xsl:element>" +
                            "</xsl:template>" +
                            "<xsl:template match=\"@*\" mode=\"remove-namespaces\">" +
                            "<xsl:attribute name=\"{local-name()}\">" +
                            "<xsl:value-of select=\".\"/>" +
                            "</xsl:attribute>" +
                            "</xsl:template>" +
                            "<xsl:template match=\"comment() | text() | processing-instruction()\" mode=\"remove-namespaces\">"
                            +
                            "<xsl:copy/>" +
                            "</xsl:template>");
        }
        xslt.append("</xsl:stylesheet>");

        return xslt.toString();
    }
}
