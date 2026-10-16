package tw.junba.reminder;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;

/** Validates the time/day subset supported by ACTION_SET_ALARM, without an OEM API. */
final class NativeAlarmPlan {
    final long when;
    final int hour, minute;
    final String repeat;
    private final int weekday;

    private NativeAlarmPlan(ZonedDateTime next, LocalTime time, String repeat, int weekday) {
        this.when=next.toInstant().toEpochMilli();
        this.hour=time.getHour();this.minute=time.getMinute();
        this.repeat=repeat;this.weekday=weekday;
    }

    static NativeAlarmPlan create(long timestamp, String repeat, long now, ZoneId zone) {
        if (!"不重複".equals(repeat) && !"每日".equals(repeat) && !"每週".equals(repeat)) {
            throw new IllegalArgumentException("原廠公開介面不支援每月或其他重複規則。請改用 Junba 本機提醒，或到原廠時鐘手動設定；不會自動改成單次鬧鐘。");
        }
        ZonedDateTime target=Instant.ofEpochMilli(timestamp).atZone(zone).withSecond(0).withNano(0);
        if ((timestamp>now || "不重複".equals(repeat)) && target.toInstant().toEpochMilli()<=now) {
            throw new IllegalArgumentException("提醒時間已過，或已進入目前這一分鐘。原廠鬧鐘只能設定到分鐘，請改選之後的時間。");
        }
        LocalTime time=target.toLocalTime();
        ZonedDateTime next=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(time).atZone(zone);
        if (next.toInstant().toEpochMilli()<=now) next=next.toLocalDate().plusDays(1).atTime(time).atZone(zone);
        if ("每週".equals(repeat)) {
            int days=(target.getDayOfWeek().getValue()-next.getDayOfWeek().getValue()+7)%7;
            next=next.toLocalDate().plusDays(days).atTime(time).atZone(zone);
        }
        if (!next.toLocalTime().equals(time)) {
            throw new IllegalArgumentException("這個時間遇到夏令時間調整，無法保證原廠時鐘的觸發時間，請在原廠時鐘手動核對設定。");
        }
        if (target.toInstant().toEpochMilli()>now && !next.toInstant().equals(target.toInstant())) {
            throw new IllegalArgumentException("原廠時鐘無法指定完整日期或未來開始日；現在送出可能在指定日期之前響鈴。請接近提醒日期再建立，或改用 Junba 本機提醒。");
        }
        int weekday=target.getDayOfWeek().getValue()%7+Calendar.SUNDAY;
        return new NativeAlarmPlan(next,time,repeat,weekday);
    }

    ArrayList<Integer> days() {
        ArrayList<Integer> days=new ArrayList<>();
        if ("每日".equals(repeat)) {
            for (int day=Calendar.SUNDAY;day<=Calendar.SATURDAY;day++) days.add(day);
        } else if ("每週".equals(repeat)) days.add(weekday);
        return days;
    }

    String ruleLabel() {
        return "每週".equals(repeat)?"每週"+new String[]{"日","一","二","三","四","五","六"}[weekday-Calendar.SUNDAY]:repeat;
    }
}
