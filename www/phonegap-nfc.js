/*jshint  bitwise: false, camelcase: false, quotmark: false, unused: vars, esversion: 6, browser: true*/
/*global cordova, console, require */

function handleNfcFromIntentFilter() {

    // 这在以前是通过 cordova.addConstructor 实现的，但在 PhoneGap-2.2.0 中出现了问题。
    // 我们需要在应用的 deviceready 代码执行完成之后，再处理启动应用的 Intent 中的 NFC 数据。
    // 升级到 2.2.0 之后，addConstructor 在 deviceReady 完成之前就已经结束了，
    // 导致 ndef 监听器还没有被注册。
    // 应该有更好的解决方案。
    if (cordova.platformId === "android" || cordova.platformId === "windows") {
        setTimeout(
            function () {
                cordova.exec(
                    function () {
                        console.log("NfcPlugin 初始化完成");
                    },
                    function (reason) {
                        console.log("NfcPlugin 初始化失败：" + reason);
                    },
                    "NfcPlugin", "init", []
                );
            }, 10
        );
    }
}

document.addEventListener('deviceready', handleNfcFromIntentFilter, false);

var ndef = {

    // 常量定义请参考 android.nfc.NdefRecord 文档
    // http://developer.android.com/reference/android/nfc/NdefRecord.html
    TNF_EMPTY: 0x0,
    TNF_WELL_KNOWN: 0x01,
    TNF_MIME_MEDIA: 0x02,
    TNF_ABSOLUTE_URI: 0x03,
    TNF_EXTERNAL_TYPE: 0x04,
    TNF_UNKNOWN: 0x05,
    TNF_UNCHANGED: 0x06,
    TNF_RESERVED: 0x07,

    RTD_TEXT: [0x54], // "T"
    RTD_URI: [0x55], // "U"
    RTD_SMART_POSTER: [0x53, 0x70], // "Sp"
    RTD_ALTERNATIVE_CARRIER: [0x61, 0x63], // "ac"
    RTD_HANDOVER_CARRIER: [0x48, 0x63], // "Hc"
    RTD_HANDOVER_REQUEST: [0x48, 0x72], // "Hr"
    RTD_HANDOVER_SELECT: [0x48, 0x73], // "Hs"

    /**
     * 创建一个 NDEF 记录的 JSON 表示。
     *
     * @tnf 3 位 TNF（类型名称格式）- 使用 TNF_* 常量之一
     * @type 字节数组，包含 0 到 255 字节，不能为空
     * @id 字节数组，包含 0 到 255 字节，不能为空
     * @payload 字节数组，包含 0 到 (2 ** 32 - 1) 字节，不能为空
     *
     * @returns NDEF 记录的 JSON 表示
     *
     * @see Ndef.textRecord, Ndef.uriRecord and Ndef.mimeMediaRecord for examples
     */
    record: function (tnf, type, id, payload) {

        // 处理空值
        if (!tnf) { tnf = ndef.TNF_EMPTY; }
        if (!type) { type = []; }
        if (!id) { id = []; }
        if (!payload) { payload = []; }

        // 将字符串转换为数组
        if (!(type instanceof Array)) {
            type = nfc.stringToBytes(type);
        }
        if (!(id instanceof Array)) {
            id = nfc.stringToBytes(id);
        }
        if (!(payload instanceof Array)) {
            payload = nfc.stringToBytes(payload);
        }

        return {
            tnf: tnf,
            type: type,
            id: id,
            payload: payload
        };
    },

    /**
     * 创建包含纯文本的 NDEF 记录的辅助方法。
     *
     * @text 要编码的文本字符串
     * @languageCode ISO/IANA 语言代码。例如：”fi”、”en-US”、”fr-CA”、”jp”。（可选）
     * @id 字节数组（可选）
     */
    textRecord: function (text, languageCode, id) {
        var payload = textHelper.encodePayload(text, languageCode);
        if (!id) { id = []; }
        return ndef.record(ndef.TNF_WELL_KNOWN, ndef.RTD_TEXT, id, payload);
    },

    /**
     * 创建包含 URI 的 NDEF 记录的辅助方法。
     *
     * @uri 字符串
     * @id 字节数组（可选）
     */
    uriRecord: function (uri, id) {
        var payload = uriHelper.encodePayload(uri);
        if (!id) { id = []; }
        return ndef.record(ndef.TNF_WELL_KNOWN, ndef.RTD_URI, id, payload);
    },

    /**
     * 创建包含绝对 URI 的 NDEF 记录的辅助方法。
     *
     * 绝对 URI 记录意味着 URI 描述了记录的有效载荷。
     *
     * 例如，SOAP 消息可以使用 "http://schemas.xmlsoap.org/soap/envelope/" 作为类型，
     * 并将 XML 内容作为有效载荷。
     *
     * 绝对 URI 也可用于为 Windows 写入 LaunchApp 记录。
     *
     * 请参阅 NDEF 规范的 2.4.2 节 Payload Type
     * http://www.nfc-forum.org/specs/spec_list#ndefts
     *
     * 请注意，默认情况下 Android 会打开绝对 URI 记录（TNF=3）的 type 字段中定义的 URI，
     * 并忽略有效载荷。BlackBerry 和 Windows 不会为 TNF=3 打开浏览器。
     *
     * 如需将 URI 作为有效载荷写入，请使用 ndef.uriRecord(uri)
     *
     * @uri 字符串
     * @payload 字节数组或字符串
     * @id 字节数组（可选）
     */
    absoluteUriRecord: function (uri, payload, id) {
        if (!id) { id = []; }
        if (!payload) { payload = []; }
        return ndef.record(ndef.TNF_ABSOLUTE_URI, uri, id, payload);
    },

    /**
     * 创建包含 MIME 媒体类型的 NDEF 记录的辅助方法。
     *
     * @mimeType 字符串
     * @payload 字节数组
     * @id 字节数组（可选）
     */
    mimeMediaRecord: function (mimeType, payload, id) {
        if (!id) { id = []; }
        return ndef.record(ndef.TNF_MIME_MEDIA, nfc.stringToBytes(mimeType), id, payload);
    },

    /**
     * 创建包含智能海报（Smart Poster）的 NDEF 记录的辅助方法。
     *
     * @ndefRecords NDEF 记录数组
     * @id 字节数组（可选）
     */
    smartPoster: function (ndefRecords, id) {
        var payload = [];

        if (!id) { id = []; }

        if (ndefRecords)
        {
            // 在编码之前确保我们拥有类似 NDEF 记录的数组
            if (ndefRecords[0] instanceof Object && ndefRecords[0].hasOwnProperty('tnf')) {
                payload = ndef.encodeMessage(ndefRecords);
            } else {
                // 假设调用者已经将 NDEF 记录编码为字节数组
                payload = ndefRecords;
            }
        } else {
            console.log("警告：期望传入 NDEF 记录数组");
        }

        return ndef.record(ndef.TNF_WELL_KNOWN, ndef.RTD_SMART_POSTER, id, payload);
    },

    /**
     * 创建空 NDEF 记录的辅助方法。
     *
     */
    emptyRecord: function() {
        return ndef.record(ndef.TNF_EMPTY, [], [], []);
    },

    /**
     * 创建 Android 应用记录（AAR）的辅助方法。
     * http://developer.android.com/guide/topics/connectivity/nfc/nfc.html#aar
     *
     */
    androidApplicationRecord: function(packageName) {
        return ndef.record(ndef.TNF_EXTERNAL_TYPE, "android.com:pkg", [], packageName);
    },

    /**
     * 将 NDEF 消息编码为可写入 NFC 标签的字节数组。
     *
     * @ndefRecords NDEF 记录数组
     *
     * @returns 字节数组
     *
     * @see NFC Data Exchange Format (NDEF) http://www.nfc-forum.org/specs/spec_list/
     */
    encodeMessage: function (ndefRecords) {

        var encoded = [],
            tnf_byte,
            type_length,
            payload_length,
            id_length,
            i,
            mb, me, // messageBegin（消息开始）, messageEnd（消息结束）
            cf = false, // chunkFlag（分块标志） TODO 实现
            sr, // boolean shortRecord（短记录标志）
            il; // boolean idLengthFieldIsPresent（ID长度字段是否存在）

        for(i = 0; i < ndefRecords.length; i++) {

            mb = (i === 0);
            me = (i === (ndefRecords.length - 1));
            sr = (ndefRecords[i].payload.length < 0xFF);
            il = (ndefRecords[i].id.length > 0);
            tnf_byte = ndef.encodeTnf(mb, me, cf, sr, il, ndefRecords[i].tnf);
            encoded.push(tnf_byte);

            type_length = ndefRecords[i].type.length;
            encoded.push(type_length);

            if (sr) {
                payload_length = ndefRecords[i].payload.length;
                encoded.push(payload_length);
            } else {
                payload_length = ndefRecords[i].payload.length;
                // 4 字节
                encoded.push((payload_length >> 24));
                encoded.push((payload_length >> 16));
                encoded.push((payload_length >> 8));
                encoded.push((payload_length & 0xFF));
            }

            if (il) {
                id_length = ndefRecords[i].id.length;
                encoded.push(id_length);
            }

            encoded = encoded.concat(ndefRecords[i].type);

            if (il) {
                encoded = encoded.concat(ndefRecords[i].id);
            }

            encoded = encoded.concat(ndefRecords[i].payload);
        }

        return encoded;
    },

    /**
     * 将字节数组解码为 NDEF 消息
     *
     * @bytes 从 NFC 标签读取的字节数组
     *
     * @returns NDEF 记录数组
     *
     * @see NFC Data Exchange Format (NDEF) http://www.nfc-forum.org/specs/spec_list/
     */
    decodeMessage: function (ndefBytes) {

        var bytes = ndefBytes.slice(0), // 克隆一份，因为解析是破坏性的
            ndef_message = [],
            tnf_byte,
            header,
            type_length = 0,
            payload_length = 0,
            id_length = 0,
            record_type = [],
            id = [],
            payload = [];

        while(bytes.length) {
            tnf_byte = bytes.shift();
            header = ndef.decodeTnf(tnf_byte);

            type_length = bytes.shift();

            if (header.sr) {
                payload_length = bytes.shift();
            } else {
                // 接下来的 4 个字节是长度
                payload_length = ((0xFF & bytes.shift()) << 24) |
                    ((0xFF & bytes.shift()) << 26) |
                    ((0xFF & bytes.shift()) << 8) |
                    (0xFF & bytes.shift());
            }

            if (header.il) {
                id_length = bytes.shift();
            }

            record_type = bytes.splice(0, type_length);
            id = bytes.splice(0, id_length);
            payload = bytes.splice(0, payload_length);

            ndef_message.push(
                ndef.record(header.tnf, record_type, id, payload)
            );

            if (header.me) { break; } // 最后一条消息
        }

        return ndef_message;
    },

    /**
     * 从 TNF 字节中解码位标志。
     *
     * @returns 包含已解码数据的对象
     *
     *  请参阅 NFC 数据交换格式（NDEF）规范第 3.2 节 RecordLayout
     */
    decodeTnf: function (tnf_byte) {
        return {
            mb: (tnf_byte & 0x80) !== 0,
            me: (tnf_byte & 0x40) !== 0,
            cf: (tnf_byte & 0x20) !== 0,
            sr: (tnf_byte & 0x10) !== 0,
            il: (tnf_byte & 0x8) !== 0,
            tnf: (tnf_byte & 0x7)
        };
    },

    /**
     * 将 NDEF 位标志编码为 TNF 字节。
     *
     * @returns TNF 字节
     *
     *  请参阅 NFC 数据交换格式（NDEF）规范第 3.2 节 RecordLayout
     */
    encodeTnf: function (mb, me, cf, sr, il, tnf) {

        var value = tnf;

        if (mb) {
            value = value | 0x80;
        }

        if (me) {
            value = value | 0x40;
        }

        // 注意：如果 cf 为真，则 me、mb、li 必须为 false，且 tnf 必须为 0x6
        if (cf) {
            value = value | 0x20;
        }

        if (sr) {
            value = value | 0x10;
        }

        if (il) {
            value = value | 0x8;
        }

        return value;
    },

    /**
     * 将 TNF 转换为字符串，以便用户友好地显示
     *
     */
    tnfToString: function (tnf) {
        var value = tnf;

        switch (tnf) {
            case ndef.TNF_EMPTY:
                value = "Empty";
                break;
            case ndef.TNF_WELL_KNOWN:
                value = "Well Known";
                break;
            case ndef.TNF_MIME_MEDIA:
                value = "Mime Media";
                break;
            case ndef.TNF_ABSOLUTE_URI:
                value = "Absolute URI";
                break;
            case ndef.TNF_EXTERNAL_TYPE:
                value = "External";
                break;
            case ndef.TNF_UNKNOWN:
                value = "Unknown";
                break;
            case ndef.TNF_UNCHANGED:
                value = "Unchanged";
                break;
            case ndef.TNF_RESERVED:
                value = "Reserved";
                break;
        }
        return value;
    }

};

// nfc 提供了对 phonegap 原生实现的 JavaScript 封装
var nfc = {
    
    multiCallbackTest: function(success, failure) {
        cordova.exec(success, failure, "NfcPlugin", "multiCallbackTest", []);
    },

    // multiCallbackTest: function(success, failure) {
    //     //cordova.exec(success, failure, "NfcPlugin", "multiCallbackTest", []);
    //     setInterval(failure, 10000, '来自 JavaScript 的测试！');
    // },
    
    addTagDiscoveredListener: function (callback, win, fail) {
        document.addEventListener("tag", callback, false);
        cordova.exec(win, fail, "NfcPlugin", "registerTag", []);
    },

    addMimeTypeListener: function (mimeType, callback, win, fail) {
        document.addEventListener("ndef-mime", callback, false);
        cordova.exec(win, fail, "NfcPlugin", "registerMimeType", [mimeType]);
    },

    addNdefListener: function (callback, win, fail) {
        document.addEventListener("ndef", callback, false);
        cordova.exec(win, fail, "NfcPlugin", "registerNdef", []);
    },

    addNdefFormatableListener: function (callback, win, fail) {
        document.addEventListener("ndef-formatable", callback, false);
        cordova.exec(win, fail, "NfcPlugin", "registerNdefFormatable", []);
    },

    write: function (ndefMessage, win, fail, options) {      
        
        if (cordova.platformId === "ios") {
          cordova.exec(win, fail, "NfcPlugin", "writeTag", [ndefMessage, options]);        
        } else {
          cordova.exec(win, fail, "NfcPlugin", "writeTag", [ndefMessage]);
        }
    },

    makeReadOnly: function (win, fail) {
        cordova.exec(win, fail, "NfcPlugin", "makeReadOnly", []);
    },

    share: function (ndefMessage, win, fail) {
        cordova.exec(win, fail, "NfcPlugin", "shareTag", [ndefMessage]);
    },

    unshare: function (win, fail) {
        cordova.exec(win, fail, "NfcPlugin", "unshareTag", []);
    },

    handover: function (uris, win, fail) {
        // 如果只有单个 URI，将其包装到数组中
        if (!Array.isArray(uris)) {
            uris = [ uris ];
        }
        cordova.exec(win, fail, "NfcPlugin", "handover", uris);
    },

    stopHandover: function (win, fail) {
        cordova.exec(win, fail, "NfcPlugin", "stopHandover", []);
    },

    erase: function (win, fail) {
        cordova.exec(win, fail, "NfcPlugin", "eraseTag", [[]]);
    },

    enabled: function (win, fail) {
        cordova.exec(win, fail, "NfcPlugin", "enabled", [[]]);
    },

    removeTagDiscoveredListener: function (callback, win, fail) {
        document.removeEventListener("tag", callback, false);
        cordova.exec(win, fail, "NfcPlugin", "removeTag", []);
    },

    removeMimeTypeListener: function(mimeType, callback, win, fail) {
        document.removeEventListener("ndef-mime", callback, false);
        cordova.exec(win, fail, "NfcPlugin", "removeMimeType", [mimeType]);
    },

    removeNdefListener: function (callback, win, fail) {
        document.removeEventListener("ndef", callback, false);
        cordova.exec(win, fail, "NfcPlugin", "removeNdef", []);
    },

    showSettings: function (win, fail) {
        cordova.exec(win, fail, "NfcPlugin", "showSettings", []);
    },

    // 仅 iOS - 使用 NFCNDEFReaderSession 扫描 NFC NDEF 标签
    scanNdef: function (options) {
        return new Promise(function(resolve, reject) {
            cordova.exec(resolve, reject, "NfcPlugin", "scanNdef", [options]);
        });
    },

    // 仅 iOS - 使用 NFCTagReaderSession 扫描 NFC 标签
    scanTag: function (options) {
        return new Promise(function(resolve, reject) {
            cordova.exec(resolve, reject, "NfcPlugin", "scanTag", [options]);
        });
    },
    
    // 仅 iOS - 取消 NFC 扫描会话
    cancelScan: function () {
        return new Promise(function(resolve, reject) {
            cordova.exec(resolve, reject, "NfcPlugin", "cancelScan", []);
        });
    },

    // 仅 iOS - 已弃用，请使用 scanNdef 或 scanTag
    beginSession: function (win, fail) {
        // cordova.exec(win, fail, "NfcPlugin", "beginSession", []);
        cordova.exec(win, fail, "NfcPlugin", "beginSession", []);
    },

    // 仅 iOS - 已弃用，请使用 cancelScan
    invalidateSession: function (win, fail) {
        cordova.exec(win, fail, "NfcPlugin", "invalidateSession", []);
    },

    // 连接以开始数据收发
    connect: function(tech, timeout) {
        return new Promise(function(resolve, reject) {
            cordova.exec(resolve, reject, 'NfcPlugin', 'connect', [tech, timeout]);
        });
    },

    // 关闭数据收发连接
    close: function() {
        return new Promise(function(resolve, reject) {
            cordova.exec(resolve, reject, 'NfcPlugin', 'close', []);
        });
    },

    // data - 用于数据收发的 ArrayBuffer 或十六进制字符串
    // 收发结果将以 ArrayBuffer 形式在 promise 的成功回调中返回
    transceive: function(data) {
        return new Promise(function(resolve, reject) {

            var buffer;
            if (typeof data === 'string') {
                buffer = util.hexStringToArrayBuffer(data);
            } else if (data instanceof ArrayBuffer) {
                buffer = data;
            } else if (data instanceof Uint8Array) {
                buffer = data.buffer;
            } else {
                reject("期望传入 ArrayBuffer 或 String 类型");
            }

            cordova.exec(resolve, reject, 'NfcPlugin', 'transceive', [buffer]);
        });
    },

    // Android NfcAdapter.enableReaderMode 标志位
    FLAG_READER_NFC_A: 0x1,
    FLAG_READER_NFC_B: 0x2,
    FLAG_READER_NFC_F: 0x4,
    FLAG_READER_NFC_V: 0x8,
    FLAG_READER_NFC_BARCODE: 0x10,
    FLAG_READER_SKIP_NDEF_CHECK: 0x80,
    FLAG_READER_NO_PLATFORM_SOUNDS: 0x100,
    
    // Android NfcAdapter.enableReaderMode
    readerMode: function(flags, readCallback, errorCallback) {
        cordova.exec(readCallback, errorCallback, 'NfcPlugin', 'readerMode', [flags]);
    },

    disableReaderMode: function(successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, 'NfcPlugin', 'disableReaderMode', []);
    }

};

var util = {
    // i 必须 <= 256
    toHex: function (i) {
        var hex;

        if (i < 0) {
            i += 256;
        }

        hex = i.toString(16);

        // 补零
        if (hex.length === 1) {
            hex = "0" + hex;
        }

        return hex;
    },

    toPrintable: function(i) {

        if (i >= 0x20 & i <= 0x7F) {
            return String.fromCharCode(i);
        } else {
            return '.';
        }
    },

    bytesToString: function(bytes) {
        // 参考 http://ciaranj.blogspot.fr/2007/11/utf8-characters-encoding-in-javascript.html

        var result = "";
        var i, c, c1, c2, c3;
        i = c = c1 = c2 = c3 = 0;

        // 执行字节序检查。
        if( bytes.length >= 3 ) {
            if( (bytes[0] & 0xef) == 0xef && (bytes[1] & 0xbb) == 0xbb && (bytes[2] & 0xbf) == 0xbf ) {
                // 数据流开头有 BOM，跳过
                i = 3;
            }
        }

        while ( i < bytes.length ) {
            c = bytes[i] & 0xff;

            if ( c < 128 ) {

                result += String.fromCharCode(c);
                i++;

            } else if ( (c > 191) && (c < 224) ) {

                if ( i + 1 >= bytes.length ) {
                    throw "意外的编码错误，UTF-8 流被截断或格式不正确";
                }
                c2 = bytes[i + 1] & 0xff;
                result += String.fromCharCode( ((c & 31) << 6) | (c2 & 63) );
                i += 2;

            } else {

                if ( i + 2 >= bytes.length  || i + 1 >= bytes.length ) {
                    throw "意外的编码错误，UTF-8 流被截断或格式不正确";
                }
                c2 = bytes[i + 1] & 0xff;
                c3 = bytes[i + 2] & 0xff;
                result += String.fromCharCode( ((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63) );
                i += 3;

            }
        }
        return result;
    },

    stringToBytes: function(string) {
        // 参考 http://ciaranj.blogspot.fr/2007/11/utf8-characters-encoding-in-javascript.html

        var bytes = [];

        for (var n = 0; n < string.length; n++) {

            var c = string.charCodeAt(n);

            if (c < 128) {

                bytes[bytes.length]= c;

            } else if((c > 127) && (c < 2048)) {

                bytes[bytes.length] = (c >> 6) | 192;
                bytes[bytes.length] = (c & 63) | 128;

            } else {

                bytes[bytes.length] = (c >> 12) | 224;
                bytes[bytes.length] = ((c >> 6) & 63) | 128;
                bytes[bytes.length] = (c & 63) | 128;

            }

        }

        return bytes;
    },

    bytesToHexString: function (bytes) {
        var dec, hexstring, bytesAsHexString = "";
        for (var i = 0; i < bytes.length; i++) {
            if (bytes[i] >= 0) {
                dec = bytes[i];
            } else {
                dec = 256 + bytes[i];
            }
            hexstring = dec.toString(16);
            // 补零
            if (hexstring.length === 1) {
                hexstring = "0" + hexstring;
            }
            bytesAsHexString += hexstring;
        }
        return bytesAsHexString;
    },

    // 如果将 record.type 改为 String 类型，则可以移除此函数
    /**
     * 如果记录的 TNF 和 type 与提供的 TNF 和 type 匹配，则返回 true。
     *
     * @record NDEF 记录
     * @tnf 3 位 TNF（类型名称格式）- 使用 TNF_* 常量之一
     * @type 字节数组或字符串
     */
    isType: function(record, tnf, type) {
        if (record.tnf === tnf) { // TNF 是 3 位的
            var recordType;
            if (typeof(type) === 'string') {
                recordType = type;
            } else {
                recordType = nfc.bytesToString(type);
            }
            return (nfc.bytesToString(record.type) === recordType);
        }
        return false;
    },

    /**
     * 将 ArrayBuffer 转换为十六进制字符串
     *
     * @param {ArrayBuffer} buffer
     * @returns {string} - 字节的十六进制表示，例如 000407AF
     */
    arrayBufferToHexString: function(buffer) {
        function toHexString(byte) {
            return ('0' + (byte & 0xFF).toString(16)).slice(-2);
        }
        var typedArray = new Uint8Array(buffer);
        var array = Array.from(typedArray);  // 需要转换为普通数组，这样 map 的结果才不是类型化数组
        var parts = array.map(function(i) { return toHexString(i) });

        return parts.join('');
    },

    /**
     * 将十六进制字符串转换为 ArrayBuffer。
     *
     * @param {string} hexString - 字节的十六进制表示
     * @return {ArrayBuffer} - 包含字节数据的 ArrayBuffer。
     */
    hexStringToArrayBuffer: function(hexString) {

        // 移除所有分隔符 - 空格、短横线或冒号
        hexString = hexString.replace(/[\s-:]/g, '');

        // 移除开头的 0x
        hexString = hexString.replace(/^0x/, '');

        // 确保字符数为偶数
        if (hexString.length % 2 != 0) {
            console.log('警告：十六进制字符串的字符数应为偶数');
        }

        // 检查是否存在非十六进制字符
        var bad = hexString.match(/[G-Z\s]/i);
        if (bad) {
            console.log('警告：发现非十六进制字符', bad);
        }

        // 将字符串拆分为八位字节对
        var pairs = hexString.match(/[\dA-F]{2}/gi);

        // 将八位字节转换为整数
        var ints = pairs.map(function(s) { return parseInt(s, 16) });

        var array = new Uint8Array(ints);
        return array.buffer;
    }

};

// 这是 ndef-js 中的一个模块
var textHelper = {

    decodePayload: function (data) {

        var languageCodeLength = (data[0] & 0x3F), // 低 6 位
            languageCode = data.slice(1, 1 + languageCodeLength),
            utf16 = (data[0] & 0x80) !== 0; // 假设为 UTF-16BE

        // TODO 未来需要处理 UTF 编码
        if (utf16) {
            console.log('警告：utf-16 数据可能无法正确处理，语言代码：', languageCode);
        }
        // 当浏览器支持足够时使用 TextDecoder
        // new TextDecoder('utf-8').decode(data.slice(languageCodeLength + 1));
        // new TextDecoder('utf-16').decode(data.slice(languageCodeLength + 1));

        return util.bytesToString(data.slice(languageCodeLength + 1));
    },

    // 编码文本载荷
    // @returns 字节数组
    encodePayload: function(text, lang, encoding) {

        // ISO/IANA 语言代码，但我们不做强制校验
        if (!lang) { lang = 'en'; }

        var encoded = util.stringToBytes(lang + text);
        encoded.unshift(lang.length);

        return encoded;
    }

};

// 这是 ndef-js 中的一个模块
var uriHelper = {
    // URI 标识符代码，来自 URI 记录类型定义 NFCForum-TS-RTD_URI_1.0 2006-07-24
    // 数组索引对应规范中的代码
    protocols: [ "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:" ],

    // 解码 URI 载荷字节
    // @returns 字符串
    decodePayload: function (data) {
        var prefix = uriHelper.protocols[data[0]];
        if (!prefix) { // 36 到 255 应为 ""
            prefix = "";
        }
        return prefix + util.bytesToString(data.slice(1));
    },

    // 使用标准前缀缩短 URI
    // @returns 字节数组
    encodePayload: function (uri) {

        var prefix,
            protocolCode,
            encoded;

        // 逐一检查每个协议，直到找到匹配项
        // "urn:" 是一个例外，需要继续检查
        // 使用 slice 跳过空字符串 ""
        uriHelper.protocols.slice(1).forEach(function(protocol) {
            if ((!prefix || prefix === "urn:") && uri.indexOf(protocol) === 0) {
                prefix = protocol;
            }
        });

        if (!prefix) {
            prefix = "";
        }

        encoded = util.stringToBytes(uri.slice(prefix.length));
        protocolCode = uriHelper.protocols.indexOf(prefix);
        // 在前面加上协议代码
        encoded.unshift(protocolCode);

        return encoded;
    }
};

// 添加此函数是因为 WP8 必须调用命名函数，iOS 也会使用它
// TODO 考虑将 NFC 事件从 JS 事件切换为使用 PG 回调
function fireNfcTagEvent(eventType, tagAsJson) {
    setTimeout(function () {
        var e = document.createEvent('Events');
        e.initEvent(eventType, true, false);
        e.tag = JSON.parse(tagAsJson);
        console.log(e.tag);
        document.dispatchEvent(e);
    }, 10);
}

// textHelper 和 uriHelper 未被导出，添加一个属性
ndef.uriHelper = uriHelper;
ndef.textHelper = textHelper;

// 创建别名
nfc.bytesToString = util.bytesToString;
nfc.stringToBytes = util.stringToBytes;
nfc.bytesToHexString = util.bytesToHexString;

// 为 plugman js-module 支持临时设置一些全局变量
// 最终应替换为通过模块引用的方式
window.nfc = nfc;
window.ndef = ndef;
window.util = util;
window.fireNfcTagEvent = fireNfcTagEvent;

// 此通道接收来自原生代码的 nfcEvent 数据
// 并触发 JavaScript 事件。
require('cordova/channel').onCordovaReady.subscribe(function() {
  require('cordova/exec')(success, null, 'NfcPlugin', 'channel', []);
  function success(message) {
    if (!message.type) { 
        console.log(message);
    } else {
        console.log("收到 NFC 数据，正在触发 '" + message.type + "' 事件");
        var e = document.createEvent('Events');
        e.initEvent(message.type);
        e.tag = message.tag;
        document.dispatchEvent(e);
    }
  }
});
