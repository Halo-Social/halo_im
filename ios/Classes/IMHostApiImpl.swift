//
//  IMHostApiImplementation.swift
//  halo_im
//
//  Created by Dale on 2024/9/5.
//

import Flutter
import UIKit
import XMTP
import LibXMTP

extension Conversation {
    static func cacheKeyForTopic(inboxId: String, topic: String) -> String {
        return "\(inboxId):\(topic)"
    }
    
    func cacheKey(_ inboxId: String) -> String {
        return Conversation.cacheKeyForTopic(inboxId: inboxId, topic: topic)
    }
}

extension XMTP.Group {
    static func cacheKeyForId(inboxId: String, id: String) -> String {
        return "\(inboxId):\(id)"
    }
    
    func cacheKey(_ inboxId: String) -> String {
        return XMTP.Group.cacheKeyForId(inboxId: inboxId, id: id)
    }
}

class IMHostApiImplementation : IMHostApi {
    
    let clientsManager = ClientsManager()
    let subscriptionsManager = IsolatedManager<Task<Void, Never>>()
    let conversationsManager = IsolatedManager<Conversation>()
    let groupsManager = IsolatedManager<XMTP.Group>()
    let messageApi: IMFlutterApiImplementation
    
    var signer: IMSigner?
    
    init(messageApi: IMFlutterApiImplementation) {
        self.messageApi = messageApi
    }
    
    func conversationConsentState(clientInboxId: String, conversationTopic: String, completion: @escaping (Result<String, any Error>) -> Void) {
        Task {
            do {
                guard let conversation = try await findConversation(inboxId: clientInboxId, topic: conversationTopic) else {
                    throw IMError.conversationNotFound(conversationTopic)
                }
                completion(.success(ConsentWrapper.consentStateToString(state: try await conversation.consentState())))
            } catch let error {
                completion(.failure(error))
            }
        }
    }
    
    func groupConsentState(clientInboxId: String, groupId: String, completion: @escaping (Result<String, any Error>) -> Void) {
        Task {
            do {
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                completion(.success(ConsentWrapper.consentStateToString(state: try group.consentState())))
            } catch let error {
                completion(.failure(error))
            }
        }
    }
    
    func abc() throws {
        messageApi.flutterTest(str: "test")
    }
    
    func createFromKeyBundle(keyBundle: String, environment: String, appVersion: String?, hasCreateIdentityCallback: Bool?, hasEnableIdentityCallback: Bool?, hasPreAuthenticateToInboxCallback: Bool?, enableV3: Bool?, dbEncryptionKey: FlutterStandardTypedData?, dbDirectory: String?, historySyncUrl: String?, completion: @escaping (Result<String, any Error>) -> Void) {
        Task {
            do {
                guard let keyBundleData = Data(base64Encoded: keyBundle),
                      let bundle = try? PrivateKeyBundle(serializedData: keyBundleData)
                else {
                    throw IMError.invalidKeyBundle
                }
                let preCreateIdentityCallback: PreEventCallback? = hasCreateIdentityCallback ?? false ? self.preCreateIdentityCallback : nil
                let preEnableIdentityCallback: PreEventCallback? = hasEnableIdentityCallback ?? false ? self.preEnableIdentityCallback : nil
                let preAuthenticateToInboxCallback: PreEventCallback? = hasPreAuthenticateToInboxCallback ?? false ? self.preAuthenticateToInboxCallback : nil
                let encryptionKeyData = dbEncryptionKey == nil ? nil : dbEncryptionKey?.data
                
                let options = createClientConfig(
                    env: environment,
                    appVersion: appVersion,
                    preEnableIdentityCallback: preEnableIdentityCallback,
                    preCreateIdentityCallback: preCreateIdentityCallback,
                    preAuthenticateToInboxCallback: preAuthenticateToInboxCallback,
                    enableV3: enableV3 ?? false,
                    dbEncryptionKey: encryptionKeyData,
                    dbDirectory: dbDirectory,
                    historySyncUrl: historySyncUrl
                );
                
                let client = try await Client.from(bundle: bundle, options: options)
                let _ = try await client.contacts.refreshConsentList();
                await clientsManager.updateClient(key: client.inboxID, client: client)
                completion(.success(client.inboxID))
            } catch let error {
                
                completion(.failure(error))
            }
        }
    }
    
    func decodeMessage(inboxId: String, topic: String, encryptedMessage: String, completion: @escaping (Result<IMDecryptedMessage, any Error>) -> Void) {
        Task {
            do {
                guard let encryptedMessageData = Data(base64Encoded: Data(encryptedMessage.utf8)) else {
                    throw IMError.noMessage
                }
                
                let envelope = XMTP.Envelope.with { envelope in
                    envelope.message = encryptedMessageData
                    envelope.contentTopic = topic
                }
                
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                
                guard let conversation = try await findConversation(inboxId: inboxId, topic: topic) else {
                    throw IMError.conversationNotFound("no conversation found for \(topic)")
                }
                let decodedMessage = try conversation.decode(envelope)
                completion(.success(try DecodedMessageWrapper.encodeToClass(decodedMessage, client: client)))
                
                
            } catch let error {
                completion(.failure(error))
            }
        }
    }
    
    func exportKeyBundle(inboxId: String, completion: @escaping (Result<String, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let bundle = try client.privateKeyBundle.serializedData().base64EncodedString()
                completion(.success(bundle))
            } catch let error {
                completion(.failure(error))
            }
        }
    }
    
    func createConversation(inboxId: String, address: String, completion: @escaping (Result<IMConversation, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let conversation = try await client.conversations.newConversation(
                    with: address
                )
                let data =  try await ConversationWrapper.encodeToClass(conversation, client: client)
                completion(.success(data))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func sendMessage(inboxId: String, topic: String, body: String, completion: @escaping (Result<String, any Error>) -> Void) {
        Task {
            do {
                guard let conversation = try await findConversation(inboxId: inboxId, topic: topic) else {
                    throw IMError.conversationNotFound("no conversation found for \(topic)")
                }
                let sending = try ContentJson.fromJson(body)
                let msgId = try await conversation.send(
                    content: sending.content,
                    options: SendOptions(contentType: sending.type)
                )
                completion(.success(msgId))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func subscribeToConversations(inboxId: String) throws {
        Task {
            guard let client = await clientsManager.getClient(key: inboxId) else {
                return
            }
            await subscriptionsManager.get(getConversationsKey(inboxId: inboxId))?.cancel()
            await subscriptionsManager.set(getConversationsKey(inboxId: inboxId), Task {
                do {
                    for try await conversation in await client.conversations.streamAll() {
                        try await messageApi.onSubscribeToConversations(inboxIdArg: inboxId, mArg: ConversationWrapper.encodeToClass(conversation, client: client))
                    }
                } catch {
                    print("Error in conversations subscription: \(error)")
                    await subscriptionsManager.get(getConversationsKey(inboxId: inboxId))?.cancel()
                }
            })
        }
    }
    
    func subscribeToMessages(inboxId: String, topic: String) throws {
        Task {
            guard let conversation = try await findConversation(inboxId: inboxId, topic: topic) else {
                return
            }
            
            guard let client = await clientsManager.getClient(key: inboxId) else {
                throw IMError.noClient
            }
            
            await subscriptionsManager.get(conversation.cacheKey(inboxId))?.cancel()
            await subscriptionsManager.set(conversation.cacheKey(inboxId), Task {
                do {
                    for try await message in conversation.streamMessages() {
                        do {
                            try messageApi.onSubscribeToMessages(inboxIdArg: inboxId, mArg: DecodedMessageWrapper.encodeToClass(message, client: client))
                            
                        } catch {
                            print("discarding message, unable to encode wrapper \(message.id)")
                        }
                    }
                } catch {
                    print("Error in messages subscription: \(error)")
                    await subscriptionsManager.get(conversation.cacheKey(inboxId))?.cancel()
                }
            })
        }
    }
    
    func subscribeToAllMessages(inboxId: String) throws {
        Task {
            guard let client = await clientsManager.getClient(key: inboxId) else {
                return
            }
            
            await subscriptionsManager.get(getMessagesKey(inboxId: inboxId))?.cancel()
            await subscriptionsManager.set(getMessagesKey(inboxId: inboxId), Task {
                do {
                    for try await message in await client.conversations.streamAllMessages() {
                        do {
                            try messageApi.onSubscribeToAllMessages(inboxIdArg: inboxId, mArg: DecodedMessageWrapper.encodeToClass(message, client: client))
                        } catch {
                            print("discarding message, unable to encode wrapper \(message.id)")
                        }
                    }
                } catch {
                    print("Error in all messages subscription: \(error)")
                    await subscriptionsManager.get(getMessagesKey(inboxId: inboxId))?.cancel()
                }
            })
        }
    }
    
    func canMessage(inboxId: String, address: String, completion: @escaping (Result<Bool, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let canMessage = try await client.canMessage(address)
                completion(.success(canMessage))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func conversationList(inboxId: String, completion: @escaping (Result<[IMConversation], any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                _ = try await client.conversations.sync()
                _ = try await client.conversations.syncAllGroups()
                let conversationContainerList = try await client.conversations.list(includeGroups: true)
                
                var results: [IMConversation] = []
                for conversation in conversationContainerList {
                    await self.conversationsManager.set(conversation.cacheKey(inboxId), conversation)
                    switch conversation {
                    case .group(let group):
                        await self.groupsManager.set(group.cacheKey(inboxId), group)
                    default: break
                    }
                    let imConversation = try await ConversationWrapper.encodeToClass(conversation, client: client)
                    results.append(imConversation)
                }
                completion(.success(results))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func loadMessages(inboxId: String, topic: String, limit: Int64?, before: Int64?, after: Int64?, direction: String?, completion: @escaping (Result<[IMDecryptedMessage], any Error>) -> Void) {
        Task {
            do {
                let beforeDate = before != nil ? Date(timeIntervalSince1970: TimeInterval(before!) / 1000) : nil
                let afterDate = after != nil ? Date(timeIntervalSince1970: TimeInterval(after!) / 1000) : nil
                
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                
                guard let conversation = try await findConversation(inboxId: inboxId, topic: topic) else {
                    throw IMError.conversationNotFound("no conversation found for \(topic)")
                }
                
                let sortDirection: Int = (direction != nil && direction == "SORT_DIRECTION_ASCENDING") ? 1 : 2
                
                let limitInt : Int? = limit != nil ? Int(truncatingIfNeeded: limit!) : nil
                
                let messages = try await conversation.messages(
                    limit: limitInt,
                    before: beforeDate,
                    after: afterDate,
                    direction: PagingInfoSortDirection(rawValue: sortDirection)
                )
                
                
                let result:[IMDecryptedMessage] = messages.compactMap { msg in
                    do {
                        return try DecodedMessageWrapper.encodeToClass(msg, client: client)
                    } catch {
                        print("discarding message, unable to encode wrapper \(msg.id)")
                        return nil
                    }
                }
                completion(.success(result))
            } catch let error {
                completion(.failure(error))
            }
            
        }
    }
    
    func allowContacts(inboxId: String, addresses: [String], completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let _ = try await client.contacts.refreshConsentList();
                try await client.contacts.allow(addresses: addresses)
                let _ = try await client.contacts.refreshConsentList();
                completion(.success(()))
            } catch let error {
                completion(.failure(error))
            }
            
        }
    }
    
    func denyContacts(inboxId: String, addresses: [String], completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let _ = try await client.contacts.refreshConsentList();
                try await client.contacts.deny(addresses: addresses)
                let _ = try await client.contacts.refreshConsentList();
                completion(.success(()))
            } catch let error {
                completion(.failure(error))
            }
        }
    }
    
    func loadBatchMessages(inboxId: String, topics: [IMMessageReq], direction: String?, completion: @escaping (Result<[IMDecryptedMessage], any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                var topicsList: [String: Pagination?] = [:]
                topics.forEach { topicreq in
                    let limit : Int? = topicreq.limit != nil ? Int(truncatingIfNeeded: topicreq.limit!) : nil
                    var directionType: PagingInfoSortDirection = .descending
                    let beforeDate = topicreq.before != nil ? Date(timeIntervalSince1970: TimeInterval(topicreq.before!) / 1000) : nil
                    let afterDate = topicreq.after != nil ? Date(timeIntervalSince1970: TimeInterval(topicreq.after!) / 1000) : nil
                    let topic = topicreq.topic
                    
                    if let directionStr = direction {
                        let sortDirection: Int = (directionStr == "SORT_DIRECTION_ASCENDING") ? 1 : 2
                        directionType = PagingInfoSortDirection(rawValue: sortDirection) ?? .descending
                    }
                    
                    let page = Pagination(
                        limit: limit ?? nil,
                        before: beforeDate,
                        after: afterDate,
                        direction: directionType
                    )
                    
                    topicsList[topic] = page
                }
                
                let decodedMessages = try await client.conversations.listBatchMessages(topics: topicsList)
                
                let result:[IMDecryptedMessage] = decodedMessages.compactMap { msg in
                    do {
                        return try DecodedMessageWrapper.encodeToClass(msg, client: client)
                    } catch {
                        print("discarding message, unable to encode wrapper \(msg.id)")
                        return nil
                    }
                }
                completion(.success(result))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func isAllowed(inboxId: String, address: String, completion: @escaping (Result<Bool, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let isAllowed = try await client.contacts.isAllowed(address)
                completion(.success(isAllowed))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func isDenied(inboxId: String, address: String, completion: @escaping (Result<Bool, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let isDenied = try await client.contacts.isDenied(address)
                completion(.success(isDenied))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    
    func refreshConsentList(inboxId: String) throws {
        Task {
            guard let client = await clientsManager.getClient(key: inboxId) else {
                throw IMError.noClient
            }
            let _ = try await client.contacts.refreshConsentList()
            
        }
    }
    
    func unsubscribeToConversations(inboxId: String) throws {
        Task {
            await subscriptionsManager.get(getConversationsKey(inboxId: inboxId))?.cancel()
        }
    }
    
    func unsubscribeToMessages(inboxId: String, topic: String) throws {
        Task {
            guard let conversation = try await findConversation(inboxId: inboxId, topic: topic) else {
                return
            }
            await subscriptionsManager.get(conversation.cacheKey(inboxId))?.cancel()
        }
    }
    
    func unsubscribeToAllMessages(inboxId: String) throws {
        Task {
            await subscriptionsManager.get(getMessagesKey(inboxId: inboxId))?.cancel()
        }
    }
    
    func preEnableIdentityCallback() {
        
    }
    
    func preCreateIdentityCallback() {
        
    }
    
    func preAuthenticateToInboxCallback() {
        
    }
    func createClientConfig(env: String, appVersion: String?, preEnableIdentityCallback: PreEventCallback? = nil, preCreateIdentityCallback: PreEventCallback? = nil, preAuthenticateToInboxCallback: PreEventCallback? = nil, enableV3: Bool = false, dbEncryptionKey: Data? = nil, dbDirectory: String? = nil, historySyncUrl: String? = nil) -> XMTP.ClientOptions {
        // Ensure that all codecs have been registered.
        switch env {
        case "local":
            return XMTP.ClientOptions(api: XMTP.ClientOptions.Api(
                env: XMTP.XMTPEnvironment.local,
                isSecure: false,
                appVersion: appVersion
            ), preEnableIdentityCallback: preEnableIdentityCallback, preCreateIdentityCallback: preCreateIdentityCallback, preAuthenticateToInboxCallback: preAuthenticateToInboxCallback, enableV3: enableV3, encryptionKey: dbEncryptionKey, dbDirectory: dbDirectory, historySyncUrl: historySyncUrl)
        case "production":
            return XMTP.ClientOptions(api: XMTP.ClientOptions.Api(
                env: XMTP.XMTPEnvironment.production,
                isSecure: true,
                appVersion: appVersion
            ), preEnableIdentityCallback: preEnableIdentityCallback, preCreateIdentityCallback: preCreateIdentityCallback, preAuthenticateToInboxCallback: preAuthenticateToInboxCallback, enableV3: enableV3, encryptionKey: dbEncryptionKey, dbDirectory: dbDirectory, historySyncUrl: historySyncUrl)
        default:
            return XMTP.ClientOptions(api: XMTP.ClientOptions.Api(
                env: XMTP.XMTPEnvironment.dev,
                isSecure: true,
                appVersion: appVersion
            ), preEnableIdentityCallback: preEnableIdentityCallback, preCreateIdentityCallback: preCreateIdentityCallback, preAuthenticateToInboxCallback: preAuthenticateToInboxCallback, enableV3: enableV3, encryptionKey: dbEncryptionKey, dbDirectory: dbDirectory, historySyncUrl: historySyncUrl)
        }
    }
    
    func findGroup(inboxId: String, id: String) async throws -> XMTP.Group? {
        guard let client = await clientsManager.getClient(key: inboxId) else {
            throw IMError.noClient
        }
        
        let cacheKey = XMTP.Group.cacheKeyForId(inboxId: client.inboxID, id: id)
        if let group = await groupsManager.get(cacheKey) {
            return group
        } else if let group = try client.findGroup(groupId: id) {
            await groupsManager.set(cacheKey, group)
            return group
        }
        
        return nil
    }
    
    
    func findConversation(inboxId: String, topic: String) async throws -> Conversation? {
        guard let client = await clientsManager.getClient(key: inboxId) else {
            throw IMError.noClient
        }
        
        let cacheKey = Conversation.cacheKeyForTopic(inboxId: inboxId, topic: topic)
        if let conversation = await conversationsManager.get(cacheKey) {
            return conversation
        } else if let conversation = try await client.conversations.list(includeGroups: true).first(where: { $0.topic == topic }) {
            await conversationsManager.set(cacheKey, conversation)
            return conversation
        }
        
        return nil
    }
    
    func auth(address: String, environment: String, appVersion: String?, hasCreateIdentityCallback: Bool?, hasEnableIdentityCallback: Bool?, hasPreAuthenticateToInboxCallback: Bool?, enableV3: Bool?, dbEncryptionKey: FlutterStandardTypedData?, dbDirectory: String?, historySyncUrl: String?, completion: @escaping (Result<String, any Error>) -> Void) {
        Task {
            do {
                let signer = IMSigner(api: self, address: address)
                self.signer = signer
                let preCreateIdentityCallback: PreEventCallback? = hasCreateIdentityCallback ?? false ? self.preCreateIdentityCallback : nil
                let preEnableIdentityCallback: PreEventCallback? = hasEnableIdentityCallback ?? false ? self.preEnableIdentityCallback : nil
                let preAuthenticateToInboxCallback: PreEventCallback? = hasPreAuthenticateToInboxCallback ?? false ? self.preAuthenticateToInboxCallback : nil
                let encryptionKeyData = dbEncryptionKey == nil ? nil : dbEncryptionKey?.data
                
                let options = createClientConfig(
                    env: environment,
                    appVersion: appVersion,
                    preEnableIdentityCallback: preEnableIdentityCallback,
                    preCreateIdentityCallback: preCreateIdentityCallback,
                    preAuthenticateToInboxCallback: preAuthenticateToInboxCallback,
                    enableV3: enableV3 ?? false,
                    dbEncryptionKey: encryptionKeyData,
                    dbDirectory: dbDirectory,
                    historySyncUrl: historySyncUrl
                );
                let client = try await XMTP.Client.create(account: signer, options: options)
                let _ = try await client.contacts.refreshConsentList();
                await self.clientsManager.updateClient(key: client.inboxID, client: client)
                self.signer = nil
                completion(.success(client.inboxID))
            } catch let error {
                completion(.failure(error))
            }
        }
        
    }
    
    func receiveSignature(requestId: String, signature: String) throws {
        try signer?.handle(id: requestId, signature: signature)
    }
    
    func staticCanMessage(address: String, appVersion: String, completion: @escaping (Result<Bool, any Error>) -> Void) {
        Task {
            do {
                let options = createClientConfig(env: "production", appVersion: appVersion)
                let canMessage = try await Client.canMessage(address, options: options)
                completion(.success(canMessage))
            } catch {
                completion(.failure(IMError.noClient))
            }
        }
    }
    
    func registerPushToken(pushServer: String, token: String, completion: @escaping (Result<String, any Error>) -> Void) {
        Task {
            XMTPPush.shared.setPushServer(pushServer)
            do {
                try await XMTPPush.shared.register(token: token)
                completion(.success(XMTPPush.shared.installationID))
            } catch {
                print("Error registering: \(error)")
                completion(.failure(error))
            }
        }
    }
    
    func subscribePushWithMetadata(inboxId: String, topics: [String]) throws {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let hmacKeysResult = await client.conversations.getHmacKeys()
                let subscriptions = topics.map { topic -> NotificationSubscription in
                    let hmacKeys = hmacKeysResult.hmacKeys
                    
                    let result = hmacKeys[topic]?.values.map { hmacKey -> NotificationSubscriptionHmacKey in
                        NotificationSubscriptionHmacKey.with { sub_key in
                            sub_key.key = hmacKey.hmacKey
                            sub_key.thirtyDayPeriodsSinceEpoch = UInt32(hmacKey.thirtyDayPeriodsSinceEpoch)
                        }
                    }
                    
                    return NotificationSubscription.with { sub in
                        sub.hmacKeys = result ?? []
                        sub.topic = topic
                    }
                }
                
                try await XMTPPush.shared.subscribeWithMetadata(subscriptions: subscriptions)
            } catch {
                print("Error subscribing: \(error)")
            }
        }
    }
    
    func getPushWithMetadata(inboxId: String, topics: [String], completion: @escaping (Result<[IMPushWithMetadata], any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let hmacKeysResult = await client.conversations.getHmacKeys()
                let metadatas = topics.map { topic -> IMPushWithMetadata in
                    let hmacKeys = hmacKeysResult.hmacKeys
                    
                    let result = hmacKeys[topic]?.values.map { hmacKey -> IMPushHMACKeys in
                        let key = hmacKey.hmacKey.base64EncodedString();
                        let hmacKey = IMPushHMACKeys(
                            key: key,
                            key2: "",
                            thirtyDayPeriodsSinceEpoch: Int64(hmacKey.thirtyDayPeriodsSinceEpoch))
                        return hmacKey
                    }
                    return IMPushWithMetadata(hmacKeys: result ?? [], topic: topic)
                }
                completion(.success(metadatas))
            } catch let error {
                print("Error subscribing: \(error)")
                completion(.failure(error))
            }
        }
    }
    
    func subscribePushTopics(inboxId: String, topics: [String]) throws {
        Task {
            do {
                try await XMTPPush.shared.subscribe(topics: topics)
            } catch {
                print("Error subscribing: \(error)")
            }
        }
    }
    
    func unsubscribePushTopics(topics: [String]) throws {
        Task {
            do {
                try await XMTPPush.shared.unsubscribe(topics: topics)
            } catch {
                print("Error unsubscribing: \(error)")
            }
        }
    }
    
    func deleteInstallationPush() throws {
        Task {
            let request = Notifications_V1_DeleteInstallationRequest.with { request in
                request.installationID = XMTPPush.shared.installationID
            }
            _ = await XMTPPush.shared.client.deleteInstallation(request: request)
            UserDefaults.standard.removeObject(forKey: XMTPPush.shared.installationIDKey)
        }
    }
    
    func updateGroupName(clientInboxId: String, groupId: String, groupName: String, completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                try await group.updateGroupName(groupName: groupName)
                completion(.success(()))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func updateGroupImageUrlSquare(clientInboxId: String, groupId: String, groupImageUrl: String, completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                try await group.updateGroupImageUrlSquare(imageUrlSquare: groupImageUrl)
                completion(.success(()))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func syncGroup(clientInboxId: String, groupId: String, completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                try await group.sync()
                completion(.success(()))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func isAdmin(clientInboxId: String, groupId: String, inboxId: String, completion: @escaping (Result<Bool, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                completion(.success(try group.isAdmin(inboxId: inboxId)))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    
    func isSuperAdmin(clientInboxId: String, groupId: String, inboxId: String, completion: @escaping (Result<Bool, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                completion(.success(try group.isSuperAdmin(inboxId: inboxId)))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func findGroup(inboxId: String, groupId: String, completion: @escaping (Result<ImGroup?, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                if let group = try client.findGroup(groupId: groupId) {
                    await completion(.success(try GroupWrapper.encodeToCloass(group, client: client)))
                } else {
                    completion(.success(nil))
                }
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func canGroupMessage(inboxId: String, peerAddresses: [String], completion: @escaping (Result<[String : Bool], any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let canMessage = try await client.canMessageV3(addresses: peerAddresses)
                completion(.success(canMessage))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func createGroup(inboxId: String, peerAddresses: [String], permission: String, groupOptionsJson: String, completion: @escaping (Result<ImGroup, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let permissionLevel: GroupPermissionPreconfiguration = {
                    switch permission {
                    case "admin_only":
                        return .adminOnly
                    default:
                        return .allMembers
                    }
                }()
                
                let createGroupParams = CreateGroupParamsWrapper.createGroupParamsFromJson(groupOptionsJson)
                let group = try await client.conversations.newGroup(
                    with: peerAddresses,
                    permissions: permissionLevel,
                    name: createGroupParams.groupName,
                    imageUrlSquare: createGroupParams.groupImageUrlSquare,
                    description: createGroupParams.groupDescription,
                    pinnedFrameUrl: createGroupParams.groupPinnedFrameUrl
                )
                let result = try await GroupWrapper.encodeToCloass(group, client: client)
                completion(.success(result))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func subscribeToAllGroupMessages(inboxId: String) throws {
        Task {
            guard let client = await clientsManager.getClient(key: inboxId) else {
                return
            }
            
            await subscriptionsManager.get(getGroupMessagesKey(inboxId: client.inboxID))?.cancel()
            await subscriptionsManager.set(getGroupMessagesKey(inboxId: client.inboxID), Task {
                do {
                    for try await message in await client.conversations.streamAllGroupMessages() {
                        do {
                            try messageApi.onSubscribeToAllMessages(inboxIdArg: inboxId, mArg: DecodedMessageWrapper.encodeToClass(message, client: client))
                            
                        } catch {
                            print("discarding message, unable to encode wrapper \(message.id)")
                        }
                    }
                } catch {
                    print("Error in all messages subscription: \(error)")
                    await subscriptionsManager.get(getGroupMessagesKey(inboxId: inboxId))?.cancel()
                }
            })
        }
    }
    
    func unsubscribeToAllGroupMessages(inboxId: String) throws {
        Task {
            await subscriptionsManager.get(getGroupMessagesKey(inboxId: inboxId))?.cancel()
        }
    }
    
    func allowGroups(inboxId: String, groupIds: [String], completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let _ = try await client.contacts.refreshConsentList();
                try await client.contacts.allowGroups(groupIds: groupIds)
                let _ = try await client.contacts.refreshConsentList();
                completion(.success(()))
            } catch let error {
                completion(.failure(error))
            }
        }
    }
    
    func denyGroups(inboxId: String, groupIds: [String], completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let _ = try await client.contacts.refreshConsentList();
                try await client.contacts.denyGroups(groupIds: groupIds)
                let _ = try await client.contacts.refreshConsentList();
                completion(.success(()))
            } catch let error {
                completion(.failure(error))
            }
        }
    }
    
    func isGroupAllowed(inboxId: String, groupId: String, completion: @escaping (Result<Bool, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let isAllowed = try await client.contacts.isGroupAllowed(groupId: groupId)
                completion(.success(isAllowed))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func isGroupDenied(inboxId: String, groupId: String, completion: @escaping (Result<Bool, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: inboxId) else {
                    throw IMError.noClient
                }
                let isDenied = try await client.contacts.isGroupDenied(groupId: groupId)
                completion(.success(isDenied))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func addGroupMembers(clientInboxId: String, groupId: String, peerAddresses: [String], completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                
                try await group.addMembers(addresses: peerAddresses)
                completion(.success(()))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func addAdmin(clientInboxId: String, groupId: String, inboxId: String, completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                
                try await group.addAdmin(inboxId: inboxId)
                completion(.success(()))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func removeAdmin(clientInboxId: String, groupId: String, inboxId: String, completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                
                try await group.removeAdmin(inboxId: inboxId)
                completion(.success(()))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    func removeGroupMembers(clientInboxId: String, groupId: String, addresses: [String], completion: @escaping (Result<Void, any Error>) -> Void) {
        Task {
            do {
                guard let client = await clientsManager.getClient(key: clientInboxId) else {
                    throw IMError.noClient
                }
                
                guard let group = try await findGroup(inboxId: clientInboxId, id: groupId) else {
                    throw IMError.conversationNotFound("no group found for \(groupId)")
                }
                
                try await group.removeMembers(addresses: addresses)
                completion(.success(()))
            } catch {
                print("ERRRO!: \(error.localizedDescription)")
                completion(.failure(error))
            }
        }
    }
    
    actor IsolatedManager<T> {
        private var map: [String: T] = [:]
        
        func set(_ key: String, _ object: T) {
            map[key] = object
        }
        
        func get(_ key: String) -> T? {
            map[key]
        }
    }
    
    actor ClientsManager {
        private var clients: [String: XMTP.Client] = [:]
        
        // A method to update the client
        func updateClient(key: String, client: XMTP.Client) {
            ContentJson.initCodecs(client: client)
            clients[key] = client
        }
        
        // A method to drop client for a given key from memory
        func dropClient(key: String) {
            clients[key] = nil
        }
        
        // A method to retrieve a client
        func getClient(key: String) -> XMTP.Client? {
            return clients[key]
        }
        
        // A method to disconnect all dbs
        func dropAllLocalDatabaseConnections() throws {
            for (_, client) in clients {
                // Call the drop method on each v3 client
                if (!client.installationID.isEmpty) {
                    try client.dropLocalDatabaseConnection()
                }
            }
        }
        
        // A method to reconnect all dbs
        func reconnectAllLocalDatabaseConnections() async throws {
            for (_, client) in clients {
                // Call the reconnect method on each v3 client
                if (!client.installationID.isEmpty) {
                    try await client.reconnectLocalDatabase()
                }
            }
        }
        
    }
    
    func getConversationsKey(inboxId: String) -> String {
        return "conversations:\(inboxId)"
    }
    
    func getMessagesKey(inboxId: String) -> String {
        return "messages:\(inboxId)"
    }
    
    func getGroupsKey(inboxId: String) -> String {
        return "groups:\(inboxId)"
    }
    
    func getGroupMessagesKey(inboxId: String) -> String {
        return "groupMessages:\(inboxId)"
    }
    
}
