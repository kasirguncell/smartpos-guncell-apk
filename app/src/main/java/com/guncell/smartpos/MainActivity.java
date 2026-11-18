package com.guncell.smartpos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String POS_URL = "https://kasir.guncell.my.id";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private WebView webView;


    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.CAMERA}, 101);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }
        webView = new WebView(this);
        setContentView(webView);
        CookieManager.getInstance().setAcceptCookie(true);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new BluetoothPrinterBridge(), "AndroidPrinter");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://wa.me") || url.startsWith("whatsapp:") || url.startsWith("mailto:") || url.startsWith("tel:")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) { request.grant(request.getResources()); }
        });
        webView.loadUrl(POS_URL);
    }

    private final ExecutorService printExecutor = Executors.newSingleThreadExecutor();

    public class BluetoothPrinterBridge {
        @JavascriptInterface
        public void printBase64(final String base64Data) {
            printExecutor.execute(() -> {
                BluetoothSocket socket = null;
                try {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter == null || !adapter.isEnabled()) { toast("Aktifkan Bluetooth HP dulu!"); return; }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        toast("Izinkan akses Bluetooth di Pengaturan Aplikasi."); return;
                    }
                    Set<BluetoothDevice> paired = adapter.getBondedDevices();
                    BluetoothDevice target = null;
                    for (BluetoothDevice dev : paired) {
                        String n = dev.getName() != null ? dev.getName().toUpperCase() : "";
                        if (n.contains("VSC") || n.contains("MP") || n.contains("RPP") || n.contains("POS") || n.contains("PRINTER") || n.contains("MTP")) { target = dev; break; }
                    }
                    if (target == null && !paired.isEmpty()) target = paired.iterator().next();
                    if (target == null) { toast("Pair printer VSC 58mm di Bluetooth HP dulu!"); return; }
                    byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    socket = target.createRfcommSocketToServiceRecord(SPP_UUID);
                    socket.connect();
                    OutputStream os = socket.getOutputStream();
                    for (int i = 0; i < bytes.length; i += 256) { os.write(bytes, i, Math.min(256, bytes.length - i)); os.flush(); Thread.sleep(20); } Thread.sleep(900);
                } catch (Exception e) {
                    toast("Gagal cetak: " + e.getMessage());
                } finally {
                    try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                }
            });
        }
    }

    private void toast(String m) { runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show()); }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
