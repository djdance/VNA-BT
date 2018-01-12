package com.simmasoftware.vna_bt;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

public class postlog {
    Context context;
    int counter=0;
    String versionName= "unknown";
    public static String crashlogsfilename= Environment.getExternalStorageDirectory()+"/VNABT_crash_logs/";
    final SimpleDateFormat _sdfWatchUID = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public postlog(Context context){
        this.context=context;
        versionName="unknown";
        try {
            versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName="error";
        }
        final File pfile = new File(crashlogsfilename);//dir+"/doors.db");
        if (!pfile.exists()){
            pfile.mkdir();
            Log.d("djd", "postLog, created "+crashlogsfilename);
        }
    }

    public void post(final String msg, final boolean andSend) {
        if (msg!=null && !msg.equalsIgnoreCase("")){
            counter++;
            new Thread(new Runnable() {
                public void run() {
                    final String s="MODEL="+ URLEncoder.encode(Build.BRAND+" "+ Build.MODEL)+"&msg="+ URLEncoder.encode(_sdfWatchUID.format(new Date())+" "+msg)+"&counter="+counter
                            +"&version=VNABT"+ URLEncoder.encode(versionName+",SDK"+ Build.VERSION.SDK_INT);
                    Log.d("djd", "postlog saving "+s);
                    try {
                        final PrintWriter pw = new PrintWriter(new FileWriter(crashlogsfilename+ System.currentTimeMillis()+".txt", true));
                        pw.append(s);
                        pw.flush();
                        pw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (andSend)
                        sendlogs();
                }
            }).start();
        }
    }
    public void post(final String msg){
        post(msg,false);
    }

    public void sendlogs(){
        final File path=new File(crashlogsfilename);
        if (path.exists() && path.canRead()) {
            final FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    File sel = new File(dir, filename);
                    return (sel.isFile() && sel.getName().endsWith("txt") && sel.canRead());
                }
            };
            String[] fList = path.list(filter);
            if (fList!=null && fList.length>0)
            for (int i = 0; i < fList.length && i<50; i++) {
                File file=null;
                BufferedReader br=null;
                boolean dontsdelete=false;
                try {
                    final String fs=crashlogsfilename+fList[i];
                    Log.d("djd","postlog, sendlogs, file["+i+"]="+fs);
                    file=new File(fs);
                    br = new BufferedReader(new FileReader(file));
                    String line;
                    //StringBuilder text = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        //text.append(line);
                        //text.append('\n');
                        line=line.trim();
                        if (!line.equals("")) {
                            URL url = new URL("https://passio3.com/www/postlogPassio.php?" + line);
                            //Log.d("djd", "postLog, sendLogs: " + url.toString());
                            URLConnection conn = url.openConnection();
                            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream())); // do not remove!
                            String lin1e;
                            StringBuilder text1 = new StringBuilder();
                            while ((lin1e = rd.readLine()) != null) {
                                text1.append(lin1e);
                            }
                            //Log.i("djd","postlog sendlogs, text1="+text1.toString());
                        }
                    }
                } catch (Exception e) {
                    if (e!=null && e.toString().contains("resolve")){
                        dontsdelete=true;
                    }
                    Log.e("djd","postLog Exception e="+e.toString());
                }// */
                if (!dontsdelete)
                    try {
                        file.delete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                try {
                    br.close() ;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public int flushLogs(){
        final File path=new File(crashlogsfilename);
        int count=0;
        if (path.exists() && path.canRead()) {
            for (File tempFile : path.listFiles()) {
                tempFile.delete();
                count++;
            }
            Log.d("djd","postlog, flushed "+count+" logs");
        }
        return count;
    }

}