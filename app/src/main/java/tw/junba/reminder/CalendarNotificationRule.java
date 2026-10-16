package tw.junba.reminder;

/** Only new Google Calendar notifications, never a calendar browser or event scraper. */
final class CalendarNotificationRule {
    static final String GOOGLE_CALENDAR="com.google.android.calendar";
    static boolean accepts(String packageName,String category,boolean summary,boolean ongoing,long postTime,long now){
        if(!GOOGLE_CALENDAR.equals(packageName)||summary||ongoing)return false;
        if(category!=null&&!category.isEmpty()&&!"event".equals(category)&&!"reminder".equals(category))return false;
        return postTime>0&&postTime<=now+30000L&&postTime>=now-120000L;
    }
    static boolean sameContent(String current,String previous){
        return current!=null&&!current.isEmpty()&&current.equals(previous);
    }
    static boolean duplicate(boolean onlyAlertOnce,long postTime,long now,long previousPost,long previousDelivery){
        return previousPost>0&&(previousPost==postTime||onlyAlertOnce||now-previousDelivery<30000L);
    }
}
