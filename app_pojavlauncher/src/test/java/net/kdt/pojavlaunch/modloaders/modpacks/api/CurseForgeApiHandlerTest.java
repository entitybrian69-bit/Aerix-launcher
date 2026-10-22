package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.*;
import org.junit.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class CurseForgeApiHandlerTest {
    private static JsonElement json(String value) { return JsonParser.parseString(value); }

    @Test public void wrappedCategoriesAreUnwrappedInsteadOfParsedAsRawArray() {
        CurseForgeApiHandler handler = new CurseForgeApiHandler(null, (base, key, path, query) ->
                json("{\"data\":[{\"id\":1,\"name\":\"Adventure\"}]}"));
        assertEquals("Adventure", handler.get("categories", JsonArray.class).get(0).getAsJsonObject().get("name").getAsString());
    }
    @Test public void wrappedMinecraftVersionsAreUnwrapped() {
        CurseForgeApiHandler handler = new CurseForgeApiHandler(null, (base, key, path, query) ->
                json("{\"data\":[{\"versionString\":\"1.21.4\"}]}"));
        assertEquals(1, handler.get("minecraft/version", JsonArray.class).size());
    }
    @Test public void rawMirrorArraysRemainSupported() {
        CurseForgeApiHandler handler = new CurseForgeApiHandler(null, (base, key, path, query) -> json("[]"));
        assertEquals(0, handler.get("categories", JsonArray.class).size());
        assertTrue(handler.get("mods/123/files", JsonObject.class).get("data").isJsonArray());
    }
    @Test public void validOfficialSearchKeepsPaginationAndDoesNotContactMirror() {
        List<String> bases = new ArrayList<>();
        CurseForgeApiHandler handler = new CurseForgeApiHandler("fixture-key", (base, key, path, query) -> {
            bases.add(base);
            return json("{\"data\":[],\"pagination\":{\"totalCount\":0}}");
        });
        assertNotNull(handler.get("mods/search", JsonObject.class).get("pagination"));
        assertEquals(Collections.singletonList(CurseForgeApiHandler.OFFICIAL), bases);
    }
    @Test public void absentBlankAndDummyKeysGoStraightToMirrorWithoutCredential() {
        for (String key : new String[]{null, "", "  ", "DUMMY", " dummy "}) {
            CurseForgeApiHandler handler = new CurseForgeApiHandler(key, (base, sentKey, path, query) -> {
                assertEquals(CurseForgeApiHandler.MIRROR, base); assertNull(sentKey);
                return json("{\"data\":[]}");
            });
            assertNotNull(handler.get("mods/search", JsonObject.class));
        }
    }
    @Test public void failedOfficialRequestFallsBackWithoutForwardingKeyOrLosingFilters() {
        HashMap<String, Object> filters = new HashMap<>();
        filters.put("gameVersion", "1.21.4"); filters.put("modLoaderType", 4); filters.put("index", 20);
        List<String> requests = new ArrayList<>();
        CurseForgeApiHandler handler = new CurseForgeApiHandler(" fixture-key ", (base, key, path, query) -> {
            requests.add(base); assertEquals(filters, query);
            if (base.equals(CurseForgeApiHandler.OFFICIAL)) { assertEquals("fixture-key", key); throw new IOException("fixture HTTP 403"); }
            assertNull(key); return json("{\"data\":[]}");
        });
        assertNotNull(handler.get("mods/search", filters, JsonObject.class));
        assertEquals(Arrays.asList(CurseForgeApiHandler.OFFICIAL, CurseForgeApiHandler.MIRROR), requests);
    }
    @Test public void malformedSuccessResponseDoesNotPreventMirrorFallback() {
        CurseForgeApiHandler handler = new CurseForgeApiHandler("fixture-key", (base, key, path, query) ->
                base.equals(CurseForgeApiHandler.OFFICIAL) ? json("{\"message\":\"denied\"}") : json("{\"data\":[]}"));
        assertNotNull(handler.get("mods/search", JsonObject.class));
    }
    @Test public void wrongDataTypeFallsBack() {
        CurseForgeApiHandler handler = new CurseForgeApiHandler("fixture-key", (base, key, path, query) ->
                base.equals(CurseForgeApiHandler.OFFICIAL) ? json("{\"data\":{}}") : json("{\"data\":[]}"));
        assertNotNull(handler.get("mods/123/files", JsonObject.class));
    }
    @Test public void totalFailureIsNotInventedEmptySuccess() {
        CurseForgeApiHandler handler = new CurseForgeApiHandler("fixture-key", (base, key, path, query) -> json("{\"data\":null}"));
        assertNull(handler.get("mods/search", JsonObject.class));
    }
    @Test public void cancellationIsNotSwallowedOrRetried() {
        int[] count = {0};
        CurseForgeApiHandler handler = new CurseForgeApiHandler("fixture-key", (base, key, path, query) -> {
            count[0]++; throw new CancellationException();
        });
        try { handler.get("mods/search", JsonObject.class); fail(); }
        catch (CancellationException expected) { assertEquals(1, count[0]); }
    }
    @Test public void detailsAndDownloadUrlKeepTheirObjectEnvelopes() {
        CurseForgeApiHandler handler = new CurseForgeApiHandler(null, (base, key, path, query) ->
                path.endsWith("download-url") ? json("{\"data\":\"https://example.test/file.jar\"}") : json("{\"data\":{\"id\":123}}"));
        assertEquals(123, handler.get("mods/123", JsonObject.class).getAsJsonObject("data").get("id").getAsInt());
        assertEquals("https://example.test/file.jar", handler.get("mods/123/files/456/download-url", JsonObject.class).get("data").getAsString());
    }
}
