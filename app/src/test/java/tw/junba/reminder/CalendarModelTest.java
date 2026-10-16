package tw.junba.reminder;

import org.json.*;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class CalendarModelTest {
    private final ZoneId taipei=ZoneId.of("Asia/Taipei");
    private final CalendarModel.Source first=new CalendarModel.Source(1,"示範帳號甲","工作日曆","com.google");
    private final CalendarModel.Source second=new CalendarModel.Source(2,"示範帳號乙","生日","com.google");
    private long time(String value){return Instant.parse(value).toEpochMilli();}
    private CalendarModel.Event event(){return new CalendarModel.Event(7,time("2026-10-17T01:00:00Z"),time("2026-10-17T02:00:00Z"),first,"看診",false);}
    @Test public void allDayBirthdaysStayOnTheSameDateAcrossTimezones(){
        CalendarModel.Event birthday=new CalendarModel.Event(8,time("2026-10-17T00:00:00Z"),time("2026-10-18T00:00:00Z"),second,"生日",true);
        for(String zone:new String[]{"Asia/Taipei","America/Los_Angeles","Pacific/Kiritimati"}){
            assertTrue(CalendarModel.onDay(birthday,LocalDate.of(2026,10,17),ZoneId.of(zone)));
            assertFalse(CalendarModel.onDay(birthday,LocalDate.of(2026,10,16),ZoneId.of(zone)));
            assertFalse(CalendarModel.onDay(birthday,LocalDate.of(2026,10,18),ZoneId.of(zone)));
        }
    }
    @Test public void midnightEndIsExclusiveAndMultiDayEventsAreVisible(){
        CalendarModel.Event span=new CalendarModel.Event(9,time("2026-10-16T15:00:00Z"),time("2026-10-18T16:00:00Z"),first,"跨日",false);
        for(int day:new int[]{16,17,18})assertTrue(CalendarModel.onDay(span,LocalDate.of(2026,10,day),taipei));
        assertFalse(CalendarModel.onDay(span,LocalDate.of(2026,10,19),taipei));
    }
    @Test public void zeroLengthEventsAppearOnlyOnTheirStartDay(){
        CalendarModel.Event point=new CalendarModel.Event(9,event().begin,event().begin,first,"工作",false);
        assertTrue(CalendarModel.onDay(point,LocalDate.of(2026,10,17),taipei));
        assertFalse(CalendarModel.onDay(point,LocalDate.of(2026,10,18),taipei));
    }
    @Test public void daylightSavingDayDoesNotAssumeTwentyFourHours(){
        ZoneId ny=ZoneId.of("America/New_York");
        CalendarModel.Event span=new CalendarModel.Event(9,time("2026-03-08T05:00:00Z"),time("2026-03-09T04:00:00Z"),first,"調時日",false);
        assertTrue(CalendarModel.onDay(span,LocalDate.of(2026,3,8),ny));assertFalse(CalendarModel.onDay(span,LocalDate.of(2026,3,9),ny));
        assertEquals(1,CalendarModel.indexDays(Arrays.asList(span),YearMonth.of(2026,3),ny).size());
    }
    @Test public void monthIndexHandlesLeapDaysAndEventsCrossingMonthBoundaries(){
        CalendarModel.Event span=new CalendarModel.Event(9,time("2028-02-28T16:00:00Z"),time("2028-03-01T16:00:00Z"),first,"跨月",false);
        Map<LocalDate,List<CalendarModel.Event>> feb=CalendarModel.indexDays(Arrays.asList(span),YearMonth.of(2028,2),taipei);
        assertEquals(Collections.singleton(LocalDate.of(2028,2,29)),feb.keySet());
        assertEquals(1,CalendarModel.indexDays(Arrays.asList(span),YearMonth.of(2028,3),taipei).size());
    }
    @Test public void calendarSelectionPreservesExplicitDeselectionAndLegacyChoices(){
        assertTrue(CalendarModel.selected(first,null,null));assertFalse(CalendarModel.selected(first,false,true));
        assertFalse(CalendarModel.selected(first,null,false));assertTrue(CalendarModel.selected(first,true,false));
        CalendarModel.Source local=new CalendarModel.Source(3,"本機","本機","local");
        assertFalse(CalendarModel.selected(local,null,null));assertTrue(CalendarModel.selected(local,null,true));
    }
    @Test public void reminderMatchingRequiresBothExactTitleAndExpectedTime(){
        CalendarModel.Event e=event();e.minutes.add(30);long received=e.begin-30*60000;
        assertSame(e,CalendarModel.reminderMatch(Arrays.asList(e)," 看診 ",received));
        assertNull(CalendarModel.reminderMatch(Arrays.asList(e),"其他活動",received));
        assertNull(CalendarModel.reminderMatch(Arrays.asList(e),"看診",received+120001));
    }
    @Test public void duplicateNamesAcrossAccountsAreAmbiguous(){
        CalendarModel.Event a=event(),b=new CalendarModel.Event(10,a.begin,a.end,second,a.title,false);a.minutes.add(0);b.minutes.add(0);
        assertNull(CalendarModel.reminderMatch(Arrays.asList(a,b),a.title,a.begin));
        assertNull(CalendarModel.uniqueTitle(Arrays.asList(a,b),a.title));
    }
    @Test public void ambiguousProviderAlertsNeverFallBackToAConvenientSingleCandidate(){
        CalendarModel.Event a=event(),b=new CalendarModel.Event(10,a.begin,a.end,second,a.title,false);a.minutes.add(0);
        assertNull(CalendarModel.match(Arrays.asList(a,b),Arrays.asList(a),a.title,a.begin));
    }
    @Test public void repeatedRowsForSameOccurrenceAreNotFalseAmbiguity(){
        CalendarModel.Event a=event();assertSame(a,CalendarModel.uniqueTitle(Arrays.asList(a,a),a.title));
    }
    @Test public void emptyAndPartialTitlesNeverMatch(){
        assertNull(CalendarModel.uniqueTitle(Arrays.asList(event()),""));
        assertNull(CalendarModel.uniqueTitle(Arrays.asList(event()),"看"));
    }
    @Test public void allDayAndUnknownDefaultReminderTimesAreNotGuessed(){
        CalendarModel.Event all=new CalendarModel.Event(8,event().begin,event().end,second,"生日",true);all.minutes.add(0);
        CalendarModel.Event timed=event();timed.minutes.add(-1);
        assertNull(CalendarModel.reminderMatch(Arrays.asList(all),"生日",all.begin));
        assertNull(CalendarModel.reminderMatch(Arrays.asList(timed),timed.title,timed.begin));
    }
    @Test public void recurrenceOccurrencesHaveDifferentIdentities(){
        CalendarModel.Event a=event(),next=new CalendarModel.Event(a.id,a.begin+7*86400000L,a.end+7*86400000L,first,a.title,false);
        assertNotEquals(a.key(),next.key());
    }
    @Test public void testAndStockReferenceRecordsNeverLightTheRealNotificationLamp() throws Exception {
        CalendarModel.Event e=event();JSONArray records=new JSONArray();
        for(String source:new String[]{"calendar_test","calendar_setup","test","note"}){
            JSONObject r=AlarmRecord.create(source,e.title,source,"",e.begin,e.begin);JSONObject meta=e.metadata("test");
            Iterator<String> keys=meta.keys();while(keys.hasNext()){String key=keys.next();r.put(key,meta.get(key));}records.put(r);
        }
        assertFalse(CalendarModel.received(records,e));
    }
    @Test public void trueReceiptLightsOnlyTheMatchingAccountAndOccurrence() throws Exception {
        CalendarModel.Event e=event();JSONObject r=e.metadata("provider_alert").put("source","calendar").put("status","failed");
        assertTrue(CalendarModel.received(new JSONArray().put(r),e)); // Receipt, not a claim that audio played.
        CalendarModel.Event next=new CalendarModel.Event(e.id,e.begin+86400000,e.end+86400000,first,e.title,false);
        CalendarModel.Event otherAccount=new CalendarModel.Event(e.id,e.begin,e.end,second,e.title,false);
        assertFalse(CalendarModel.received(new JSONArray().put(r),next));assertFalse(CalendarModel.received(new JSONArray().put(r),otherAccount));
        assertEquals(e.key(),CalendarModel.recordEventKey(r));
    }
    @Test public void olderUnassignedReceiptsDoNotGuessAnEvent() throws Exception {
        JSONObject old=new JSONObject().put("source","calendar").put("title","Google 日曆提醒：看診\n09:00");
        assertEquals("看診",CalendarModel.notificationTitle(old));assertFalse(CalendarModel.received(new JSONArray().put(old),event()));
        assertEquals("",CalendarModel.recordEventKey(old));
    }
    @Test public void suggestedStockTimeUsesFutureReminderBeforeEventStart(){
        CalendarModel.Event e=event();e.minutes.add(30);e.minutes.add(10);
        assertEquals(e.begin-30*60000,CalendarModel.stockTime(e,e.begin-3600000,taipei));
        assertEquals(e.begin-10*60000,CalendarModel.stockTime(e,e.begin-20*60000,taipei));
    }
    @Test public void allDayStockSuggestionUsesTheBirthdayDateAtNineLocalTime(){
        CalendarModel.Event e=new CalendarModel.Event(8,time("2026-10-17T00:00:00Z"),time("2026-10-18T00:00:00Z"),second,"生日",true);
        assertEquals(time("2026-10-17T01:00:00Z"),CalendarModel.stockTime(e,0,taipei));
    }
    @Test public void creatingAStockReferenceDoesNotScheduleAnAlarm() throws Exception {
        JSONObject reference=AlarmRecord.create("key","生日","calendar_setup","",0,0);
        assertEquals("reference",reference.getString("status"));assertFalse(AlarmRecord.pending(reference));
    }
}
