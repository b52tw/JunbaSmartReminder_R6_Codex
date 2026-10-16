package tw.junba.reminder;

import org.junit.Test;
import org.json.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class NativeAlarmModelTest {
    private final long now=Instant.parse("2026-10-17T01:00:00Z").toEpochMilli();
    private JSONObject source() throws Exception {return new JSONObject().put("noteId",42).put("title","看診\n帶健保卡").put("when",now+3600000);}
    private SimpleNote chosen() {return SimpleNote.create("看診","帶健保卡",2026,10,17,10,0,now,ZoneId.of("Asia/Taipei"));}
    private JSONObject request() throws Exception {return NativeAlarmModel.requested(null,source(),chosen(),now);}
    private CalendarModel.Event event(){CalendarModel.Event e=new CalendarModel.Event(99,now+7200000,now+10800000,new CalendarModel.Source(1,"account","日曆","com.google"),"會議",false);e.minutes.add(30);return e;}

    @Test public void handoffIsPendingAndCannotProveNativeCreation() throws Exception {
        JSONObject r=request();assertEquals("pending",r.getString("status"));assertFalse(NativeAlarmModel.confirmed(r,source()));
        assertEquals(chosen().time,r.getLong("when"));assertTrue(r.getString("label").contains("2026/10/17 10:00"));
    }
    @Test public void manuallyRegisteringExistingClockIsDistinctFromSendingIntent() throws Exception {
        JSONObject r=NativeAlarmModel.registered(null,source(),chosen(),now);
        assertTrue(NativeAlarmModel.confirmed(r,source()));assertEquals("manual-registration",r.getString("origin"));
        assertFalse(r.has("requested"));assertEquals(now,r.getLong("registered"));
        JSONObject pending=NativeAlarmModel.mark(r,source(),"pending",now+1);
        assertTrue(NativeAlarmModel.status(pending,source()).contains("本人登記"));
    }
    @Test public void sendingAfterRegistrationResetsStateAndRetainsRegistrationHistory() throws Exception {
        JSONObject old=NativeAlarmModel.registered(null,source(),chosen(),now);JSONObject next=NativeAlarmModel.requested(old,source(),chosen(),now+1);
        assertEquals("handoff",next.getString("origin"));assertFalse(next.has("registered"));assertFalse(NativeAlarmModel.confirmed(next,source()));
        assertEquals("manual-registration",next.getJSONArray("priorRequests").getJSONObject(0).getString("origin"));
    }
    @Test public void confirmationIsExplicitAndBindsExactSource() throws Exception {
        JSONObject r=NativeAlarmModel.mark(request(),source(),"manual-confirmed",now+10);
        assertTrue(NativeAlarmModel.confirmed(r,source()));assertEquals(now+10,r.getLong("confirmed"));
        assertFalse(NativeAlarmModel.confirmed(r,source().put("when",now+7200000)));
        assertFalse(NativeAlarmModel.confirmed(r,source().put("title","其他記事")));
    }
    @Test public void sourceStatusAndPermissionFlagsDoNotInvalidateNativeReview() throws Exception {
        JSONObject r=NativeAlarmModel.mark(request(),source(),"manual-confirmed",now);
        assertTrue(NativeAlarmModel.confirmed(r,source().put("status","cancelled").put("systemScheduled",false)));
    }
    @Test public void oldConfirmedRecordsArePreservedButMustBeReviewed() throws Exception {
        JSONObject old=new JSONObject().put("key","note:42").put("status","manual-confirmed").put("privateExtension","retain");
        assertFalse(NativeAlarmModel.confirmed(old,source()));assertTrue(NativeAlarmModel.status(old,source()).contains("舊版"));
        JSONObject next=NativeAlarmModel.requested(old,source(),chosen(),now);
        assertEquals("retain",next.getString("privateExtension"));assertEquals("manual-confirmed",next.getJSONArray("priorRequests").getJSONObject(0).getString("status"));
    }
    @Test public void resendingKeepsHistoryAndResetsConfirmationWithoutMutatingOld() throws Exception {
        JSONObject old=NativeAlarmModel.mark(request(),source(),"manual-confirmed",now+1);String before=old.toString();
        JSONObject next=NativeAlarmModel.requested(old,source(),chosen(),now+2);
        assertEquals(before,old.toString());assertFalse(next.has("confirmed"));assertEquals(1,next.getJSONArray("priorRequests").length());
        JSONObject third=NativeAlarmModel.requested(next,source(),chosen(),now+3);
        assertEquals(2,third.getJSONArray("priorRequests").length());assertFalse(third.getJSONArray("priorRequests").getJSONObject(1).has("priorRequests"));
    }
    @Test public void userCanRecordRemovalButCannotConfirmRemovedRequest() throws Exception {
        JSONObject r=NativeAlarmModel.mark(request(),source(),"manual-removed",now+1);
        assertFalse(NativeAlarmModel.confirmed(r,source()));assertTrue(NativeAlarmModel.status(r,source()).contains("本人已標記"));
        try{NativeAlarmModel.mark(r,source(),"manual-confirmed",now+2);fail();}catch(IllegalStateException expected){}
    }
    @Test public void cannotConfirmChangedOrMissingSource() throws Exception {
        for(JSONObject current:new JSONObject[]{source().put("title","已修改"),null}){
            try{NativeAlarmModel.mark(request(),current,"manual-confirmed",now+2);fail();}catch(IllegalStateException expected){}
        }
    }
    @Test public void requestRevisionPreventsConfirmingAnotherHandoffFromOldDialog() throws Exception {
        JSONObject old=request(),newer=NativeAlarmModel.requested(old,source(),chosen(),now+1);
        assertFalse(NativeAlarmModel.sameRequest(newer,old));assertTrue(NativeAlarmModel.sameRequest(old,new JSONObject(old.toString())));
        assertFalse(NativeAlarmModel.sameRequest(old.put("needsReview",true),request()));
    }
    @Test public void sourceSnapshotChangesInvalidateEvenIfLedgerStillHasOldTime() throws Exception {
        JSONObject r=NativeAlarmModel.mark(request(),source(),"manual-confirmed",now+1).put("needsReview",true);
        assertFalse(NativeAlarmModel.confirmed(r,source()));assertTrue(NativeAlarmModel.status(r,source()).contains("已變更"));
    }
    @Test public void replacementRetainsUnknownAndMalformedEntriesAndOldArray() throws Exception {
        JSONArray old=new JSONArray().put("future-schema-value").put(new JSONObject().put("key","other").put("x",7)).put(request());
        String before=old.toString();JSONArray result=NativeAlarmModel.replace(old,NativeAlarmModel.mark(request(),source(),"manual-confirmed",now+1));
        assertEquals(3,result.length());assertEquals("future-schema-value",result.getString(0));assertEquals(7,result.getJSONObject(1).getInt("x"));assertEquals(before,old.toString());
    }
    @Test public void notesAndUnifiedReminderUseSameKeyAndBasis() throws Exception {
        JSONObject note=new JSONObject().put("id",42).put("title","看診").put("content","帶健保卡").put("time",now+3600000);
        assertEquals("note:42",NativeAlarmModel.key(NativeAlarmModel.note(note)));
        assertEquals(NativeAlarmModel.basis(source()),NativeAlarmModel.basis(NativeAlarmModel.note(note)));
    }
    @Test public void sourceEventChangesAndReminderRemovalRequireReview() throws Exception {
        CalendarModel.Event e=event();long time=e.begin-1800000;JSONObject snapshot=NativeAlarmModel.event(e,time);
        Set<String> desired=new HashSet<>(Collections.singleton(snapshot.getString("linkId")));
        assertFalse(NativeAlarmModel.changed(snapshot,e,desired,true));
        assertTrue(NativeAlarmModel.changed(snapshot,e,Collections.emptySet(),true));assertTrue(NativeAlarmModel.changed(snapshot,null,desired,true));
        e.minutes.clear();CalendarModel.Event renamed=new CalendarModel.Event(e.id,e.begin,e.end,e.calendar,"修改名稱",false);
        assertTrue(NativeAlarmModel.changed(snapshot,renamed,desired,true));
        assertTrue(NativeAlarmModel.changed(snapshot,new CalendarModel.Event(e.id,e.begin,e.end,e.calendar,e.title,true),desired,true));
    }
    @Test public void unavailableOptionalAlertRowsDoNotFalselyInvalidate() throws Exception {
        CalendarModel.Event e=event();JSONObject r=NativeAlarmModel.event(e,e.begin-1800000).put("providerTime",true);
        assertFalse(NativeAlarmModel.changed(r,e,Collections.emptySet(),false));
        assertTrue(NativeAlarmModel.changed(r,e,Collections.emptySet(),true));
    }
    @Test public void manualTimesAreIndependentFromProviderReminderChanges() throws Exception {
        CalendarModel.Event e=event();JSONObject r=NativeAlarmModel.event(e,e.begin).put("linkId","manual:"+e.key()+":"+e.begin);
        assertFalse(NativeAlarmModel.changed(r,e,Collections.emptySet(),true));
        JSONObject snoozed=NativeAlarmModel.event(e,e.begin).put("snoozeCount",1);
        assertFalse(NativeAlarmModel.changed(snoozed,e,Collections.emptySet(),true));
    }
    @Test public void queueExcludesTestsReceiptsRemovedPastAndReferenceRows() throws Exception {
        JSONObject formal=source().put("key","a").put("linkId","note:42:time").put("source","note_link").put("status","scheduled").put("linkEnabled",true).put("scheduleAccepted",true);
        JSONArray rows=new JSONArray().put(formal).put(new JSONObject(formal.toString()).put("source","note_test"))
            .put(new JSONObject(formal.toString()).put("linkEnabled",false)).put(new JSONObject(formal.toString()).put("when",now-1))
            .put(new JSONObject(formal.toString()).put("status","reference")).put(new JSONObject(formal.toString()).put("status","received"));
        assertEquals(1,NativeAlarmModel.candidates(rows,now).size());
    }
    @Test public void queueOrdersByTimeAndDoesNotMutateSchedules() throws Exception {
        JSONObject later=source().put("key","a").put("linkId","a").put("source","calendar_link").put("status","scheduled").put("linkEnabled",true).put("scheduleAccepted",true);later.remove("noteId");
        JSONObject earlier=new JSONObject(later.toString()).put("key","b").put("linkId","b").put("when",now+1000);
        JSONArray rows=new JSONArray().put(later).put(earlier);String before=rows.toString();
        assertEquals("b",NativeAlarmModel.candidates(rows,now).get(0).getString("key"));assertEquals(before,rows.toString());
    }
}
