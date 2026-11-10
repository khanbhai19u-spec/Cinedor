package com.mayra.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.Settings;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phone actions MAYRA can perform. Each one is a Claude "tool": Claude decides when to call it,
 * the app runs it here and sends the result back.
 *
 * Android does not let normal apps silently control everything (e.g. toggle Wi-Fi, send SMS
 * in the background). Where Android requires the user's tap, we open the right screen
 * pre-filled so the user only confirms.
 */
public final class PhoneTools {

    public interface PermissionAsker { void ask(String... permissions); }

    private final Activity act;
    private final MemoryStore memory;
    private final PermissionAsker asker;

    public PhoneTools(Activity act, MemoryStore memory, PermissionAsker asker) {
        this.act = act; this.memory = memory; this.asker = asker;
    }

    // ---------------------------------------------------------------- tool definitions

    public static List<Tool> definitions() {
        List<Tool> t = new ArrayList<>();
        t.add(tool("remember", "Save a lasting fact about the user to long-term memory (name, preferences, people, routines, goals). Use when the user asks you to remember something or shares a stable personal detail worth keeping. Never save passwords, PINs, OTPs or card numbers.",
                req("fact", "string", "The fact, written as one short self-contained sentence.")));
        t.add(tool("forget", "Delete one saved memory by its id (shown like [m3] in the memory list).",
                req("memory_id", "string", "Memory id, e.g. m3")));
        t.add(tool("open_app", "Open an installed app on the phone by its name (e.g. YouTube, WhatsApp, Facebook, Camera, Calculator, bKash).",
                req("app_name", "string", "App name as the user said it")));
        t.add(tool("phone_call", "Start a phone call. Give either a phone number or a contact name from the user's contacts. Only call when the user clearly asked to call.",
                opt("phone_number", "string", "Number to call"),
                opt("contact_name", "string", "Contact name to look up")));
        t.add(tool("send_sms", "Open the SMS app with the message pre-filled; the user taps send.",
                opt("phone_number", "string", "Recipient number"),
                opt("contact_name", "string", "Recipient contact name"),
                req("message", "string", "Message text")));
        t.add(tool("whatsapp_message", "Open WhatsApp chat with a number and pre-filled text; the user taps send.",
                opt("phone_number", "string", "Number with country code, e.g. 8801712345678"),
                opt("contact_name", "string", "Contact name to look up"),
                req("message", "string", "Message text")));
        t.add(tool("set_alarm", "Set an alarm in the clock app.",
                req("hour", "integer", "Hour 0-23 (24-hour clock)"),
                req("minute", "integer", "Minute 0-59"),
                opt("label", "string", "Alarm label")));
        t.add(tool("set_timer", "Start a countdown timer.",
                req("seconds", "integer", "Duration in seconds"),
                opt("label", "string", "Timer label")));
        t.add(tool("add_calendar_event", "Open the calendar with a new event pre-filled.",
                req("title", "string", "Event title"),
                req("start", "string", "Start, local time, format yyyy-MM-dd HH:mm"),
                opt("end", "string", "End, local time, format yyyy-MM-dd HH:mm"),
                opt("location", "string", "Location"),
                opt("description", "string", "Notes")));
        t.add(tool("open_url", "Open a web page in the browser.",
                req("url", "string", "Full https URL")));
        t.add(tool("play_youtube", "Search YouTube and open the results (songs, videos, tutorials).",
                req("query", "string", "What to search for")));
        t.add(tool("navigate", "Open Google Maps directions or search for a place.",
                req("destination", "string", "Place or address"),
                opt("mode", "string", "driving, walking, bicycling or transit")));
        t.add(tool("flashlight", "Turn the phone's flashlight (torch) on or off.",
                req("on", "boolean", "true = on, false = off")));
        t.add(tool("set_volume", "Set the phone's volume level.",
                req("level_percent", "integer", "0-100"),
                opt("stream", "string", "media (default), ring, alarm or notification")));
        t.add(tool("open_settings", "Open a phone settings screen (Android does not allow apps to toggle these directly).",
                req("page", "string", "wifi, bluetooth, internet, mobile_data, location, display, sound, battery, apps, airplane, notifications, hotspot, nfc, or general")));
        t.add(tool("device_status", "Get current time, date, battery level, charging state and network status."));
        t.add(tool("share_text", "Open the Android share sheet with some text.",
                req("text", "string", "Text to share")));
        t.add(tool("copy_to_clipboard", "Copy text to the clipboard.",
                req("text", "string", "Text to copy")));
        return t;
    }

    private static final class P {
        final String name, type, desc; final boolean required;
        P(String n, String t, String d, boolean r) { name = n; type = t; desc = d; required = r; }
    }
    private static P req(String n, String t, String d) { return new P(n, t, d, true); }
    private static P opt(String n, String t, String d) { return new P(n, t, d, false); }

    private static Tool tool(String name, String desc, P... params) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        List<String> required = new ArrayList<>();
        for (P p : params) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", p.type);
            schema.put("description", p.desc);
            props.putAdditionalProperty(p.name, JsonValue.from(schema));
            if (p.required) required.add(p.name);
        }
        return Tool.builder()
                .name(name)
                .description(desc)
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(required).build())
                .build();
    }

    // ---------------------------------------------------------------- execution

    /** Runs a tool. Must be called on the UI thread. Throws with a readable message on failure. */
    public String run(String name, Map<String, Object> in) throws Exception {
        switch (name) {
            case "remember": {
                MemoryStore.Item it = memory.add(str(in, "fact", true));
                return "Saved to memory as [" + it.id + "].";
            }
            case "forget":
                return memory.remove(str(in, "memory_id", true)) ? "Deleted." : "No memory with that id.";
            case "open_app": return openApp(str(in, "app_name", true));
            case "phone_call": return call(in);
            case "send_sms": return sms(in);
            case "whatsapp_message": return whatsapp(in);
            case "set_alarm": {
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, num(in, "hour"))
                        .putExtra(AlarmClock.EXTRA_MINUTES, num(in, "minute"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                String label = str(in, "label", false);
                if (label != null) i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
                start(i);
                return String.format(Locale.US, "Alarm set for %02d:%02d.", num(in, "hour"), num(in, "minute"));
            }
            case "set_timer": {
                Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, num(in, "seconds"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                String label = str(in, "label", false);
                if (label != null) i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
                start(i);
                return "Timer started for " + num(in, "seconds") + " seconds.";
            }
            case "add_calendar_event": return calendar(in);
            case "open_url": {
                String url = str(in, "url", true);
                if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
                start(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return "Opened " + url;
            }
            case "play_youtube": {
                String q = str(in, "query", true);
                start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))));
                return "Opened YouTube search for: " + q;
            }
            case "navigate": {
                String dest = str(in, "destination", true);
                String mode = str(in, "mode", false);
                String m = "driving".equals(mode) ? "d" : "walking".equals(mode) ? "w" : "bicycling".equals(mode) ? "b" : "transit".equals(mode) ? "r" : null;
                Uri uri = m != null ? Uri.parse("google.navigation:q=" + Uri.encode(dest) + "&mode=" + m)
                        : Uri.parse("geo:0,0?q=" + Uri.encode(dest));
                try {
                    start(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException e) {
                    start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(dest))));
                }
                return "Opened maps for " + dest;
            }
            case "flashlight": return torch(bool(in, "on"));
            case "set_volume": return volume(in);
            case "open_settings": return settings(str(in, "page", true));
            case "device_status": return status();
            case "share_text": {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, str(in, "text", true));
                start(Intent.createChooser(i, "Share"));
                return "Share sheet opened.";
            }
            case "copy_to_clipboard":
                Ui.copy(act, str(in, "text", true));
                return "Copied.";
            default:
                throw new Exception("Unknown tool: " + name);
        }
    }

    /** Short Bangla label shown in the chat while a tool runs. */
    public static String label(String name) {
        switch (name) {
            case "remember": return "🧠 মনে রাখছি…";
            case "forget": return "🧠 মুছে ফেলছি…";
            case "open_app": return "📱 অ্যাপ খুলছি…";
            case "phone_call": return "📞 কল করছি…";
            case "send_sms": return "✉️ SMS তৈরি করছি…";
            case "whatsapp_message": return "💬 WhatsApp খুলছি…";
            case "set_alarm": return "⏰ অ্যালার্ম সেট করছি…";
            case "set_timer": return "⏱️ টাইমার চালু করছি…";
            case "add_calendar_event": return "📅 ক্যালেন্ডারে যোগ করছি…";
            case "open_url": return "🌐 লিংক খুলছি…";
            case "play_youtube": return "▶️ YouTube খুলছি…";
            case "navigate": return "🗺️ ম্যাপ খুলছি…";
            case "flashlight": return "🔦 টর্চ…";
            case "set_volume": return "🔊 ভলিউম ঠিক করছি…";
            case "open_settings": return "⚙️ সেটিংস খুলছি…";
            case "device_status": return "🔋 ফোনের অবস্থা দেখছি…";
            default: return "⚙️ কাজ করছি…";
        }
    }

    private void start(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        act.startActivity(i);
    }

    private String openApp(String query) throws Exception {
        PackageManager pm = act.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        String q = norm(query);
        ResolveInfo best = null;
        int bestScore = 0;
        for (ResolveInfo ri : apps) {
            String label = norm(String.valueOf(ri.loadLabel(pm)));
            String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
            int score = 0;
            if (label.equals(q)) score = 100;
            else if (label.startsWith(q)) score = 80;
            else if (label.contains(q)) score = 60;
            else if (pkg.contains(q.replace(" ", ""))) score = 50;
            else if (q.contains(label) && label.length() >= 3) score = 40;
            if (score > bestScore) { bestScore = score; best = ri; }
        }
        if (best == null) throw new Exception("No installed app matches '" + query + "'.");
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) throw new Exception("App cannot be opened.");
        start(launch);
        return "Opened " + best.loadLabel(pm);
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", "").trim();
    }

    private String resolveNumber(Map<String, Object> in) throws Exception {
        String number = str(in, "phone_number", false);
        if (number != null && !number.trim().isEmpty()) return number.replaceAll("[^0-9+]", "");
        String name = str(in, "contact_name", false);
        if (name == null || name.trim().isEmpty()) throw new Exception("Give a phone_number or contact_name.");
        if (act.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            asker.ask(Manifest.permission.READ_CONTACTS);
            throw new Exception("Contacts permission is not granted yet. A permission dialog was shown; ask the user to allow it and try again, or to say the number.");
        }
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] proj = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        List<String> matches = new ArrayList<>();
        String exact = null;
        try (Cursor c = act.getContentResolver().query(uri, proj,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", new String[]{"%" + name.trim() + "%"}, null)) {
            while (c != null && c.moveToNext()) {
                String n = c.getString(0), num = c.getString(1);
                if (n.equalsIgnoreCase(name.trim()) && exact == null) exact = num;
                if (matches.size() < 6) matches.add(n + ": " + num);
            }
        }
        if (exact != null) return exact.replaceAll("[^0-9+]", "");
        if (matches.size() == 1) return matches.get(0).substring(matches.get(0).lastIndexOf(':') + 1).replaceAll("[^0-9+]", "");
        if (matches.isEmpty()) throw new Exception("No contact named '" + name + "'.");
        throw new Exception("Several contacts match; ask the user which one: " + matches);
    }

    private String call(Map<String, Object> in) throws Exception {
        String number = resolveNumber(in);
        if (act.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            start(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number)));
            return "Calling " + number + ".";
        }
        start(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        asker.ask(Manifest.permission.CALL_PHONE);
        return "Opened the dialer with " + number + " (user taps call). Direct-call permission was requested for next time.";
    }

    private String sms(Map<String, Object> in) throws Exception {
        String number = resolveNumber(in);
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number)).putExtra("sms_body", str(in, "message", true));
        start(i);
        return "SMS composer opened for " + number + "; the user needs to tap send.";
    }

    private String whatsapp(Map<String, Object> in) throws Exception {
        String number = resolveNumber(in).replace("+", "");
        if (number.startsWith("01") && number.length() == 11) number = "88" + number; // Bangladesh local format
        start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number + "?text=" + Uri.encode(str(in, "message", true)))));
        return "WhatsApp opened for " + number + "; the user needs to tap send.";
    }

    private String calendar(Map<String, Object> in) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        Date start = f.parse(str(in, "start", true));
        String endS = str(in, "end", false);
        Date end = endS != null ? f.parse(endS) : new Date(start.getTime() + 60 * 60 * 1000L);
        Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, str(in, "title", true))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.getTime())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.getTime());
        String loc = str(in, "location", false), desc = str(in, "description", false);
        if (loc != null) i.putExtra(CalendarContract.Events.EVENT_LOCATION, loc);
        if (desc != null) i.putExtra(CalendarContract.Events.DESCRIPTION, desc);
        start(i);
        return "Calendar opened with the event; the user taps save.";
    }

    private String torch(boolean on) throws Exception {
        CameraManager cm = (CameraManager) act.getSystemService(Context.CAMERA_SERVICE);
        for (String id : cm.getCameraIdList()) {
            Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (Boolean.TRUE.equals(flash)) {
                cm.setTorchMode(id, on);
                return on ? "Flashlight on." : "Flashlight off.";
            }
        }
        throw new Exception("This phone has no flashlight.");
    }

    private String volume(Map<String, Object> in) throws Exception {
        AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
        String s = str(in, "stream", false);
        int stream = "ring".equals(s) ? AudioManager.STREAM_RING : "alarm".equals(s) ? AudioManager.STREAM_ALARM
                : "notification".equals(s) ? AudioManager.STREAM_NOTIFICATION : AudioManager.STREAM_MUSIC;
        int pct = Math.max(0, Math.min(100, num(in, "level_percent")));
        int max = am.getStreamMaxVolume(stream);
        try {
            am.setStreamVolume(stream, Math.round(max * pct / 100f), AudioManager.FLAG_SHOW_UI);
        } catch (SecurityException e) {
            throw new Exception("Android blocked changing this volume (Do Not Disturb is on).");
        }
        return "Volume set to " + pct + "%.";
    }

    private String settings(String page) {
        String action;
        switch (page) {
            case "wifi": action = Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS; break;
            case "internet": action = Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.ACTION_WIRELESS_SETTINGS; break;
            case "bluetooth": action = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            case "mobile_data": action = Settings.ACTION_DATA_ROAMING_SETTINGS; break;
            case "location": action = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
            case "display": action = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "sound": action = Settings.ACTION_SOUND_SETTINGS; break;
            case "battery": action = Intent.ACTION_POWER_USAGE_SUMMARY; break;
            case "apps": action = Settings.ACTION_APPLICATION_SETTINGS; break;
            case "airplane": action = Settings.ACTION_AIRPLANE_MODE_SETTINGS; break;
            case "notifications": action = Settings.ACTION_APP_NOTIFICATION_SETTINGS; break;
            case "hotspot": action = Settings.ACTION_WIRELESS_SETTINGS; break;
            case "nfc": action = Settings.ACTION_NFC_SETTINGS; break;
            default: action = Settings.ACTION_SETTINGS;
        }
        Intent i = new Intent(action);
        if (Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(action)) i.putExtra(Settings.EXTRA_APP_PACKAGE, act.getPackageName());
        try {
            start(i);
        } catch (ActivityNotFoundException e) {
            start(new Intent(Settings.ACTION_SETTINGS));
        }
        return "Opened " + page + " settings; the user can change it there.";
    }

    private String status() {
        StringBuilder sb = new StringBuilder();
        sb.append("Now: ").append(new SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.US).format(new Date())).append('\n');
        BatteryManager bm = (BatteryManager) act.getSystemService(Context.BATTERY_SERVICE);
        sb.append("Battery: ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).append('%')
                .append(bm.isCharging() ? " (charging)" : "").append('\n');
        ConnectivityManager cm = (ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        String net = nc == null ? "offline" : nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "mobile data" : "connected";
        sb.append("Network: ").append(net).append('\n');
        AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
        int mode = am.getRingerMode();
        sb.append("Ringer: ").append(mode == AudioManager.RINGER_MODE_SILENT ? "silent" : mode == AudioManager.RINGER_MODE_VIBRATE ? "vibrate" : "normal");
        return sb.toString();
    }

    // ---------------------------------------------------------------- input helpers

    private static String str(Map<String, Object> in, String key, boolean required) throws Exception {
        Object v = in.get(key);
        if (v == null || String.valueOf(v).trim().isEmpty()) {
            if (required) throw new Exception("Missing '" + key + "'.");
            return null;
        }
        return String.valueOf(v);
    }

    private static int num(Map<String, Object> in, String key) throws Exception {
        Object v = in.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return (int) Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { throw new Exception("'" + key + "' must be a number."); }
    }

    private static boolean bool(Map<String, Object> in, String key) throws Exception {
        Object v = in.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v == null) throw new Exception("Missing '" + key + "'.");
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
