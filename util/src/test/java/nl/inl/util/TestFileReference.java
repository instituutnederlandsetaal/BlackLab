package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestFileReference {

    @Test
    public void testCharArray() {
        FileReference ref = FileReference.fromCharArray("path", new char[] {'a', 'b', 'c'}, null);
        StringBuilder b = new StringBuilder();
        ref.getTextContent(0, -1).appendToStringBuilder(b);
        Assert.assertEquals(3, b.length());
        Assert.assertEquals('a', b.charAt(0));
        Assert.assertEquals('b', b.charAt(1));
        Assert.assertEquals('c', b.charAt(2));
    }

}
