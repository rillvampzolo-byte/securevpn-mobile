package com.securevpn.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SecureVpnService extends VpnService {
    private static final String TAG = "SecureVpnService";
    private static final String CHANNEL_ID = "vpn_channel";
    private ParcelFileDescriptor tunInterface;
    private Thread vpnThread;

    public int onStartCommand(Intent intent, String flags, int startId) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("SecureVPN")
                .setContentText("VPN is running")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build();
        startForeground(1, notification);

        // Запуск туннеля
        startVpnTunnel();
        return START_STICKY;
    }

    private void startVpnTunnel() {
        vpnThread = new Thread(() -> {
            try {
                // Копируем xray из jniLibs в рабочую папку
                File xrayFile = new File(getFilesDir(), "xray");
                if (!xrayFile.exists()) {
                    InputStream in = getResources().getAssets().open("xray");
                    FileOutputStream out = new FileOutputStream(xrayFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    in.close(); out.close();
                    xrayFile.setExecutable(true);
                }

                // Создаём конфиг XRay
                String config = "{\n" +
                    "  \"inbounds\": [{\n" +
                    "    \"port\": 10808,\n" +
                    "    \"protocol\": \"socks\",\n" +
                    "    \"settings\": { \"udp\": true }\n" +
                    "  }],\n" +
                    "  \"outbounds\": [{\n" +
                    "    \"protocol\": \"vless\",\n" +
                    "    \"settings\": {\n" +
                    "      \"vnext\": [{\n" +
                    "        \"address\": \"62.60.148.122\",\n" +
                    "        \"port\": 443,\n" +
                    "        \"users\": [{\n" +
                    "          \"id\": \"a19b0581-6edf-407d-9dc9-3807d4f4425b\",\n" +
                    "          \"flow\": \"xtls-rprx-vision\",\n" +
                    "          \"encryption\": \"none\"\n" +
                    "        }]\n" +
                    "      }]\n" +
                    "    },\n" +
                    "    \"streamSettings\": {\n" +
                    "      \"network\": \"tcp\",\n" +
                    "      \"security\": \"reality\",\n" +
                    "      \"realitySettings\": {\n" +
                    "        \"serverName\": \"www.microsoft.com\",\n" +
                    "        \"publicKey\": \"h7TUJlqZxLr-vWc7jZZ05Sz6ITNqdRTrWeZrPzijtXk\",\n" +
                    "        \"shortId\": \"\"\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }]\n" +
                    "}";

                File configFile = new File(getFilesDir(), "config.json");
                FileOutputStream fos = new FileOutputStream(configFile);
                fos.write(config.getBytes());
                fos.close();

                // Запускаем XRay
                ProcessBuilder pb = new ProcessBuilder(xrayFile.getAbsolutePath(), "-c", configFile.getAbsolutePath());
                pb.environment().put("HOME", getFilesDir().getAbsolutePath());
                pb.start();

                Thread.sleep(500);

                // Создаём VPN-туннель
                Builder builder = new Builder();
                builder.setSession("SecureVPN");
                builder.addAddress("10.0.0.2", 32);
                builder.addRoute("0.0.0.0", 0);
                builder.addDnsServer("1.1.1.1");
                tunInterface = builder.establish();

                Log.d(TAG, "VPN tunnel established with XRay");
            } catch (Exception e) {
                Log.e(TAG, "VPN error", e);
            }
        });
        vpnThread.start();
    }

    public void onDestroy() {
        super.onDestroy();
        try {
            if (tunInterface != null) tunInterface.close();
        } catch (Exception e) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}