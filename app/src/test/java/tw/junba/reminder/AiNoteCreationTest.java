package tw.junba.reminder;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiNoteCreationTest {
    private AiReminderDraft draft() throws Exception {return AiReminderDraftTest.draft(AiReminderDraftTest.meeting());}
    @Test public void storesAiFieldsAndOnlyChosenLocalSchedulingFlag() throws Exception {
        AiNoteCreation nativeNote=AiNoteCreation.prepare(new JSONArray(),10000,"draft-1",draft(),false);
        assertTrue(nativeNote.created);assertFalse(nativeNote.note.getBoolean("remind"));
        assertEquals(30,nativeNote.note.getInt("ahead"));assertEquals("不重複",nativeNote.note.getString("repeat"));
        assertEquals("帶報告",nativeNote.note.getString("content"));
    }
    @Test public void repeatedConfirmationIsIdempotent() throws Exception {
        AiNoteCreation one=AiNoteCreation.prepare(new JSONArray(),10000,"same-draft",draft(),true);
        AiNoteCreation retry=AiNoteCreation.prepare(one.notes,one.nextId,"same-draft",draft(),true);
        assertFalse(retry.created);assertEquals(one.id,retry.id);assertEquals(1,retry.notes.length());
    }
    @Test public void differentDraftCanIntentionallyCreateSameContent() throws Exception {
        AiNoteCreation one=AiNoteCreation.prepare(new JSONArray(),10000,"draft-1",draft(),true);
        AiNoteCreation two=AiNoteCreation.prepare(one.notes,one.nextId,"draft-2",draft(),true);
        assertTrue(two.created);assertEquals(2,two.notes.length());assertNotEquals(one.id,two.id);
    }
    @Test public void importedHighIdsAreNotOverwrittenAndInputIsUntouched() throws Exception {
        JSONArray old=new JSONArray().put(new JSONObject().put("id",50000).put("title","既有記事").put("extra","保留"));
        String original=old.toString();AiNoteCreation next=AiNoteCreation.prepare(old,10000,"new",draft(),true);
        assertEquals(original,old.toString());assertEquals(50001,next.id);assertEquals(2,next.notes.length());
        assertEquals("保留",next.notes.getJSONObject(0).getString("extra"));
    }
    @Test public void blankNotesCanBeSavedWithoutAlarm() throws Exception {
        AiReminderDraft blank=AiReminderDraft.fromJson(new JSONObject().put("hasReminder",false),"",AiReminderDraftTest.NOW,AiReminderDraftTest.ZONE);
        AiNoteCreation result=AiNoteCreation.prepare(new JSONArray(),10000,"blank",blank,false);
        assertEquals("無訊息",result.note.getString("title"));assertEquals("無訊息",result.note.getString("content"));assertFalse(result.note.getBoolean("remind"));
    }
    @Test(expected=IllegalArgumentException.class) public void missingDraftIdentityCannotWrite() throws Exception {
        AiNoteCreation.prepare(new JSONArray(),10000,"",draft(),true);
    }
    @Test(expected=IllegalArgumentException.class) public void idOverflowCannotOverwrite() throws Exception {
        AiNoteCreation.prepare(new JSONArray().put(new JSONObject().put("id",Integer.MAX_VALUE)),10000,"new",draft(),true);
    }
}
