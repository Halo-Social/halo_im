//
//  GroupWrapper.swift
//  XMTPReactNative
//
//  Created by Naomi Plasterer on 2/9/24.
//

import Foundation
import XMTP

// Wrapper around XMTP.Group to allow passing these objects back into react native.
struct GroupWrapper {
//	static func encodeToObj(_ group: XMTP.Group, client: XMTP.Client) throws -> [String: Any] {
//		return [
//			"clientAddress": client.address,
//			"id": group.id,
//			"createdAt": UInt64(group.createdAt.timeIntervalSince1970 * 1000),
//			"members": try group.members.compactMap { member in return try MemberWrapper.encode(member) },
//			"version": "GROUP",
//			"topic": group.topic,
//			"creatorInboxId": try group.creatorInboxId(),
//			"isActive": try group.isActive(),
//			"addedByInboxId": try group.addedByInboxId(),
//			"name": try group.groupName(),
//			"imageUrlSquare": try group.groupImageUrlSquare(),
//			"description": try group.groupDescription()
//			// "pinnedFrameUrl": try group.groupPinnedFrameUrl()
//		]
//	}
//
//	static func encode(_ group: XMTP.Group, client: XMTP.Client) throws -> String {
//		let obj = try encodeToObj(group, client: client)
//		let data = try JSONSerialization.data(withJSONObject: obj)
//		guard let result = String(data: data, encoding: .utf8) else {
//			throw WrapperError.encodeError("could not encode group")
//		}
//		return result
//	}
    
    static func encodeToCloass(_ group: XMTP.Group, client: XMTP.Client) async throws -> ImGroup {
        let members = try await group.members.map { m -> ImMember in
            var permissionLevelStr = ""
            switch m.permissionLevel {
            case .Member:
                permissionLevelStr = "member"
            case .Admin:
                permissionLevelStr = "admin"
            case .SuperAdmin:
                permissionLevelStr = "super_admin"
            }
            let member = ImMember(inboxId: m.inboxId, addresses: m.addresses, permissionLevel: permissionLevelStr)
            return member
        }
        
        return ImGroup(
            clientAddress: client.address,
            id: group.id,
            createdAt: Int64(group.createdAt.timeIntervalSince1970 * 1000),
            version: "GROUP",
            topic: group.topic,
            creatorInboxId: try group.creatorInboxId(),
            isActive: try group.isActive(),
            addedByInboxId: try group.addedByInboxId(),
            name: try group.groupName(),
            imageUrlSquare: try group.groupImageUrlSquare(),
            description: try group.groupDescription(),
            members: members)
    }
}
