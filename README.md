# cordova-plugin-nfc-t

> 基于 [phonegap-nfc](https://github.com/chariotsolutions/phonegap-nfc) 改版，仅保留 Android 和 iOS 平台，支持 NDEF 标签的读取与写入。

---

## 安装

```bash
# 方式一：从 GitHub 安装
npm install github:lanyangayang/cordova-plugin-nfc-t

# Capacitor 项目安装后执行同步
npx cap sync
```

---

## 快速上手

### 1. 读取 NFC 标签

#### Android（后台持续监听）

```javascript
// 注册 NDEF 标签监听器
nfc.addNdefListener(
  function (nfcEvent) {
    var tag = nfcEvent.tag;
    var ndefMessage = tag.ndefMessage;

    // 读取第一条记录的 payload 并转为字符串
    var payload = nfc.bytesToString(ndefMessage[0].payload);
    console.log('读取到的内容：', payload);
  },
  function () {
    console.log('监听器注册成功，等待 NFC 标签...');
  },
  function (error) {
    console.log('注册失败：', error);
  }
);
```

#### iOS（主动启动扫描）

```javascript
// 点击按钮等时机调用，启动 NFC 扫描
async function scanNfc() {
  try {
    let tag = await nfc.scanNdef();
    let payload = nfc.bytesToString(tag.ndefMessage[0].payload);
    console.log('读取到的内容：', payload);
  } catch (err) {
    console.log('扫描失败：', err);
  }
}
```

### 2. 写入 NFC 标签

```javascript
// 创建一条文本记录
var message = [
  ndef.textRecord('Hello NFC!')
];

// Android：必须在 NDEF 事件回调中调用
// iOS：调用后会自动弹出扫描界面
nfc.write(
  message,
  function () {
    console.log('写入成功');
  },
  function (error) {
    console.log('写入失败：', error);
  }
);
```

### 3. 常用 NDEF 记录类型

```javascript
// 文本记录
ndef.textRecord('你好，世界', 'zh-CN');

// URI 记录
ndef.uriRecord('https://www.example.com');

// MIME 类型记录
ndef.mimeMediaRecord('application/json', JSON.stringify({ key: 'value' }));

// 空记录
ndef.emptyRecord();

// Android 应用记录（AAR）
ndef.androidApplicationRecord('com.example.app');
```

---

## 常用 API

### 监听类

| 方法 | 说明 | 支持平台 |
|---|---|---|
| `nfc.addNdefListener(callback, success, error)` | 监听 NDEF 标签 | Android / iOS |
| `nfc.addTagDiscoveredListener(callback, success, error)` | 监听所有标签 | Android |
| `nfc.addMimeTypeListener(mimeType, callback, success, error)` | 监听指定 MIME 类型 | Android |
| `nfc.removeNdefListener(callback, success, error)` | 移除 NDEF 监听 | Android / iOS |

### 读写类

| 方法 | 说明 | 支持平台 |
|---|---|---|
| `nfc.write(ndefMessage, success, error)` | 写入 NDEF 消息 | Android / iOS |
| `nfc.makeReadOnly(success, error)` | 设置标签为只读（不可逆） | Android |
| `nfc.erase(success, error)` | 擦除标签内容 | Android |

### iOS 扫描

| 方法 | 说明 | 支持平台 |
|---|---|---|
| `nfc.scanNdef(options)` | 启动 NDEF 扫描（返回 Promise） | iOS |
| `nfc.scanTag(options)` | 启动标签扫描，可获取 UID（返回 Promise） | iOS |
| `nfc.cancelScan()` | 取消扫描（返回 Promise） | iOS |

### 其他

| 方法 | 说明 | 支持平台 |
|---|---|---|
| `nfc.enabled(success, error)` | 检查 NFC 是否可用 | Android / iOS |
| `nfc.showSettings(success, error)` | 打开系统 NFC 设置 | Android |
| `nfc.share(message, success, error)` | Android Beam 点对点分享 | Android |

---

## 工具函数

```javascript
// 字节数组 ↔ 字符串
nfc.bytesToString(bytes);
nfc.stringToBytes(string);

// 字节数组 ↔ 十六进制字符串
nfc.bytesToHexString(bytes);
util.hexStringToArrayBuffer(hexString);
util.arrayBufferToHexString(buffer);
```

---

## 平台差异

| 特性 | Android | iOS |
|---|---|---|
| 后台持续监听 | ✅ 支持（addNdefListener） | ❌ 不支持 |
| 主动启动扫描 | ❌ 不需要 | ✅ 必须调用 scanNdef / scanTag |
| 写入时机 | 必须在标签事件回调中写入 | 调用 write 自动弹出扫描 |
| 获取标签 UID | ✅ 支持 | ⚠️ 部分标签（需使用 scanTag） |
| 系统 NFC 弹窗 | ❌ 无 | ✅ 有（系统原生界面） |

---

## 注意事项

### Android
- 必须在手机 **设置中开启 NFC** 才能使用
- 写入操作 **必须在 NDEF 事件回调内** 调用（标签离开后无法写入）
- `makeReadOnly` 是**永久性**操作，无法撤销

### iOS
- 需要 iPhone 7 及以上机型，iOS 11+ 支持读取，iOS 13+ 支持写入
- 必须在 **Xcode 中开启 NFC 能力**（Capabilities → Near Field Communication Tag Reading）
- 扫描时会弹出系统原生 NFC 界面，`alertMessage` 可以自定义提示文字
- Info.plist 中需要配置 `NFCReaderUsageDescription` 描述

---

## 相关链接

- 原项目：[phonegap-nfc](https://github.com/chariotsolutions/phonegap-nfc)
- NDEF 规范：[NFC Forum](http://www.nfc-forum.org/specs/spec_list#ndefts)
