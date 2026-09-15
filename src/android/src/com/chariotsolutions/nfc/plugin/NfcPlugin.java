package com.chariotsolutions.nfc.plugin;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

// 使用通配符导入以便支持 Cordova 3.x
import org.apache.cordova.*; // Cordova 3.x

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

public class NfcPlugin extends CordovaPlugin implements NfcAdapter.OnNdefPushCompleteCallback {
    private static final String REGISTER_MIME_TYPE = "registerMimeType";
    private static final String REMOVE_MIME_TYPE = "removeMimeType";
    private static final String REGISTER_NDEF = "registerNdef";
    private static final String REMOVE_NDEF = "removeNdef";
    private static final String REGISTER_NDEF_FORMATABLE = "registerNdefFormatable";
    private static final String REGISTER_DEFAULT_TAG = "registerTag";
    private static final String REMOVE_DEFAULT_TAG = "removeTag";
    private static final String WRITE_TAG = "writeTag";
    private static final String MAKE_READ_ONLY = "makeReadOnly";
    private static final String ERASE_TAG = "eraseTag";
    private static final String SHARE_TAG = "shareTag";
    private static final String UNSHARE_TAG = "unshareTag";
    private static final String HANDOVER = "handover"; // Android Beam
    private static final String STOP_HANDOVER = "stopHandover";
    private static final String ENABLED = "enabled";
    private static final String INIT = "init";
    private static final String SHOW_SETTINGS = "showSettings";

    private static final String NDEF = "ndef";
    private static final String NDEF_MIME = "ndef-mime";
    private static final String NDEF_FORMATABLE = "ndef-formatable";
    private static final String TAG_DEFAULT = "tag";

    private static final String READER_MODE = "readerMode";
    private static final String DISABLE_READER_MODE = "disableReaderMode";

    // TagTechnology IsoDep、NfcA、NfcB、NfcV、NfcF、MifareClassic、MifareUltralight
    private static final String CONNECT = "connect";
    private static final String CLOSE = "close";
    private static final String TRANSCEIVE = "transceive";
    private TagTechnology tagTechnology = null;
    private Class<?> tagTechnologyClass;

    private static final String CHANNEL = "channel";

    private static final String STATUS_NFC_OK = "NFC_OK";
    private static final String STATUS_NO_NFC = "NO_NFC";
    private static final String STATUS_NFC_DISABLED = "NFC_DISABLED";
    private static final String STATUS_NDEF_PUSH_DISABLED = "NDEF_PUSH_DISABLED";

    private static final String TAG = "NfcPlugin";
    private final List<IntentFilter> intentFilters = new ArrayList<>();
    private final ArrayList<String[]> techLists = new ArrayList<>();

    private NdefMessage p2pMessage = null;
    private PendingIntent pendingIntent = null;

    private Intent savedIntent = null;

    private CallbackContext readerModeCallback;
    private CallbackContext channelCallback;
    private CallbackContext shareTagCallback;
    private CallbackContext handoverCallback;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "执行 " + action);

        // 如果 NFC 已禁用，可以调用 showSettings
        // 如果设备没有 NFC，可能需要跳过此操作
        if (action.equalsIgnoreCase(SHOW_SETTINGS)) {
            showSettings(callbackContext);
            return true;
        }

        // 通道在插件启动时建立
        if (action.equalsIgnoreCase(CHANNEL)) {
            channelCallback = callbackContext;
            return true; // short circuit
        }

        // 即使 NFC 已禁用，也允许禁用阅读器模式
        if (action.equalsIgnoreCase(DISABLE_READER_MODE)) {
            disableReaderMode(callbackContext);
            return true; // short circuit
        }

        if (!getNfcStatus().equals(STATUS_NFC_OK)) {
            callbackContext.error(getNfcStatus());
            return true; // short circuit
        }

        createPendingIntent();

        if (action.equalsIgnoreCase(READER_MODE)) {
            int flags = data.getInt(0);
            readerMode(flags, callbackContext);

        } else if (action.equalsIgnoreCase(REGISTER_MIME_TYPE)) {
            registerMimeType(data, callbackContext);

        } else if (action.equalsIgnoreCase(REMOVE_MIME_TYPE)) {
            removeMimeType(data, callbackContext);

        } else if (action.equalsIgnoreCase(REGISTER_NDEF)) {
            registerNdef(callbackContext);

        } else if (action.equalsIgnoreCase(REMOVE_NDEF)) {
            removeNdef(callbackContext);

        } else if (action.equalsIgnoreCase(REGISTER_NDEF_FORMATABLE)) {
            registerNdefFormatable(callbackContext);

        } else if (action.equals(REGISTER_DEFAULT_TAG)) {
            registerDefaultTag(callbackContext);

        } else if (action.equals(REMOVE_DEFAULT_TAG)) {
            removeDefaultTag(callbackContext);

        } else if (action.equalsIgnoreCase(WRITE_TAG)) {
            writeTag(data, callbackContext);

        } else if (action.equalsIgnoreCase(MAKE_READ_ONLY)) {
            makeReadOnly(callbackContext);

        } else if (action.equalsIgnoreCase(ERASE_TAG)) {
            eraseTag(callbackContext);

        } else if (action.equalsIgnoreCase(SHARE_TAG)) {
            shareTag(data, callbackContext);

        } else if (action.equalsIgnoreCase(UNSHARE_TAG)) {
            unshareTag(callbackContext);

        } else if (action.equalsIgnoreCase(HANDOVER)) {
            handover(data, callbackContext);

        } else if (action.equalsIgnoreCase(STOP_HANDOVER)) {
            stopHandover(callbackContext);

        } else if (action.equalsIgnoreCase(INIT)) {
            init(callbackContext);

        } else if (action.equalsIgnoreCase(ENABLED)) {
            // 每次调用前都会检查状态
            // 如果代码执行到这里，说明 NFC 已启用
            callbackContext.success(STATUS_NFC_OK);

        } else if (action.equalsIgnoreCase(CONNECT)) {
            String tech = data.getString(0);
            int timeout = data.optInt(1, -1);
            connect(tech, timeout, callbackContext);

        } else if (action.equalsIgnoreCase(TRANSCEIVE)) {
            CordovaArgs args = new CordovaArgs(data); // execute is using the old signature with JSON data

            byte[] command = args.getArrayBuffer(0);
            transceive(command, callbackContext);

        } else if (action.equalsIgnoreCase(CLOSE)) {
            close(callbackContext);

        } else {
            // 无效的操作
            return false;
        }

        return true;
    }

    private String getNfcStatus() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
        if (nfcAdapter == null) {
            return STATUS_NO_NFC;
        } else if (!nfcAdapter.isEnabled()) {
            return STATUS_NFC_DISABLED;
        } else {
            return STATUS_NFC_OK;
        }
    }

    private void readerMode(int flags, CallbackContext callbackContext) {
        Bundle extras = new Bundle(); // 未使用
        readerModeCallback = callbackContext;
        getActivity().runOnUiThread(() -> {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
            nfcAdapter.enableReaderMode(getActivity(), callback, flags, extras);
        });

    }

    private void disableReaderMode(CallbackContext callbackContext) {
        getActivity().runOnUiThread(() -> {
            readerModeCallback = null;
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
            if (nfcAdapter != null) {
                nfcAdapter.disableReaderMode(getActivity());
            }
            callbackContext.success();
        });
    }

    private NfcAdapter.ReaderCallback callback = new NfcAdapter.ReaderCallback() {
        @Override
        public void onTagDiscovered(Tag tag) {

            JSONObject json;

            // 如果标签支持 Ndef，尝试返回一条 Ndef 消息
            List<String> techList = Arrays.asList(tag.getTechList());
            if (techList.contains(Ndef.class.getName())) {
                Ndef ndef = Ndef.get(tag);
                json = Util.ndefToJSON(ndef);
            } else {
                json = Util.tagToJSON(tag);
            }

            Intent tagIntent = new Intent();
            tagIntent.putExtra(NfcAdapter.EXTRA_TAG, tag);
            setIntent(tagIntent);

            PluginResult result = new PluginResult(PluginResult.Status.OK, json);
            result.setKeepCallback(true);
            if (readerModeCallback != null) {
                readerModeCallback.sendPluginResult(result);
            } else {
                Log.i(TAG, "readerModeCallback 为 null - 阅读器模式可能在此期间已被禁用");
            }

        }
    };

    private void registerDefaultTag(CallbackContext callbackContext) {
        addTagFilter();
        restartNfc();
        callbackContext.success();
    }

    private void removeDefaultTag(CallbackContext callbackContext) {
        removeTagFilter();
        restartNfc();
        callbackContext.success();
    }

    private void registerNdefFormatable(CallbackContext callbackContext) {
        addTechList(new String[]{NdefFormatable.class.getName()});
        restartNfc();
        callbackContext.success();
    }

    private void registerNdef(CallbackContext callbackContext) {
        addTechList(new String[]{Ndef.class.getName()});
        restartNfc();
        callbackContext.success();
    }

    private void removeNdef(CallbackContext callbackContext) {
        removeTechList(new String[]{Ndef.class.getName()});
        restartNfc();
        callbackContext.success();
    }

    private void unshareTag(CallbackContext callbackContext) {
        p2pMessage = null;
        stopNdefPush();
        shareTagCallback = null;
        callbackContext.success();
    }

    private void init(CallbackContext callbackContext) {
        Log.d(TAG, "启用插件 " + getIntent());

        startNfc();
        if (!recycledIntent()) {
            parseMessage();
        }
        callbackContext.success();
    }

    private void removeMimeType(JSONArray data, CallbackContext callbackContext) throws JSONException {
        String mimeType = data.getString(0);
        removeIntentFilter(mimeType);
        restartNfc();
        callbackContext.success();
    }

    private void registerMimeType(JSONArray data, CallbackContext callbackContext) throws JSONException {
        String mimeType = "";
        try {
            mimeType = data.getString(0);
            intentFilters.add(createIntentFilter(mimeType));
            restartNfc();
            callbackContext.success();
        } catch (MalformedMimeTypeException e) {
            callbackContext.error("无效的 MIME 类型 " + mimeType);
        }
    }

    // 投机取巧，写入一条空记录。实际上某些类型的标签可能可以被擦除。
    private void eraseTag(CallbackContext callbackContext) {
        Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        NdefRecord[] records = {
                new NdefRecord(NdefRecord.TNF_EMPTY, new byte[0], new byte[0], new byte[0])
        };
        writeNdefMessage(new NdefMessage(records), tag, callbackContext);
    }

    private void writeTag(JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (getIntent() == null) {  // TODO 移除此判断并处理标签丢失
            callbackContext.error("写入标签失败，收到的 intent 为 null");
        }

        Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        NdefRecord[] records = Util.jsonToNdefRecords(data.getString(0));
        writeNdefMessage(new NdefMessage(records), tag, callbackContext);
    }

    private void writeNdefMessage(final NdefMessage message, final Tag tag, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                Ndef ndef = Ndef.get(tag);
                if (ndef != null) {
                    ndef.connect();

                    if (ndef.isWritable()) {
                        int size = message.toByteArray().length;
                        if (ndef.getMaxSize() < size) {
                            callbackContext.error("标签容量为 " + ndef.getMaxSize() +
                                    " 字节，消息大小为 " + size + " 字节。");
                        } else {
                            ndef.writeNdefMessage(message);
                            callbackContext.success();
                        }
                    } else {
                        callbackContext.error("标签为只读");
                    }
                    ndef.close();
                } else {
                    NdefFormatable formatable = NdefFormatable.get(tag);
                    if (formatable != null) {
                        formatable.connect();
                        formatable.format(message);
                        callbackContext.success();
                        formatable.close();
                    } else {
                        callbackContext.error("标签不支持 NDEF");
                    }
                }
            } catch (FormatException e) {
                callbackContext.error(e.getMessage());
            } catch (TagLostException e) {
                callbackContext.error(e.getMessage());
            } catch (IOException e) {
                callbackContext.error(e.getMessage());
            }
        });
    }

    private void makeReadOnly(final CallbackContext callbackContext) {

        if (getIntent() == null) { // 标签丢失
            callbackContext.error("设置标签为只读失败，收到的 intent 为 null");
            return;
        }

        final Tag tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            callbackContext.error("设置标签为只读失败，标签为 null");
            return;
        }

        cordova.getThreadPool().execute(() -> {
            boolean success = false;
            String message = "无法将标签设置为只读";

            Ndef ndef = Ndef.get(tag);

            try {
                if (ndef != null) {

                    ndef.connect();

                    if (!ndef.isWritable()) {
                        message = "标签不可写入";
                    } else if (ndef.canMakeReadOnly()) {
                        success = ndef.makeReadOnly();
                    } else {
                        message = "标签无法被设置为只读";
                    }

                } else {
                    message = "标签不是 NDEF 类型";
                }

            } catch (IOException e) {
                Log.e(TAG, "设置标签为只读失败", e);
                if (e.getMessage() != null) {
                    message = e.getMessage();
                } else {
                    message = e.toString();
                }
            }

            if (success) {
                callbackContext.success();
            } else {
                callbackContext.error(message);
            }
        });
    }

    private void shareTag(JSONArray data, CallbackContext callbackContext) throws JSONException {
        NdefRecord[] records = Util.jsonToNdefRecords(data.getString(0));
        this.p2pMessage = new NdefMessage(records);

        startNdefPush(callbackContext);
    }

    // setBeamPushUris
    // 你提供的每个 Uri 必须使用 'file' 或 'content' 协议。
    // 注意此方法优先级高于 setNdefPush
    //
    // 参见 http://developer.android.com/reference/android/nfc/NfcAdapter.html#setBeamPushUris(android.net.Uri[],%20android.app.Activity)
    private void handover(JSONArray data, CallbackContext callbackContext) throws JSONException {

        Uri[] uri = new Uri[data.length()];

        for (int i = 0; i < data.length(); i++) {
            uri[i] = Uri.parse(data.getString(i));
        }

        startNdefBeam(callbackContext, uri);
    }

    private void stopHandover(CallbackContext callbackContext) {
        stopNdefBeam();
        handoverCallback = null;
        callbackContext.success();
    }

    private void showSettings(CallbackContext callbackContext) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
            getActivity().startActivity(intent);
        } else {
            Intent intent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
            getActivity().startActivity(intent);
        }
        callbackContext.success();
    }

    private void createPendingIntent() {
        if (pendingIntent == null) {
            Activity activity = getActivity();
            Intent intent = new Intent(activity, activity.getClass());
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pendingIntent = PendingIntent.getActivity(activity, 0, intent, 0);
        }
    }

    private void addTechList(String[] list) {
        this.addTechFilter();
        this.addToTechList(list);
    }

    private void removeTechList(String[] list) {
        this.removeTechFilter();
        this.removeFromTechList(list);
    }

    private void addTechFilter() {
        intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED));
    }

    private void removeTechFilter() {
        Iterator<IntentFilter> iterator = intentFilters.iterator();
        while (iterator.hasNext()) {
            IntentFilter intentFilter = iterator.next();
            if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intentFilter.getAction(0))) {
                iterator.remove();
            }
        }
    }

    private void addTagFilter() {
        intentFilters.add(new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED));
    }

    private void removeTagFilter() {
        Iterator<IntentFilter> iterator = intentFilters.iterator();
        while (iterator.hasNext()) {
            IntentFilter intentFilter = iterator.next();
            if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intentFilter.getAction(0))) {
                iterator.remove();
            }
        }
    }

    private void restartNfc() {
        stopNfc();
        startNfc();
    }

    private void startNfc() {
        createPendingIntent(); // onResume 可能在 execute 之前调用 startNfc

        getActivity().runOnUiThread(() -> {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

            if (nfcAdapter != null && !getActivity().isFinishing()) {
                try {
                    IntentFilter[] intentFilters = getIntentFilters();
                    String[][] techLists = getTechLists();
                    // 除非已添加了一些 intent 过滤器或技术列表，否则不要启动 NFC，
                    // 因为空列表相当于通配符，会接收所有扫描事件
                    if (intentFilters.length > 0 || techLists.length > 0) {
                        nfcAdapter.enableForegroundDispatch(getActivity(), getPendingIntent(), intentFilters, techLists);
                    }

                    if (p2pMessage != null) {
                        nfcAdapter.setNdefPushMessage(p2pMessage, getActivity());
                    }
                } catch (IllegalStateException e) {
                    // issue 110 - 用户在 NFC 初始化时按主页键退出应用
                    Log.w(TAG, "启动 NFC 时出现非法状态异常。假定应用正在终止。");
                }

            }
        });
    }

    private void stopNfc() {
        Log.d(TAG, "停止 NFC");
        getActivity().runOnUiThread(() -> {

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

            if (nfcAdapter != null) {
                try {
                    nfcAdapter.disableForegroundDispatch(getActivity());
                } catch (IllegalStateException e) {
                    // issue 125 - 用户在 NFC 运行时按返回键退出应用
                    Log.w(TAG, "停止 NFC 时出现非法状态异常。假定应用正在终止。");
                }
            }
        });
    }

    private void startNdefBeam(final CallbackContext callbackContext, final Uri[] uris) {
        getActivity().runOnUiThread(() -> {

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

            if (nfcAdapter == null) {
                callbackContext.error(STATUS_NO_NFC);
            } else if (!nfcAdapter.isNdefPushEnabled()) {
                callbackContext.error(STATUS_NDEF_PUSH_DISABLED);
            } else {
                nfcAdapter.setOnNdefPushCompleteCallback(NfcPlugin.this, getActivity());
                try {
                    nfcAdapter.setBeamPushUris(uris, getActivity());

                    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                    result.setKeepCallback(true);
                    handoverCallback = callbackContext;
                    callbackContext.sendPluginResult(result);

                } catch (IllegalArgumentException e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void startNdefPush(final CallbackContext callbackContext) {
        getActivity().runOnUiThread(() -> {

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

            if (nfcAdapter == null) {
                callbackContext.error(STATUS_NO_NFC);
            } else if (!nfcAdapter.isNdefPushEnabled()) {
                callbackContext.error(STATUS_NDEF_PUSH_DISABLED);
            } else {
                nfcAdapter.setNdefPushMessage(p2pMessage, getActivity());
                nfcAdapter.setOnNdefPushCompleteCallback(NfcPlugin.this, getActivity());

                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(true);
                shareTagCallback = callbackContext;
                callbackContext.sendPluginResult(result);
            }
        });
    }

    private void stopNdefPush() {
        getActivity().runOnUiThread(() -> {

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

            if (nfcAdapter != null) {
                nfcAdapter.setNdefPushMessage(null, getActivity());
            }

        });
    }

    private void stopNdefBeam() {
        getActivity().runOnUiThread(() -> {

            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());

            if (nfcAdapter != null) {
                nfcAdapter.setBeamPushUris(null, getActivity());
            }

        });
    }

    private void addToTechList(String[] techs) {
        techLists.add(techs);
    }

    private void removeFromTechList(String[] techs) {
        Iterator<String[]> iterator = techLists.iterator();
        while (iterator.hasNext()) {
            String[] list = iterator.next();
            if (Arrays.equals(list, techs)) {
                iterator.remove();
            }
        }
    }

    private void removeIntentFilter(String mimeType) {
        Iterator<IntentFilter> iterator = intentFilters.iterator();
        while (iterator.hasNext()) {
            IntentFilter intentFilter = iterator.next();
            String mt = intentFilter.getDataType(0);
            if (mimeType.equals(mt)) {
                iterator.remove();
            }
        }
    }

    private IntentFilter createIntentFilter(String mimeType) throws MalformedMimeTypeException {
        IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        intentFilter.addDataType(mimeType);
        return intentFilter;
    }

    private PendingIntent getPendingIntent() {
        return pendingIntent;
    }

    private IntentFilter[] getIntentFilters() {
        return intentFilters.toArray(new IntentFilter[intentFilters.size()]);
    }

    private String[][] getTechLists() {
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return techLists.toArray(new String[0][0]);
    }

    private void parseMessage() {
        cordova.getThreadPool().execute(() -> {
            Log.d(TAG, "解析消息 " + getIntent());
            Intent intent = getIntent();
            String action = intent.getAction();
            Log.d(TAG, "动作 " + action);
            if (action == null) {
                return;
            }

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));

            if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
                Ndef ndef = Ndef.get(tag);
                fireNdefEvent(NDEF_MIME, ndef, messages);

            } else if (action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
                for (String tagTech : tag.getTechList()) {
                    Log.d(TAG, tagTech);
                    if (tagTech.equals(NdefFormatable.class.getName())) {
                        fireNdefFormatableEvent(tag);
                    } else if (tagTech.equals(Ndef.class.getName())) { //
                        Ndef ndef = Ndef.get(tag);
                        fireNdefEvent(NDEF, ndef, messages);
                    }
                }
            }

            if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
                fireTagEvent(tag);
            }

            setIntent(new Intent());
        });
    }

    // 通过通道发送事件数据，以便 JavaScript 端可以触发事件
    private void sendEvent(String type, JSONObject tag) {

        try {
            JSONObject event = new JSONObject();
            event.put("type", type);       // TAG_DEFAULT、NDEF、NDEF_MIME、NDEF_FORMATABLE
            event.put("tag", tag);         // 表示 NFC 标签和 NDEF 消息的 JSON 对象

            PluginResult result = new PluginResult(PluginResult.Status.OK, event);
            result.setKeepCallback(true);
            channelCallback.sendPluginResult(result);
        } catch (JSONException e) {
            Log.e(TAG, "通过通道发送 NFC 事件时出错", e);
        }

    }

    private void fireNdefEvent(String type, Ndef ndef, Parcelable[] messages) {
        JSONObject json = buildNdefJSON(ndef, messages);
        sendEvent(type, json);
    }

    private void fireNdefFormatableEvent(Tag tag) {
        sendEvent(NDEF_FORMATABLE, Util.tagToJSON(tag));
    }

    private void fireTagEvent(Tag tag) {
        sendEvent(TAG_DEFAULT, Util.tagToJSON(tag));
    }

    private JSONObject buildNdefJSON(Ndef ndef, Parcelable[] messages) {

        JSONObject json = Util.ndefToJSON(ndef);

        // 点对点通信时 ndef 为 null
        // 对于可格式化 NDEF 的标签，ndef 和 messages 都为 null
        if (ndef == null && messages != null) {

            try {

                if (messages.length > 0) {
                    NdefMessage message = (NdefMessage) messages[0];
                    json.put("ndefMessage", Util.messageToJSON(message));
                    // 猜测类型，希望有更确定的方式来判断类型
                    json.put("type", "NDEF Push Protocol");
                }

                if (messages.length > 1) {
                    Log.wtf(TAG, "预期只有一条 ndefMessage，但找到了 " + messages.length + " 条");
                }

            } catch (JSONException e) {
                // 不应该发生
                Log.e(Util.TAG, "将 ndefMessage 转换为 json 失败", e);
            }
        }
        return json;
    }

    private boolean recycledIntent() { // TODO 这是权宜之计，需要找到真正的解决方案

        int flags = getIntent().getFlags();
        if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) {
            Log.i(TAG, "从历史记录启动，清除回收的 intent");
            setIntent(new Intent());
            return true;
        }
        return false;
    }

    @Override
    public void onPause(boolean multitasking) {
        Log.d(TAG, "onPause " + getIntent());
        super.onPause(multitasking);
        if (multitasking) {
            // NFC 不能在后台运行
            stopNfc();
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        Log.d(TAG, "onResume " + getIntent());
        super.onResume(multitasking);
        startNfc();
    }

    @Override
    public void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent " + intent);
        super.onNewIntent(intent);
        setIntent(intent);
        savedIntent = intent;
        parseMessage();
    }

    private Activity getActivity() {
        return this.cordova.getActivity();
    }

    private Intent getIntent() {
        return getActivity().getIntent();
    }

    private void setIntent(Intent intent) {
        getActivity().setIntent(intent);
    }

    @Override
    public void onNdefPushComplete(NfcEvent event) {

        // handover（beam）优先级高于 share tag（ndef push）
        if (handoverCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, "已将消息发送到对端设备");
            result.setKeepCallback(true);
            handoverCallback.sendPluginResult(result);
        } else if (shareTagCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, "已与对端设备共享消息");
            result.setKeepCallback(true);
            shareTagCallback.sendPluginResult(result);
        }

    }

    /**
     * 通过此 TagTechnology 对象启用对标签的 I/O 操作。
     * *
     *
     * @param tech            TagTechnology 类名，例如 'android.nfc.tech.IsoDep' 或 'android.nfc.tech.NfcV'
     * @param timeout         标签超时时间
     * @param callbackContext Cordova 回调上下文
     */
    private void connect(final String tech, final int timeout, final CallbackContext callbackContext) {
        this.cordova.getThreadPool().execute(() -> {
            try {

                Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
                if (tag == null && savedIntent != null) {
                    tag = savedIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                }

                if (tag == null) {
                    Log.e(TAG, "没有标签");
                    callbackContext.error("没有标签");
                    return;
                }

                JSONObject resultObject = new JSONObject();

                // 获取此标签支持的技术
                List<String> techList = Arrays.asList(tag.getTechList());
                if (techList.contains(tech)) {
                    // 使用反射调用静态函数 Tech.get(tag)
                    tagTechnologyClass = Class.forName(tech);
                    Method method = tagTechnologyClass.getMethod("get", Tag.class);
                    tagTechnology = (TagTechnology) method.invoke(null, tag);

                    // 如果该技术支持，返回 maxTransceiveLength 并返回给用户
                    try {
                        Method maxTransceiveLengthMethod = tagTechnologyClass.getMethod("getMaxTransceiveLength");
                        resultObject.put("maxTransceiveLength", maxTransceiveLengthMethod.invoke(tagTechnology));
                    } catch(NoSuchMethodException e) {
                        // 某些技术不支持此功能，直接忽略即可。
                    } catch(JSONException e) {
                        Log.e(TAG, "序列化 JSON 时出错", e);
                    }
                }

                if (tagTechnology == null) {
                    callbackContext.error("标签不支持 " + tech);
                    return;
                }

                tagTechnology.connect();
                setTimeout(timeout);
                callbackContext.success(resultObject);

            } catch (IOException ex) {
                Log.e(TAG, "标签连接失败", ex);
                callbackContext.error("标签连接失败");

                // 用户不应该遇到这些反射错误
            } catch (ClassNotFoundException e) {
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error(e.getMessage());
            } catch (NoSuchMethodException e) {
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error(e.getMessage());
            } catch (IllegalAccessException e) {
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error(e.getMessage());
            } catch (InvocationTargetException e) {
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error(e.getMessage());
            }
        });
    }

    // 通过反射调用 tagTech 的 setTimeout，失败则静默处理
    private void setTimeout(int timeout) {
        if (timeout < 0) {
            return;
        }
        try {
            Method setTimeout = tagTechnologyClass.getMethod("setTimeout", int.class);
            setTimeout.invoke(tagTechnology, timeout);
        } catch (NoSuchMethodException e) {
            // 忽略
        } catch (IllegalAccessException e) {
            // 忽略
        } catch (InvocationTargetException e) {
            // 忽略
        }
    }

    /**
     * 禁用此 TagTechnology 对象对标签的 I/O 操作，并释放资源。
     *
     * @param callbackContext Cordova 回调上下文
     */
    private void close(CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {

                if (tagTechnology != null && tagTechnology.isConnected()) {
                    tagTechnology.close();
                    tagTechnology = null;
                    callbackContext.success();
                } else {
                    // 连接已断开
                    callbackContext.success();
                }

            } catch (IOException ex) {
                Log.e(TAG, "关闭 NFC 连接时出错", ex);
                callbackContext.error("关闭 NFC 连接时出错：" + ex.getLocalizedMessage());
            }
        });
    }

    /**
     * 向标签发送原始命令并接收响应。
     *
     * @param data            传递给标签的 byte[] 命令
     * @param callbackContext Cordova 回调上下文
     */
    private void transceive(final byte[] data, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                if (tagTechnology == null) {
                    Log.e(TAG, "没有技术对象");
                    callbackContext.error("没有技术对象");
                    return;
                }
                if (!tagTechnology.isConnected()) {
                    Log.e(TAG, "未连接");
                    callbackContext.error("未连接");
                    return;
                }

                // 使用反射以便支持多种标签类型
                Method transceiveMethod = tagTechnologyClass.getMethod("transceive", byte[].class);
                @SuppressWarnings("PrimitiveArrayArgumentToVarargsMethod")
                byte[] response = (byte[]) transceiveMethod.invoke(tagTechnology, data);

                callbackContext.success(response);

            } catch (NoSuchMethodException e) {
                String error = "TagTechnology " + tagTechnologyClass.getName() + " 没有 transceive 函数";
                Log.e(TAG, error, e);
                callbackContext.error(error);
            } catch (NullPointerException e) {
                // 如果线程池仍在处理标签时标签已被关闭，可能会发生此情况。
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error(e.getMessage());
            } catch (IllegalAccessException e) {
                Log.e(TAG, e.getMessage(), e);
                callbackContext.error(e.getMessage());
            } catch (InvocationTargetException e) {
                Log.e(TAG, e.getMessage(), e);
                Throwable cause = e.getCause();
                callbackContext.error(cause.getMessage());
            }
        });
    }

}
