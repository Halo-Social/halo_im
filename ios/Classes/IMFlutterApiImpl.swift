//
//  IMFlutterApiImplementation.swift
//  halo_im
//
//  Created by Dale on 2024/9/11.
//

import Flutter
import UIKit
import XMTP
import LibXMTP

class IMFlutterApiImplementation {
    let messageApi: IMFlutterApi
    init(binaryMessenger: FlutterBinaryMessenger) {
        self.messageApi = IMFlutterApi(binaryMessenger: binaryMessenger)
    }
    
    func flutterTest(str: String) {
        DispatchQueue.main.async {
            self.messageApi.flutterApiTest(test: str) { _ in }
        }
    }
    
    func onSignMessage(requestId requestIdArg: String, message messageArg: String) {
        DispatchQueue.main.async {
            self.messageApi.onSignMessage(requestId: requestIdArg, message: messageArg) { _ in}
        }
    }
    
    func onSubscribeToConversations(inboxIdArg: String, mArg: IMConversation) {
        DispatchQueue.main.async {
            self.messageApi.onSubscribeToConversations(inboxId: inboxIdArg, conversation: mArg) { _ in }
        }
    }
    
    func onSubscribeToMessages(inboxIdArg: String, mArg: IMDecryptedMessage) {
        DispatchQueue.main.async {
            self.messageApi.onSubscribeToMessages(inboxId: inboxIdArg, message: mArg) { _ in }
        }
    }
    
    func onSubscribeToAllMessages(inboxIdArg: String, mArg: IMDecryptedMessage) {
        DispatchQueue.main.async {
            self.messageApi.onSubscribeToAllMessages(inboxId: inboxIdArg, message: mArg) { _ in }
        }
    }
}
