package org.asf.emuferalandroidlauncher;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.asf.rats.ConnectiveHTTPServer;
import org.asf.rats.processors.HttpUploadProcessor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {
    private static final String chatServer = "cyan.ddns.net";
    private static final String vcServer = "voice.fer.al";
    private static final int gamePort = 6968;
    private static final int chatPort = 6972;
    private static final int vcPort = 6973;

    private Service service;
    public static String statusMessage;

    private boolean bound = false;

    public static ActivityResultLauncher<Intent> genericActivityResultLauncher;

    private ServiceConnection serviceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            MainActivity.this.service = ((Service.ServiceBinder) service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            service = null;
        }
    };

    // SO: https://stackoverflow.com/a/18638588
    // Credits to Pedrohdz
    public static String wifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (Exception ex) {
            ipAddressString = null;
        }
        return ipAddressString;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        genericActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                service.activityResult = result;
            }
        });

        //Storage Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        TextView status = findViewById(R.id.textView);
        new Thread(() -> {
            runOnUiThread(() -> {
                status.setText("Preparing backend proxies...");
            });

            if (!isServiceRunning(Service.class))
                startForegroundService(new Intent(this, Service.class));
            bound = bindService(new Intent(this, Service.class), serviceConn, Context.BIND_AUTO_CREATE);

            // Start game
            try {
                runOnUiThread(() -> {
                    status.setText("Checking fer.al app...");
                });

                Intent launch = getPackageManager().getLaunchIntentForPackage("com.WildWorks.Feral");
                if (launch != null) {
                    // Check app modification

                    // Find apk
                    ApplicationInfo v = getPackageManager().getApplicationInfo("com.WildWorks.Feral", 0);
                    String apk = v.publicSourceDir;

                    // Check file for reference to emuferal online modifications
                    ZipFile zip = new ZipFile(apk);
                    ZipEntry ent = zip.getEntry("assets/bin/Data/sharedassets1.assets.split2");
                    InputStream strm = zip.getInputStream(zip.getEntry("assets/bin/Data/sharedassets1.assets.split2"));
                    byte[] sharedAssets = null;
                    // Read file
                    byte[] data = new byte[(int)ent.getSize()];
                    for (int i = 0; i < data.length; i++)
                        data[i] = (byte)strm.read();
                    sharedAssets = data;

                    // Find reference to the chat server
                    int index = findBytes(data, "cyan.ddns.net".getBytes("UTF-8"));
                    boolean hasMods = index != -1;
                    zip.close();

                    // Check if the modification is present
                    if (!hasMods) {
                        // Install perms
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            if(!getPackageManager().canRequestPackageInstalls()){
                                runOnUiThread(() -> {
                                    Toast.makeText(getApplicationContext(), "Please enable app installation permissions, this is needed to install the modified client.", Toast.LENGTH_LONG).show();
                                });
                                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                        .setData(Uri.parse(String.format("package:%s", getPackageName()))), 1);
                            }
                        }
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.REQUEST_DELETE_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.REQUEST_DELETE_PACKAGES}, 1);
                        }

                        runOnUiThread(() -> {
                            status.setText("Modifying game client...");
                        });

                        // Its not
                        // Make a modified assets file and prepare for modding the client

                        // Change director and api
                        replaceData(sharedAssets, "https://api.fer.al", "localhost:6970");
                        replaceData(sharedAssets, "https://director.fer.al", "http://localhost:6969");

                        // Change chat servers
                        replaceData(sharedAssets, "securechat.fer.al", chatServer);
                        replaceData(sharedAssets, "voice.fer.al", vcServer);

                        // Change encryption
                        int offset = findBytes(sharedAssets, "http://localhost:6969".getBytes()) - 8;
                        sharedAssets[offset] = 0;

                        // Change ports
                        // Game port
                        byte[] newGamePort = reverse(ByteBuffer.allocate(4).putInt(gamePort).array());
                        insert(sharedAssets, newGamePort, offset - 16);
                        // Chat port
                        byte[] newChatPort = reverse(ByteBuffer.allocate(4).putInt(chatPort).array());
                        insert(sharedAssets, newChatPort, findBytes(sharedAssets, chatServer.getBytes(StandardCharsets.UTF_8)) + "securechat.fer.al".length() + 3);
                        // VC port
                        byte[] newVcPort = reverse(ByteBuffer.allocate(4).putInt(vcPort).array());
                        insert(sharedAssets, newVcPort, findBytes(sharedAssets, vcServer.getBytes(StandardCharsets.UTF_8)) + "voice.fer.al".length());

                        // Show guide message
                        String ip = wifiIpAddress(getApplicationContext());
                        if (ip == null) {
                            // Set message
                            runOnUiThread(() -> {
                                status.setText("Please connect to a Wi-fi network for the following steps, you will need a pc on the same network.");
                            });

                            while (ip == null) {
                                ip = wifiIpAddress(getApplicationContext());
                                Thread.sleep(10000);
                            }
                        }

                        // Set message
                        service.registerModServerProcessor(sharedAssets);
                        final String ipF = ip;
                        statusMessage = "Modification has been prepared, on a windows/linux/mac device, please visit the following website to apply the modification on this device.:\n\nhttp://" + ipF + ":" + service.modServer.getPort() + "/guide";
                        runOnUiThread(() -> {
                            status.setText(statusMessage);
                        });

                        // Wait for the API clear message
                        String lastMessage = statusMessage;
                        while (!service.hasReceivedModification) {
                            if (!statusMessage.equals(lastMessage)){
                                runOnUiThread(() -> {
                                    status.setText(statusMessage);
                                });
                                lastMessage = statusMessage;
                            }
                            Thread.sleep(1000);
                        }

                        // Update launch
                        launch = getPackageManager().getLaunchIntentForPackage("com.WildWorks.Feral");
                    }

                    service.modServer.stop();
                    runOnUiThread(() -> {
                        status.setText("Launching game...");
                    });

                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interruptedException) {
                    }
                    finish();
                    startActivity(launch);
                } else {
                    runOnUiThread(() -> {
                        status.setText("Fer.al is not installed, please install the original client to perform client modification.");
                    });
                }
            } catch (Exception e) {
                // Log
                runOnUiThread(() -> {
                    status.setText("Caught an exception:\n" + e);
                });
            }
        }).start();
    }

    private void insert(byte[] source, byte[] data, int offset) {
        for (byte b : data) {
            source[offset++] = b;
        }
    }

    private byte[] reverse(byte[] data) {
        int ind = 0;
        byte[] iRev = new byte[data.length];
        for (int i = data.length - 1; i >= 0; i--) {
            iRev[ind++] = data[i];
        }
        return iRev;
    }

    private void replaceData(byte[] assetsData, String source, String target) throws UnsupportedEncodingException {
        // Locate byte offset
        int offset = findBytes(assetsData, source.getBytes("UTF-8"));
        // Overwrite the data
        int length = ByteBuffer.wrap(reverse(Arrays.copyOfRange(assetsData,offset - 4, offset))).getInt();
        byte[] addr = target.getBytes(StandardCharsets.UTF_8);
        for (int i = offset; i < offset + length; i++)
            if (i - offset >= addr.length)
                assetsData[i] = 0;
            else
                assetsData[i] = addr[i - offset];
    }

    private int findBytes(byte[] source, byte[] match)
    {
        ArrayList<Byte> buffer = new ArrayList<Byte>();
        for (int i = 0; i < source.length; i++)
        {
            int pos = buffer.size();
            byte b = source[i];
            if (pos < match.length && b == match[pos])
            {
                buffer.add(b);
            }
            else if (pos == match.length)
            {
                return i - buffer.size();
            }
            else if (pos != 0)
            {
                buffer.clear();
            }
        }
        return -1;
    }

    private boolean isServiceRunning(Class<?> service) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (info.service.getClassName().equals(service.getTypeName()))
                return true;
        }

        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bound) {
            bound = false;
            unbindService(serviceConn);
        }
    }
}