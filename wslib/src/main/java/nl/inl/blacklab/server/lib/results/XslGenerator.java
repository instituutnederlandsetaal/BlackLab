package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;

import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.util.XPathUtil;

/** Generates a basic HTML view, not a replacement for a corpus-specific stylesheet. */
public class XslGenerator {
    private static final String STYLESHEET = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                ${namespaces} exclude-result-prefixes="#all">
              <xsl:output encoding="UTF-8" method="html" omit-xml-declaration="yes"/>

              <xsl:template match="/">
                <!-- A format without configured namespaces ignores source namespaces. -->
                <xsl:variable name="source" as="document-node()">
                  <xsl:choose>
                    <xsl:when test="${stripNamespaces}">
                      <xsl:document>
                        <xsl:apply-templates select="node()" mode="strip-namespaces"/>
                      </xsl:document>
                    </xsl:when>
                    <xsl:otherwise><xsl:sequence select="."/></xsl:otherwise>
                  </xsl:choose>
                </xsl:variable>
                <xsl:for-each select="$source">
                  <!-- Check source words, without buffering the entire rendered HTML tree. -->
                  <xsl:choose>
                    <xsl:when test="${hasWords}">
                      ${calls}
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:text>No words have been found within this entire document. Check the format's document, container and word paths and namespace declarations. A partial document may also be missing the ancestors required by those paths.</xsl:text>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:for-each>
              </xsl:template>

              ${fields}

              <xsl:mode name="strip-namespaces" on-no-match="shallow-copy"/>
              <xsl:template match="*" mode="strip-namespaces">
                <xsl:element name="{local-name()}">
                  <xsl:apply-templates select="@* | node()" mode="strip-namespaces"/>
                </xsl:element>
              </xsl:template>
              <xsl:template match="@*" mode="strip-namespaces">
                <xsl:attribute name="{local-name()}" select="."/>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String ORDERED_FIELD = """
            <xsl:template name="field-${id}">
              <xsl:for-each select="${documentPath}">
                <xsl:for-each select="${containerPath}">
                  <xsl:variable name="container" select="."/>
                  <!-- Evaluate wordPath in its own context, without a / that reorders nodes. -->
                  <xsl:variable name="words" as="element()*" select="${wordPath}"/>
                  <xsl:for-each select="$words">
                    <xsl:variable name="i" select="position()"/>
                    <xsl:variable name="previousWord" select="$words[$i - 1]"/>
                    <xsl:variable name="nextWord" select="$words[$i + 1]"/>
                    <xsl:value-of select="${punctBefore}"/>
                    <xsl:call-template name="word-${id}">
                      <xsl:with-param name="highlights" select="ancestor::*[local-name() = 'hl']"/>
                    </xsl:call-template>
                    <xsl:if test="position() = last()">
                      <xsl:value-of select="${punctAfter}"/>
                    </xsl:if>
                  </xsl:for-each>
                </xsl:for-each>
              </xsl:for-each>
            </xsl:template>
            """;

    private static final String STRUCTURAL_FIELD = """
            <xsl:template name="field-${id}">
              <xsl:apply-templates mode="field-${id}"/>
            </xsl:template>

            <xsl:template match="text()" mode="field-${id}"/>

            <xsl:template match="${wordMatch}" mode="field-${id}">
              <xsl:call-template name="word-${id}">
                <xsl:with-param name="highlights" select="ancestor::*[local-name() = 'hl']"/>
              </xsl:call-template>
              <xsl:text> </xsl:text>
            </xsl:template>
            """;

    private static final String WORD = """
            <!-- Named calls retain the word's context, including ancestors used by annotations. -->
            <xsl:template name="word-${id}">
              <xsl:param name="highlights" as="element()*" select="()"/>
              <xsl:choose>
                <xsl:when test="exists($highlights)">
                  <span class="hl">
                    <xsl:copy-of select="$highlights[1]/@*"/>
                    <xsl:call-template name="word-${id}">
                      <xsl:with-param name="highlights" select="$highlights[position() gt 1]"/>
                    </xsl:call-template>
                  </span>
                </xsl:when>
                <xsl:otherwise>
                  <span class="word">
                    ${lemma}
                    <xsl:value-of select="${wordValue}"/>
                  </span>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:template>
            """;

    private static final String INLINE = """
            <xsl:template match="${match}" mode="field-${id}">
              <span>
                <xsl:attribute name="class"><xsl:text>${cssClass}</xsl:text></xsl:attribute>
                <xsl:apply-templates mode="field-${id}"/>
              </span>
            </xsl:template>
            """;

    private static final String LEMMA = """
            <xsl:attribute name="data-toggle">tooltip</xsl:attribute>
            <xsl:attribute name="data-lemma"><xsl:value-of select="${lemmaValue}"/></xsl:attribute>
            """;

    private XslGenerator() {
    }

    private static String render(String template, Map<String, String> values) {
        // Replacement values may themselves contain ${...} in XPath literals or XSLT fragments.
        // Only our templates are interpreted; supplied values are never expanded again.
        return new StringSubstitutor(values).setDisableSubstitutionInValues(true)
                .setEnableUndefinedVariableException(true).replace(template);
    }

    private static String xml(String value) {
        return StringEscapeUtils.escapeXml10(value);
    }

    private static String annotationPath(ConfigAnnotation annotation) {
        String value = Objects.requireNonNullElse(annotation.getValuePath(), ".");
        String base = annotation.getBasePath();
        // Unlike path concatenation, simple mapping preserves the expression and its yield order.
        return xml(base == null ? value : "(" + base + ") ! (" + value + ")");
    }

    /** Generate XSLT for an XML input format. Non-XML formats have no XML view. */
    public static String generateXsltFromConfig(ConfigInputFormat config) throws NotFound {
        if (config.getFileType() != ConfigInputFormat.FileType.XML)
            throw new NotFound("NOT_FOUND", "The format '" + config.getName()
                    + "' does not apply to XML-type documents, and cannot be converted to XSLT.");

        Map<String, String> namespaces = new LinkedHashMap<>(config.getNamespaces());
        namespaces.putIfAbsent("xs", "http://www.w3.org/2001/XMLSchema");
        StringBuilder namespaceAttributes = new StringBuilder();
        namespaces.forEach((prefix, uri) -> namespaceAttributes.append(prefix.isEmpty()
                ? " xpath-default-namespace=\"" : " xmlns:" + prefix + "=\"").append(xml(uri)).append('"'));

        StringBuilder fields = new StringBuilder();
        StringBuilder calls = new StringBuilder();
        var wordChecks = new ArrayList<String>();
        int id = 0;
        for (var field : config.getAnnotatedFields().values()) {
            if (field.getAnnotations().isEmpty())
                continue;
            // Keep the existing display convention: prefer word, otherwise the first annotation.
            ConfigAnnotation word = field.getAnnotation("word");
            if (word == null)
                word = field.getAnnotations().iterator().next();
            ConfigAnnotation lemma = field.getAnnotation("lemma");
            Map<String, String> values = new HashMap<>();
            values.put("id", Integer.toString(id++));
            values.put("wordPath", xml(field.getWordPath()));
            values.put("documentPath", xml(config.getDocumentPath()));
            values.put("containerPath", xml(field.getContainerPath()));
            wordChecks.add("exists((" + config.getDocumentPath() + ") ! (" + field.getContainerPath()
                    + ") ! (" + field.getWordPath() + "))");
            values.put("wordValue", annotationPath(word));
            values.put("lemma", lemma == null || lemma == word || lemma.getValuePath() == null ? ""
                    : render(LEMMA, Map.of("lemmaValue", annotationPath(lemma))));

            if (field.getInlineTags().isEmpty()) {
                // Without inline formatting, render the XPath sequence directly. A match pattern
                // cannot represent arbitrary XPath, and XML traversal would discard token order.
                values.put("punctBefore", xml(Objects.requireNonNullElse(field.getPunctBeforePath(),
                        "if (empty($previousWord)) then '' else ' '")));
                // Keep the old separating space between containers/fields unless punctuation is explicit.
                values.put("punctAfter", xml(Objects.requireNonNullElse(field.getPunctAfterLastWordPath(),
                        field.hasExplicitPunctuation() ? "''" : "' '")));
                fields.append(render(ORDERED_FIELD, values));
            } else {
                // Preserve structural rendering for formats with inline formatting. These still
                // require match-compatible paths and document-order words, as before.
                values.put("wordMatch", xml(XPathUtil.joinXpath(config.getDocumentPath(),
                        field.getContainerPath(), field.getWordPath())));
                fields.append(render(STRUCTURAL_FIELD, values));
                for (var tag : field.getInlineTags()) {
                    String cssClass = tag.getDisplayAs().isEmpty()
                            ? tag.getPath().replaceAll("\\b\\w+:", "").replaceAll("\\W+", " ").trim().replace(" ", "-")
                            : tag.getDisplayAs();
                    fields.append(render(INLINE, Map.of("id", values.get("id"), "cssClass", xml(cssClass),
                            "match", xml(XPathUtil.joinXpath(config.getDocumentPath(),
                                    field.getContainerPath(), tag.getPath())))));
                }
            }
            fields.append(render(WORD, values));
            calls.append(render("<xsl:call-template name=\"field-${id}\"/>\n", values));
        }
        return render(STYLESHEET, Map.of("namespaces", namespaceAttributes.toString(),
                "stripNamespaces", config.getNamespaces().isEmpty() ? "true()" : "false()",
                "hasWords", xml(wordChecks.isEmpty() ? "false()" : String.join(" or ", wordChecks)),
                "fields", fields.toString(), "calls", calls.toString()));
    }
}
