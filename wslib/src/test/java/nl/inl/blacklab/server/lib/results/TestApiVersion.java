package nl.inl.blacklab.server.lib.results;

import org.junit.Assert;
import org.junit.Test;

public class TestApiVersion {
    @Test
    public void testApiVersion() {
        Assert.assertEquals(ApiVersion.TEST_V0_LATEST, ApiVersion.fromValue("v0"));
        Assert.assertEquals(ApiVersion.TEST_V0_2_EXP, ApiVersion.fromValue("v0.2-exp"));

        Assert.assertEquals(ApiVersion.CURRENT, ApiVersion.fromValue("cur"));
        Assert.assertEquals(ApiVersion.CURRENT, ApiVersion.fromValue("current"));
        Assert.assertEquals(ApiVersion.EXPERIMENTAL, ApiVersion.fromValue("exp"));
        Assert.assertEquals(ApiVersion.EXPERIMENTAL, ApiVersion.fromValue("experimental"));
        Assert.assertEquals(ApiVersion.V4_LATEST, ApiVersion.fromValue("v4"));
        Assert.assertEquals(ApiVersion.V4_0, ApiVersion.fromValue("4.0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonExistent() {
        ApiVersion.fromValue("3.0-doesntexist");
    }
}
