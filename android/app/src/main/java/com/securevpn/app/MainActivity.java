package com.securevpn.app;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int VPN_REQUEST_CODE = 999;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Intent intent = new Intent(this, SecureVpnService.class);
                startService(intent);
                bridge.getWebView().evaluateJavascript("if(window._vpnCallback)window._vpnCallback('connected')", null);
            } else {
                bridge.getWebView().evaluateJavascript("if(window._vpnCallback)window._vpnCallback('denied')", null);
            }
        }
    }

    public class VpnBridge {
        @JavascriptInterface
        public void connect() {
            Intent intent = VpnService.prepare(MainActivity.this);
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST_CODE);
            } else {
                Intent vpnIntent = new Intent(MainActivity.this, SecureVpnService.class);
                startService(vpnIntent);
                bridge.getWebView().evaluateJavascript("if(window._vpnCallback)window._vpnCallback('connected')", null);
            }
        }

        @JavascriptInterface
        public void disconnect() {
            stopService(new Intent(MainActivity.this, SecureVpnService.class));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        bridge.getWebView().addJavascriptInterface(new VpnBridge(), "VpnBridge");
    }
}