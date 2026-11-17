package com.samong.myhome;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * หน้าโฮม (Launcher) ที่เปิดแอป "สมุดจดของฉัน" จาก GitHub Pages
 * ปุ่ม App ในหน้าเว็บเรียก HomeBridge.openPhoneHome() เพื่อสลับไปหน้าโฮมเดิมของมือถือ
 */
public class MainActivity extends Activity {

    static final String SCOPE = "https://samongmee04-spec.github.io/To-do-list/";
    static final String HOME_URL = SCOPE + "index.html";

    static final int REQ_FILE = 11;
    static final int REQ_MIC = 12;
    static final int REQ_LOC = 13;

    WebView web;
    ValueCallback<Uri[]> fileCb;

    SpeechRecognizer recognizer;
    String listenId;
    String pendingListenId, pendingLang;

    GeolocationPermissions.Callback geoCb;
    String geoOrigin;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#7CC3F0"));
        getWindow().setNavigationBarColor(Color.parseColor("#FAF3EC"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        web = new WebView(this);
        setContentView(web);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setSupportMultipleWindows(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setAllowFileAccess(false);
        ws.setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        ws.setUserAgentString(ws.getUserAgentString() + " MyHomeLauncher/1");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "HomeBridge");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url.startsWith("http")) openExternal(url);
        });

        web.loadUrl(HOME_URL);
    }

    /* ---------- ลิงก์: หน้าในแอปเปิดในนี้ นอกนั้นส่งต่อให้แอปอื่น ---------- */

    boolean handleUrl(String url) {
        if (url.startsWith(SCOPE)) return false;
        if (url.startsWith("blob:") || url.startsWith("data:") || url.startsWith("about:")) return false;
        if (url.startsWith("intent:")) {
            try {
                Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
                startActivity(i);
            } catch (Exception e) {
                try {
                    Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    String fb = i.getStringExtra("browser_fallback_url");
                    if (fb != null) openExternal(fb);
                    else toast("ไม่พบแอปที่จะเปิดลิงก์นี้");
                } catch (Exception ignored) {
                    toast("เปิดลิงก์ไม่ได้");
                }
            }
            return true;
        }
        openExternal(url);
        return true;
    }

    void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("ไม่พบแอปที่จะเปิดลิงก์นี้");
        }
    }

    class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            return handleUrl(r.getUrl().toString());
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError err) {
            if (!r.isForMainFrame()) return;
            String html = "<html><head><meta name=viewport content='width=device-width,initial-scale=1'></head>"
                    + "<body style='margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;"
                    + "gap:16px;background:#7CC3F0;font-family:sans-serif;color:#1c2b3a;text-align:center'>"
                    + "<div style='font-size:48px'>☁️</div><div style='font-size:18px;font-weight:700'>ยังต่ออินเทอร์เน็ตไม่ได้</div>"
                    + "<button onclick=\"location.href='" + HOME_URL + "'\" style='padding:12px 28px;border:0;border-radius:22px;font-size:16px;background:#fff'>ลองใหม่</button>"
                    + "<button onclick='HomeBridge.openPhoneHome()' style='padding:12px 28px;border:0;border-radius:22px;font-size:16px;background:#1c1e22;color:#fff'>ไปหน้าแอปของมือถือ</button>"
                    + "</body></html>";
            v.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        }
    }

    class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (fileCb != null) fileCb.onReceiveValue(null);
            fileCb = cb;
            try {
                Intent i = p.createIntent();
                if (p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE)
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(i, REQ_FILE);
            } catch (Exception e) {
                fileCb = null;
                toast("เปิดคลังรูปไม่ได้");
                return false;
            }
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                cb.invoke(origin, true, false);
            } else {
                geoOrigin = origin;
                geoCb = cb;
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            }
        }

        /* ลิงก์ target=_blank / window.open */
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean userGesture, Message resultMsg) {
            WebView tmp = new WebView(MainActivity.this);
            tmp.setWebViewClient(new WebViewClient() {
                boolean done = false;

                void go(WebView v, String u) {
                    if (done || u == null || u.equals("about:blank")) return;
                    done = true;
                    if (!handleUrl(u)) web.loadUrl(u);
                    v.stopLoading();
                    v.post(v::destroy);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    go(v, r.getUrl().toString());
                    return true;
                }

                @Override
                public void onPageStarted(WebView v, String u, android.graphics.Bitmap icon) {
                    go(v, u);
                }
            });
            WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
            t.setWebView(tmp);
            resultMsg.sendToTarget();
            return true;
        }
    }

    /* ---------- ปุ่ม App: สลับไปหน้าโฮมเดิมของมือถือ ---------- */

    void openPhoneHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(home, PackageManager.MATCH_ALL);
        ResolveInfo pick = null;
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            if (pkg.equals("com.android.settings") || ri.activityInfo.name.contains("FallbackHome")) continue;
            if (pkg.equals("com.sec.android.app.launcher")) { pick = ri; break; }
            if (pick == null) pick = ri;
        }
        if (pick != null) {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                i.setClassName(pick.activityInfo.packageName, pick.activityInfo.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
        toast("ไม่พบหน้าโฮมเดิม — เลือกได้ที่ การตั้งค่า > แอปพลิเคชัน > แอปเริ่มต้น");
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception ignored) { }
    }

    /* ---------- ไมค์: แทน Web Speech ที่ WebView ไม่มีให้ ---------- */

    void startListen(String id, String lang) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingListenId = id;
            pendingLang = lang;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sr(id, "error", "service-not-allowed");
            sr(id, "end", "");
            return;
        }
        if (listenId != null) {
            String old = listenId;
            cancelListen();
            sr(old, "end", "");
        }
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        listenId = id;
        final String myId = id;
        recognizer.setRecognitionListener(new RecognitionListener() {
            boolean ended = false;

            void finish() {
                if (ended) return;
                ended = true;
                if (myId.equals(listenId)) listenId = null;
                sr(myId, "end", "");
            }

            public void onReadyForSpeech(Bundle b) { sr(myId, "start", ""); }
            public void onBeginningOfSpeech() { }
            public void onRmsChanged(float v) { }
            public void onBufferReceived(byte[] b) { }
            public void onEndOfSpeech() { }
            public void onEvent(int t, Bundle b) { }

            public void onPartialResults(Bundle b) {
                String t = first(b);
                if (t != null && !t.isEmpty()) sr(myId, "partial", t);
            }

            public void onResults(Bundle b) {
                String t = first(b);
                if (t != null && !t.isEmpty()) sr(myId, "final", t);
                else sr(myId, "error", "no-speech");
                finish();
            }

            public void onError(int code) {
                String e;
                switch (code) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: e = "no-speech"; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: e = "not-allowed"; break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: e = "network"; break;
                    case SpeechRecognizer.ERROR_AUDIO: e = "audio-capture"; break;
                    case SpeechRecognizer.ERROR_CLIENT: e = "aborted"; break;
                    default: e = "error-" + code;
                }
                sr(myId, "error", e);
                finish();
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(i);
    }

    static String first(Bundle b) {
        if (b == null) return null;
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (l == null || l.isEmpty()) ? null : l.get(0);
    }

    void stopListen(String id) {
        if (recognizer != null && id.equals(listenId)) recognizer.stopListening();
    }

    void cancelListen() {
        if (recognizer != null) recognizer.cancel();
        listenId = null;
    }

    void sr(String id, String type, String text) {
        String js = "window.__nsr&&__nsr(" + JSONObject.quote(id) + "," + JSONObject.quote(type) + "," + JSONObject.quote(text) + ")";
        web.evaluateJavascript(js, null);
    }

    /* ---------- บันทึกไฟล์ (รูป / .ics) ลงโฟลเดอร์ Download ---------- */

    void saveFile(String name, String mime, String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            if (mime.contains(";")) mime = mime.substring(0, mime.indexOf(';')).trim();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, mime);
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            ContentResolver cr = getContentResolver();
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("insert");
            try (OutputStream os = cr.openOutputStream(uri)) {
                os.write(data);
            }
            if (name.toLowerCase().endsWith(".ics") || mime.equals("text/calendar")) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "text/calendar");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    toast("บันทึก " + name + " ไว้ในโฟลเดอร์ Download แล้ว");
                }
            } else {
                toast("บันทึกไว้ในโฟลเดอร์ Download แล้ว");
            }
        } catch (Exception e) {
            toast("บันทึกไฟล์ไม่สำเร็จ");
        }
    }

    /* ---------- สะพานให้หน้าเว็บเรียก ---------- */

    class Bridge {
        @JavascriptInterface public void openPhoneHome() { runOnUiThread(MainActivity.this::openPhoneHome); }
        @JavascriptInterface public void listen(String id, String lang) { runOnUiThread(() -> startListen(id, lang == null || lang.isEmpty() ? "th-TH" : lang)); }
        @JavascriptInterface public void stopListen(String id) { runOnUiThread(() -> MainActivity.this.stopListen(id)); }
        @JavascriptInterface public void abortListen(String id) { runOnUiThread(() -> { if (id.equals(listenId)) cancelListen(); }); }
        @JavascriptInterface public void saveFile(String name, String mime, String b64) { runOnUiThread(() -> MainActivity.this.saveFile(name, mime, b64)); }
        @JavascriptInterface public String version() { return "1"; }
    }

    /* ---------- ปุ่มของระบบ ---------- */

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__launcherBack?__launcherBack():false)", v -> {
            if (!"true".equals(v) && web.canGoBack()) web.goBack();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && web != null) {
            String u = web.getUrl();
            if (u == null || !u.startsWith(SCOPE + "index.html")) web.loadUrl(HOME_URL);
            else web.evaluateJavascript("window.__launcherHome&&__launcherHome()", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.getSettings().setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }
    }

    @Override
    protected void onPause() {
        if (listenId != null) {
            String old = listenId;
            cancelListen();
            sr(old, "end", "");
        }
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                out = new Uri[n];
                for (int k = 0; k < n; k++) out[k] = data.getClipData().getItemAt(k).getUri();
            } else if (data.getData() != null) {
                out = new Uri[]{data.getData()};
            }
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_MIC && pendingListenId != null) {
            String id = pendingListenId, lang = pendingLang;
            pendingListenId = null;
            if (ok) startListen(id, lang);
            else { sr(id, "error", "not-allowed"); sr(id, "end", ""); }
        } else if (req == REQ_LOC && geoCb != null) {
            geoCb.invoke(geoOrigin, ok, false);
            geoCb = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        super.onDestroy();
    }

    boolean isOnline() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
