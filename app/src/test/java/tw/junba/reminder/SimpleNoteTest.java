package tw.junba.reminder;

import org.json.*;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class SimpleNoteTest {
    private final ZoneId zone=ZoneId.of("Asia/Taipei");
    private final long now=Instant.parse("2026-10-16T08:00:30Z").toEpochMilli();
    private SimpleNote draft(){return SimpleNote.create(" 吃藥 "," 飯後一顆 ",2026,10,17,9,5,now,zone);}

    @Test public void keepsNameContentAndAbsoluteDateForHumanVerification(){
        SimpleNote d=draft();assertEquals("吃藥",d.title);assertEquals("飯後一顆",d.content);
        assertEquals(9,d.hour);assertEquals(5,d.minute);
        assertEquals(Instant.parse("2026-10-17T01:05:00Z").toEpochMilli(),d.time);
        assertEquals("吃藥｜2026/10/17 09:05\n飯後一顆",d.label);
    }
    @Test public void blankFieldsAreIndependent(){
        SimpleNote d=SimpleNote.create(" ",null,2026,10,17,9,5,now,zone);
        assertEquals("無訊息",d.title);assertEquals("無訊息",d.content);
        assertEquals("無訊息｜2026/10/17 09:05",d.label);
        assertEquals("無訊息",SimpleNote.create(null,"內容",2026,10,17,9,5,now,zone).title);
    }
    @Test public void distantDatesAreAllowedBecauseNativeDateIsManuallyVerified(){
        assertTrue(SimpleNote.create("遠期","",2028,2,29,9,5,now,zone).time>now);
    }
    @Test public void impossibleDateAndHourAreRejected(){
        assertThrows(DateTimeException.class,()->SimpleNote.create("","",2027,2,29,9,0,now,zone));
        assertThrows(DateTimeException.class,()->SimpleNote.create("","",2026,10,17,24,0,now,zone));
    }
    @Test public void currentMinuteAndPastCannotBecomeTomorrowAccidentally(){
        assertThrows(IllegalArgumentException.class,()->SimpleNote.create("","",2026,10,16,16,0,now,zone));
        assertThrows(IllegalArgumentException.class,()->SimpleNote.create("","",2026,10,15,16,1,now,zone));
        assertTrue(SimpleNote.create("","",2026,10,16,16,1,now,zone).time>now);
    }
    @Test public void daylightSavingGapAndOverlapRequireAnotherTime(){
        ZoneId ny=ZoneId.of("America/New_York");long before=Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        assertThrows(IllegalArgumentException.class,()->SimpleNote.create("","",2026,3,8,2,30,before,ny));
        assertThrows(IllegalArgumentException.class,()->SimpleNote.create("","",2026,11,1,1,30,before,ny));
    }
    @Test public void legacyAdvanceBecomesTheActualAlarmTime() throws Exception {
        JSONObject old=new JSONObject().put("title","開會").put("time",draft().time).put("ahead",30);
        SimpleNote d=SimpleNote.fromStored(old,now,zone);
        assertEquals(8,d.hour);assertEquals(35,d.minute);assertEquals("開會",d.content);
    }
    @Test public void blankOptionalBodyDoesNotDuplicateTheName() throws Exception {
        JSONObject old=new JSONObject().put("title","名稱").put("content","無訊息").put("time",draft().time);
        assertEquals("名稱｜2026/10/17 09:05",SimpleNote.fromStored(old,now,zone).label);
    }
    @Test public void newNoteDoesNotScheduleJunbaAndDoesNotMutateInput() throws Exception {
        JSONArray old=new JSONArray("[{\"id\":10025,\"title\":\"舊資料\",\"extra\":{\"v\":1}}]");String snapshot=old.toString();
        SimpleNote.Change c=SimpleNote.prepare(old,10000,null,"token",draft());
        assertTrue(c.created);assertEquals(10026,c.id);assertEquals(10027,c.nextId);
        assertEquals(2,c.notes.length());assertFalse(c.note.getBoolean("remind"));
        assertEquals(0,c.note.getInt("ahead"));assertEquals("不重複",c.note.getString("repeat"));
        assertEquals(1,c.notes.getJSONObject(0).getJSONObject("extra").getInt("v"));assertEquals(snapshot,old.toString());
    }
    @Test public void editingKeepsIdUnknownFieldsAndOtherNotes() throws Exception {
        JSONArray old=new JSONArray("[{\"id\":10001,\"remind\":true,\"extra\":\"保留\"},{\"id\":10009,\"title\":\"不變\"}]");
        SimpleNote.Change c=SimpleNote.prepare(old,10010,10001,"edit",draft());
        assertEquals(10001,c.id);assertEquals(2,c.notes.length());assertEquals("保留",c.note.getString("extra"));
        assertEquals("不變",c.notes.getJSONObject(1).getString("title"));assertFalse(c.note.getBoolean("remind"));
        assertTrue(old.getJSONObject(0).getBoolean("remind"));
    }
    @Test public void repeatedSaveTokenCannotCreateOrEditTwice() throws Exception {
        SimpleNote.Change first=SimpleNote.prepare(new JSONArray(),10000,null,"once",draft());
        SimpleNote.Change second=SimpleNote.prepare(first.notes,first.nextId,null,"once",draft());
        assertFalse(second.created);assertEquals(first.id,second.id);assertEquals(1,second.notes.length());
        assertEquals(first.nextId,second.nextId);
    }
    @Test public void missingOriginalAndMissingTokenDoNotCreateDuplicates() throws Exception {
        JSONArray old=new JSONArray();
        assertThrows(IllegalArgumentException.class,()->SimpleNote.prepare(old,10000,555,"edit",draft()));
        assertThrows(IllegalArgumentException.class,()->SimpleNote.prepare(old,10000,null,"",draft()));
        assertEquals(0,old.length());
    }
    @Test public void exhaustedIdsNeverWrapButExistingNoteCanStillBeEdited() throws Exception {
        JSONArray old=new JSONArray().put(new JSONObject().put("id",Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->SimpleNote.prepare(old,10000,null,"new",draft()));
        SimpleNote.Change edit=SimpleNote.prepare(old,10000,Integer.MAX_VALUE,"edit",draft());
        assertEquals(Integer.MAX_VALUE,edit.id);assertEquals(Integer.MAX_VALUE,edit.nextId);assertEquals(1,edit.notes.length());
    }
}
