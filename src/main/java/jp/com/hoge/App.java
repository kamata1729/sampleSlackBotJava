package jp.com.hoge;

import java.io.*;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.lang.StringBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import com.amazonaws.services.lambda.runtime.*;

public class App implements RequestStreamHandler
{
    public static String SLACK_BOT_USER_ACCESS_TOKEN = "";
    public static String SLACK_APP_AUTH_TOKEN = "";
    public static String USER_ID = "";

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        App.SLACK_BOT_USER_ACCESS_TOKEN = System.getenv("SLACK_BOT_USER_ACCESS_TOKEN");
        App.SLACK_APP_AUTH_TOKEN = System.getenv("SLACK_APP_AUTH_TOKEN");
        App.USER_ID = System.getenv("USER_ID");

        // デフォルトのresponse 何も返信しないと何度か同じイベントが送られてくる
        String response = "HTTP 200 OK\nContent-type: text/plain\nOK";
        try{
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8"))); 
            String jsonText = readAll(rd);
            System.out.println(jsonText); //System.outの出力はCloudWatchLogsに書き込まれる
            JSONObject json = new JSONObject(jsonText);

            // Event APIのテストの時
            if (json.has("type")) {
                String eventType = json.get("type").toString();
                if (eventType.equals("url_verification")) {
                    // challenge の内容をresponseに設定する
                    response = "HTTP 200 OK\nContent-type: text/plain\n" + json.get("challenge").toString();
                }
            }
            
            // app_mentionイベントの時
            if (json.has("event")) {
                JSONObject eventObject = json.getJSONObject("event");
                if(eventObject.has("type")) {
                    String eventType = eventObject.get("type").toString();
                    if (eventType.equals("app_mention")){
                        String user = eventObject.get("user").toString();
                        if (user.equals(App.USER_ID)) { return; } // 発言がbot user自身の場合は無視する
                        String channel = eventObject.get("channel").toString();
                        String text = eventObject.get("text").toString();
                        String responseText = text.replace(App.USER_ID, user);
                        System.out.println(responseText);
                        System.out.println(postMessage(responseText, channel));
                    }
                }
            }       
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            // responseの内容をoutputStreamに書き込む
            outputStream.write(response.getBytes());
            outputStream.flush();
            outputStream.close();
        }
        return;
    }

    /* get String from BufferedReader */
    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    /* post message to selected channel */
    public static String postMessage(String message, String channel) {
        String strUrl = "https://slack.com/api/chat.postMessage";
        String ret = "";
        URL url;

        HttpURLConnection urlConnection = null;
        try {
            url = new URL(strUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch(IOException e) {
            e.printStackTrace();
            return "IOException";
        }
        
        urlConnection.setDoOutput(true);
        urlConnection.setConnectTimeout(100000);
        urlConnection.setReadTimeout(100000);
        urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        String auth = "Bearer " + App.SLACK_BOT_USER_ACCESS_TOKEN;
        urlConnection.setRequestProperty("Authorization", auth);

        try {
            urlConnection.setRequestMethod("POST");
        } catch(ProtocolException e) {
            e.printStackTrace();
            return "ProtocolException";
        }

        try {
            urlConnection.connect();
        } catch(IOException e) {
            e.printStackTrace();
            return "IOException";
        }

        HashMap<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("token", App.SLACK_APP_AUTH_TOKEN);
        jsonMap.put("channel", channel);
        jsonMap.put("text", message);
        jsonMap.put("username", " sampleBot");

        OutputStream outputStream = null;
        try {
            outputStream = urlConnection.getOutputStream();
        } catch(IOException e) {
            e.printStackTrace();
            return "IOException";
        }

        if (jsonMap.size() > 0) {
            JSONObject responseJsonObject = new JSONObject(jsonMap);
            String jsonText = responseJsonObject.toString();
            PrintStream ps = new PrintStream(outputStream);
            ps.print(jsonText);
            ps.close();
        }

        try {
            if (outputStream != null) {
                outputStream.close();
            }
            int responseCode = urlConnection.getResponseCode();
            BufferedReader rd = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));
            ret = readAll(rd);
        } catch(IOException e) {
            e.printStackTrace();
            return "IOException";
        
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }         
        }
        return ret;
    }
}