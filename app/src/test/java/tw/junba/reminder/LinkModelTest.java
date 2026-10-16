package tw.junba.reminder;

import org.json.*;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class LinkModelTest {
    private final long now=Instant.parse("2026-10-17T01:00:00Z").toEpochMilli();
    private final CalendarModel.Source calendar=new CalendarModel.Source(1,"示範帳號","個人日曆","com.google");
    private CalendarModel.Event event(){CalendarModel.Event e=new CalendarModel.Event(7,now+3600000,now+7200000,calendar,"會議",false);e.minutes.add(30);e.minutes.add(0);return e;}
    private JSONObject linked() throws Exception {
        CalendarModel.Event e=event();LinkModel.Request request=LinkModel.plan(Arrays.asList(e),now,now+86400000).get(0);
        JSONObject r=AlarmRecord.create("key",e.title,"calendar_link",request.id,now,request.when);
        Iterator<String> keys=request.metadata().keys();JSONObject metadata=request.metadata();while(keys.hasNext()){String key=keys.next();r.put(key,metadata.get(key));}
        return r.put("linkEnabled",true).put("scheduleAccepted",true).put("systemScheduled",true).put("originalWhen",request.when);
    }
    @Test public void futureGoogleRemindersAreLinkedBeforeAnyNotificationArrives(){
        List<LinkModel.Request> plan=LinkModel.plan(Arrays.asList(event()),now,now+86400000);
        assertEquals(2,plan.size());assertEquals(now+1800000,plan.get(0).when);assertEquals(now+3600000,plan.get(1).when);
    }
    @Test public void providerAlarmAndSameExplicitReminderDoNotDuplicate(){
        CalendarModel.Event e=event();e.minutes.add(30);e.alertTimes.add(now+1800000);
        assertEquals(2,LinkModel.plan(Arrays.asList(e,e),now,now+86400000).size());
    }
    @Test public void pastAndOutsideHorizonRemindersAreNotSuddenlyReplayed(){
        assertTrue(LinkModel.plan(Arrays.asList(event()),now+7200000,now+86400000).isEmpty());
        assertTrue(LinkModel.plan(Arrays.asList(event()),now,now+1000).isEmpty());
    }
    @Test public void allDayAndDefaultRemindersRequireAnActualProviderTime(){
        CalendarModel.Event e=new CalendarModel.Event(8,now+86400000,now+2*86400000,calendar,"生日",true);e.minutes.add(-1);e.minutes.add(0);
        assertTrue(LinkModel.plan(Arrays.asList(e),now,now+2*86400000).isEmpty());
        e.alertTimes.add(now+80000000);assertEquals(1,LinkModel.plan(Arrays.asList(e),now,now+2*86400000).size());
        CalendarModel.Event timed=new CalendarModel.Event(9,event().begin,event().end,calendar,"預設",false);timed.minutes.add(-1);
        assertTrue(LinkModel.plan(Arrays.asList(timed),now,now+2*86400000).isEmpty());
    }
    @Test public void planningNeverMutatesSourceReminderSettings(){
        CalendarModel.Event e=event();List<Integer> original=new ArrayList<>(e.minutes);
        LinkModel.plan(Arrays.asList(e),now,now+86400000);assertEquals(original,e.minutes);assertTrue(e.alertTimes.isEmpty());
    }
    @Test public void stableIdsKeepADeletedLinkDisabledAcrossRefreshAndTitleEdits(){
        CalendarModel.Event old=event(),renamed=new CalendarModel.Event(old.id,old.begin,old.end,calendar,"改名",false);renamed.minutes.addAll(old.minutes);
        List<LinkModel.Request> first=LinkModel.plan(Arrays.asList(old),now,now+86400000),next=LinkModel.plan(Arrays.asList(renamed),now,now+86400000);
        Set<String> disabled=new HashSet<>();for(LinkModel.Request request:first)disabled.add(request.id);
        assertTrue(LinkModel.schedulable(next,disabled,250).isEmpty());
        assertEquals(first.get(0).id,next.get(0).id);
    }
    @Test public void disabledLinksDoNotConsumeTheSystemAlarmLimit(){
        List<LinkModel.Request> plan=LinkModel.plan(Arrays.asList(event()),now,now+86400000);
        Set<String> allowed=LinkModel.schedulable(plan,Collections.singleton(plan.get(0).id),1);
        assertEquals(Collections.singleton(plan.get(1).id),allowed);
    }
    @Test public void futureRecurrencesHaveDifferentConnectionIds(){
        CalendarModel.Event a=event(),b=new CalendarModel.Event(a.id,a.begin+7*86400000L,a.end+7*86400000L,calendar,a.title,false);b.minutes.addAll(a.minutes);
        List<LinkModel.Request> requests=LinkModel.plan(Arrays.asList(a,b),now,now+8*86400000L);
        Set<String> ids=new HashSet<>();for(LinkModel.Request r:requests)ids.add(r.id);assertEquals(4,ids.size());
    }
    @Test public void acceptedFutureAlarmLightsRedWithoutAReceivedNotification() throws Exception {
        JSONObject r=linked();assertFalse(r.has("notificationPostTime"));assertTrue(LinkModel.linked(r,now,true));assertEquals(1,LinkModel.eventLamp(new JSONArray().put(r),event(),now,true));
    }
    @Test public void receivedNotificationAloneDoesNotLightRed() throws Exception {
        JSONObject r=event().metadata("manual").put("source","calendar").put("status","received");
        assertFalse(LinkModel.linked(r,now,true));assertEquals(0,LinkModel.eventLamp(new JSONArray().put(r),event(),now,true));
    }
    @Test public void failureDisabledStateAndMissingPermissionsDoNotPretendToBeLinked() throws Exception {
        JSONObject r=linked();assertFalse(LinkModel.linked(r,now,false));r.put("status","failed");assertFalse(LinkModel.linked(r,now,true));
        r=linked().put("linkEnabled",false);assertFalse(LinkModel.linked(r,now,true));
        r=linked().put("systemScheduled",false);assertFalse(LinkModel.linked(r,now,true));
        r=linked().put("scheduleAccepted",false);assertFalse(LinkModel.linked(r,now,true));
    }
    @Test public void cancellingKeepsReceiptAndSourceMetadataButTurnsTheLampOff() throws Exception {
        JSONObject r=linked().put("notificationPostTime",now).put("status","cancelled").put("linkEnabled",false).put("systemScheduled",false);
        assertEquals(event().id,r.getLong("eventId"));assertEquals(now,r.getLong("notificationPostTime"));
        assertEquals(0,LinkModel.eventLamp(new JSONArray().put(r),event(),now,true));
    }
    @Test public void currentlyRingingOccurrenceIsOrangeAndOtherDatesAreUnaffected() throws Exception {
        JSONObject r=AlarmRecord.ringing(linked(),0,now+1800000,120000);assertNotNull(r);
        assertEquals(2,LinkModel.eventLamp(new JSONArray().put(r),event(),now+1800000,true));
        CalendarModel.Event other=new CalendarModel.Event(event().id,event().begin+86400000,event().end+86400000,calendar,event().title,false);
        assertEquals(0,LinkModel.eventLamp(new JSONArray().put(r),other,now+1800000,true));
    }
    @Test public void testAndVendorReferenceNeverBecomeFormalRedLinks() throws Exception {
        for(String source:new String[]{"test","calendar_test","note_test","link_test","calendar_setup"}){
            JSONObject r=linked().put("source",source);if("calendar_setup".equals(source))r.put("status","reference");
            assertFalse(LinkModel.linked(r,now,true));
        }
    }
    @Test public void completedConnectionRemainsIdentifiableUntilExplicitlyRemoved() throws Exception {
        JSONObject r=linked().put("status","stopped").put("systemScheduled",false);
        assertTrue(LinkModel.linked(r,now,true));r.put("linkEnabled",false);assertFalse(LinkModel.linked(r,now,true));
    }
    @Test public void anOldUndeliveredScheduleIsNotShownAsReady() throws Exception {
        assertFalse(LinkModel.linked(linked(),now+3600000,true));
    }
    @Test public void noteIndicatorsUseTheCorrectNoteId() throws Exception {
        JSONObject r=linked().put("source","note_link").put("noteId",10001);
        assertEquals(1,LinkModel.noteLamp(new JSONArray().put(r),10001,now,true));assertEquals(0,LinkModel.noteLamp(new JSONArray().put(r),10002,now,true));
    }
    @Test public void changedOrRemovedSourceRemindersRetireOnlyJunbaSchedules() throws Exception {
        JSONObject r=linked();Set<Long> selected=Collections.singleton(calendar.id);Set<String> events=Collections.singleton(event().key());
        assertTrue(LinkModel.retire(r,selected,Collections.emptySet(),events,now,now+86400000,true));
        assertFalse(LinkModel.retire(r,selected,Collections.singleton(r.getString("linkId")),events,now,now+86400000,true));
        assertTrue(LinkModel.retire(r,Collections.emptySet(),Collections.singleton(r.getString("linkId")),events,now,now+86400000,true));
    }
    @Test public void manualSnoozesAndManualTimesSurviveAutomaticReconciliation() throws Exception {
        JSONObject snoozed=linked().put("snoozeCount",1).put("status","snoozed");
        assertFalse(LinkModel.retire(snoozed,Collections.emptySet(),Collections.emptySet(),Collections.emptySet(),now,now+86400000,true));
        assertFalse(LinkModel.retire(linked().put("managed",false),Collections.emptySet(),Collections.emptySet(),Collections.emptySet(),now,now+86400000,true));
    }
    @Test public void incompleteOptionalAlarmRowsDoNotErasePreviouslyKnownAllDayTimes() throws Exception {
        JSONObject r=linked().put("allDay",true).put("providerTime",true);
        assertFalse(LinkModel.retire(r,Collections.singleton(calendar.id),Collections.emptySet(),Collections.singleton(event().key()),now,now+86400000,false));
        assertTrue(LinkModel.retire(r,Collections.singleton(calendar.id),Collections.emptySet(),Collections.emptySet(),now,now+86400000,false));
    }
    @Test public void explicitFarFutureConnectionOutsideRollingWindowIsPreserved() throws Exception {
        JSONObject r=linked().put("originalWhen",now+100*86400000L);
        assertFalse(LinkModel.retire(r,Collections.singleton(calendar.id),Collections.emptySet(),Collections.emptySet(),now,now+90*86400000L,true));
    }
    @Test public void realNotificationReusesItsAcceptedPrelinkInsteadOfRingingTwice() throws Exception {
        JSONObject r=linked();long when=r.getLong("when");
        assertEquals(1,LinkModel.notificationRoute(new JSONArray().put(r),event().title,when,Collections.emptySet()));
        assertEquals(0,LinkModel.notificationRoute(new JSONArray().put(r),"別的活動",when,Collections.emptySet()));
        assertEquals(0,LinkModel.notificationRoute(new JSONArray().put(r),event().title,when+120001,Collections.emptySet()));
    }
    @Test public void explicitlyRemovedPrelinkCannotBeRecreatedByItsGoogleNotification() throws Exception {
        JSONObject r=linked().put("status","cancelled").put("linkEnabled",false);long when=r.getLong("when");
        assertEquals(2,LinkModel.notificationRoute(new JSONArray().put(r),event().title,when,Collections.singleton(r.getString("linkId"))));
    }
    @Test public void justDeliveredAlarmIsNotMarkedMissedBeforeAudioServiceStarts() throws Exception {
        JSONObject r=linked();long due=r.getLong("when");
        assertFalse(LinkModel.missed(r,due));assertFalse(LinkModel.missed(r,due+119999));assertTrue(LinkModel.missed(r,due+120001));
    }
    @Test public void ringingAndFutureSnoozesAreNeverTreatedAsMissed() throws Exception {
        JSONObject r=linked().put("status","ringing");assertFalse(LinkModel.missed(r,now+86400000));
        r=AlarmRecord.snoozed(linked(),30,now);assertFalse(LinkModel.missed(r,now));
    }
    @Test public void providerAndExplicitTimesRemainSortedAndBoundedByHorizon(){
        CalendarModel.Event e=event();e.alertTimes.add(now+1000);e.alertTimes.add(now+2*86400000L);
        List<LinkModel.Request> requests=LinkModel.plan(Arrays.asList(e),now,now+86400000);
        assertEquals(3,requests.size());assertEquals(now+1000,requests.get(0).when);assertEquals(now+3600000,requests.get(2).when);
    }
}
