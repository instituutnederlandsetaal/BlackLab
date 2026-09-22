package nl.inl.blacklab.indexers.config.saxon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.BeforeClass;
import org.junit.Test;

import net.sf.saxon.Controller;
import net.sf.saxon.om.DocumentKey;
import net.sf.saxon.om.DocumentPool;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import nl.inl.blacklab.search.BlackLab;

public class TestSaxonHelperDocCache {

    @BeforeClass
    public static void beforeClass() {
        BlackLab.implicitInstance(); // init plugin system (includes IndexSourceType "test")
    }

    @Test
    public void testClearDocUrisLoadedByCurrentThread() throws Exception {
        String uri = "test:word";
        XPathCompiler compiler = SaxonHelper.newXPathFactory();
        compiler.declareVariable(new QName("u"));
        XPathSelector selector = compiler.compile("string(doc($u)/TEI/text/w)").load();
        selector.setVariable(new QName("u"), new XdmAtomicValue(uri));
        XdmItem result = selector.evaluateSingle();
        assertEquals("word", result == null ? null : result.getStringValue());

        Controller controller = selector.getUnderlyingXPathContext().getXPathContextObject().getController();
        DocumentPool pool = controller.getDocumentPool();
        assertNotNull(pool.find(new DocumentKey(uri)));

        SaxonHelper.registerControllerForCurrentThread(controller);
        SaxonHelper.clearDocUrisLoadedByCurrentThread();
        assertNull(controller.getDocumentPool().find(new DocumentKey(uri)));
    }
}
