# PhoneGap NFC 插件（中文版）

> 本项目基于 [phonegap-nfc](https://github.com/chariotsolutions/phonegap-nfc) v1.2.0 改版，仅保留 Android 和 iOS 平台，并对代码和文档进行了中文化处理。

NFC插件允许你读取和写入 NFC 标签。你还可以与其他支持 NFC 的设备进行数据互传和接收。

用途
* 从 NFC 标签读取数据
* 向 NFC 标签写入数据
* 向其他支持 NFC 的设备发送数据
* 从 NFC 设备接收数据
* 向 NFC 标签发送原始命令（ISO 14443-3A、ISO 14443-3A、ISO 14443-4、JIS 6319-4、ISO 15693）

本插件使用 NDEF（NFC 数据交换格式，NFC Data Exchange Format）以在 NFC 设备、标签类型和操作系统之间实现最大兼容性。

## 支持的平台

- Android
- iOS 11+

## 目录

* [安装](#安装)
* [NFC](#nfc)
* [NDEF](#ndef)
  - [NdefMessage](#ndefmessage)
  - [NdefRecord](#ndefrecord)
* [事件](#事件)
* [平台差异](#平台差异)
* [扫描标签时启动 Android 应用](#扫描标签时启动-android-应用)

# 安装

### Cordova

    $ cordova plugin add phonegap-nfc

### PhoneGap

    $ phonegap plugin add phonegap-nfc

## iOS 注意事项

从 iOS 11 开始，iPhone 7（及更新机型）支持读取 NFC NDEF 标签。iOS 13 新增了向 NFC 标签写入 NDEF 消息的支持。iOS 13 还增加了从某些 NFC 标签获取 UID（唯一标识符）的能力。在 iOS 上，用户必须启动 NFC 会话才能扫描标签。这与 Android 可以持续扫描 NFC 标签的方式不同。[nfc.scanNdef](#nfcscanndef) 和 [nfc.scanTag](#nfcscantag) 函数用于启动 NFC 扫描会话。NFC 标签通过 Promise 返回给调用方。如果你现有代码使用已废弃的 `nfc.beginSession`，请更新为使用 `nfc.scanNdef`。

`scanNdef` 函数使用 [NFCNDEFReaderSession](https://developer.apple.com/documentation/corenfc/nfcndefreadersession) 来检测 NDEF（NFC 数据交换格式）标签。`scanTag` 使用 iOS 13 中新增的 [NFCTagReaderSession](https://developer.apple.com/documentation/corenfc/nfctagreadersession) 来检测 ISO15693、FeliCa 和 MIFARE 标签。`scanTag` 函数会在返回 NDEF 消息的同时，返回*部分* NFC 标签的 UID 和标签类型。`scanTag` 还可以读取某些不含 NDEF 消息的 RFID（射频识别）标签。`scanTag` 无法扫描某些 NDEF 标签，包括 Topaz 和 Mifare Classic。

每次扫描前都必须调用 [nfc.scanNdef](#nfcscanndef) 和 [nfc.scanTag](#nfcscantag)。

在 iOS 上写入 NFC 标签使用与其他平台相同的 [nfc.write](#nfcwrite) 函数。虽然函数相同，但 iOS 上的行为有所不同。在 iOS 设备上调用 `nfc.write` 将启动一个新的扫描会话，并将数据写入扫描到的标签。

# NFC

> nfc 对象提供对设备 NFC 传感器的访问。

## 方法

- [nfc.addNdefListener](#nfcaddndeflistener)
- [nfc.addTagDiscoveredListener](#nfcaddtagdiscoveredlistener)
- [nfc.addMimeTypeListener](#nfcaddmimetypelistener)
- [nfc.addNdefFormatableListener](#nfcaddndefformatablelistener)
- [nfc.write](#nfcwrite)
- [nfc.makeReadOnly](#nfcmakereadonly)
- [nfc.share](#nfcshare)
- [nfc.unshare](#nfcunshare)
- [nfc.erase](#nfcerase)
- [nfc.handover](#nfchandover)
- [nfc.stopHandover](#nfcstophandover)
- [nfc.enabled](#nfcenabled)
- [nfc.showSettings](#nfcshowsettings)
- ~~nfc.beginSession~~（已废弃，推荐使用 [scanNdef](#nfcscanndef) / [scanTag](#nfcscantag)）
- ~~nfc.invalidateSession~~（已废弃，推荐使用 [cancelScan](#nfccancelscan)）
- [nfc.scanNdef](#nfcscanndef)
- [nfc.scanTag](#nfcscanTag)
- [nfc.cancelScan](#nfccancelscan)

## 读取器模式

- [nfc.readerMode](#nfcreadermode)
- [nfc.disableReaderMode](#nfcdisablereadermode)

## 标签技术函数

- [nfc.connect](#nfcconnect)
- [nfc.transceive](#nfctransceive)
- [nfc.close](#nfcclose)
- [ISO-DEP 示例](#iso-depiso-14443-4示例)

## nfc.addNdefListener

注册一个事件监听器，用于监听任何 NDEF 标签。

    nfc.addNdefListener(callback, [onSuccess], [onFailure]);

### 参数

- __callback__：读取到 NDEF 标签时调用的回调函数。
- __onSuccess__：（可选）监听器添加成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.addNdefListener` 函数注册 ndef 事件的回调。

当读取到 NDEF 标签时会触发 ndef 事件。

在 Android 上，已注册的 [mimeTypeListeners](#nfcaddmimetypelistener) 优先级高于这个更通用的 NDEF 监听器。

在 iOS 上，扫描标签前必须调用 `nfc.beginSession`。

### 支持的平台

- Android
- iOS

## nfc.removeNdefListener

移除之前通过 `nfc.addNdefListener` 注册的 NDEF 标签事件监听器。

    nfc.removeNdefListener(callback, [onSuccess], [onFailure]);

不建议移除监听器。相反，可以在回调中忽略不再需要处理的消息。

### 参数

- __callback__：之前注册的回调函数。
- __onSuccess__：（可选）监听器成功移除时调用的回调函数。
- __onFailure__：（可选）移除过程中发生错误时调用的回调函数。

### 支持的平台

- Android
- iOS

## nfc.addTagDiscoveredListener

注册一个事件监听器，用于监听匹配任何标签类型的标签。

    nfc.addTagDiscoveredListener(callback, [onSuccess], [onFailure]);

### 参数

- __callback__：检测到标签时调用的回调函数。
- __onSuccess__：（可选）监听器添加成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.addTagDiscoveredListener` 函数注册标签事件的回调。

当手机检测到任何标签时触发此事件。

### 支持的平台

- Android

## nfc.removeTagDiscoveredListener

移除之前通过 `nfc.addTagDiscoveredListener` 注册的事件监听器。

    nfc.removeTagDiscoveredListener(callback, [onSuccess], [onFailure]);

不建议移除监听器。相反，可以在回调中忽略不再需要处理的消息。

### 参数

- __callback__：之前注册的回调函数。
- __onSuccess__：（可选）监听器成功移除时调用的回调函数。
- __onFailure__：（可选）移除过程中发生错误时调用的回调函数。

### 支持的平台

- Android

## nfc.addMimeTypeListener

注册一个事件监听器，用于监听匹配指定 MIME（多用途互联网邮件扩展，Multipurpose Internet Mail Extensions）类型的 NDEF 标签。

    nfc.addMimeTypeListener(mimeType, callback, [onSuccess], [onFailure]);

### 参数

- __mimeType__：用于过滤消息的 MIME 类型。
- __callback__：读取到匹配 MIME 类型的 NDEF 标签时调用的回调函数。
- __onSuccess__：（可选）监听器添加成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.addMimeTypeListener` 函数注册 ndef-mime 事件的回调。

当读取到 `Ndef.TNF_MIME_MEDIA` 标签且匹配指定的 MIME 类型时，会触发 ndef-mime 事件。

可以多次调用此函数来注册不同的 MIME 类型。所有 MIME 消息应使用*同一个*处理函数。

    nfc.addMimeTypeListener("text/json", *onNfc*, success, failure);
    nfc.addMimeTypeListener("text/demo", *onNfc*, success, failure);

在 Android 上，用于过滤的 MIME 类型应始终为小写。（参见 [IntentFilter.addDataType()](http://developer.android.com/reference/android/content/IntentFilter.html#addDataType\(java.lang.String\))）

### 支持的平台

- Android

## nfc.removeMimeTypeListener

移除之前通过 `nfc.addMimeTypeListener` 注册的事件监听器。

    nfc.removeMimeTypeListener(mimeType, callback, [onSuccess], [onFailure]);

不建议移除监听器。相反，可以在回调中忽略不再需要处理的消息。

### 参数

- __mimeType__：用于过滤消息的 MIME 类型。
- __callback__：之前注册的回调函数。
- __onSuccess__：（可选）监听器成功移除时调用的回调函数。
- __onFailure__：（可选）移除过程中发生错误时调用的回调函数。

### 支持的平台

- Android

## nfc.addNdefFormatableListener

注册一个事件监听器，用于监听可格式化的 NDEF 标签。

    nfc.addNdefFormatableListener(callback, [onSuccess], [onFailure]);

### 参数

- __callback__：读取到可 NDEF 格式化的标签时调用的回调函数。
- __onSuccess__：（可选）监听器添加成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.addNdefFormatableListener` 函数注册 ndef-formatable 事件的回调。

当读取到可以进行 NDEF 格式化的标签时，会触发 ndef-formatable 事件。对于已经格式化为 NDEF 的标签不会触发此事件。ndef-formatable 事件不包含 NdefMessage。

### 支持的平台

- Android

## nfc.write

向 NFC 标签写入一条 NDEF 消息。

NDEF 消息是由一个或多个 NDEF 记录组成的数组。

    var message = [
        ndef.textRecord("hello, world"),
        ndef.uriRecord("http://github.com/chariotsolutions/phonegap-nfc")
    ];

    nfc.write(message, [onSuccess], [onFailure]);

### 参数

- __ndefMessage__：NDEF 记录数组。
- __onSuccess__：（可选）标签写入成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.write` 函数向 NFC 标签写入 NdefMessage。

在 **Android** 上，此方法*必须*在 NDEF 事件处理函数内调用。

在 **iOS** 上，此方法可以在 NDEF 事件处理函数外调用，它会启动一个新的扫描会话。你也可以复用读取会话来写入数据，参见下面的示例。

### 示例

#### Android

在 Android 上，write 必须在事件处理函数内调用。

    function onNfc(nfcEvent) {
    
        console.log(nfcEvent.tag);
        
        var message = [
            ndef.textRecord(new String(new Date()))
        ];
        
        nfc.write(
            message,
            success => console.log('wrote data to tag'),
            error => console.log(error)
        );

    nfc.addNdefListener(onNfc);


#### iOS - 简单用法

在 iOS 上调用 `nfc.write` 将创建一个新会话，并在用户点击 NFC 标签时写入数据。

        var message = [
            ndef.textRecord("Hello, world")
        ];

        nfc.write(
            message,
            success => console.log('wrote data to tag'),
            error => console.log(error)
        );

#### iOS - 读写结合

在 iOS 上，你可以选择使用读取会话来写入 NFC 标签。

        try {
            let tag = await nfc.scanNdef({ keepSessionOpen: true});

            // 可以在此处读取标签数据
            console.log(tag);
            
            // 此示例写入一条带有时间戳的新消息
            var message = [
                ndef.textRecord(new String(new Date()))
            ];

            nfc.write(
                message,
                success => console.log('wrote data to tag'),
                error => console.log(error)
            );

        } catch (err) {
            console.log(err);
        }

### 支持的平台

- Android
- iOS

## nfc.makeReadOnly

将 NFC 标签设为只读。**警告：此操作是永久性的。**

    nfc.makeReadOnly([onSuccess], [onFailure]);

### 参数

- __onSuccess__：（可选）标签锁定成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.makeReadOnly` 函数将 NFC 标签设为只读。**警告：此操作是永久性的**，无法撤销。

在 **Android** 上，此方法*必须*在 NDEF 事件处理函数内调用。

使用示例

    onNfc: function(nfcEvent) {

        var record = [
            ndef.textRecord("hello, world")
        ];

        var failure = function(reason) {
            alert("ERROR: " + reason);
        };

        var lockSuccess = function() {
            alert("Tag is now read only.");
        };

        var lock = function() {
            nfc.makeReadOnly(lockSuccess, failure);
        };

        nfc.write(record, lock, failure);

    },

### 支持的平台

- Android

## nfc.share

通过点对点（P2P，Peer-to-Peer）方式共享 NDEF 消息。

NDEF 消息是由一个或多个 NDEF 记录组成的数组。

    var message = [
        ndef.textRecord("hello, world")
    ];

    nfc.share(message, [onSuccess], [onFailure]);

### 参数

- __ndefMessage__：NDEF 记录数组。
- __onSuccess__：（可选）消息推送成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.share` 函数通过点对点方式写入 NdefMessage。对于另一台设备来说，这应该表现为一个 NFC 标签。

### 支持的平台

- Android

### 平台差异

    Android - 共享消息直到调用 unshare 为止

## nfc.unshare

停止通过点对点方式共享 NDEF 数据。

    nfc.unshare([onSuccess], [onFailure]);

### 参数

- __onSuccess__：（可选）共享停止时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.unshare` 函数停止通过点对点方式共享数据。

### 支持的平台

- Android

## nfc.erase

擦除 NDEF 标签

    nfc.erase([onSuccess], [onFailure]);

### 参数

- __onSuccess__：（可选）擦除成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.erase` 函数通过写入空消息来擦除标签。在写入之前会对未格式化的标签进行格式化。

此方法*必须*在 NDEF 事件处理函数内调用。

### 支持的平台

- Android

## nfc.handover

通过 NFC 切换（handover）将文件发送到另一台设备。

    var uri = "content://media/external/audio/media/175";
    nfc.handover(uri, [onSuccess], [onFailure]);


    var uris = [
        "content://media/external/audio/media/175",
        "content://media/external/audio/media/176",
        "content://media/external/audio/media/348"
    ];
    nfc.handover(uris, [onSuccess], [onFailure]);


### 参数

- __uri__：字符串形式的 URI（统一资源标识符，Uniform Resource Identifier），或 URI *数组*。
- __onSuccess__：（可选）消息推送成功时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.handover` 函数使用切换技术向 NFC 对等设备共享文件。通过指定 file:// 或 context:// URI 或 URI 列表来发送文件。文件传输由 NFC 发起，但实际传输通过蓝牙或 Wi-Fi 完成，这由 NFC 切换请求处理。Android 端代码负责构建切换 NFC 消息。

此功能仅适用于 Android，但可以为其他平台添加实现。

### 支持的平台

- Android

## nfc.stopHandover

停止通过 NFC 切换共享 NDEF 数据。

    nfc.stopHandover([onSuccess], [onFailure]);

### 参数

- __onSuccess__：（可选）共享停止时调用的回调函数。
- __onFailure__：（可选）发生错误时调用的回调函数。

### 说明

`nfc.stopHandover` 函数停止通过点对点方式共享数据。

### 支持的平台

- Android

## nfc.showSettings

显示设备上的 NFC 设置。

    nfc.showSettings(success, failure);

### 说明

`showSettings` 函数打开操作系统的 NFC 设置。

### 参数

- __success__：成功回调函数 [可选]
- __failure__：错误回调函数，发生错误时调用。 [可选]

### 快速示例

    nfc.showSettings();

### 支持的平台

- Android

## nfc.enabled

检查设备上 NFC 是否可用且已启用。

    nfc.enabled(onSuccess, onFailure);

### 参数

- __onSuccess__：NFC 已启用时调用的回调函数。
- __onFailure__：NFC 已禁用或缺失时调用的回调函数。

### 说明

`nfc.enabled` 函数明确检查手机是否有 NFC 以及 NFC 是否已启用。如果一切正常，将调用成功回调。如果有问题，将使用原因代码调用失败回调。

如果设备不支持 NFC，原因将为 **NO_NFC**；如果用户已禁用 NFC，原因将为 **NFC_DISABLED**。

注意：在 Android 上，每次 API 调用前都会检查 NFC 状态，**NO_NFC** 或 **NFC_DISABLED** 可能在*任何*失败函数中返回。

### 支持的平台

- Android
- iOS

## nfc.scanNdef

调用 `scanNdef` 将启动 iOS NFC 扫描会话。NFC 标签将通过 Promise 返回。

    nfc.scanNdef();

### 说明

`scanNdef` 函数启动 [NFCNDEFReaderSession](https://developer.apple.com/documentation/corenfc/nfcndefreadersession)，允许 iOS 扫描 NFC 标签。

### 返回值

- Promise

### 快速示例

    // Promise 方式
    nfc.scanNdef().then(
        tag => console.log(JSON.stringify(tag)),
        err => console.log(err)
    );

    // Async Await 方式
    try {
        let tag = await nfc.scanNdef();
        console.log(JSON.stringify(tag));
    } catch (err) {
        console.log(err);
    }
    

### 支持的平台

- iOS

## nfc.scanTag

调用 `scanTag` 将启动 iOS NFC 扫描会话。NFC 标签将通过 Promise 返回。

    nfc.scanTag();

### 说明

`scanTag` 函数启动 [NFCTagReaderSession](https://developer.apple.com/documentation/corenfc/nfctagreadersession)，允许 iOS 扫描 NFC 标签。

标签读取器会尝试从 NFC 标签获取 UID。它还可以从某些非 NDEF 标签中读取 UID。

在 iOS 上读取 NFC 标签请使用 [scanNdef](#nfcscanndef)，除非你需要获取标签 UID。

### 返回值

- Promise

### 快速示例

    // Promise 方式
    nfc.scanTag().then(
        tag => {
            console.log(JSON.stringify(tag))
            if (tag.id) {
                console.log(nfc.bytesToHexString(tag.id));
            }            
        },
        err => console.log(err)
    );

    // Async Await 方式
    try {
        let tag = await nfc.scanTag();
        console.log(JSON.stringify(tag));
        if (tag.id) {
            console.log(nfc.bytesToHexString(tag.id));
        }
    } catch (err) {
        console.log(err);
    }
    

### 支持的平台

- iOS


## nfc.cancelScan

使由 `scanNdef` 或 `scanTag` 启动的 NFC 会话失效。

    nfc.cancelScan();
    
### 说明

`cancelScan` 函数停止 [NFCReaderSession](https://developer.apple.com/documentation/corenfc/nfcreadersession)，将控制权交还给你的应用。

### 返回值

- Promise

### 快速示例

    nfc.cancelScan().then(
        success => { console.log('Cancelled NFC session')}, 
        err => { console.log(`Error cancelling session ${err}`)}
    );

### 支持的平台

- iOS


# 读取器模式函数

## nfc.readerMode

读取 NFC 标签，将标签数据发送到成功回调。

    nfc.readerMode(flags, readCallback, errorCallback);

### 说明

在读取器模式下，当读取到 NFC 标签时，结果将作为标签对象返回给读取回调。注意，在读取器模式下*不*使用常规的事件监听器。回调接收的标签对象*不带*事件包装器。

    {
        "isWritable": true,
        "id": [4, 96, 117, 74, -17, 34, -128],
        "techTypes": ["android.nfc.tech.IsoDep", "android.nfc.tech.NfcA", "android.nfc.tech.Ndef"],
        "type": "NFC Forum Type 4",
        "canMakeReadOnly": false,
        "maxSize": 2046,
        "ndefMessage": [{
            "id": [],
            "type": [116, 101, 120, 116, 47, 112, 103],
            "payload": [72, 101, 108, 108, 111, 32, 80, 104, 111, 110, 101, 71, 97, 112],
            "tnf": 2
        }]
    }

启用读取器模式时，前台调度和点对点功能将被禁用。

标志（flags）控制扫描哪些标签。读取器模式的一个好处是，通过添加 `nfc.FLAG_READER_NO_PLATFORM_SOUNDS` 标志，可以在扫描 NFC 标签时禁用系统提示音。有关标志的更多信息，请参见 Android 的 [NfcAdapter.enableReaderMode()](https://developer.android.com/reference/android/nfc/NfcAdapter#enableReaderMode(android.app.Activity,%20android.nfc.NfcAdapter.ReaderCallback,%20int,%20android.os.Bundle)) 文档。

### 参数

- __flags__：表示轮询技术和其他可选参数的标志
- __readCallback__：扫描到 NFC 标签时调用的回调函数。
- __errorCallback__：NFC 已禁用或缺失时调用的回调函数。

### 快速示例

    nfc.readerMode(
        nfc.FLAG_READER_NFC_A | nfc.FLAG_READER_NO_PLATFORM_SOUNDS, 
        nfcTag => console.log(JSON.stringify(nfcTag)),
        error => console.log('NFC reader mode failed', error)
    );

### 支持的平台

- Android

## nfc.disableReaderMode

禁用 NFC 读取器模式。

    nfc.disableNfcReaderMode(successCallback, errorCallback);

### 说明

禁用 NFC 读取器模式。

### 参数

- __successCallback__：NFC 读取器模式禁用成功时调用的回调函数。
- __errorCallback__：无法禁用 NFC 读取器模式时调用的回调函数。

### 快速示例

    nfc.disableReaderMode(
        () => console.log('NFC reader mode disabled'),
        error => console.log('Error disabling NFC reader mode', error)
    )

### 支持的平台

- Android


# 标签技术函数

标签技术函数提供对标签 I/O 操作的访问。连接到标签、使用 transceive 发送命令、关闭标签。更多详情请参见 Android 的 [TagTechnology](https://developer.android.com/reference/android/nfc/tech/TagTechnology) 及其实现类，如 [IsoDep](https://developer.android.com/reference/android/nfc/tech/IsoDep) 和 [NfcV](https://developer.android.com/reference/android/nfc/tech/NfcV)。这些新的 API 基于 Promise 而非回调。

#### ISO-DEP（ISO 14443-4）示例

    const DESFIRE_SELECT_PICC = '00 A4 04 00 07 D2 76 00 00 85 01 00';
    const DESFIRE_SELECT_AID = '90 5A 00 00 03 AA AA AA 00'

    async function handleDesfire(nfcEvent) {
        
        const tagId = nfc.bytesToHexString(nfcEvent.tag.id);
        console.log('Processing', tagId);

        try {
            await nfc.connect('android.nfc.tech.IsoDep', 500);
            console.log('connected to', tagId);
            
            let response = await nfc.transceive(DESFIRE_SELECT_PICC);
            ensureResponseIs('9000', response);
            
            response = await nfc.transceive(DESFIRE_SELECT_AID);
            ensureResponseIs('9100', response);
            // 91a0 表示未找到请求的应用

            alert('Selected application AA AA AA');

            // 更多 transcieve 命令可在此添加
            
        } catch (error) {
            alert(error);
        } finally {
            await nfc.close();
            console.log('closed');
        }

    }

    function ensureResponseIs(expectedResponse, buffer) {
        const responseString = util.arrayBufferToHexString(buffer);
        if (expectedResponse !== responseString) {
            const error = 'Expecting ' + expectedResponse + ' but received ' + responseString;
            throw error;
        }
    }

    function onDeviceReady() {
        nfc.addTagDiscoveredListener(handleDesfire);
    }

    document.addEventListener('deviceready', onDeviceReady, false);

## nfc.connect

连接到标签并启用此 TagTechnology 对象对标签的 I/O 操作。

    nfc.connect(tech);

    nfc.connect(tech, timeout);

### 说明

`connect` 函数启用此 TagTechnology 对象对标签的 I/O 操作。`nfc.connect` 应在收到来自 `addTagDiscoveredListener` 或 `readerMode` 回调的 nfcEvent 之后调用。一次只能有一个 TagTechnology 对象连接到一个标签。

更多信息请参见 Android 的 [TagTechnology.connect()](https://developer.android.com/reference/android/nfc/tech/TagTechnology.html#connect())。

### 参数

- __tech__：标签技术，例如 android.nfc.tech.IsoDep
- __timeout__：transceive（字节数组）超时时间，单位为毫秒 [可选]

### 返回值

- 连接成功时返回 Promise，如果标签技术支持，还可能带有 maxTransceiveLength 属性

### 快速示例

    nfc.addTagDiscoveredListener(function(nfcEvent) {
        nfc.connect('android.nfc.tech.IsoDep', 500).then(
            () => console.log('connected to', nfc.bytesToHexString(nfcEvent.tag.id)),
            (error) => console.log('connection failed', error)
        );
    })

### 支持的平台

- Android

## nfc.transceive

向标签发送原始命令并接收响应。

    nfc.transceive(data);

### 说明

`transceive` 函数向标签发送原始命令并接收响应。调用 `transceive` 前必须先调用 `nfc.connect`。传递给 transceive 的数据可以是字节的十六进制字符串表示，也可以是 ArrayBuffer。响应以 ArrayBuffer 形式在 Promise 中返回。

更多信息请参见 Android 文档 [IsoDep.transceive()](https://developer.android.com/reference/android/nfc/tech/IsoDep.html#transceive(byte[]))、[NfcV.transceive()](https://developer.android.com/reference/android/nfc/tech/NfcV.html#transceive(byte[]))、[MifareUltralight.transceive()](https://developer.android.com/reference/android/nfc/tech/MifareUltralight.html#transceive(byte[]))。

### 参数

- __data__：十六进制数据字符串或 ArrayBuffer

### 返回值

- Promise，响应数据为 ArrayBuffer

### 快速示例

    // Promise 风格
    nfc.transceive('90 5A 00 00 03 AA AA AA 00').then(
        response => console.log(util.arrayBufferToHexString(response)),
        error => console.log('Error selecting DESFire application')
    )

    // async await 风格
    const response = await nfc.transceive('90 5A 00 00 03 AA AA AA 00');
    console.log('response =',util.arrayBufferToHexString(response));

### 支持的平台

- Android

## nfc.close

关闭 TagTechnology 连接。

    nfc.close();

### 说明

`close` 函数禁用此 TagTechnology 对象对标签的 I/O 操作，并释放资源。

更多信息请参见 Android 的 [TagTechnology.close()](https://developer.android.com/reference/android/nfc/tech/TagTechnology.html#close())。

### 参数

- 无

### 返回值

- 连接成功关闭时返回 Promise

### 快速示例

    nfc.transceive().then(
        () => console.log('connection closed'),
        (error) => console.log('error closing connection', error);
    )

### 支持的平台

- Android

# NDEF

> `ndef` 对象提供 NDEF 常量、创建 NdefRecord 的函数以及数据转换函数。
> 有关常量的文档请参见 [android.nfc.NdefRecord](http://developer.android.com/reference/android/nfc/NdefRecord.html)

## NdefMessage

表示包含一条或多条 NdefRecord 的 NDEF（NFC 数据交换格式）数据消息。
本插件使用 NdefRecord 数组来表示 NdefMessage。

## NdefRecord

表示一条逻辑的（非分块的）NDEF（NFC 数据交换格式）记录。

### 属性

- __tnf__：3 位 TNF（类型名称格式，Type Name Format）— 使用 TNF_* 常量之一
- __type__：字节数组，包含 0 到 255 字节，不能为空
- __id__：字节数组，包含 0 到 255 字节，不能为空
- __payload__：字节数组，包含 0 到 (2 的 32 次方 - 1) 字节，不能为空

`ndef` 对象有一个用于创建 NdefRecord 的函数

    var type = "text/pg",
        id = [],
        payload = nfc.stringToBytes("Hello World"),
        record = ndef.record(ndef.TNF_MIME_MEDIA, type, id, payload);

还有一些针对某些记录类型的辅助函数

创建 URI 记录

    var record = ndef.uriRecord("http://chariotsolutions.com");

创建纯文本记录

    var record = ndef.textRecord("Plain text message");

创建 MIME 类型记录

    var mimeType = "text/pg",
        payload = "Hello Phongap",
        record = ndef.mimeMediaRecord(mimeType, nfc.stringToBytes(payload));

创建空记录

    var record = ndef.emptyRecord();

创建 Android 应用记录（AAR，Android Application Record）

    var record = ndef.androidApplicationRecord('com.example');

参见 `ndef.record`、`ndef.textRecord`、`ndef.mimeMediaRecord` 和 `ndef.uriRecord`。

Ndef 对象具有在某些数据类型与字节数组之间进行转换的函数。

更多文档请参见 [phonegap-nfc.js](https://github.com/chariotsolutions/phonegap-nfc/blob/master/www/phonegap-nfc.js) 源码。

# 事件

读取 NFC 标签时会触发事件。通过向 `nfc` 对象注册回调函数来添加监听器。例如 `nfc.addNdefListener(myNfcListener, win, fail);`

## NfcEvent

### 属性

- __type__：事件类型
- __tag__：Ndef 标签

### 类型

- tag
- ndef-mime
- ndef
- ndef-formatable

标签内容取决于平台。

在 Android 上扫描标签时可能包含 `id` 和 `techTypes`。

`id` 和 `serialNumber` 是同一值的不同名称。`id` 通常显示为十六进制字符串 `nfc.bytesToHexString(tag.id)`。

假设将以下 NDEF 消息写入标签，读取时将产生以下事件。

    var ndefMessage = [
        ndef.createMimeRecord('text/pg', 'Hello PhoneGap')
    ];

#### Android 上的示例事件

    {
        type: 'ndef',
        tag: {
            "isWritable": true,
            "id": [4, 96, 117, 74, -17, 34, -128],
            "techTypes": ["android.nfc.tech.IsoDep", "android.nfc.tech.NfcA", "android.nfc.tech.Ndef"],
            "type": "NFC Forum Type 4",
            "canMakeReadOnly": false,
            "maxSize": 2046,
            "ndefMessage": [{
                "id": [],
                "type": [116, 101, 120, 116, 47, 112, 103],
                "payload": [72, 101, 108, 108, 111, 32, 80, 104, 111, 110, 101, 71, 97, 112],
                "tnf": 2
            }]
        }
    }

## 获取事件详情

在事件触发之前，扫描到的标签的原始内容会写入日志。在 Android 上使用 `adb logcat`。

你也可以在事件处理函数中记录标签内容。`console.log(JSON.stringify(nfcEvent.tag))` 注意，为避免循环引用，应该对 tag 而非 event 进行字符串化。

# 平台差异

## 非 NDEF 标签

只有 Android 可以从非 NDEF NFC 标签读取数据。

## Mifare Classic 标签

许多较新的 Android 手机无法读取 Mifare Classic 标签。Mifare Ultralight 标签可以正常使用，因为它们是 NFC Forum Type 2 标签。

## 标签 ID 和元数据

只有 Android 可以从标签读取标签 ID 和其他元数据（如容量、只读状态或标签技术）。

## 多个监听器

可以在 JavaScript 中注册多个监听器，例如 addNdefListener、addTagDiscoveredListener、addMimeTypeListener。

在 Android 上，只会触发最具体的事件。如果扫描到 Mime Media 标签，只会调用 addMimeTypeListener 的回调，而不会调用 addNdefListener 中定义的回调。你可以为多个监听器使用同一个事件处理函数。

## addTagDiscoveredListener

在 Android 上，addTagDiscoveredListener 扫描非 NDEF 标签和 NDEF 标签。标签事件不包含 ndefMessage，即使标签上有 NDEF 消息也是如此。要获取 NDEF 信息，请使用 addNdefListener 或 addMimeTypeListener。

### 在 *Android* 上使用 addTagDiscoveredListener 扫描到的非 NDEF 标签

    {
        type: 'tag',
        tag: {
            "id": [-81, 105, -4, 64],
            "techTypes": ["android.nfc.tech.MifareClassic", "android.nfc.tech.NfcA", "android.nfc.tech.NdefFormatable"]
        }
    }


### 在 *Android* 上使用 addTagDiscoveredListener 扫描到的 NDEF 标签

    {
        type: 'tag',
        tag: {
            "id": [4, 96, 117, 74, -17, 34, -128],
            "techTypes": ["android.nfc.tech.IsoDep", "android.nfc.tech.NfcA", "android.nfc.tech.Ndef"]
        }
    }

# 扫描标签时启动 Android 应用

在 Android 上，可以使用意图（Intent）在读取 NFC 标签时启动你的应用。这是可选的，在 AndroidManifest.xml 中配置。

    <intent-filter>
      <action android:name="android.nfc.action.NDEF_DISCOVERED" />
      <data android:mimeType="text/pg" />
      <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>

注意：`data android:mimeType="text/pg"` 应与你在 JavaScript 中指定的数据类型匹配。

我们发现有必要在 activity 元素中添加 `android:noHistory="true"`，以便在用户按下主屏幕按钮后，扫描标签仍能启动应用。

有关过滤 NFC 意图的更多信息，请参见 Android 文档 [filtering for NFC intents](http://developer.android.com/guide/topics/connectivity/nfc/nfc.html#ndef-disc)。
