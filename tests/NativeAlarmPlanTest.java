package tw.junba.reminder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;

/** Host-side regression tests; no Android device, API key or user data required. */
public final class NativeAlarmPlanTest {
    private static final ZoneId TAIPEI=ZoneId.of("Asia/Taipei");
    private static int passed;
    private static long at(String text){return ZonedDateTime.parse(text).toInstant().toEpochMilli();}
    private static NativeAlarmPlan plan(String target,String repeat,String now){
        return NativeAlarmPlan.create(at(target),repeat,at(now),TAIPEI);
    }
    private static void check(boolean condition,String label){
        if(!condition)throw new AssertionError(label);passed++;
    }
    private static void rejected(String target,String repeat,String now,String label){
        try{plan(target,repeat,now);}catch(IllegalArgumentException expected){passed++;return;}
        throw new AssertionError(label);
    }
    public static void main(String[] args){
        String now="2026-10-16T10:00:30+08:00";
        NativeAlarmPlan once=plan("2026-10-16T10:02:59+08:00","不重複",now);
        check(once.when==at("2026-10-16T10:02:00+08:00"),"minute precision");
        check(once.hour==10 && once.minute==2 && once.days().isEmpty(),"one-off clock extras");
        check(plan("2026-10-17T09:00:00+08:00","不重複",now).when==at("2026-10-17T09:00:00+08:00"),"next morning");
        check(plan("2026-10-17T10:00:00+08:00","不重複",now).when==at("2026-10-17T10:00:00+08:00"),"same minute tomorrow after current minute");
        rejected("2026-10-16T10:00:59+08:00","不重複",now,"future seconds cannot roll into tomorrow");
        rejected("2026-10-16T10:00:59+08:00","每日",now,"future seconds cannot silently skip a daily occurrence");
        rejected("2026-10-16T09:00:00+08:00","不重複",now,"expired one-off");
        rejected("2026-10-18T09:00:00+08:00","不重複",now,"distant one-off");
        rejected("2026-10-18T09:00:00+08:00","每日",now,"daily cannot start days later");
        rejected("2026-10-30T09:00:00+08:00","每週",now,"weekly cannot start weeks later");
        rejected("2026-10-16T11:00:00+08:00","每月",now,"monthly cannot become one-off");
        rejected("2026-10-16T11:00:00+08:00","未知規則",now,"unknown recurrence");
        rejected("2026-10-16T11:00:00+08:00",null,now,"missing recurrence");
        NativeAlarmPlan daily=plan("2025-01-01T08:00:00+08:00","每日",now);
        check(daily.when==at("2026-10-17T08:00:00+08:00"),"old daily finds next time without changing original note");
        check(daily.days().equals(Arrays.asList(1,2,3,4,5,6,7)),"daily includes all Calendar weekdays");
        NativeAlarmPlan weekly=plan("2026-10-09T09:00:00+08:00","每週",now);
        check(weekly.when==at("2026-10-23T09:00:00+08:00"),"old weekly after today's time");
        check(weekly.days().equals(Arrays.asList(Calendar.FRIDAY)),"weekly Friday extra");
        check("每週五".equals(weekly.ruleLabel()),"weekly preview");
        NativeAlarmPlan sunday=plan("2026-10-18T08:00:00+08:00","每週",now);
        check(sunday.days().equals(Arrays.asList(Calendar.SUNDAY)),"ISO Sunday maps to Calendar Sunday");
        check(plan("2026-10-09T11:00:00+08:00","每週",now).when==at("2026-10-16T11:00:00+08:00"),"old weekly before today's time");
        check(plan("2026-10-16T00:00:00+08:00","每日","2026-10-16T23:59:50+08:00").when==at("2026-10-17T00:00:00+08:00"),"midnight rollover");
        // A 25-hour autumn day makes a fixed 24-hour cutoff incorrect.
        NativeAlarmPlan fall=NativeAlarmPlan.create(at("2026-11-01T06:50:00-05:00"),"不重複",
            at("2026-10-31T07:00:00-04:00"),ZoneId.of("America/New_York"));
        check(fall.when==at("2026-11-01T06:50:00-05:00"),"next local occurrence across DST fall");
        // The confirmation button must revalidate after the preview has expired.
        rejected("2026-10-16T10:02:59+08:00","不重複","2026-10-16T10:02:01+08:00","expired preview");
        System.out.println("NativeAlarmPlan: "+passed+" checks passed");
    }
}
