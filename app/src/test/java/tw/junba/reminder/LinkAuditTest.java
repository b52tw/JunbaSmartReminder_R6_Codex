package tw.junba.reminder;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class LinkAuditTest {
    private final long now=1800000000000L;
    private CalendarModel.Event event(boolean allDay){return new CalendarModel.Event(5,now+7200000,now+10800000,new CalendarModel.Source(1,"account","日曆","com.google"),"活動",allDay);}
    private JSONObject linked(CalendarModel.Event e,long when,String kind) throws Exception {
        return e.metadata(kind).put("source","calendar_link").put("linkId",new LinkModel.Request(e,when).id).put("when",when).put("status","scheduled").put("systemScheduled",true).put("scheduleAccepted",true).put("linkEnabled",true);
    }
    private LinkAudit.Report inspect(CalendarModel.Event e,JSONArray rows) {return LinkAudit.inspect(e,rows,now,now+86400000,true,true,true,true,false,Collections.emptySet(),new JSONObject());}
    @Test public void partialCoverageShowsMissingInsteadOfClaimingAllLinked() throws Exception {
        CalendarModel.Event e=event(false);e.minutes.addAll(Arrays.asList(30,10));LinkAudit.Report r=inspect(e,new JSONArray().put(linked(e,e.begin-1800000,"prelinked")));
        assertEquals(2,r.expected);assertEquals(1,r.covered);assertEquals(1,r.missing.size());
    }
    @Test public void fullCoverageHasNoMissingReasonsAndSourceIsUnchanged() throws Exception {
        CalendarModel.Event e=event(false);e.minutes.add(30);String before=e.metadata("original").toString();
        LinkAudit.Report r=inspect(e,new JSONArray().put(linked(e,e.begin-1800000,"prelinked")));
        assertEquals(1,r.covered);assertTrue(r.missing.isEmpty());assertEquals(before,e.metadata("original").toString());assertEquals(Arrays.asList(30),e.minutes);
    }
    @Test public void testAndRawReceiptsCannotCoverFormalReminder() throws Exception {
        CalendarModel.Event e=event(false);e.minutes.add(30);JSONObject test=linked(e,e.begin-1800000,"test").put("source","calendar_test");
        JSONObject receipt=new JSONObject(test.toString()).put("source","calendar").put("status","received").put("scheduleAccepted",false);
        assertEquals(0,inspect(e,new JSONArray().put(test).put(receipt)).covered);
    }
    @Test public void allDayWithoutExactAlertNeverGuessesBirthdayTime(){CalendarModel.Event e=event(true);e.minutes.add(30);LinkAudit.Report r=inspect(e,new JSONArray());assertEquals(0,r.expected);assertTrue(r.text().contains("全天"));}
    @Test public void exactAllDayAlertIsSupported() throws Exception {CalendarModel.Event e=event(true);e.alertTimes.add(now+1000);assertEquals(1,inspect(e,new JSONArray().put(linked(e,now+1000,"prelinked"))).covered);}
    @Test public void defaultsAndNoReminderHaveDifferentExplanations(){CalendarModel.Event e=event(false);assertTrue(inspect(e,new JSONArray()).text().contains("未提供提醒設定"));e.minutes.add(-1);assertTrue(inspect(e,new JSONArray()).text().contains("預設提醒"));}
    @Test public void expiredRemindersAreNotReplayed(){CalendarModel.Event e=event(false);e.minutes.add(1440);LinkAudit.Report r=inspect(e,new JSONArray());assertEquals(0,r.expected);assertTrue(r.text().contains("已過"));}
    @Test public void capReasonComesFromActualSyncOutcome() throws Exception {CalendarModel.Event e=event(false);e.minutes.add(30);JSONObject outcomes=new JSONObject().put(new LinkModel.Request(e,e.begin-1800000).id,"limit");LinkAudit.Report r=LinkAudit.inspect(e,new JSONArray(),now,now+86400000,true,true,true,true,false,Collections.emptySet(),outcomes);assertTrue(r.text().contains("250"));}
    @Test public void explicitRemovalIsNotSuggestedForAutomaticRestoration(){CalendarModel.Event e=event(false);e.minutes.add(30);LinkAudit.Report r=LinkAudit.inspect(e,new JSONArray(),now,now+86400000,true,true,true,true,true,Collections.emptySet(),new JSONObject());assertTrue(r.text().contains("本人已移除"));}
    @Test public void permissionAndScopeAreExplained(){CalendarModel.Event e=event(false);e.minutes.add(30);assertTrue(LinkAudit.inspect(e,new JSONArray(),now,now+86400000,true,true,false,true,false,Collections.emptySet(),new JSONObject()).text().contains("權限"));assertTrue(LinkAudit.inspect(e,new JSONArray(),now,now+1000,true,true,true,true,false,Collections.emptySet(),new JSONObject()).text().contains("範圍"));}
    @Test public void manualTimeIsShownSeparatelyFromSourceCoverage() throws Exception {CalendarModel.Event e=event(false);JSONObject manual=linked(e,now+600000,"manual_time").put("linkId","manual:custom");LinkAudit.Report r=inspect(e,new JSONArray().put(manual));assertEquals(0,r.expected);assertEquals(1,r.manual);assertTrue(r.text().contains("自行指定連動 1"));}
    @Test public void offIncompleteAndFailedHaveActionableExplanations() throws Exception {CalendarModel.Event e=event(false);e.minutes.add(30);JSONArray rows=new JSONArray();assertTrue(LinkAudit.inspect(e,rows,now,Long.MAX_VALUE,false,true,true,true,false,Collections.emptySet(),new JSONObject()).text().contains("開關"));assertTrue(LinkAudit.inspect(e,rows,now,Long.MAX_VALUE,true,false,true,true,false,Collections.emptySet(),new JSONObject()).text().contains("已關閉"));assertTrue(LinkAudit.inspect(e,rows,now,Long.MAX_VALUE,true,true,true,false,false,Collections.emptySet(),new JSONObject()).text().contains("不完整"));rows.put(linked(e,e.begin-1800000,"prelinked").put("systemScheduled",false).put("error","排程配額"));assertTrue(inspect(e,rows).text().contains("排程配額"));}
}
