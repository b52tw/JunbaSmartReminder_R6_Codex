package tw.junba.reminder;

import java.time.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiReminderDraftTest {
    static final ZoneId ZONE=ZoneId.of("Asia/Taipei");
    static final long NOW=ZonedDateTime.parse("2026-10-16T10:00:00+08:00").toInstant().toEpochMilli();
    static JSONObject meeting() throws Exception {
        return new JSONObject().put("title","開會").put("content","帶報告")
            .put("dateTime","2026-10-17 14:00").put("advanceMinutes",30).put("repeat","不重複").put("hasReminder",true);
    }
    static AiReminderDraft draft(JSONObject json) {return AiReminderDraft.fromJson(json,"原始口述",NOW,ZONE);}
    @Test public void preservesEventAndActualReminderTime() throws Exception {
        AiReminderDraft d=draft(meeting());assertTrue(d.hasReminder);assertEquals(30,d.advanceMinutes);
        assertEquals(ZonedDateTime.parse("2026-10-17T14:00:00+08:00").toInstant().toEpochMilli(),d.eventTime);
        assertEquals(ZonedDateTime.parse("2026-10-17T13:30:00+08:00").toInstant().toEpochMilli(),d.reminderTime());
    }
    @Test public void preservesDailyWeeklyAndMonthly() throws Exception {
        for(String r:new String[]{"每日","每週","每月"}){
            AiReminderDraft d=draft(meeting().put("repeat",r));assertTrue(d.hasReminder);assertEquals(r,d.repeat);
        }
    }
    @Test public void supportsFiveDaysAdvance() throws Exception {
        AiReminderDraft d=draft(meeting().put("dateTime","2026-10-22 14:00").put("advanceMinutes",7200));
        assertTrue(d.hasReminder);assertEquals(ZonedDateTime.parse("2026-10-17T14:00:00+08:00").toInstant().toEpochMilli(),d.reminderTime());
    }
    @Test public void doesNotGuessAmbiguousTime() throws Exception {
        AiReminderDraft d=draft(meeting().put("hasReminder",false).put("dateTime","").put("clarification","明天下午幾點？"));
        assertFalse(d.hasReminder);assertEquals("明天下午幾點？",d.issue);
    }
    @Test public void ordinaryNoteDoesNotAcquireAlarm() throws Exception {
        AiReminderDraft d=draft(new JSONObject().put("hasReminder",false).put("content","今天學到的事"));
        assertFalse(d.hasReminder);assertTrue(d.issue.isEmpty());assertEquals("今天學到的事",d.content);
    }
    @Test public void blankTitleAndBodyRemainSavable() throws Exception {
        AiReminderDraft d=AiReminderDraft.fromJson(new JSONObject().put("hasReminder",false).put("title","").put("content",""),"",NOW,ZONE);
        assertEquals("無訊息",d.title);assertEquals("無訊息",d.content);
    }
    @Test public void missingBodyKeepsTranscript() throws Exception {
        JSONObject j=meeting();j.remove("content");assertEquals("原始口述",draft(j).content);
    }
    @Test public void negativeDecimalOrStringAdvanceNeedsClarification() throws Exception {
        for(Object v:new Object[]{-1,1.5,"30","Infinity"}){
            AiReminderDraft d=draft(meeting().put("advanceMinutes",v));assertFalse(d.hasReminder);assertFalse(d.issue.isEmpty());
        }
    }
    @Test public void rejectsInvalidDateAndTrailingText() throws Exception {
        for(String value:new String[]{"2026-02-30 14:00","2026-10-17","2026-10-17 14:00 garbage",""}){
            AiReminderDraft d=draft(meeting().put("dateTime",value));assertFalse(d.hasReminder);assertFalse(d.issue.isEmpty());
        }
    }
    @Test public void eventInFutureButReminderPastNeedsClarification() throws Exception {
        AiReminderDraft d=draft(meeting().put("dateTime","2026-10-16 10:15"));assertFalse(d.hasReminder);assertFalse(d.issue.isEmpty());
    }
    @Test public void previewMustBeRevalidatedAfterTimePasses() throws Exception {
        JSONObject j=meeting();AiReminderDraft before=draft(j);
        assertTrue(before.hasReminder);
        assertFalse(AiReminderDraft.fromJson(j,"原文",before.reminderTime()+1,ZONE).hasReminder);
    }
    @Test public void unknownRuleAndMalformedFlagAreNotSilentlyAccepted() throws Exception {
        assertFalse(draft(meeting().put("repeat","每小時")).issue.isEmpty());
        assertFalse(draft(meeting().put("hasReminder","true")).issue.isEmpty());
    }
}
