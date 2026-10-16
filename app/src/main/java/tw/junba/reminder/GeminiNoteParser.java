package tw.junba.reminder;

import java.net.*;
import java.io.*;
import org.json.*;

/** Parses a user-confirmed voice transcript. Never schedules anything directly. */
final class GeminiNoteParser {
    private GeminiNoteParser(){}
    static JSONObject parse(String apiKey,String model,String transcript) throws Exception {
        URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+
            URLEncoder.encode(model,"UTF-8")+":generateContent");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        try {
            conn.setRequestMethod("POST");conn.setRequestProperty("x-goog-api-key",apiKey);
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
            conn.setConnectTimeout(12000);conn.setReadTimeout(25000);conn.setDoOutput(true);
            String prompt="請分析以下繁體中文使用者口述的記事或提醒指令。只輸出一個 JSON 物件，"+
                "欄位必須是 title、content、dateTime、advanceMinutes、repeat、hasReminder。"+
                "title 與 content 無法判斷請填 '無訊息'，不可以省略；dateTime 格式 yyyy-MM-dd HH:mm；"+
                "日期時間不夠明確時用空字串並填 hasReminder=false，不要猜日期。"+
                "repeat 只能是 不重複、每日、每週、每月。advanceMinutes 是非負整數。"+
                "這是手機的當地時間，現在為 "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.TAIWAN).format(new java.util.Date())+
                "。不要在 JSON 外加說明。口述："+transcript;
            JSONObject req=new JSONObject().put("contents",new JSONArray().put(new JSONObject()
                .put("parts",new JSONArray().put(new JSONObject().put("text",prompt)))));
            try(OutputStream out=conn.getOutputStream()){out.write(req.toString().getBytes("UTF-8"));}
            int http=conn.getResponseCode();InputStream stream=http>=400?conn.getErrorStream():conn.getInputStream();
            StringBuilder sb=new StringBuilder();
            if(stream!=null)try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,"UTF-8"))){
                String line;while((line=reader.readLine())!=null && sb.length()<30000)sb.append(line);
            }
            if(http!=200)throw new IOException("Gemini HTTP "+http+"：請先在設定查詢並選擇可用模型");
            JSONObject response=new JSONObject(sb.toString());
            JSONArray candidates=response.optJSONArray("candidates");
            if(candidates==null||candidates.length()==0)throw new JSONException("Gemini 沒有回傳辨識內容");
            JSONObject first=candidates.optJSONObject(0);
            JSONObject body=first==null?null:first.optJSONObject("content");
            JSONArray parts=body==null?null:body.optJSONArray("parts");
            StringBuilder answer=new StringBuilder();
            if(parts!=null)for(int j=0;j<parts.length();j++){
                JSONObject part=parts.optJSONObject(j);
                if(part!=null&&!part.optBoolean("thought",false))answer.append(part.optString("text",""));
            }
            String raw=answer.toString().trim();
            int start=raw.indexOf('{'),end=raw.lastIndexOf('}');
            if(start<0||end<=start)throw new JSONException("AI 回覆不是 JSON；已保留原始語音文字");
            JSONObject parsed=new JSONObject(raw.substring(start,end+1));
            if(!parsed.has("title"))parsed.put("title","無訊息");
            if(!parsed.has("content"))parsed.put("content",transcript);
            return parsed;
        }finally{conn.disconnect();}
    }
}
