package com.securevpn.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;

public class SecureVpnService extends VpnService {
    private static final String TAG = "SVPN";
    private ParcelFileDescriptor tun;
    private Process xrayProcess;

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("vpn", "VPN", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, "vpn")
                .setContentTitle("SecureVPN")
                .setContentText("Connected")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build();
        startForeground(1, n);

        new Thread(() -> {
            try {
                String xrayPath = getApplicationInfo().nativeLibraryDir + "/libxray.so";
                String config = "{\"log\":{\"loglevel\":\"warning\"},\"inbounds\":[{\"port\":10808,\"protocol\":\"socks\",\"settings\":{\"udp\":true}}],\"outbounds\":[{\"protocol\":\"vless\",\"settings\":{\"vnext\":[{\"address\":\"62.60.148.122\",\"port\":443,\"users\":[{\"id\":\"a19b0581-6edf-407d-9dc9-3807d4f4425b\",\"flow\":\"xtls-rprx-vision\",\"encryption\":\"none\"}]}]},\"streamSettings\":{\"network\":\"xhttp\",\"security\":\"reality\",\"realitySettings\":{\"serverName\":\"www.amazon.com\",\"publicKey\":\"i1hQQ1DCQGQ6wswYO1X9eOhGncX2i5IRZ1h-dW23cFE\",\"shortId\":\"19804ea488ef93\"}}}]}";

                File configFile = new File(getFilesDir(), "config.json");
                FileOutputStream fos = new FileOutputStream(configFile);
                fos.write(config.getBytes());
                fos.close();

                ProcessBuilder pb = new ProcessBuilder(xrayPath, "-c", configFile.getAbsolutePath());
                pb.redirectErrorStream(true);
                xrayProcess = pb.start();
                Log.d(TAG, "XRay started");
                Thread.sleep(5000);
                Log.d(TAG, "XRay alive: " + xrayProcess.isAlive());

                Builder b = new Builder();
                b.setSession("SecureVPN");
                b.addAddress("10.0.0.2", 32);
                b.addAddress("fd00::2", 128);
                b.addRoute("0.0.0.0", 0);
                b.addRoute("::", 0);
                b.addRoute("10.0.0.0", 8);
                b.addRoute("172.16.0.0", 12);
                b.addRoute("192.168.0.0", 16);
                b.addDnsServer("8.8.8.8");
                b.addDnsServer("1.1.1.1");
                b.addDisallowedApplication(getPackageName());
                b.setMtu(1500);
                b.setBlocking(false);
                tun = b.establish();
                Log.d(TAG, "Tunnel OK");

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                stopSelf();
            }
        }).start();

        return START_STICKY;
    }

    public void onDestroy() {
        super.onDestroy();
        if (tun != null) try { tun.close(); } catch (Exception e) {}
        if (xrayProcess != null) xrayProcess.destroy();
        Log.d(TAG, "VPN destroyed");
        stopForeground(true);
        stopSelf();
    }
}