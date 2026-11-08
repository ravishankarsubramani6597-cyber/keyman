package com.keyman.engine;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Map;

/** Optional Android JSON boundary: one record, no transport or storage. */
public final class KeySenseTypingSessionV1Json {
    private KeySenseTypingSessionV1Json() { }

    public static JSONObject toJsonObject(KeySenseTypingSessionV1 record)
            throws JSONException {
        if (record == null) throw new IllegalArgumentException("Record required");
        JSONObject result = new JSONObject();
        for (Map.Entry<String, Object> entry : record.toFieldMap().entrySet()) {
            result.put(entry.getKey(),
                    entry.getValue() == null ? JSONObject.NULL : entry.getValue());
        }
        return result;
    }
}
