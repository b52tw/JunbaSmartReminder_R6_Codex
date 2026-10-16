package tw.junba.reminder;

import org.junit.Test;
import static org.junit.Assert.*;

public class CalendarNotificationRuleTest {
    private final long now=1800000000000L;
    private boolean accepts(String pkg,String category,boolean summary,boolean ongoing,long post){
        return CalendarNotificationRule.accepts(pkg,category,summary,ongoing,post,now);
    }
    @Test public void onlyGoogleCalendarCanTriggerSound(){
        assertTrue(accepts(CalendarNotificationRule.GOOGLE_CALENDAR,"event",false,false,now));
        for(String pkg:new String[]{null,"","com.android.calendar","com.google.android.gm","com.android.deskclock"})
            assertFalse(accepts(pkg,"event",false,false,now));
    }
    @Test public void summariesOngoingAndNonReminderCategoriesAreIgnored(){
        String p=CalendarNotificationRule.GOOGLE_CALENDAR;
        assertFalse(accepts(p,"event",true,false,now));assertFalse(accepts(p,"event",false,true,now));
        for(String category:new String[]{"service","progress","status","email","promo"})assertFalse(accepts(p,category,false,false,now));
        assertTrue(accepts(p,"reminder",false,false,now));assertTrue(accepts(p,null,false,false,now));
        assertTrue(accepts(p,"",false,false,now));
    }
    @Test public void oldAndInvalidTimestampsDoNotReplayNotifications(){
        String p=CalendarNotificationRule.GOOGLE_CALENDAR;
        assertFalse(accepts(p,"event",false,false,0));assertFalse(accepts(p,"event",false,false,now-120001));
        assertTrue(accepts(p,"event",false,false,now-120000));assertTrue(accepts(p,"event",false,false,now+30000));
        assertFalse(accepts(p,"event",false,false,now+30001));
    }
    @Test public void unchangedNotificationContentDoesNotRestartAfterStop(){
        assertTrue(CalendarNotificationRule.sameContent("same-digest","same-digest"));
        assertFalse(CalendarNotificationRule.sameContent("new-reminder","old-reminder"));
        assertFalse(CalendarNotificationRule.sameContent("",""));
        assertFalse(CalendarNotificationRule.sameContent(null,"old"));
    }
    @Test public void firstDeliveryIsAllowedEvenWithOnlyAlertOnce(){
        assertFalse(CalendarNotificationRule.duplicate(true,now,now,0,0));
    }
    @Test public void sameNotificationPostNeverRingsAgain(){
        assertTrue(CalendarNotificationRule.duplicate(false,now-90000,now,now-90000,now-90000));
    }
    @Test public void onlyAlertOnceUpdatesDoNotRingAgain(){
        assertTrue(CalendarNotificationRule.duplicate(true,now,now,now-90000,now-90000));
    }
    @Test public void noisyUpdatesAreThrottledButLaterReminderCanRing(){
        assertTrue(CalendarNotificationRule.duplicate(false,now,now,now-20000,now-29999));
        assertFalse(CalendarNotificationRule.duplicate(false,now,now,now-20000,now-30000));
    }
}
