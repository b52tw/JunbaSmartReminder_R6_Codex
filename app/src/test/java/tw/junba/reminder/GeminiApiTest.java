package tw.junba.reminder;

import java.net.SocketTimeoutException;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeminiApiTest {
    static JSONObject model(String name,String method) throws Exception {
        return new JSONObject().put("name","models/"+name).put("supportedGenerationMethods",new JSONArray().put(method));
    }
    static JSONObject response(String text) throws Exception {
        return new JSONObject().put("candidates",new JSONArray().put(new JSONObject().put("content",new JSONObject()
            .put("parts",new JSONArray().put(new JSONObject().put("text",text))))));
    }
    @Test public void automaticallyChoosesSupportedStableFlashAndPaginates() throws Exception {
        List<String> calls=new ArrayList<>();
        GeminiApi.Transport mock=(method,path,body)->{
            calls.add(path);
            if(path.equals("models?pageSize=1000"))return new JSONObject().put("models",new JSONArray()
                .put(model("gemini-3-pro","generateContent")).put(model("gemini-4-flash-preview","generateContent")))
                .put("nextPageToken","page 2");
            if(path.startsWith("models?pageSize=1000&pageToken="))return new JSONObject().put("models",new JSONArray()
                .put(model("gemini-3-flash","generateContent")).put(model("gemini-3-flash-image","generateContent"))
                .put(model("gemini-embedding","embedContent")));
            assertEquals("models/gemini-3-flash:generateContent",path);return response("完成");
        };
        JSONObject result=GeminiApi.generate(mock,"",new JSONObject());assertEquals("gemini-3-flash",result.getString("_model"));assertEquals(3,calls.size());
    }
    @Test public void staleModelIsReplacedOnceWithoutUserChoosing() throws Exception {
        List<String> calls=new ArrayList<>();
        JSONObject result=GeminiApi.generate((method,path,body)->{
            calls.add(path);if(path.equals("models/old-model:generateContent"))throw new GeminiApi.ApiException(404);
            if(method.equals("GET"))return new JSONObject().put("models",new JSONArray().put(model("gemini-current-flash","generateContent")));
            return response("完成");
        },"old-model",new JSONObject());
        assertEquals("gemini-current-flash",result.getString("_model"));assertEquals(3,calls.size());
    }
    @Test public void existingWorkingModelDoesNotNeedListCall() throws Exception {
        JSONObject result=GeminiApi.generate((method,path,body)->{assertEquals("POST",method);return response("完成");},"gemini-selected",new JSONObject());
        assertEquals("gemini-selected",result.getString("_model"));
    }
    @Test public void permissionsAndQuotaDoNotTriggerRepeatedCalls() throws Exception {
        for(int status:new int[]{401,403,429}){
            int[] count={0};try{
                GeminiApi.generate((m,p,b)->{count[0]++;throw new GeminiApi.ApiException(status);},"gemini-selected",new JSONObject());fail();
            }catch(GeminiApi.ApiException error){assertEquals(status,error.status);}
            assertEquals(1,count[0]);
        }
    }
    @Test public void timeoutIsNotReportedAsInvalidKeyAndDoesNotRetrySilently() throws Exception {
        int[] count={0};try{
            GeminiApi.generate((m,p,b)->{count[0]++;throw new SocketTimeoutException();},"gemini-selected",new JSONObject());fail();
        }catch(SocketTimeoutException error){
            String message=GeminiApi.errorMessage(error);assertTrue(message.contains("逾時"));assertTrue(message.contains("尚無法判定"));
            assertTrue(message.contains("原文已保留"));
        }
        assertEquals(1,count[0]);
    }
    @Test public void errorMessagesNeverEchoRemoteContentOrCredential() throws Exception {
        String marker="sensitive-marker-not-a-real-key";
        assertFalse(GeminiApi.errorMessage(new java.io.IOException(marker)).contains(marker));
        assertFalse(GeminiApi.errorMessage(new JSONException(marker)).contains(marker));
    }
    @Test public void ignoresThoughtPartsButKeepsAnswer() throws Exception {
        JSONObject r=response("答案");r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            .put(new JSONObject().put("thought",true).put("text","不該顯示"));assertEquals("答案",GeminiApi.text(r));
    }
    @Test(expected=JSONException.class) public void emptyResponseIsNotSuccess() throws Exception {GeminiApi.text(new JSONObject());}
    @Test(expected=java.io.IOException.class) public void noSupportedModelsIsNotSuccess() throws Exception {
        GeminiApi.models((m,p,b)->new JSONObject().put("models",new JSONArray().put(model("embedding","embedContent"))));
    }
    @Test public void fallbackIsBoundedWhenReplacementAlsoFails() throws Exception {
        int[] count={0};try{
            GeminiApi.generate((method,path,body)->{count[0]++;
                if(method.equals("GET"))return new JSONObject().put("models",new JSONArray().put(model("gemini-new","generateContent")));
                throw new GeminiApi.ApiException(404);
            },"gemini-old",new JSONObject());fail();
        }catch(GeminiApi.ApiException expected){assertEquals(3,count[0]);}
    }
}
