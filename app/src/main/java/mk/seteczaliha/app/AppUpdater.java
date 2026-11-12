package mk.seteczaliha.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;

final class AppUpdater {
    private static final String BASE="https://github.com/Gjo4e/setec-zaliha/releases/download/setec-tools-updates/";
    private final MainActivity activity;
    private boolean busy;
    AppUpdater(MainActivity activity){this.activity=activity;}
    private void ui(Runnable r){activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())r.run();});}
    private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    private HttpURLConnection connect(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent","SetecTools-Updater");
        return c;
    }
    void check(boolean manual){
        if(busy)return;busy=true;
        new Thread(()->{
            try{
                HttpURLConnection c=connect(BASE+"update.json");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()){
                    byte[] b=new byte[4096];int n;
                    while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>65536)throw new IOException("Invalid manifest");}
                }finally{c.disconnect();}
                JSONObject m=new JSONObject(out.toString("UTF-8"));
                PackageInfo installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
                long current=android.os.Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
                long version=m.getLong("versionCode");
                JSONObject asset=m.getJSONObject("packages").getJSONObject(activity.getPackageName());
                ui(()->{
                    busy=false;
                    if(version<=current){if(manual)toast("Ја користиш најновата верзија.");return;}
                    new AlertDialog.Builder(activity).setTitle("Достапно е ажурирање")
                        .setMessage("Верзија "+m.optString("versionName")+"\n\n"+m.optString("notes","Подобрувања и поправки."))
                        .setNegativeButton("Подоцна",null)
                        .setPositiveButton("Ажурирај сега",(d,w)->download(asset,version)).show();
                });
            }catch(Exception e){ui(()->{busy=false;if(manual)toast("Не може да се провери ажурирањето. Обиди се повторно.");});}
        },"SetecUpdateCheck").start();
    }
    private void download(JSONObject asset,long version){
        if(busy)return;
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("Дозвола за ажурирање")
                .setMessage("Android бара дозвола за инсталирање од Setec Tools. По дозволувањето, повторно притисни Провери за ажурирање.")
                .setNegativeButton("Откажи",null).setPositiveButton("Поставки",(d,w)->activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())))).show();
            return;
        }
        busy=true;
        ProgressDialog progress=new ProgressDialog(activity);
        progress.setTitle("Преземање на ажурирање");progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);progress.setCancelable(false);progress.show();
        new Thread(()->{
            File file=null;
            try{
                String name=asset.getString("file");
                if(!name.matches("Setec-Tools-[0-9]+-(original|separate)\\.apk"))throw new IOException("Invalid file");
                File dir=new File(activity.getCacheDir(),"updates");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Storage");
                file=new File(dir,name);
                HttpURLConnection c=connect(BASE+name);
                long size=asset.getLong("size"),done=0;
                if(size<=0||size>250L*1024*1024)throw new IOException("Invalid size");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(file)){
                    byte[] b=new byte[65536];int n;
                    while((n=in.read(b))!=-1){done+=n;if(done>size)throw new IOException("Invalid size");out.write(b,0,n);digest.update(b,0,n);int pct=(int)(done*100/size);ui(()->progress.setProgress(pct));}
                }finally{c.disconnect();}
                StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(done!=size||!hex.toString().equalsIgnoreCase(asset.getString("sha256")))throw new IOException("Invalid checksum");
                PackageManager pm=activity.getPackageManager();
                PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);
                PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
                if(candidate==null||!activity.getPackageName().equals(candidate.packageName)||!Arrays.equals(candidate.signatures,installed.signatures)||
                    (android.os.Build.VERSION.SDK_INT>=28?candidate.getLongVersionCode():candidate.versionCode)!=version)
                    throw new IOException("Потписот или верзијата не одговара.");
                File apk=file;
                ui(()->{busy=false;progress.dismiss();try{
                    Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".files",apk);
                    Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(intent);
                }catch(Exception e){toast("Не може да се отвори инсталацијата.");}});
            }catch(Exception e){if(file!=null)file.delete();ui(()->{busy=false;progress.dismiss();toast("Ажурирањето не успеа: "+e.getMessage());});}
        },"SetecUpdateDownload").start();
    }
}
