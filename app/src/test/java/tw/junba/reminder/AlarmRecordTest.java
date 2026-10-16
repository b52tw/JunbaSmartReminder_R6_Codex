package tw.junba.reminder;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AlarmRecordTest {
    private final long now=1792144800123L;
    private JSONObject record() throws Exception{return AlarmRecord.create("unique","吃藥","calendar","source",now,now+1000);}
    @Test public void keepsCalendarDeliveryMetadataForLocalHistory() throws Exception {
        JSONObject r=record();assertEquals("吃藥",r.getString("title"));assertEquals("calendar",r.getString("source"));
        assertEquals("source",r.getString("sourceKey"));assertEquals(now,r.getLong("receivedAt"));assertTrue(AlarmRecord.pending(r));
    }
    @Test public void allThreeDelaysUseTheTimeOfTheUsersAction() throws Exception {
        for(int minutes:new int[]{10,30,60}){
            JSONObject old=record();old.put("when",now-86400000L);
            JSONObject next=AlarmRecord.snoozed(old,minutes,now);
            assertEquals(now+minutes*60000L,next.getLong("when"));assertEquals("snoozed",next.getString("status"));
            assertEquals(1,next.getInt("generation"));assertEquals(1,next.getInt("snoozeCount"));
        }
    }
    @Test public void resnoozingReplacesTheTimeAndAdvancesTheDeliveryGeneration() throws Exception {
        JSONObject first=AlarmRecord.snoozed(record(),10,now);
        JSONObject next=AlarmRecord.snoozed(first,60,now+5000);
        assertEquals(now+5000+3600000L,next.getLong("when"));assertEquals(2,next.getInt("generation"));
        assertNull(AlarmRecord.ringing(next,first.getInt("generation"),now+600000,120000));
    }
    @Test public void cancelledAndStoppedRecordsCannotBeDeliveredByStaleAlarms() throws Exception {
        for(String status:new String[]{"cancelled","stopped","failed","blocked","missed","superseded"}){
            JSONObject r=record().put("status",status);
            assertNull(AlarmRecord.ringing(r,0,now+1000,120000));assertFalse(AlarmRecord.pending(r));
        }
    }
    @Test public void duplicateDeliveryDoesNotRestartTheSameRing() throws Exception {
        JSONObject ringing=AlarmRecord.ringing(record(),0,now+1000,120000);
        assertNotNull(ringing);assertEquals(now+121000,ringing.getLong("activeUntil"));
        assertNull(AlarmRecord.ringing(ringing,0,now+5000,120000));
    }
    @Test public void serviceTeardownCannotOverwriteAPersistedSnooze() throws Exception {
        JSONObject ringing=AlarmRecord.ringing(record(),0,now+1000,120000);
        JSONObject snoozed=AlarmRecord.snoozed(ringing,30,now+2000);
        JSONObject afterDestroy=AlarmRecord.finished(snoozed,"stopped",now+3000);
        assertEquals("snoozed",afterDestroy.getString("status"));assertEquals(now+2000+1800000L,afterDestroy.getLong("when"));
    }
    @Test public void stoppingTheCurrentRingDoesNotEraseItsHistory() throws Exception {
        JSONObject ringing=AlarmRecord.ringing(record(),0,now+1000,120000);
        JSONObject stopped=AlarmRecord.finished(ringing,"stopped",now+2000);
        assertEquals("stopped",stopped.getString("status"));assertEquals("吃藥",stopped.getString("title"));
        assertEquals(now,stopped.getLong("receivedAt"));assertEquals("ringing",ringing.getString("status"));
    }
    @Test public void transitionsPreserveUnknownFieldsAndDoNotMutateInput() throws Exception {
        JSONObject old=record().put("futureField",new JSONObject().put("v",3));String snapshot=old.toString();
        JSONObject next=AlarmRecord.snoozed(old,10,now);assertEquals(3,next.getJSONObject("futureField").getInt("v"));
        assertEquals(snapshot,old.toString());
    }
    @Test public void remindersHaveIndependentIdentityAndSchedules() throws Exception {
        JSONObject first=AlarmRecord.snoozed(record(),10,now);
        JSONObject second=AlarmRecord.snoozed(AlarmRecord.create("another","開會","calendar","other",now,now),30,now);
        assertNotEquals(first.getString("key"),second.getString("key"));
        assertEquals(now+600000,first.getLong("when"));assertEquals(now+1800000,second.getLong("when"));
    }
    @Test public void invalidDelaysAndGenerationOverflowAreRejected() throws Exception {
        for(int minutes:new int[]{-1,0,5,11,120})assertThrows(IllegalArgumentException.class,()->AlarmRecord.snoozed(record(),minutes,now));
        JSONObject exhausted=record().put("generation",Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->AlarmRecord.snoozed(exhausted,10,now));
    }
    @Test public void changedSchedulesCannotFireEarly() throws Exception {
        JSONObject snoozed=AlarmRecord.snoozed(record(),60,now);
        assertNull(AlarmRecord.ringing(snoozed,1,now+600000,120000));
        assertNotNull(AlarmRecord.ringing(snoozed,1,now+3600000,120000));
    }
}
