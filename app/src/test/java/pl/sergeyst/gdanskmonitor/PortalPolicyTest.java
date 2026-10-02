package pl.sergeyst.gdanskmonitor;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;

public class PortalPolicyTest {
    private JSONArray fields(boolean notes) throws Exception {
        JSONArray fields = new JSONArray();
        for (String value : new String[]{"Example User", "WSC-EXAMPLE", "2026-03-30", "Złożenie wniosku", "Example description"})
            fields.put(new JSONObject().put("value", value));
        if (notes) fields.put(new JSONObject().put("value", "Example note"));
        fields.put(new JSONObject().put("value", "Example document"));
        return fields;
    }

    @Test public void translationExcludesIdentityAndCredentials() {
        for (String key : new String[]{"name", "caseNumber", "filedDate", "password", "login", "", "unknown"})
            assertFalse(key, PortalTranslator.translatable(key));
        for (String key : new String[]{"stage", "stageDescription", "notes", "documents"})
            assertTrue(key, PortalTranslator.translatable(key));
    }
    @Test public void minimumIntervalIsEnforced() {
        assertFalse(MonitorSchedule.validInterval(0)); assertFalse(MonitorSchedule.validInterval(14));
        assertTrue(MonitorSchedule.validInterval(15)); assertTrue(MonitorSchedule.validInterval(1440));
        assertFalse(MonitorSchedule.validInterval(1441));
        for (long interval : MonitorSchedule.INTERVALS) assertTrue(MonitorSchedule.validInterval(interval));
    }
    @Test public void emptyNotesDoNotShiftDocuments() throws Exception {
        JSONArray fields = fields(false);
        assertEquals("stageDescription", MonitorEngine.fieldKey(fields, 4));
        assertEquals("documents", MonitorEngine.fieldKey(fields, 5));
    }
    @Test public void populatedNotesHaveSeparateKey() throws Exception {
        JSONArray fields = fields(true);
        assertEquals("notes", MonitorEngine.fieldKey(fields, 5));
        assertEquals("documents", MonitorEngine.fieldKey(fields, 6));
    }
    @Test public void explicitKeysTakePrecedence() throws Exception {
        JSONArray fields = fields(false); fields.getJSONObject(5).put("key", "notes");
        assertEquals("notes", MonitorEngine.fieldKey(fields, 5));
        assertEquals("", MonitorEngine.fieldKey(new JSONArray(), 0));
    }
    @Test public void translationsAndLabelsDoNotModifyOriginalSnapshot() throws Exception {
        JSONObject original = new JSONObject().put("fields", fields(false));
        String before = original.toString();
        JSONObject display = new JSONObject(before); display.getJSONArray("fields").getJSONObject(3).put("value", "Translated example");
        assertEquals(before, original.toString());
        Method canonical = MonitorEngine.class.getDeclaredMethod("canonical", JSONObject.class); canonical.setAccessible(true);
        JSONObject relabeled = new JSONObject(before); relabeled.getJSONArray("fields").getJSONObject(3).put("key", "stage").put("label", "Stage");
        assertEquals(canonical.invoke(null, original), canonical.invoke(null, relabeled));
        assertNotEquals(canonical.invoke(null, original), canonical.invoke(null, display));
    }
}
