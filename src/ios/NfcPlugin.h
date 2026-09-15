#ifndef NfcPlugin_h
#define NfcPlugin_h

#import <Cordova/CDV.h>
#import <CoreNFC/CoreNFC.h>
#import <WebKit/WebKit.h>

@interface NfcPlugin : CDVPlugin <NFCNDEFReaderSessionDelegate, NFCTagReaderSessionDelegate> {
}

// iOS 特有 API

// 已废弃，请使用 scanNdef 或 scanTag
- (void)beginSession:(CDVInvokedUrlCommand *)command;
// 已废弃，请使用 stopScan
- (void)invalidateSession:(CDVInvokedUrlCommand *)command;

// iOS 13 新增
- (void)scanNdef:(CDVInvokedUrlCommand *)command;
- (void)scanTag:(CDVInvokedUrlCommand *)command;
- (void)cancelScan:(CDVInvokedUrlCommand *)command;

// 标准 PhoneGap NFC API
- (void)registerNdef:(CDVInvokedUrlCommand *)command;
- (void)removeNdef:(CDVInvokedUrlCommand *)command;
- (void)enabled:(CDVInvokedUrlCommand *)command;
- (void)writeTag:(CDVInvokedUrlCommand *)command;

// 内部实现
- (void)channel:(CDVInvokedUrlCommand *)command;

@end

#endif
