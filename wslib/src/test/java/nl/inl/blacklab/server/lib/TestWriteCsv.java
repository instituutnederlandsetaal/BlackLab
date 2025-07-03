package nl.inl.blacklab.server.lib;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Assert;
import org.junit.Test;

public class TestWriteCsv {

    void assertEscapesTo(String expected, String... values) throws IOException {
        final StringWriter csvContent = new StringWriter();
        final CSVPrinter csvPrinter = new CSVPrinter(csvContent, CSVFormat.DEFAULT);
        csvPrinter.printRecord(WriteCsv.escape(values));
        final String result = csvContent.toString().trim();
        Assert.assertEquals(expected, result);
    }

    @Test
    public void testEscape() throws IOException {
        assertEscapesTo("TAS", "TAS");
        assertEscapesTo("A|B", "A", "B");
        assertEscapesTo("A C|B", "A C", "B");
        assertEscapesTo("\"A\"\"C|B\"", "A\"C", "B");
        assertEscapesTo("\"A,C|B\"", "A,C", "B");
        assertEscapesTo("A;C|B", "A;C", "B");
        assertEscapesTo("A\\nC|B", "A\nC", "B");
        assertEscapesTo("A\\rC|B", "A\rC", "B");
        assertEscapesTo("A\\\\C|B", "A\\C", "B");
    }
}
