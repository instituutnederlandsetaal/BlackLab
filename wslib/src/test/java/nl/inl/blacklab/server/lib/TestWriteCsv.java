package nl.inl.blacklab.server.lib;

import java.io.IOException;
import java.io.StringWriter;
import java.util.EnumMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.ResultSummaryCommonFields;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumHits;
import nl.inl.blacklab.webservice.WsParam;

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

    @Test
    public void testSummaryIncludesCsvDescriptionAtTop() throws IOException, ReflectiveOperationException {
        Map<WsParam, Object> parameterValues = new EnumMap<>(WsParam.class);
        parameterValues.put(WsParam.CSV_DESCRIPTION, "Export for issue 655");
        parameterValues.put(WsParam.WAIT_FOR_TOTAL_COUNT, true);
        QueryParamsMap params = new QueryParamsMap("test-index", null, parameterValues, null, null, true);
        ResultSummaryNumHits summaryNumHits = new ResultSummaryNumHits(new ResultsStatsSaved(0), new ResultsStatsSaved(0),
                true, null, null);
        ResultSummaryCommonFields summaryFields = new ResultSummaryCommonFields(null, null, null, null, null, null,
                null, null, params, null, summaryNumHits);

        final StringWriter csvContent = new StringWriter();
        final CSVPrinter csvPrinter = new CSVPrinter(csvContent, CSVFormat.DEFAULT);
        WriteCsv.addSummaryCsvCommon(csvPrinter, 2,
                ResponseStreamer.get(Mockito.mock(DataStream.class), ApiVersion.CURRENT),
                summaryFields, summaryNumHits);
        csvPrinter.flush();

        Assert.assertArrayEquals(new String[] {
                "summary.description,Export for issue 655",
                "summary.params.waitfortotal,true"
        }, csvContent.toString().trim().split("\\R"));
        Assert.assertFalse(csvContent.toString().contains("summary.params.csvdescription"));
    }
}
