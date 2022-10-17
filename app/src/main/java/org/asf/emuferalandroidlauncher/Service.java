package org.asf.emuferalandroidlauncher;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import org.asf.rats.ConnectiveHTTPServer;
import org.asf.rats.processors.HttpUploadProcessor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Random;
import java.util.logging.Logger;

public class Service extends android.app.Service {
    private Random rnd = new Random();
    private class ModServerProcessor extends HttpUploadProcessor {
        public byte[] sharedAssets;
        private File oldApk = null;

        public ModServerProcessor(byte[] sharedAssets) {
            this.sharedAssets = sharedAssets;
        }

        private ComponentName currentApp() {
            ComponentName cn;
            ActivityManager am = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                cn = am.getAppTasks().get(0).getTaskInfo().topActivity;
            } else {
                cn = am.getRunningTasks(1).get(0).topActivity;
            }
            return cn;
        }

        @Override
        public void process(String contentType, Socket client, String method) {
            if (getRequestPath().equals("/api/complete")) {
                hasReceivedModification = true;
                getResponse().setResponseStatus(201, "No content");
            } else if (getRequestPath().equals("/api/pull/sharedassets1.assets.split2")) {
                getResponse().headers.put("Content-Disposition", "attachment");
                setBody(sharedAssets);
            } else if (getRequestPath().equals("/api/pull/base.apk")) {
                getResponse().headers.put("Content-Disposition", "attachment");
                try {
                    setBody(new FileInputStream(getPackageManager().getApplicationInfo("com.WildWorks.Feral", 0).publicSourceDir));
                } catch (Exception e) {
                }
            } else if (getRequestPath().equals("/api/uninstall-og")) {
                try {
                    MainActivity.statusMessage = "Uninstalling OG game...";
                    Uri packageURI = Uri.parse("package:com.WildWorks.Feral");
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);

                    // Launch
                    activityResult = null;
                    MainActivity.genericActivityResultLauncher.launch(uninstallIntent);

                    // Wait for result
                    while (activityResult == null)
                        Thread.sleep(1000);

                    // Check result
                    if (activityResult.getResultCode() != 0 || getPackageManager().getLaunchIntentForPackage("com.WildWorks.Feral") != null)
                        getResponse().setResponseStatus(204, "No content"); // Not uninstalled
                } catch (Exception e) {
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:".concat("org.asf.emuferalonlineandroidlauncher"))).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    getResponse().setResponseStatus(500, "Server error");
                }
            } else if (getRequestPath().equals("/api/install-app")) {
                try {
                    long id = rnd.nextLong();
                    while (id < 100000000000000000l || id > 999999999999999999l)
                        id = rnd.nextLong();
                    long token = rnd.nextLong();
                    while (token < 100000000000000000l || token > 999999999999999999l)
                        token = rnd.nextLong();
                    if (oldApk != null && oldApk.exists())
                        oldApk.delete();
                    File apk = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "emuferalmodifiedclient-" + token + "-" + id + ".apk");
                    oldApk = apk;

                    // Save file
                    FileOutputStream out = new FileOutputStream(apk);
                    long length = Long.valueOf(getRequest().headers.get("Content-Length"));
                    long i = 0;
                    while (i < length) {
                        byte[] data = new byte[10000];
                        int l = getRequestBodyStream().read(data);
                        out.write(data, 0, l);
                        i += l;
                        int progress = (int)((100d / (double)length) * i);
                        if (progress > 100)
                            progress = 100;
                        MainActivity.statusMessage = "Receiving data... (" + progress + "%)";
                    }
                    out.close();

                    // Show code
                    MainActivity.statusMessage = "Client modification code:\n" + Long.toString(id, 16);

                    // Callback
                    String uBase = "http://" + MainActivity.wifiIpAddress(getApplicationContext()) + ":" + getServer().getPort();
                    setBody("callback=" + uBase + "/api/installcomplete/" + Long.toString(token, 16) + "/%code%");
                } catch (Exception e) {
                    getResponse().setResponseStatus(500, "Server error");
                }
            } else if (getRequestPath().startsWith("/api/installcomplete/")) {
                String code = getRequestPath().substring("/api/installcomplete/".length());
                String token = code.substring(0, code.indexOf("/"));
                code = code.substring(code.indexOf("/") + 1);
                if (!code.matches("^[0-9A-Za-z]+$") || !token.matches("^[0-9A-Za-z]+$")) {
                    getResponse().setResponseStatus(404, "Not found");
                }

                // Find apk
                File apk;
                try {
                    apk = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "emuferalmodifiedclient-" + Long.parseLong(token, 16) + "-" + Long.parseLong(code, 16) + ".apk");
                } catch (Exception e){
                    getResponse().setResponseStatus(404, "Not found");
                    return;
                }
                if (!apk.exists()) {
                    getResponse().setResponseStatus(404, "Not found");
                    return;
                }

                // Install
                try {
                    // Install request
                    activityResult = null;
                    MainActivity.statusMessage = "Installing modified client...";
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uriFromFile(getApplicationContext(), apk), "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    MainActivity.genericActivityResultLauncher.launch(intent);

                    // Wait for result
                    while (activityResult == null)
                        Thread.sleep(1000);

                    // Check result
                    if (activityResult.getResultCode() != 0 || getPackageManager().getLaunchIntentForPackage("com.WildWorks.Feral") == null) {
                        MainActivity.statusMessage = "Client modification code:\n" + code;
                        getResponse().setResponseStatus(404, "Not Found"); // Not installed
                    } else
                        apk.delete();
                } catch (Exception e) {
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:".concat("org.asf.emuferalonlineandroidlauncher"))).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    getResponse().setResponseStatus(500, "Server error");
                }
            } else if (getRequestPath().equals("/api/mod.info")) {
                String uBase = "http://" + MainActivity.wifiIpAddress(getApplicationContext()) + ":" + getServer().getPort();
                getResponse().setContent("text/plain", "base-apk=" + uBase + "/api/pull/base.apk\n" +
                        "modify:assets/bin/Data/sharedassets1.assets.split2=" + uBase + "/api/pull/sharedassets1.assets.split2\n" +
                        "complete-call=" + uBase + "/api/complete\n" +
                        "remove-old-call=" + uBase + "/api/uninstall-og\n" +
                        "install-call=" + uBase + "/api/install-app");
            } else {
                // Check resources
                switch (getRequestPath().substring(1)) {
                    case "guide": {
                        getResponse().setContent("text/html", getApplicationContext().getResources().openRawResource(R.raw.guide));
                    }
                    default: {
                        getResponse().setResponseStatus(404, "Not found");
                    }
                }
            }
        }

        private Uri uriFromFile(Context context, File file) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
            } else {
                return Uri.fromFile(file);
            }
        }

        @Override
        public HttpUploadProcessor createNewInstance() {
            return new ModServerProcessor(sharedAssets);
        }

        @Override
        public String path() {
            return "/";
        }

        @Override
        public boolean supportsGet() {
            return true;
        }

        @Override
        public boolean supportsChildPaths() {
            return true;
        }
    }

    public class ProxyProcessor extends HttpUploadProcessor {
        private String proxy;
        public ProxyProcessor(String proxy) {
            this.proxy = proxy;
        }

        @Override
        public void process(String contentType, Socket client, String method) {
            byte[] body = new byte[0];
            if (method.equals("POST")) {
                ByteArrayOutputStream strm = new ByteArrayOutputStream();
                try {
                    getRequest().transferRequestBody(strm);
                } catch (IOException e) {
                }
                body = strm.toByteArray();
                Log.println(Log.DEBUG, "PROXY", getRequest().path + ":\n" + new String(body));
            }

            // Proxy request
            try {
                HttpURLConnection conn = (HttpURLConnection)new URL(proxy + getRequest().path).openConnection();
                conn.setRequestMethod(method);
                for (String header : getRequest().headers.keySet()) {
                    conn.addRequestProperty(header, getRequest().headers.get(header));
                }
                if (body.length != 0) {
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(body);
                }

                // Get response
                int res = conn.getResponseCode();
                if (res >= 200 && res < 400)
                    getResponse().setContent(conn.getHeaderField("Content-Type"), conn.getInputStream());
                else
                    getResponse().setContent(conn.getHeaderField("Content-Type"), conn.getErrorStream());
                conn.getHeaderFields().forEach((k, v) -> {
                    if (k == null)
                        return;
                    v.forEach(t -> getResponse().setHeader(k, t, true));
                });
            } catch (IOException e) {
                setResponseCode(503);
                setResponseMessage("Service unavailable");
            }
        }

        @Override
        public HttpUploadProcessor createNewInstance() {
            return new ProxyProcessor(proxy);
        }

        @Override
        public String path() {
            return "/";
        }

        @Override
        public boolean supportsGet() {
            return true;
        }

        @Override
        public boolean supportsChildPaths() {
            return true;
        }
    }

    public ActivityResult activityResult;
    private final ServiceBinder binder = new ServiceBinder();
    public boolean hasReceivedModification;

    public ConnectiveHTTPServer apiServer = new ConnectiveHTTPServer();
    public ConnectiveHTTPServer directorServer = new ConnectiveHTTPServer();
    public ConnectiveHTTPServer modServer = new ConnectiveHTTPServer();

    private static final String apiProxy = "https://aerialworks.ddns.net:6970/";
    private static final String directorProxy = "http://aerialworks.ddns.net:6969/";

    public class ServiceBinder extends Binder {
        Service getService() {
            return Service.this;
        }
    }

    @Override
    public void onCreate() {
        // Workaround from SO
        String CHANNEL_ID = "core";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "EmuFeral Connection Manager",
                NotificationManager.IMPORTANCE_MIN);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("")
                .setContentText("")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setShowWhen(false).build();
        startForeground(1, notification);

        // Mod server
        Random portRnd = new Random();
        while (true) {
            try {
                int port = portRnd.nextInt(9999);
                while (port <= 1024)
                    port = portRnd.nextInt(9999);
                modServer.setPort(port);
                modServer.start();
                break;
            } catch (Exception e) {}
        }

        // API
        apiServer.setPort(6970);
        apiServer.registerProcessor(new ProxyProcessor(apiProxy));
        try {
            apiServer.start();
        } catch (IOException e) {
            stopSelf();
            return;
        }

        // Director
        directorServer.setPort(6969);
        directorServer.registerProcessor(new ProxyProcessor(directorProxy));
        try {
            directorServer.start();
        } catch (IOException e) {
            stopSelf();
            return;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void registerModServerProcessor(byte[] sharedAssets) {
        modServer.registerProcessor(new Service.ModServerProcessor(sharedAssets));
    }

    @Override
    public void onDestroy() {
        apiServer.stop();
        directorServer.stop();
        modServer.stop();
    }

}
