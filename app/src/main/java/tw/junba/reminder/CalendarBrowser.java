package tw.junba.reminder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;

/** Month browsing is read-only and separate from the notification-driven alarm path. */
final class CalendarBrowser implements AutoCloseable {
    private final Activity activity;private final Runnable redraw,records,settings,googleTest;
    private final Consumer<CalendarModel.Event> test,stock,link;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private YearMonth month=YearMonth.now();private LocalDate day=LocalDate.now();
    private CalendarRepository.Snapshot snapshot;private YearMonth loaded;
    private Map<LocalDate,List<CalendarModel.Event>> indexed=new HashMap<>();private Set<String> receivedKeys=new HashSet<>();
    private boolean loading,closed;private String error="";private int generation,dayPage;
    CalendarBrowser(Activity activity,Runnable redraw,Runnable records,Runnable settings,Runnable googleTest,Consumer<CalendarModel.Event> test,Consumer<CalendarModel.Event> stock,Consumer<CalendarModel.Event> link){
        this.activity=activity;this.redraw=redraw;this.records=records;this.settings=settings;this.googleTest=googleTest;this.test=test;this.stock=stock;this.link=link;
        try{day=LocalDate.parse(activity.getSharedPreferences("junba_calendar_view",0).getString("day",day.toString()));month=YearMonth.from(day);}catch(Exception ignored){}
    }
    void invalidate(){generation++;loading=false;snapshot=null;loaded=null;error="";}
    private int dp(int v){return (int)(v*activity.getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(LinearLayout parent,String value,int size){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(0xffeeeeef);view.setPadding(0,dp(6),0,dp(6));parent.addView(view);return view;}
    private Button button(LinearLayout parent,String value,Runnable action){Button b=new Button(activity);b.setText(value);b.setAllCaps(false);parent.addView(b);b.setOnClickListener(v->action.run());return b;}
    private void choose(LocalDate chosen){dayPage=0;day=chosen;month=YearMonth.from(chosen);activity.getSharedPreferences("junba_calendar_view",0).edit().putString("day",chosen.toString()).apply();redraw.run();}
    private void load(){
        if(loading||closed)return;loading=true;error="";final int request=++generation;final YearMonth target=month;
        worker.execute(()->{
            CalendarRepository.Snapshot result=null;String failure="";
            try{
                List<CalendarModel.Source> sources=CalendarRepository.sources(activity);Set<Long> selected=new HashSet<>();
                for(CalendarModel.Source source:sources)if(CalendarRepository.selected(activity,source))selected.add(source.id);
                result=CalendarRepository.load(activity,target.atDay(1),target.plusMonths(1).atDay(1),selected);
            }catch(SecurityException e){failure="日曆讀取權限未允許；響鈴控制仍可使用。";}
            catch(Exception e){failure="手機未提供完整日曆資料，請確認 Google 帳號同步，或重試讀取。";}
            final CalendarRepository.Snapshot done=result;final String message=failure;
            final Map<LocalDate,List<CalendarModel.Event>> days=done==null?new HashMap<>():CalendarModel.indexDays(done.events,target,ZoneId.systemDefault());
            main.post(()->{if(closed||request!=generation)return;loading=false;snapshot=done;indexed=days;loaded=target;error=message;redraw.run();});
        });
    }
    void render(LinearLayout root){
        text(root,"Google 日曆月份瀏覽",22);
        if(!CalendarRepository.granted(activity)){
            text(root,"允許讀取日曆後，可選帳號、瀏覽手機已同步活動；不會修改 Google 資料。原有通知響鈴與停止功能照常保留。",16);
            button(root,"允許讀取手機日曆",()->activity.requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},102));
            button(root,"開啟 App 權限設定",()->{
                try{activity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+activity.getPackageName())));}catch(Exception ignored){}
            });return;
        }
        text(root,"🔴 已收到並對應真實 Google 通知；灰點只表示有活動。測試會另列，不點紅燈。生日／Google 工作僅能顯示手機日曆有提供的項目。",15);
        button(root,"選擇帳號／日曆",this::accounts);
        button(root,"重新讀取",()->{invalidate();redraw.run();});
        LinearLayout nav=new LinearLayout(activity);root.addView(nav);
        Button previous=new Button(activity);previous.setText("‹");nav.addView(previous,new LinearLayout.LayoutParams(0,-2,1));previous.setOnClickListener(v->{YearMonth m=month.minusMonths(1);choose(m.atDay(Math.min(day.getDayOfMonth(),m.lengthOfMonth())));});
        TextView title=text(nav,month.getYear()+" 年 "+month.getMonthValue()+" 月",20);title.setGravity(android.view.Gravity.CENTER);title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,3));
        Button next=new Button(activity);next.setText("›");nav.addView(next,new LinearLayout.LayoutParams(0,-2,1));next.setOnClickListener(v->{YearMonth m=month.plusMonths(1);choose(m.atDay(Math.min(day.getDayOfMonth(),m.lengthOfMonth())));});
        button(root,"回到今天",()->choose(LocalDate.now()));
        button(root,"選年月日",()->new DatePickerDialog(activity,(v,y,m,d)->choose(LocalDate.of(y,m+1,d)),day.getYear(),day.getMonthValue()-1,day.getDayOfMonth()).show());
        if(snapshot==null||!month.equals(loaded)){
            if(!month.equals(loaded)&&!loading)load();text(root,loading?"正在讀取日曆…":error,16);
            if(!error.isEmpty())text(root,error,16);return;
        }
        if(snapshot.sources.isEmpty())text(root,"尚未找到手機日曆。請先在 Google 日曆完成帳號同步。",16);
        else if(snapshot.events.isEmpty())text(root,"此月在所選帳號／日曆中沒有可讀活動；可調整勾選。",16);
        if(snapshot.truncated)text(root,"本月超過 5,000 筆，僅顯示前 5,000 筆，請減少勾選日曆。",15);
        JSONArray receipts=AlarmStore.all(activity);receivedKeys=new HashSet<>();
        for(int i=0;i<receipts.length();i++){JSONObject receipt=receipts.optJSONObject(i);if(receipt!=null&&"calendar".equals(receipt.optString("source")))receivedKeys.add(CalendarModel.recordEventKey(receipt));}
        grid(root);
        text(root,day+" 的活動",21);List<CalendarModel.Event> daily=indexed.getOrDefault(day,Collections.emptyList());
        button(root,"測試這一天的活動響鈴…",()->{
            if(daily.isEmpty()){Toast.makeText(activity,"本日沒有可讀活動可測試",Toast.LENGTH_LONG).show();return;}
            String[] names=new String[daily.size()];for(int i=0;i<names.length;i++)names[i]=daily.get(i).title+" · "+daily.get(i).calendar.label();
            new AlertDialog.Builder(activity).setTitle("選一筆活動測試（5 秒）").setItems(names,(d,w)->test.accept(daily.get(w))).setNegativeButton("取消",null).show();
        });
        button(root,"開 Google 建立真正的測試通知…",googleTest);
        if(daily.isEmpty())text(root,"本日沒有可讀活動。Google 工作與部分生日可能未提供給手機日曆，並不代表 Google 沒有資料。",16);
        if(dayPage*20>=daily.size())dayPage=0;
        int pageStart=dayPage*20,pageEnd=Math.min(daily.size(),pageStart+20);
        if(daily.size()>20){
            text(root,"本日共 "+daily.size()+" 筆，第 "+(dayPage+1)+" 頁",15);
            if(dayPage>0)button(root,"前 20 筆",()->{dayPage--;redraw.run();});
            if(pageEnd<daily.size())button(root,"後 20 筆",()->{dayPage++;redraw.run();});
        }
        for(CalendarModel.Event event:daily.subList(pageStart,pageEnd)){
            LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(12),dp(10),dp(12));card.setBackgroundColor(0xff252527);root.addView(card);
            text(card,event.title,21);text(card,event.calendar.label(),15);
            text(card,event.allDay?"全天（依日期顯示）":java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm").format(Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()))+" 起",16);
            String reminder=event.minutes.isEmpty()?"未提供提醒設定":event.minutes.toString().replace("-1","預設時間未提供")+" 分鐘前";
            text(card,"來源提醒："+reminder,14);
            boolean received=receivedKeys.contains(event.key());TextView lamp=text(card,received?"● 已接受真實通知":"○ 尚無已對應的真實通知",17);lamp.setTextColor(received?0xffff6868:0xffbbbbbb);
            for(int n=receipts.length()-1;n>=0;n--){JSONObject record=receipts.optJSONObject(n);if(record==null||!CalendarModel.belongs(record,event))continue;
                if("calendar".equals(record.optString("source"))){text(card,"通知收到："+new java.text.SimpleDateFormat("MM/dd HH:mm",Locale.TAIWAN).format(new Date(record.optLong("receivedAt")))+
                    ("manual".equals(record.optString("matchKind"))?"（本人對應）":"provider_alert".equals(record.optString("matchKind"))?"（依手機提醒紀錄對應）":"（依名稱與提醒時間唯一對應）"),14);break;}
            }
            for(int n=receipts.length()-1;n>=0;n--){JSONObject record=receipts.optJSONObject(n);
                if(record!=null&&"calendar_test".equals(record.optString("source"))&&CalendarModel.belongs(record,event)){
                    text(card,record.has("testConfirmedAt")?"測試：本人已確認聽見":"測試紀錄："+CalendarModel.testStatus(record)+"，不代表 Google 已送通知",14);break;}
            }
            button(card,"測試此活動響鈴",()->test.accept(event));
            button(card,"交給原廠建立單筆鬧鐘…",()->stock.accept(event));
            if(!received)button(card,"對應已收到的 Google 通知…",()->link.accept(event));
        }
        button(root,"查看通知與測試紀錄",records);button(root,"通知連動與權限設定",settings);
        text(root,"帳號勾選篩選瀏覽與測試；現有 Google 通知響鈴仍由連動開關控制。無法唯一辨識的通知留在紀錄，不猜測活動或帳號。",14);
    }
    private void grid(LinearLayout root){
        LinearLayout weekdays=new LinearLayout(activity);root.addView(weekdays);
        for(String label:new String[]{"日","一","二","三","四","五","六"}){TextView v=text(weekdays,label,15);v.setGravity(17);v.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));}
        int offset=month.atDay(1).getDayOfWeek().getValue()%7;LinearLayout row=null;
        for(int cell=0;cell<((offset+month.lengthOfMonth()+6)/7)*7;cell++){
            if(cell%7==0){row=new LinearLayout(activity);root.addView(row);}
            TextView v=new TextView(activity);v.setTextSize(16);v.setGravity(17);v.setPadding(0,dp(4),0,dp(4));row.addView(v,new LinearLayout.LayoutParams(0,dp(64),1));
            int number=cell-offset+1;if(number<1||number>month.lengthOfMonth())continue;
            LocalDate date=month.atDay(number);int count=0,red=0;
            for(CalendarModel.Event event:indexed.getOrDefault(date,Collections.emptyList())){count++;if(receivedKeys.contains(event.key()))red++;}
            v.setText(number+"\n"+(red>0?"●":count>0?"·":""));v.setTextColor(red>0?0xffff6868:Color.WHITE);
            if(date.equals(day))v.setBackgroundColor(0xff315158);v.setContentDescription(date+"，"+count+" 筆活動，"+red+" 筆已收到對應通知");v.setOnClickListener(view->choose(date));
        }
    }
    private void accounts(){
        if(snapshot==null){Toast.makeText(activity,"請等待日曆讀取完成後再選擇",Toast.LENGTH_LONG).show();return;}
        List<CalendarModel.Source> sources=snapshot.sources;String[] labels=new String[sources.size()];boolean[] chosen=new boolean[sources.size()];
        for(int i=0;i<labels.length;i++){CalendarModel.Source source=sources.get(i);labels[i]=source.account+"\n"+source.name+("com.google".equals(source.type)?"":"（"+source.type+"）");chosen[i]=CalendarRepository.selected(activity,source);}
        new AlertDialog.Builder(activity).setTitle("選擇帳號與日曆（可複選）").setMultiChoiceItems(labels,chosen,(d,i,on)->chosen[i]=on)
            .setPositiveButton("套用",(d,w)->{SharedPreferences.Editor edit=activity.getSharedPreferences("junba_calendar_view",0).edit();for(int i=0;i<chosen.length;i++)edit.putBoolean("cal_"+sources.get(i).id,chosen[i]);edit.apply();invalidate();redraw.run();})
            .setNegativeButton("取消",null).show();
    }
    @Override public void close(){closed=true;generation++;worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
