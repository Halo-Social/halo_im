//
//  ConversationWrapper.swift
//
//  Created by Pat Nakajima on 4/21/23.
//

import Foundation
import XMTP

// Wrapper around XMTP.Conversation to allow passing these objects back into react native.
struct ConversationWrapper {
    static func encodeToObj(_ conversation: XMTP.Conversation, client: XMTP.Client) throws -> [String: Any] {
        var context = [:] as [String: Any]
        if case let .v2(cv2) = conversation {
            context = [
                "conversationID": cv2.context.conversationID,
                "metadata": cv2.context.metadata,
            ]
        }
        var consentProof: String? = nil
        if (conversation.consentProof != nil) {
            consentProof = try conversation.consentProof?.serializedData().base64EncodedString()
        }
        return [
            "clientAddress": client.address,
            "topic": conversation.topic,
            "createdAt": UInt64(conversation.createdAt.timeIntervalSince1970 * 1000),
            "context": context,
            "peerAddress": try conversation.peerAddress,
            "version": "DIRECT",
            "conversationID": conversation.conversationID ?? "",
            "keyMaterial": conversation.keyMaterial?.base64EncodedString() ?? "",
            "consentProof": consentProof ?? ""
        ]
    }
    
    static func encode(_ conversation: XMTP.Conversation, client: XMTP.Client) throws -> String {
        let obj = try encodeToObj(conversation, client: client)
        let data = try JSONSerialization.data(withJSONObject: obj)
        guard let result = String(data: data, encoding: .utf8) else {
            throw WrapperError.encodeError("could not encode conversation")
        }
        return result
    }
    
    static func encodeToClass(_ conversation: XMTP.Conversation, client: XMTP.Client) async throws -> IMConversation {
        var consentProof: String? = nil
        if (conversation.consentProof != nil) {
            consentProof = try conversation.consentProof?.serializedData().base64EncodedString()
        }
        
        var consentStateStr = ""
        
//        let consentState = try await conversation.consentState();
        
        var groupName = ""
        var groupId = ""
        var groupDescription = ""
        var groupIcon = ""
        var peerAddress = ""
        
        switch conversation {
        case .group(let group):
            groupName = try group.groupName()
            groupId = group.id
            groupDescription = try group.groupDescription()
            groupIcon = try group.groupImageUrlSquare()
            peerAddress = group.id
        default:
            groupName = ""
            groupId = ""
            groupDescription = ""
            groupIcon = ""
            peerAddress = try conversation.peerAddress
        }
        
        let conversation = IMConversation(
            clientAddress: client.address,
            topic: conversation.topic,
            peerAddress: peerAddress,
            version: conversation.versionString(),
            createdAt: Int64(conversation.createdAt.timeIntervalSince1970 * 1000),
            conversationID: conversation.conversationID ?? "",
            keyMaterial: conversation.keyMaterial?.base64EncodedString() ?? "",
            consentProof: consentProof ?? "",
            isAllowed: false,
            isDenied: false,
            groupName: groupName,
            groupDescription: groupDescription, 
            groupId: groupId,
            groupIcon: groupIcon
        );
        return conversation;
    }
}

extension Conversation {
    func versionString() -> String {
        switch self {
        case .v1:
            return "V1"
        case .v2:
            return "V2"
        case .group:
            return "GROUP"
        }
    }
    
}
