package tw.junba.reminder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import org.json.JSONObject;

/** A validated preview. Constructing it never saves or schedules a note. */
final class AiReminderDraft {
    final String title,content,repeat,issue;
    final long eventTime;
    final int advanceMinutes;
    final boolean hasReminder;
    private AiReminderDraft(String title,String content,String repeat,long eventTime,
                            int advanceMinutes,boolean hasReminder,String issue){
        this.title=title;this.content=content;this.repeat=repeat;this.eventTime=eventTime;
        this.advanceMinutes=advanceMinutes;this.hasReminder=hasReminder;this.issue=issue;
    }
    static AiReminderDraft fromJson(JSONObject parsed,String transcript,long now,ZoneId zone){
        String title=text(parsed.optString("title",""),"無訊息");
        String body=text(parsed.optString("content",""),text(transcript,"無訊息"));
        String repeat=parsed.optString("repeat","不重複");
        boolean requested=Boolean.TRUE.equals(parsed.opt("hasReminder"));
        long eventTime=0;int advance=0;String issue=parsed.opt("hasReminder") instanceof Boolean?"":"AI 未確認是否需要提醒，請重試或補充。";
        if(requested){
            try{
                Object value=parsed.opt("advanceMinutes");
                if(value!=null&&value!=JSONObject.NULL){
                    if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||
                        ((Number)value).doubleValue()!=((Number)value).intValue()||
                        ((Number)value).intValue()<0||((Number)value).intValue()>5256000)
                        throw new IllegalArgumentException("提前提醒分鐘數不明確，請補充。");
                    advance=((Number)value).intValue();
                }
                if(!"不重複".equals(repeat)&&!"每日".equals(repeat)&&!"每週".equals(repeat)&&!"每月".equals(repeat))
                    throw new IllegalArgumentException("重複規則無法辨識，請補充每日、每週或每月。");
                LocalDateTime date=LocalDateTime.parse(parsed.optString("dateTime",""),
                    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT));
                if(zone.getRules().getValidOffsets(date).size()!=1)
                    throw new IllegalArgumentException("這個時間遇到時區調整，請補充確切時間。");
                eventTime=date.atZone(zone).toInstant().toEpochMilli();
                if(eventTime-advance*60000L<=now)
                    throw new IllegalArgumentException("活動或提前提醒時間已過，請補充下一次日期與時間。");
            }catch(IllegalArgumentException ex){issue=ex.getMessage();}
            catch(Exception ex){issue="日期或時間不完整，請補充日期與幾點。";}
        }else if(!parsed.optString("clarification","").trim().isEmpty())issue=parsed.optString("clarification").trim();
        return new AiReminderDraft(title,body,repeat,eventTime,advance,requested&&issue.isEmpty(),issue);
    }
    long reminderTime(){return eventTime-advanceMinutes*60000L;}
    private static String text(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value.trim();}
}
