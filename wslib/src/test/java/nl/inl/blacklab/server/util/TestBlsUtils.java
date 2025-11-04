package nl.inl.blacklab.server.util;

import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternWildcard;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;

public class TestBlsUtils {

    final BlackLabIndex index = new MockBlackLabIndex();

    @Test
    public void testIsValidIndexName() {
        Assert.assertTrue(Index.isValidIndexName("user@example.com:my_index_name"));
        Assert.assertTrue(Index.isValidIndexName("user@example.com:my-index-name1"));
        Assert.assertTrue(Index.isValidIndexName("user@example.com:a1"));
        Assert.assertTrue(Index.isValidIndexName("0abd"));
        Assert.assertFalse(Index.isValidIndexName("user@example.com:"));
        Assert.assertFalse(Index.isValidIndexName("user@example.com:bla:bla"));
        Assert.assertFalse(Index.isValidIndexName("user@example.com:bla:"));
        Assert.assertFalse(Index.isValidIndexName(":user@example.com:bla"));
        Assert.assertFalse(Index.isValidIndexName(":user@example.com:bla:"));
        Assert.assertFalse(Index.isValidIndexName(""));
        Assert.assertFalse(Index.isValidIndexName("a*b"));
        Assert.assertFalse(Index.isValidIndexName("a/b"));
    }

    @Test
    public void testParsePatt() throws BlsException {
        TextPattern pattThe = new TextPatternRegex("the");
        Assert.assertEquals(pattThe, BlsUtils.parsePatt(index, "\"the\"", "bcql"));
    }

    @Test
    public void testParsePattContextQL() throws BlsException {
        TextPattern pattThe = new TextPatternWildcard("the", "word", null);
        Assert.assertEquals(pattThe, BlsUtils.parsePatt(index, "\"the\"", "contextql"));
    }

    @Test(expected = BadRequest.class)
    public void testParsePattWrongLanguage() throws BlsException {
        BlsUtils.parsePatt(index, "\"the\"", "swahili");
    }

    @Test(expected = BadRequest.class)
    public void testParsePattNoPattern() throws BlsException {
        BlsUtils.parsePatt(index, "", "bcql");
    }

    @Test
    public void testParseFilter() throws BlsException {
        Query f = new TermQuery(new Term("author", "me"));
        Assert.assertEquals(f, BlsUtils.parseFilter(index, "author:me", "luceneql"));
        Assert.assertEquals(f, BlsUtils.parseFilter(index, "author:me", "luceneql", true));
    }

    @Test
    public void testParseFilterContextQl() throws BlsException {
        Query f = new WildcardQuery(new Term("author", "me"));
        Assert.assertEquals(f, BlsUtils.parseFilter(index, "author = me", "contextql"));
    }

    @Test(expected = BadRequest.class)
    public void testParseFilterWrongLanguage() throws BlsException {
        BlsUtils.parseFilter(index, "author:me", "bcql");
    }

    @Test(expected = BadRequest.class)
    public void testParseFilterNoFilter() throws BlsException {
        BlsUtils.parseFilter(index, "", "luceneql", true);
    }

    @Test
    public void testDescribeInterval() {
        Assert.assertEquals("1s", BlsUtils.describeIntervalSec(1));
        Assert.assertEquals("5m", BlsUtils.describeIntervalSec(300));
        Assert.assertEquals("5m01s", BlsUtils.describeIntervalSec(301));
    }

    @Test
    public void testWildcardIpMatches() {
        Assert.assertTrue(BlsUtils.wildcardIpMatches("1.2.3.*", "1.2.3.4"));
        Assert.assertTrue(BlsUtils.wildcardIpMatches("1.2.*.4", "1.2.3.4"));
        Assert.assertTrue(BlsUtils.wildcardIpMatches("*.2.3.4", "1.2.3.4"));
        Assert.assertTrue(BlsUtils.wildcardIpMatches("1.2.3.4", "1.2.3.4"));

        Assert.assertFalse(BlsUtils.wildcardIpMatches("1.2.3.*", "112.3.4"));
        Assert.assertFalse(BlsUtils.wildcardIpMatches("1.2.3.4", "112.3.4"));
        Assert.assertFalse(BlsUtils.wildcardIpMatches("1.2.3.4", "1.2.3.5"));

        final String ipv6local = "0:0:0:0:0:0:0:1";
        Assert.assertTrue(BlsUtils.wildcardIpMatches(ipv6local, ipv6local));

        List<String> adr = List.of("127.0.0.1", ipv6local, "172.16.10.19");
        Assert.assertTrue(BlsUtils.wildcardIpsContain(adr, ipv6local));
    }

}
