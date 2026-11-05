package mk.seteczaliha.app;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to an operator-owned HTTPS backend. Never calls OpenAI from the APK
 * and never asks for/stores the OpenAI API key on Android.
 */
final class AiResearchClient {
    static final class Source {
        final String title,url;
        Source(String title,String url){this.title=title;this.url=url;}
    }
    static final class Result {
        final String summary,note;
        final List<Source> sources;
        final boolean verified;
        Result(String summary,String note,List<Source> sources,boolean verified){
            this.summary=summary;this.note=note;this.sources=sources;this.verified=verified;
        }
    }
    private AiResearchClient(){}

    static boolean validServerUrl(String raw){
        if(raw==null)return false;
        try{
            Uri u=Uri.parse(raw.trim());
            return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null
                &&!u.getHost().trim().isEmpty()
                &&(u.getUserInfo()==null)
                &&(u.getQuery()==null)
                &&(u.getFragment()==null);
        }catch(Exception e){return false;}
    }

    static Result research(String baseUrl,String clientToken,String model)throws Exception {
        if(!validServerUrl(baseUrl))throw new Exception("AI server mora da ima HTTPS adresa.");
        if(clientToken==null||clientToken.trim().length()<16)
            throw new Exception("Vnesi validen pristapen token za AI serverot.");
        String clean=model==null?"":model.replaceAll("[\\r\\n\\t]+"," ").replaceAll("\\s+"," ").trim();
        if(clean.length()<4||clean.length()>100||clean.matches("\\d+"))
            throw new Exception("Prvo skeniraj ili vnesi tocen model.");
        Uri uri=Uri.parse(baseUrl.trim());
        String path=uri.getPath();
        String endpoint=(path==null||path.isEmpty()||"/".equals(path))
            ?baseUrl.trim().replaceAll("/+$","")+"/research"
            :baseUrl.trim().replaceAll("/+$","");
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(13000);
        connection.setReadTimeout(65000);
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Authorization","Bearer "+clientToken.trim());
        byte[] payload=new JSONObject().put("model",clean).toString()
            .getBytes(StandardCharsets.UTF_8);
        try {
            try(OutputStream output=connection.getOutputStream()){output.write(payload);}
            int status=connection.getResponseCode();
            if(status==401||status==403)
                throw new Exception("AI serverot go odbi pristapniot token.");
            if(status==429)
                throw new Exception("AI serverot ima privremeno ogranicuvanje. Probaj podocna.");
            if(status<200||status>=300)
                throw new Exception("AI serverot ne odgovori ("+status+").");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(BufferedInputStream stream=new BufferedInputStream(connection.getInputStream())){
                byte[] block=new byte[4096];int n;
                while((n=stream.read(block))!=-1){
                    if(buffer.size()+n>48000)
                        throw new Exception("AI odgovorot e predolg.");
                    buffer.write(block,0,n);
                }
            }
            JSONObject value=new JSONObject(buffer.toString("UTF-8"));
            String returnedModel=value.optString("model").trim();
            if(!clean.equals(returnedModel))
                throw new Exception("AI odgovorot e za drug model; nema da go prikazam.");
            String summary=value.optString("summary").trim();
            if(summary.length()<12)
                throw new Exception("AI ne najde dovolno podoci za modelot.");
            if(summary.length()>2500)summary=summary.substring(0,2500);
            List<Source> sources=new ArrayList<>();
            JSONArray found=value.optJSONArray("sources");
            if(found!=null){
                for(int i=0;i<Math.min(found.length(),5);i++){
                    JSONObject source=found.optJSONObject(i);
                    if(source==null)continue;
                    String url=source.optString("url");
                    String title=source.optString("title");
                    if(!isHttpsUrl(url))continue;
                    sources.add(new Source(title.length()>100?title.substring(0,100):title,url));
                }
            }
            return new Result(summary,value.optString("note"),sources,
                value.optBoolean("verified")&&!sources.isEmpty());
        }finally {connection.disconnect();}
    }
    private static boolean isHttpsUrl(String url){
        try{Uri u=Uri.parse(url);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null;}
        catch(Exception e){return false;}
    }
}
