package net.kdt.pojavlaunch.modloaders.modpacks.api;

import org.junit.Test;
import java.net.*;
import java.util.*;
import static org.junit.Assert.*;

public class ApiConnectionPolicyTest {
    static class Connection extends HttpURLConnection {
        Connection() throws Exception { super(new URL("https://example.test/api")); }
        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
    }
    @Test public void apiGetsHaveFiniteTimeoutsAndHonestClientIdentity() throws Exception {
        Connection connection = new Connection();
        ApiHandler.configureGetConnection(connection, null);
        assertEquals(8000, connection.getConnectTimeout());
        assertEquals(15000, connection.getReadTimeout());
        assertEquals("application/json", connection.getRequestProperty("Accept"));
        assertTrue(connection.getRequestProperty("User-Agent").startsWith("AerixLauncher/"));
        assertTrue(connection.getInstanceFollowRedirects());
    }
    @Test public void authenticatedGetCannotRedirectItsKeyToAnotherHost() throws Exception {
        Connection connection = new Connection();
        ApiHandler.configureGetConnection(connection, Collections.singletonMap("x-api-key", "fixture-key"));
        assertFalse(connection.getInstanceFollowRedirects());
        assertEquals("fixture-key", connection.getRequestProperty("x-api-key"));
    }
}
