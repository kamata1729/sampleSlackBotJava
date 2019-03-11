package jp.com.hoge;

import java.io.*;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
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
    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        // デフォルトのresponse 何も返信しないと何度か同じイベントが送られてくる
        String response = "HTTP 200 OK\nContent-type: text/plain\nOK";
        try{
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8"))); 
            String jsonText = readAll(rd);
            System.out.println(jsonText); //System.outの出力はCloudWatchLogsに書き込まれる
            JSONObject json = new JSONObject(jsonText);
            if (json.has("type")) {
                String eventType = json.get("type").toString();
                if (eventType.equals("url_verification")) {
                    // challenge の内容をresponseに設定する
                    response = "HTTP 200 OK\nContent-type: text/plain\n" + json.get("challenge").toString();
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
}