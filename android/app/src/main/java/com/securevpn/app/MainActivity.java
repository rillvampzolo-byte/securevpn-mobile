package com.securevpn.app;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

public class MainActivity extends BridgeActivity {
    private static final int VPN_REQUEST_CODE = 100;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPlugin(VpnPlugin.class);
    }

    @CapacitorPlugin(name = "VpnPlugin")
    public static class VpnPlugin extends Plugin {

        @PluginMethod
        public void connect(PluginCall call) {
            Intent intent = VpnService.prepare(getContext());
            if (intent != null) {
                startActivityForResult(call, intent, VPN_REQUEST_CODE);
            } else {
                startVpn();
                call.resolve();
            }
        }

        @PluginMethod
        public void disconnect(PluginCall call) {
            stopVpn();
            call.resolve();
        }

        private void startVpn() {
            Intent intent = new Intent(getContext(), SecureVpnService.class);
            getContext().startService(intent);
        }

        private void stopVpn() {
            Intent intent = new Intent(getContext(), SecureVpnService.class);
            getContext().stopService(intent);
        }

        @Override
        protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
            super.handleOnActivityResult(requestCode, resultCode, data);
            if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
                startVpn();
                getSavedCall().resolve();
            }
        }
    }
}