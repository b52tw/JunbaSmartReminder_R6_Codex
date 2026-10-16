package tw.junba.reminder;

import java.time.ZonedDateTime;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeminiNoteParserTest {
    @Test public void transcriptToNoteToStockAlarmKeepsReminderSemantics() throws Exception {
        ZonedDateTime now=ZonedDateTime.parse("2026-10-16T10:00:00+08:00[Asia/Taipei]");
        JSONObject expected=AiReminderDraftTest.meeting().put("repeat","每週");
        JSONObject parsed=GeminiNoteParser.parse((method,path,request)->{
            String prompt=request.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text");
            assertTrue(prompt.contains("2026-10-16T10:00+08:00"));assertTrue(prompt.contains("Asia/Taipei"));
            assertTrue(prompt.contains("明天下午兩點開會"));
            assertEquals("application/json",request.getJSONObject("generationConfig").getString("responseMimeType"));
            return GeminiApiTest.response(expected.toString());
        },"gemini-selected","明天下午兩點開會，提前三十分鐘提醒，每週一次",now);
        AiReminderDraft draft=AiReminderDraft.fromJson(parsed,"原文",now.toInstant().toEpochMilli(),now.getZone());
        AiNoteCreation note=AiNoteCreation.prepare(new JSONArray(),10000,"confirmed",draft,false);
        NativeAlarmPlan plan=NativeAlarmPlan.create(draft.reminderTime(),draft.repeat,now.toInstant().toEpochMilli(),now.getZone());
        assertEquals(13,plan.hour);assertEquals(30,plan.minute);assertEquals("每週六",plan.ruleLabel());
        assertEquals(30,note.note.getInt("ahead"));assertEquals("每週",note.note.getString("repeat"));
        assertFalse(note.note.getBoolean("remind")); // No duplicate Junba alarm when stock is selected.
    }
    @Test(expected=JSONException.class) public void malformedAiOutputDoesNotBecomeANote() throws Exception {
        GeminiNoteParser.parse((m,p,b)->GeminiApiTest.response("無法辨識"),"gemini-selected","原文",ZonedDateTime.now());
    }
}
