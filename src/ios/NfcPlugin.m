#import "NfcPlugin.h"

@interface NfcPlugin() {
    NSString* sessionCallbackId;
    NSString* channelCallbackId;
    id<NFCNDEFTag> connectedTag API_AVAILABLE(ios(13.0));
    NFCNDEFStatus connectedTagStatus API_AVAILABLE(ios(13.0));
}
@property (nonatomic, assign) BOOL writeMode;
@property (nonatomic, assign) BOOL shouldUseTagReaderSession;
@property (nonatomic, assign) BOOL sendCallbackOnSessionStart;
@property (nonatomic, assign) BOOL returnTagInCallback;
@property (nonatomic, assign) BOOL returnTagInEvent;
@property (nonatomic, assign) BOOL keepSessionOpen;
@property (strong, nonatomic) NFCReaderSession *nfcSession API_AVAILABLE(ios(11.0));
@property (strong, nonatomic) NFCNDEFMessage *messageToWrite API_AVAILABLE(ios(11.0));
@end

@implementation NfcPlugin

- (void)pluginInitialize {

    NSLog(@"PhoneGap NFC - Cordova 插件");
    NSLog(@"(c) 2017-2020 Don Coleman");

    [super pluginInitialize];
    
    if (@available(iOS 11, *)) {
        if (![NFCNDEFReaderSession readingAvailable]) {
            NSLog(@"NFC 功能不可用");
        }
    } else {
        NSLog(@"iOS 11 之前的系统不支持 NFC");
    }
}

#pragma mark - Cordova 插件方法

- (void)channel:(CDVInvokedUrlCommand *)command {
    // channel 用于将 NFC 标签数据发送到 Web 视图
    channelCallbackId = [command.callbackId copy];
}

- (void)beginSession:(CDVInvokedUrlCommand*)command {
    NSLog(@"beginSession");
    NSLog(@"警告：beginSession 已废弃，请使用 scanNdef 或 scanTag。");

    self.shouldUseTagReaderSession = NO;
    self.sendCallbackOnSessionStart = YES;  // 不确定之前为什么这么做
    self.returnTagInCallback = NO;
    self.returnTagInEvent = YES;
    self.keepSessionOpen = NO;

    [self startScanSession:command];
}

- (void)scanNdef:(CDVInvokedUrlCommand*)command {
    NSLog(@"scanNdef");

    self.shouldUseTagReaderSession = NO;
    self.sendCallbackOnSessionStart = NO;
    self.returnTagInCallback = YES;
    self.returnTagInEvent = NO;

    NSArray<NSDictionary *> *options = [command argumentAtIndex:0];
    self.keepSessionOpen = [options valueForKey:@"keepSessionOpen"];

    [self startScanSession:command];
}

- (void)scanTag:(CDVInvokedUrlCommand*)command {
    NSLog(@"scanTag");

    self.shouldUseTagReaderSession = YES;
    self.sendCallbackOnSessionStart = NO;
    self.returnTagInCallback = YES;
    self.returnTagInEvent = NO;

    NSArray<NSDictionary *> *options = [command argumentAtIndex:0];
    self.keepSessionOpen = [options valueForKey:@"keepSessionOpen"];

    [self startScanSession:command];
}

- (void)writeTag:(CDVInvokedUrlCommand*)command API_AVAILABLE(ios(13.0)){
    NSLog(@"writeTag");

    self.writeMode = YES;
    self.shouldUseTagReaderSession = NO;
    BOOL reusingSession = NO;

    NSArray<NSDictionary *> *ndefData = [command argumentAtIndex:0];

    // 创建 NDEF 消息
    NSMutableArray<NFCNDEFPayload*> *payloads = [NSMutableArray new];

    @try {
        for (id recordData in ndefData) {
            NSNumber *tnfNumber = [recordData objectForKey:@"tnf"];
            NFCTypeNameFormat tnf = (uint8_t)[tnfNumber intValue];
            NSData *type = [self uint8ArrayToNSData:[recordData objectForKey:@"type"]];
            NSData *identifier = [self uint8ArrayToNSData:[recordData objectForKey:@"identifiers"]];
            NSData *payload  = [self uint8ArrayToNSData:[recordData objectForKey:@"payload"]];
            NFCNDEFPayload *record = [[NFCNDEFPayload alloc] initWithFormat:tnf type:type identifier:identifier payload:payload];
            [payloads addObject:record];
        }
        NSLog(@"%@", payloads);
        NFCNDEFMessage *message = [[NFCNDEFMessage alloc] initWithNDEFRecords:payloads];
        self.messageToWrite = message;
    } @catch(NSException *e) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"无效的 NDEF 消息"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    if (self.nfcSession && self.nfcSession.isReady) {       // 复用现有会话
        reusingSession = YES;
    } else {                                                // 创建新会话
        if (self.shouldUseTagReaderSession) {
            NSLog(@"使用 NFCTagReaderSession");

            self.nfcSession = [[NFCTagReaderSession new]
                       initWithPollingOption:(NFCPollingISO14443 | NFCPollingISO15693)
                       delegate:self queue:dispatch_get_main_queue()];

        } else {
            NSLog(@"使用 NFCTagReaderSession");
            self.nfcSession = [[NFCNDEFReaderSession new]initWithDelegate:self queue:nil invalidateAfterFirstRead:FALSE];
        }
    }

    self.nfcSession.alertMessage = @"请靠近可写入的 NFC 标签以更新数据。";
    sessionCallbackId = [command.callbackId copy];

    if (reusingSession) {                   // 复用读取会话进行写入
        self.keepSessionOpen = NO;          // 写入完成后关闭会话
        [self writeNDEFTag:self.nfcSession status:connectedTagStatus tag:connectedTag];
    } else {
        [self.nfcSession beginSession];
    }
}

- (void)cancelScan:(CDVInvokedUrlCommand*)command API_AVAILABLE(ios(11.0)){
    NSLog(@"cancelScan");
    if (self.nfcSession) {
        [self.nfcSession invalidateSession];
    }
    connectedTag = NULL;
    connectedTagStatus = NFCNDEFStatusNotSupported;
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)invalidateSession:(CDVInvokedUrlCommand*)command {
    NSLog(@"invalidateSession");
    NSLog(@"警告：invalidateSession 已废弃，请使用 cancelScan。");

    if (_nfcSession) {
        [_nfcSession invalidateSession];
    }
    // 始终返回 OK。也可以从 NFCNDEFReaderSessionDelegate 发送状态
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

// 这里不做任何操作，事件监听器在 JavaScript 中注册
- (void)registerNdef:(CDVInvokedUrlCommand *)command {
    NSLog(@"registerNdef");
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

// 这里不做任何操作，事件监听器在 JavaScript 中移除
- (void)removeNdef:(CDVInvokedUrlCommand *)command {
    NSLog(@"removeNdef");
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)enabled:(CDVInvokedUrlCommand *)command {
    NSLog(@"enabled");
    CDVPluginResult *pluginResult;
    if (@available(iOS 11.0, *)) {
        if ([NFCNDEFReaderSession readingAvailable]) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"NO_NFC"];
        }
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"NO_NFC"];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark - NFCNDEFReaderSessionDelegate 委托方法

// iOS 11 和 12
- (void) readerSession:(NFCNDEFReaderSession *)session didDetectNDEFs:(NSArray<NFCNDEFMessage *> *)messages API_AVAILABLE(ios(11.0)) {
    NSLog(@"NFCNDEFReaderSession didDetectNDEFs");

    session.alertMessage = @"标签读取成功。";
    for (NFCNDEFMessage *message in messages) {
        [self fireNdefEvent: message];
    }
}

// iOS 13
- (void) readerSession:(NFCNDEFReaderSession *)session didDetectTags:(NSArray<__kindof id<NFCNDEFTag>> *)tags API_AVAILABLE(ios(13.0)) {

    if (tags.count > 1) {
        session.alertMessage = @"检测到多个标签，请移除所有标签后重试。";
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 500 * NSEC_PER_MSEC), dispatch_get_main_queue(), ^{
            NSLog(@"重新开始轮询");
            [session restartPolling];
        });
        return;
    }

    id<NFCNDEFTag> tag = [tags firstObject];

    [session connectToTag:tag completionHandler:^(NSError * _Nullable error) {
        if (error) {
            NSLog(@"%@", error);
            [self closeSession:session withError:@"连接标签时出错。"];
            return;
        }

        [self processNDEFTag:session tag:tag];
    }];

}

- (void) readerSession:(NFCNDEFReaderSession *)session didInvalidateWithError:(NSError *)error API_AVAILABLE(ios(11.0)) {
    NSLog(@"readerSession 已结束");
    if (error.code == NFCReaderSessionInvalidationErrorFirstNDEFTagRead) { // 不是错误
        NSLog(@"NDEF 标签读取成功，会话已结束");
        return;
    } else {
        [self sendError:error.localizedDescription];
    }
}

- (void) readerSessionDidBecomeActive:(nonnull NFCReaderSession *)session API_AVAILABLE(ios(11.0)) {
    NSLog(@"readerSessionDidBecomeActive");
    [self sessionDidBecomeActive:session];
}

#pragma mark - NFCTagReaderSessionDelegate 委托方法

- (void)tagReaderSessionDidBecomeActive:(NFCTagReaderSession *)session API_AVAILABLE(ios(13.0)) {
    NSLog(@"tagReaderSessionDidBecomeActive");
    [self sessionDidBecomeActive:session];
}

- (void)tagReaderSession:(NFCTagReaderSession *)session didDetectTags:(NSArray<__kindof id<NFCTag>> *)tags API_AVAILABLE(ios(13.0)) {
    NSLog(@"tagReaderSession didDetectTags");

    if (tags.count > 1) {
        session.alertMessage = @"检测到多个标签，请移除所有标签后重试。";
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 500 * NSEC_PER_MSEC), dispatch_get_main_queue(), ^{
            NSLog(@"重新开始轮询");
            [session restartPolling];
        });
        return;
    }

    id<NFCTag> tag = [tags firstObject];
    NSMutableDictionary *tagMetaData = [self getTagInfo:tag];
    id<NFCNDEFTag> ndefTag = (id<NFCNDEFTag>)tag;

    [session connectToTag:tag completionHandler:^(NSError * _Nullable error) {
        if (error) {
            NSLog(@"%@", error);
            [self closeSession:session withError:@"连接标签时出错。"];
            return;
        }

        [self processNDEFTag:session tag:ndefTag metaData:tagMetaData];
    }];
}

- (void)tagReaderSession:(NFCTagReaderSession *)session didInvalidateWithError:(NSError *)error API_AVAILABLE(ios(13.0)) {
    NSLog(@"tagReaderSession 已结束");
    [self sendError:error.localizedDescription];
}

#pragma mark - 通用 NDEF 处理

// 处理 scanNdef、scanTag 和 beginSession
- (void)startScanSession:(CDVInvokedUrlCommand*)command {

    self.writeMode = NO;

    NSLog(@"shouldUseTagReaderSession %d", self.shouldUseTagReaderSession);
    NSLog(@"callbackOnSessionStart %d", self.sendCallbackOnSessionStart);
    NSLog(@"returnTagInCallback %d", self.returnTagInCallback);
    NSLog(@"returnTagInEvent %d", self.returnTagInEvent);

    if (@available(iOS 13.0, *)) {

        if (self.shouldUseTagReaderSession) {
            NSLog(@"使用 NFCTagReaderSession");
            self.nfcSession = [[NFCTagReaderSession new]
                           initWithPollingOption:(NFCPollingISO14443 | NFCPollingISO15693)
                           delegate:self queue:dispatch_get_main_queue()];
        } else {
            NSLog(@"使用 NFCNDEFReaderSession");
            self.nfcSession = [[NFCNDEFReaderSession new]initWithDelegate:self queue:nil invalidateAfterFirstRead:TRUE];
        }
        sessionCallbackId = [command.callbackId copy];
        self.nfcSession.alertMessage = @"请靠近 NFC 标签进行扫描。";
        [self.nfcSession beginSession];

    } else if (@available(iOS 11.0, *)) {
        NSLog(@"iOS 版本低于 13，使用 NFCNDEFReaderSession");
        self.nfcSession = [[NFCNDEFReaderSession new]initWithDelegate:self queue:nil invalidateAfterFirstRead:TRUE];
        sessionCallbackId = [command.callbackId copy];
        self.nfcSession.alertMessage = @"请靠近 NFC 标签进行扫描。";
        [self.nfcSession beginSession];
    } else {
        NSLog(@"iOS 版本低于 11，不支持 NFC");
        CDVPluginResult *pluginResult;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"NFC 需要 iOS 11 或更高版本"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }

}

- (void)processNDEFTag: (NFCReaderSession *)session tag:(__kindof id<NFCNDEFTag>)tag API_AVAILABLE(ios(13.0)) {
    [self processNDEFTag:session tag:tag metaData:[NSMutableDictionary new]];
}

- (void)processNDEFTag: (NFCReaderSession *)session tag:(__kindof id<NFCNDEFTag>)tag metaData: (NSMutableDictionary * _Nonnull)metaData API_AVAILABLE(ios(13.0)) {

    [tag queryNDEFStatusWithCompletionHandler:^(NFCNDEFStatus status, NSUInteger capacity, NSError * _Nullable error) {
        if (error) {
            NSLog(@"%@", error);
            [self closeSession:session withError:@"获取标签状态时出错。"];
            return;
        }

        if (self.writeMode) {
            [self writeNDEFTag:session status:status tag:tag];
        } else {
            // 保存标签和状态，以便写入时复用
            if (self.keepSessionOpen) {
                self->connectedTagStatus = status;
                self->connectedTag = tag;
            }
            [self readNDEFTag:session status:status tag:tag metaData:metaData];
        }

    }];
}

- (void)readNDEFTag:(NFCReaderSession * _Nonnull)session status:(NFCNDEFStatus)status tag:(id<NFCNDEFTag>)tag metaData:(NSMutableDictionary * _Nonnull)metaData  API_AVAILABLE(ios(13.0)){

    if (status == NFCNDEFStatusNotSupported) {
        NSLog(@"标签不支持 NDEF");
        [self fireTagEvent:metaData];
        [self closeSession:session];
        return;
    }

    if (status == NFCNDEFStatusReadOnly) {
        metaData[@"isWritable"] = @FALSE;
    } else if (status == NFCNDEFStatusReadWrite) {
        metaData[@"isWritable"] = @TRUE;
    }

    [tag readNDEFWithCompletionHandler:^(NFCNDEFMessage * _Nullable message, NSError * _Nullable error) {

        // 错误码 403 "NDEF 标签不包含任何 NDEF 消息" 对本插件来说不是错误
        if (error && error.code != 403) {
            NSLog(@"%@", error);
            [self closeSession:session withError:@"读取失败。"];
            return;
        } else {
            NSLog(@"%@", message);
            session.alertMessage = @"标签读取成功。";
            [self fireNdefEvent:message metaData:metaData];
            [self closeSession:session];
        }

    }];

}

- (void)writeNDEFTag:(NFCReaderSession * _Nonnull)session status:(NFCNDEFStatus)status tag:(id<NFCNDEFTag>)tag  API_AVAILABLE(ios(13.0)){
    switch (status) {
        case NFCNDEFStatusNotSupported:
            [self closeSession:session withError:@"标签不符合 NDEF 规范。"];  // 备选消息 "标签不支持 NDEF。"
            break;
        case NFCNDEFStatusReadOnly:
            [self closeSession:session withError:@"标签为只读。"];
            break;
        case NFCNDEFStatusReadWrite: {

            [tag writeNDEF: self.messageToWrite completionHandler:^(NSError * _Nullable error) {
                if (error) {
                    NSLog(@"%@", error);
                    [self closeSession:session withError:@"写入失败。"];
                } else {
                    session.alertMessage = @"数据已写入 NFC 标签。";
                    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:self->sessionCallbackId];
                    [self closeSession:session];
                }
            }];
            break;

        }
        default:
            [self closeSession:session withError:@"未知的 NDEF 标签状态。"];
    }
}

#pragma mark - 标签读取器辅助函数

// 获取标签元数据 - 类型和 uid
- (NSMutableDictionary *) getTagInfo:(id<NFCTag>)tag API_AVAILABLE(ios(13.0)) {

    NSMutableDictionary *tagInfo = [NSMutableDictionary new];

    NSData *uid;
    NSString *type;

    switch (tag.type) {
        case NFCTagTypeFeliCa:
            type = @"NFCTagTypeFeliCa";
            uid = nil;
            break;
        case NFCTagTypeMiFare:
            type = @"NFCTagTypeMiFare";
            uid = [[tag asNFCMiFareTag] identifier];
            break;
        case NFCTagTypeISO15693:
            type = @"NFCTagTypeISO15693";
            uid = [[tag asNFCISO15693Tag] identifier];
            break;
        case NFCTagTypeISO7816Compatible:
            type = @"NFCTagTypeISO7816Compatible";
            uid = [[tag asNFCISO7816Tag] identifier];
            break;
        default:
            type = @"Unknown";
            uid = nil;
            break;
    }

    NSLog(@"getTagInfo: %@ uid 为 %@", type, uid);

    [tagInfo setValue:type forKey:@"type"];
    if (uid) {
        [tagInfo setValue:uid forKey:@"id"];
    }
    return tagInfo;
}

#pragma mark - 内部实现

- (void) sendError:(NSString *)message {
    // 仅当回调 id 存在时才发送错误
    if (sessionCallbackId) {
        NSLog(@"sendError: %@", message);
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:message];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:sessionCallbackId];
    }
}

- (void) sessionDidBecomeActive:(NFCReaderSession *) session  API_AVAILABLE(ios(11.0)){
    if (sessionCallbackId && self.sendCallbackOnSessionStart) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [pluginResult setKeepCallback:@YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:sessionCallbackId];
    }
}

- (void) closeSession:(NFCReaderSession *) session  API_AVAILABLE(ios(11.0)){

    // 这是一个 hack，保持读取会话打开以便后续写入
    if (self.keepSessionOpen) {
        return;
    }

    // 清除回调，避免 Cordova 收到 "用户终止会话" 的消息
    sessionCallbackId = NULL;
    connectedTag = NULL;
    connectedTagStatus = NFCNDEFStatusNotSupported;
    [session invalidateSession];
}

- (void) closeSession:(NFCReaderSession *) session withError:(NSString *) errorMessage  API_AVAILABLE(ios(11.0)){
    [self sendError:errorMessage];

    // 清除回调，避免 Cordova 收到 "用户终止会话" 的消息
    sessionCallbackId = NULL;
    connectedTag = NULL;
    connectedTagStatus = NFCNDEFStatusNotSupported;

    if (@available(iOS 13.0, *)) {
        [session invalidateSessionWithErrorMessage:errorMessage];
    } else {
        [session invalidateSession];
    }
}

-(void) fireTagEvent:(NSDictionary *)metaData API_AVAILABLE(ios(11.0)) {
    // 数据来自标签，但在 JavaScript 中仍以 NDEF 事件形式呈现
    [self fireNdefEvent:nil metaData:metaData];
}

-(void) fireNdefEvent:(NFCNDEFMessage *) ndefMessage API_AVAILABLE(ios(11.0)) {
    [self fireNdefEvent:ndefMessage metaData:nil];
}

// TODO 重命名此方法，因为我们使用 channel 或 callback 而不是触发事件
-(void) fireNdefEvent:(NFCNDEFMessage *) ndefMessage metaData:(NSDictionary *)metaData API_AVAILABLE(ios(11.0)) {
    NSLog(@"fireNdefEvent");

    NSMutableDictionary *nfcEvent = [NSMutableDictionary new];
    nfcEvent[@"type"] = @"ndef";
    nfcEvent[@"tag"] = [self buildTagDictionary:ndefMessage metaData:metaData];

    if (sessionCallbackId && self.returnTagInCallback) {
        NSLog(@"通过 sessionCallbackId 发送 NFC 数据");
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:nfcEvent[@"tag"]];
//        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:sessionCallbackId];
        sessionCallbackId = NULL;
    }

    if (channelCallbackId && self.returnTagInEvent) {
        NSLog(@"通过 channelCallbackId 发送 NFC 数据以触发 NDEF 事件");

        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:nfcEvent];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:channelCallbackId];
    }
}

// 表示 NFC 标签的 NSDictionary
// NSData 字段被转换为 uint8_t 数组
-(NSDictionary *) buildTagDictionary:(NFCNDEFMessage *) ndefMessage metaData: (NSDictionary *)metaData API_AVAILABLE(ios(11.0)) {

    NSMutableDictionary *dictionary = [NSMutableDictionary new];

    // 从标签元数据开始
    if (metaData) {
        [dictionary setDictionary:metaData];
    }

    // 将 uid 从 NSData 转换为 uint8_t 数组
    NSData *uid = [dictionary objectForKey:@"id"];
    if (uid) {
        dictionary[@"id"] = [self uint8ArrayFromNSData: uid];
    }

    if (ndefMessage) {
        NSMutableArray *array = [NSMutableArray new];
        for (NFCNDEFPayload *record in ndefMessage.records){
            NSDictionary* recordDictionary = [self ndefRecordToNSDictionary:record];
            [array addObject:recordDictionary];
        }
        [dictionary setObject:array forKey:@"ndefMessage"];
    }

    return [dictionary copy];
}

-(NSDictionary *) ndefRecordToNSDictionary:(NFCNDEFPayload *) ndefRecord API_AVAILABLE(ios(11.0)) {
    NSMutableDictionary *dict = [NSMutableDictionary new];
    dict[@"tnf"] = [NSNumber numberWithInt:(int)ndefRecord.typeNameFormat];
    dict[@"type"] = [self uint8ArrayFromNSData: ndefRecord.type];
    dict[@"id"] = [self uint8ArrayFromNSData: ndefRecord.identifier];
    dict[@"payload"] = [self uint8ArrayFromNSData: ndefRecord.payload];
    NSDictionary *copy = [dict copy];
    return copy;
}

- (NSArray *) uint8ArrayFromNSData:(NSData *) data {
    const void *bytes = [data bytes];
    NSMutableArray *array = [NSMutableArray array];
    for (NSUInteger i = 0; i < [data length]; i += sizeof(uint8_t)) {
        uint8_t elem = OSReadLittleInt(bytes, i);
        [array addObject:[NSNumber numberWithInt:elem]];
    }
    return array;
}

- (NSData *) uint8ArrayToNSData:(NSArray *) array {
    // NSLog(@"nsDataFromUint8Array 输入 %@", array);

    NSMutableData *data = [[NSMutableData alloc] initWithCapacity: [array count]];
    for (NSNumber *number in array) {
        uint8_t b = (uint8_t)[number unsignedIntValue];
        // NSLog(@"> %hhu", b);
        [data appendBytes:&b length:1];
    }
    return data;
}

- (NSString*) dictionaryAsJSONString:(NSDictionary *)dict {
    NSError *error;
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:dict options:0 error:&error];
    NSString *jsonString;
    if (! jsonData) {
        jsonString = [NSString stringWithFormat:@"为 NDEF 消息创建 JSON 时出错：%@", error];
        NSLog(@"%@", jsonString);
    } else {
        jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    }
    return jsonString;
}

@end
