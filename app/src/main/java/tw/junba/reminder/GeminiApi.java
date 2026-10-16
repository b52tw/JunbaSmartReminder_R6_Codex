package tw.junba.reminder;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.SSLException;
import org.json.*;

/** Discovers supported models and keeps credentials/transcripts out of errors. */
final class GeminiApi {
    interface Transport { JSONObject call(String method,String path,JSONObject body) throws Exception; }
    static final class ApiException extends IOException {
        final int status;
        ApiException(int status){super(message(status));this.status=status;}
        private static String message(int status){
            if(status==401||status==403)return "Gemini 金鑰或專案權限未通過（HTTP "+status+"）；原文已保留。";
            if(status==429)return "Gemini 配額或速率已達上限，請稍後重試；原文已保留。";
            if(status==404||status==410)return "Gemini 模型目前無法使用；原文已保留。";
            return "Gemini 暫時無法完成要求（HTTP "+status+"）；原文已保留。";
        }
    }
    static String errorMessage(Exception error){
        if(error instanceof SocketTimeoutException)return "等待 Gemini 連線或回覆逾時；尚無法判定金鑰是否有效。原文已保留，可直接重試，或切換 Wi-Fi／行動網路比較。";
        if(error instanceof UnknownHostException||error instanceof ConnectException)return "目前無法連線至 Gemini；請檢查手機網路。原文已保留，可直接重試。";
        if(error instanceof SSLException)return "Gemini 安全連線未完成；請檢查手機日期時間與網路。原文已保留。";
        if(error instanceof ApiException)return error.getMessage();
        if(error instanceof JSONException)return "Gemini 回覆不完整或無法辨識，原文已保留，可直接重試。";
        return "Gemini 暫時未能完成處理，原文已保留，可直接重試。";
    }
    static Transport https(String key){
        return (method,path,body)->{
            HttpURLConnection c=(HttpURLConnection)new URL("https://generativelanguage.googleapis.com/v1beta/"+path).openConnection();
            try{
                c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(45000);
                c.setRequestProperty("x-goog-api-key",key);
                if(body!=null){
                    c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setDoOutput(true);
                    try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                }
                int status=c.getResponseCode();if(status!=200)throw new ApiException(status);
                StringBuilder result=new StringBuilder();
                try(Reader reader=new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)){
                    char[] buffer=new char[4096];int count;
                    while((count=reader.read(buffer))!=-1){
                        if(result.length()+count>1000000)throw new IOException("Gemini 回覆過長");
                        result.append(buffer,0,count);
                    }
                }
                return new JSONObject(result.toString());
            }finally{c.disconnect();}
        };
    }
    static List<String> models(Transport transport) throws Exception {
        Set<String> names=new HashSet<>();String token="";
        for(int page=0;page<10;page++){
            JSONObject response=transport.call("GET","models?pageSize=1000"+
                (token.isEmpty()?"":"&pageToken="+URLEncoder.encode(token,"UTF-8")),null);
            JSONArray models=response.optJSONArray("models");
            if(models!=null)for(int i=0;i<models.length();i++){
                JSONObject model=models.optJSONObject(i);if(model==null)continue;
                String name=model.optString("name","").replaceFirst("^models/","");
                JSONArray methods=model.optJSONArray("supportedGenerationMethods");boolean generate=false;
                if(methods!=null)for(int j=0;j<methods.length();j++)if("generateContent".equals(methods.optString(j)))generate=true;
                if(generate&&validModel(name)&&name.startsWith("gemini-")&&
                    !name.matches(".*(tts|image|embedding|native-audio|live|robotics|computer-use).*"))names.add(name);
            }
            token=response.optString("nextPageToken","");if(token.isEmpty())break;
            if(page==9)throw new IOException("模型清單過長");
        }
        List<String> sorted=new ArrayList<>(names);
        sorted.sort(Comparator.comparingInt(GeminiApi::rank).thenComparing(Comparator.reverseOrder()));
        if(sorted.isEmpty())throw new IOException("這組金鑰未提供可用的文字生成模型");
        return sorted;
    }
    private static int rank(String model){
        return (model.contains("preview")||model.contains("experimental")||model.contains("exp-")?10:0)+
            (model.contains("flash")?0:model.contains("pro")?2:3);
    }
    private static boolean validModel(String model){return model!=null&&model.matches("[A-Za-z0-9._-]+");}
    static JSONObject generate(Transport transport,String preferred,JSONObject request) throws Exception {
        String model=validModel(preferred)?preferred:models(transport).get(0);
        JSONObject response;
        try{response=transport.call("POST","models/"+model+":generateContent",request);}
        catch(ApiException error){
            if(error.status!=404&&error.status!=410)throw error;
            List<String> available=models(transport);available.remove(model);
            if(available.isEmpty())throw error;
            model=available.get(0);response=transport.call("POST","models/"+model+":generateContent",request);
        }
        response.put("_model",model);return response;
    }
    static String text(JSONObject response) throws JSONException {
        JSONArray candidates=response.optJSONArray("candidates");
        JSONObject candidate=candidates==null?null:candidates.optJSONObject(0);
        JSONObject content=candidate==null?null:candidate.optJSONObject("content");
        JSONArray parts=content==null?null:content.optJSONArray("parts");StringBuilder result=new StringBuilder();
        if(parts!=null)for(int i=0;i<parts.length();i++){
            JSONObject part=parts.optJSONObject(i);
            if(part!=null&&!part.optBoolean("thought",false))result.append(part.optString("text",""));
        }
        if(result.toString().trim().isEmpty())throw new JSONException("Gemini 沒有回傳可用文字");
        return result.toString().trim();
    }
}
