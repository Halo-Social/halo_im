package halo.social.halo_im.xmtp

import ConsentWrapper.Companion.consentStateToString
import CreateGroupParamsWrapper
import DecodedMessageWrapper
import IMConversation
import IMDecryptedMessage
import IMMessageReq
import IMPushHMACKeys
import IMPushWithMetadata
import ImGroup
import ImMember
import MemberWrapper
import PermissionPolicySetWrapper
import android.content.Context
import android.util.Base64
import android.util.Base64.NO_WRAP
import android.util.Log
import expo.modules.xmtpreactnativesdk.wrappers.ContentJson
import halo.social.halo_im.api.IMFlutterApiImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.Group
import org.xmtp.android.library.SendOptions
import org.xmtp.android.library.XMTPEnvironment
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.codecs.description
import org.xmtp.android.library.libxmtp.PermissionLevel
import org.xmtp.android.library.messages.EnvelopeBuilder
import org.xmtp.android.library.messages.MessageDeliveryStatus
import org.xmtp.android.library.messages.Pagination
import org.xmtp.android.library.push.PushPreferences
import org.xmtp.android.library.push.Service
import org.xmtp.android.library.push.XMTPPush
import org.xmtp.proto.message.api.v1.MessageApiOuterClass
import org.xmtp.proto.message.contents.PrivateKeyOuterClass
import uniffi.xmtpv3.org.xmtp.android.library.libxmtp.GroupPermissionPreconfiguration
import uniffi.xmtpv3.org.xmtp.android.library.libxmtp.PermissionOption
import java.util.Date

class HaloImManger(private val appContext: Context) {

    companion object {
        private const val TAG = "XMTPHelper"
        private const val ACCOUNT_TYPE = "com.kucoin.wallet.dev"
    }

    private val clients: MutableMap<String, Client> = mutableMapOf()
    private val conversations: MutableMap<String, Conversation> = mutableMapOf()
    private val subscriptions: MutableMap<String, Job> = mutableMapOf()
    private val imFlutterApiImpl = IMFlutterApiImpl()
    private var xmtpPush: XMTPPush? = null
    private var haloSingerKey: HaloSingerKey? = null
    private val groups: MutableMap<String, Group> = mutableMapOf()

    /**
     * 这个函数用于导入私秘钥
     */
    suspend fun createFromKeyBundle(
        keyBundle: String,
        environment: String,
        appVersion: String?,
        hasCreateIdentityCallback: Boolean?,
        hasEnableIdentityCallback: Boolean?,
        hasPreAuthenticateToInboxCallback: Boolean?,
        enableV3: Boolean?,
        dbEncryptionKey: ByteArray?,
        dbDirectory: String?,
        historySyncUrl: String?,
    ): String {
        val clientOptions = ClientOptions(
            api = ClientOptions.Api(
                XMTPEnvironment.PRODUCTION,
                appVersion = appVersion,
                isSecure = true
            ),
            enableV3 = true,
            appContext = appContext,
            dbEncryptionKey = dbEncryptionKey,
            dbDirectory = dbDirectory,
        )
        val bundle = PrivateKeyOuterClass.PrivateKeyBundle.parseFrom(
            Base64.decode(keyBundle, NO_WRAP)
        )
        val client = Client().buildFromBundle(bundle = bundle, options = clientOptions)
        ContentJson.Companion
        clients[client.inboxId] = client
        refreshConsentList(client.inboxId)
        return client.inboxId;
    }

    fun exportKeyBundle(inboxId: String): String {
        val client = clients[inboxId] ?: throw XMTPException("No client");
        return Base64.encodeToString(client.privateKeyBundle.toByteArray(), NO_WRAP)
    }

    suspend fun auth(
        address: String,
        environment: String,
        appVersion: String?,
        hasCreateIdentityCallback: Boolean?,
        hasEnableIdentityCallback: Boolean?,
        hasPreAuthenticateToInboxCallback: Boolean?,
        enableV3: Boolean?,
        dbEncryptionKey: ByteArray?,
        dbDirectory: String?,
        historySyncUrl: String?
    ): String {
        val clientOptions = ClientOptions(
            api = ClientOptions.Api(
                XMTPEnvironment.PRODUCTION,
                appVersion = appVersion,
                isSecure = true
            ),
            enableV3 = true,
            appContext = appContext,
            dbEncryptionKey = dbEncryptionKey,
            dbDirectory = dbDirectory,
        )
        haloSingerKey = HaloSingerKey(imFlutterApiImpl, address)
        val client = Client().create(account = haloSingerKey!!, options = clientOptions)
        ContentJson.Companion
        clients[client.inboxId] = client
        refreshConsentList(client.inboxId)
        return client.inboxId;
    }

    suspend fun receiveSignature(requestId: String, signature: String) {
        haloSingerKey?.handle(requestId, signature);
    }

    /**
     * 创建会话
     */
    suspend fun createConversation(inboxId: String, address: String): IMConversation {
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val conversation = client.conversations.newConversation(address)
        Log.i(TAG, "createConversation - topic = ${conversation.topic}")

//        val consentState = conversation.consentState()
        val groupName: String
        val groupId: String
        val groupDescription: String
        val groupIcon: String
        when (conversation.version) {
            Conversation.Version.GROUP -> {
                val group = (conversation as Conversation.Group).group
                groupName = group.name;
                groupId = group.id
                groupDescription = group.description
                groupIcon = group.imageUrlSquare
            }

            else -> {
                groupName = ""
                groupId = ""
                groupDescription = ""
                groupIcon = ""
            }
        }
        return IMConversation(
            clientAddress = client.address,
            createdAt = conversation.createdAt.time,
            topic = conversation.topic,
            peerAddress = conversation.peerAddress,
            version = conversation.version.name,
            conversationID = (conversation.conversationId ?: ""),
            keyMaterial = (conversation.keyMaterial?.let {
                Base64.encodeToString(it, NO_WRAP)
            } ?: ""),
            consentProof = if (conversation.consentProof != null) Base64.encodeToString(
                conversation.consentProof?.toByteArray(), NO_WRAP
            ) else "",
            isDenied = false,
            isAllowed = false,
            groupName = groupName,
            groupIcon = groupIcon,
            groupId = groupId,
            groupDescription = groupDescription
        )
    }

    /**
     * 发送文本消息
     */
    suspend fun sendMessage(
        inboxId: String,
        conversationTopic: String,
        contentJson: String
    ): String {
        Log.i(TAG, "sendMessage")
        val conversation =
            findConversation(
                inboxId = inboxId,
                topic = conversationTopic
            )
                ?: throw XMTPException("no conversation found for $conversationTopic")
        ContentJson.Companion
        val sending = ContentJson.fromJson(contentJson)
        return conversation.send(
            content = sending.content,
            options = SendOptions(contentType = sending.type)
        )

    }

    private suspend fun findConversation(
        inboxId: String,
        topic: String,
    ): Conversation? {
        val client = clients[inboxId] ?: throw XMTPException("No client $inboxId")

        val cacheKey = "${inboxId}:${topic}"
        val cacheConversation = conversations[cacheKey]
        if (cacheConversation != null) {
            return cacheConversation
        } else {
            val conversation = client.conversations.list(true)
                .firstOrNull { it.topic == topic }
            if (conversation != null) {
                conversations[conversation.cacheKey(inboxId)] = conversation
                return conversation
            }
        }
        return null
    }

    fun Conversation.cacheKey(inboxId: String): String {
        return "${inboxId}:${topic}"
    }


    private fun getConversationsKey(inboxId: String): String {
        return "conversations:$inboxId"
    }

    /**
     * 订阅新的会话监听
     */
    fun subscribeToConversations(inboxId: String) {
        val client = clients[inboxId] ?: throw XMTPException("No client")

        subscriptions[getConversationsKey(inboxId)]?.cancel()
        subscriptions[getConversationsKey(inboxId)] = CoroutineScope(Dispatchers.IO).launch {
            client.conversations.streamAll().collect { conversation ->
                run {
                    if (conversation.keyMaterial == null) {
                        Log.i(TAG, "Null key material before encode conversation")
                    }
//                    val consentState = conversation.consentState()
                    val groupName: String
                    val groupId: String
                    val groupDescription: String
                    val groupIcon: String
                    when (conversation.version) {
                        Conversation.Version.GROUP -> {
                            val group = (conversation as Conversation.Group).group
                            groupName = group.name;
                            groupId = group.id
                            groupDescription = group.description
                            groupIcon = group.imageUrlSquare
                        }

                        else -> {
                            groupName = ""
                            groupId = ""
                            groupDescription = ""
                            groupIcon = ""
                        }
                    }
                    val mapAnyAny = IMConversation(
                        clientAddress = client.address,
                        createdAt = conversation.createdAt.time,
                        topic = conversation.topic,
                        peerAddress = conversation.peerAddress,
                        version = conversation.version.name,
                        conversationID = (conversation.conversationId ?: ""),
                        keyMaterial = (conversation.keyMaterial?.let {
                            Base64.encodeToString(
                                it,
                                Base64.NO_WRAP
                            )
                        } ?: ""),
                        consentProof = if (conversation.consentProof != null) Base64.encodeToString(
                            conversation.consentProof?.toByteArray(),
                            Base64.NO_WRAP
                        ) else "",
                        isDenied = false,
                        isAllowed = false,
                        groupName = groupName,
                        groupIcon = groupIcon,
                        groupId = groupId,
                        groupDescription = groupDescription
                    )
                    imFlutterApiImpl.onSubscribeToConversations(inboxId, mapAnyAny)
                }
            }
        }
    }

    /**
     * 取消订阅新的会话监听
     */
    fun unsubscribeFromConversations(inboxId: String) {
        logV("unsubscribeFromConversations")
        subscriptions[getConversationsKey(inboxId)]?.cancel()
    }

    /**
     * 订阅新消息监听
     */
    suspend fun subscribeToMessages(inboxId: String, topic: String) {
        Log.e(TAG, "subscribeToMessages method")
        val conversation =
            findConversation(
                inboxId = inboxId,
                topic = topic
            ) ?: return
        subscriptions[conversation.cacheKey(inboxId)]?.cancel()
        subscriptions[conversation.cacheKey(inboxId)] =
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    conversation.streamDecryptedMessages().collect { message ->
                        val contentM: Map<String, Any> =
                            ContentJson(message.encodedContent).toJsonMap()
                        Log.d(TAG, "subscribeToMessages")
                        val dm = IMDecryptedMessage(
                            id = message.id,
                            topic = message.topic,
                            contentTypeId = message.encodedContent.type.description,
                            content = contentM.toNullableMap(),
                            senderAddress = message.senderAddress,
                            sent = message.sentAt.time,
                            deliveryStatus = message.deliveryStatus.toString()
                        )

                        imFlutterApiImpl.onSubscribeToMessages(inboxId, dm)
                    }
                } catch (e: Exception) {
                    Log.e("XMTPModule", "Error in messages subscription: $e")
                    subscriptions[conversation.cacheKey(inboxId)]?.cancel()
                }
            }
    }

    /**
     * 取消订阅新消息监听
     */
    suspend fun unsubscribeFromMessages(inboxId: String, topic: String) {
        val conversation =
            findConversation(
                inboxId = inboxId,
                topic = topic
            ) ?: return
        subscriptions[conversation.cacheKey(inboxId)]?.cancel()
    }


    /**
     * 加载消息
     */
    suspend fun loadMessages(
        inboxId: String,
        topic: String,
        limit: Int?,
        before: Long?,
        after: Long?,
        direction: String?
    ): List<IMDecryptedMessage> {
        Log.e(TAG, "loadMessages method")
        val conversation =
            findConversation(
                inboxId = inboxId,
                topic = topic,
            ) ?: throw XMTPException("no conversation found for $topic")
        val beforeDate = if (before != null) Date(before) else null
        val afterDate = if (after != null) Date(after) else null
        val group = findGroup(inboxId, "");

        val list = conversation.decryptedMessages(
            limit = limit,
//            before = beforeDate,
//            after = afterDate,
            direction = MessageApiOuterClass.SortDirection.valueOf(
                direction ?: "SORT_DIRECTION_DESCENDING"
            )
        ).map { e ->
            val map2: Map<String, Any> = ContentJson(e.encodedContent).toJsonMap()
            IMDecryptedMessage(
                id = e.id,
                topic = e.topic,
                contentTypeId = e.encodedContent.type.description,
                content = map2.toNullableMap(),
                senderAddress = e.senderAddress,
                sent = e.sentAt.time,
                deliveryStatus = e.deliveryStatus.toString()
            )
        }

        Log.d(TAG, "loadMessages size ${list.size}")
        return list.filter {
            Date(it.sent).after(afterDate ?: Date(0)) &&
                    Date(it.sent).before(beforeDate ?: Date(System.currentTimeMillis())
            )
        }
    }

    suspend fun canMessage(inboxId: String, peerAddress: String): Boolean {
        Log.e(TAG, "canMessage")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        return client.canMessage(peerAddress)
    }

    suspend fun canGroupMessage(
        inboxId: String,
        peerAddresses: List<String>
    ): Map<String, Boolean> {
        logV("canGroupMessage")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        return client.canMessageV3(peerAddresses)
    }


    suspend fun listConversations(inboxId: String): List<IMConversation> {
        Log.e(TAG, "listConversations")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        client.conversations.syncGroups()
        client.conversations.syncAllGroups()
        val conversationList = client.conversations.list(true)
        val list = conversationList.map { conversation ->
            conversations[conversation.cacheKey(inboxId)] = conversation
            if (conversation.keyMaterial == null) {
                logV("Null key material before encode conversation")
            }
//            val consentState = conversation.consentState()
            val groupName: String
            val groupId: String
            val groupDescription: String
            val groupIcon: String
            when (conversation.version) {
                Conversation.Version.GROUP -> {
                    val group = (conversation as Conversation.Group).group
                    groupName = group.name;
                    groupId = group.id
                    groupDescription = group.description
                    groupIcon = group.imageUrlSquare
                }

                else -> {
                    groupName = ""
                    groupId = ""
                    groupDescription = ""
                    groupIcon = ""
                }
            }
            IMConversation(
                clientAddress = client.address,
                createdAt = conversation.createdAt.time,
                topic = conversation.topic,
                peerAddress = conversation.peerAddress,
                version = conversation.version.name,
                conversationID = (conversation.conversationId ?: ""),
                keyMaterial = (conversation.keyMaterial?.let {
                    Base64.encodeToString(
                        it,
                        Base64.NO_WRAP
                    )
                } ?: ""),
                consentProof = if (conversation.consentProof != null) Base64.encodeToString(
                    conversation.consentProof?.toByteArray(),
                    Base64.NO_WRAP
                ) else "",
                isDenied = false,
                isAllowed = false,
                groupName = groupName,
                groupIcon = groupIcon,
                groupId = groupId,
                groupDescription = groupDescription
            )
        }
        return list
    }


    private fun logV(log: String) {
        Log.i(TAG, log)
    }


    /**
     * 是否可以发消息
     * 是否已经在xmtp网络注册
     */
    suspend fun staticCanMessage(
        peerAddress: String,
        appVersion: String
    ): Boolean {
        try {
            logV("staticCanMessage")
            val options = ClientOptions(
                api = ClientOptions.Api(
                    XMTPEnvironment.PRODUCTION,
                    appVersion = appVersion,
                    isSecure = true
                ),
                enableV3 = true,
                appContext = appContext,
            )
            return Client.canMessage(peerAddress = peerAddress, options = options)
        } catch (e: Exception) {
            throw XMTPException("Failed to create client: ${e.message}")
        }
    }


    fun dropClient(inboxId: String) {
        logV("dropClient")
        clients.remove(inboxId)
    }

    suspend fun loadBatchMessages(
        inboxId: String,
        topics: List<IMMessageReq>,
        direct: String?
    ): List<IMDecryptedMessage> {
        logV("loadBatchMessages")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val topicsList = mutableListOf<Pair<String, Pagination>>()
        topics.forEach {
            val topic = it.topic
            var limit: Int? = null
            var before: Long? = null
            var after: Long? = null
            var direction: MessageApiOuterClass.SortDirection =
                MessageApiOuterClass.SortDirection.SORT_DIRECTION_DESCENDING

            try {
                limit = it.limit?.toInt()
                before = it.before
                after = it.after
                direction = MessageApiOuterClass.SortDirection.valueOf(
                    if (direct.isNullOrBlank()) {
                        "SORT_DIRECTION_DESCENDING"
                    } else {
                        direct
                    }
                )
            } catch (e: Exception) {
                Log.e(
                    "XMTPModule",
                    "Pagination given incorrect information ${e.message}"
                )
            }

            val page = Pagination(
                limit = if (limit != null && limit > 0) limit else null,
                before = if (before != null && before > 0) Date(before) else null,
                after = if (after != null && after > 0) Date(after) else null,
                direction = direction
            )

            topicsList.add(Pair(topic, page))
        }

        return client.conversations.listBatchDecryptedMessages(topicsList)
            .map {
                val map2: Map<String, Any> = ContentJson(it.encodedContent).toJsonMap()
                IMDecryptedMessage(
                    id = it.id,
                    topic = it.topic,
                    contentTypeId = it.encodedContent.type.description,
                    content = map2.toNullableMap(),
                    senderAddress = it.senderAddress,
                    sent = it.sentAt.time,
                    deliveryStatus = it.deliveryStatus.toString()
                )
            }
    }


    suspend fun isAllowed(inboxId: String, address: String): Boolean {
        logV("isAllowed")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val allowed = client.contacts.isAllowed(address)
        return allowed
    }

    suspend fun isDenied(inboxId: String, address: String): Boolean {
        logV("isDenied")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        return client.contacts.isDenied(address)
    }

    suspend fun denyContacts(inboxId: String, addresses: List<String>) {
        logV("denyContacts")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        client.contacts.deny(addresses)
        refreshConsentList(inboxId)
    }

    suspend fun allowContacts(inboxId: String, addresses: List<String>) {
        val client = clients[inboxId] ?: throw XMTPException("No client")
        client.contacts.allow(addresses)
        refreshConsentList(inboxId)
    }


    suspend fun refreshConsentList(inboxId: String) {
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val consentList = client.contacts.refreshConsentList()
        //consentList.entries.map { ConsentWrapper.encode(it.value) }
    }

    fun registerPushToken(pushServer: String, token: String): String? {
        logV("registerPushToken pushServer - ${pushServer}  token - ${token}")
        xmtpPush = XMTPPush(appContext, pushServer)
        xmtpPush?.register(token)
        return xmtpPush?.installationId
    }


    /**
     * 推送订阅Topics
     * 订阅新消息提醒
     */
    fun subscribePushTopics(inboxId: String, topics: List<String>) {
        logV("subscribePushTopics")
        Log.i("XMTPPush -", "HaloImManger #subscribePushTopics ${inboxId} #### ${topics}")
        if (topics.isNotEmpty()) {
            if (xmtpPush == null) {
                throw XMTPException("Push server not registered")
            }
            xmtpPush?.subscribe(topics)
            Log.i("XMTPPush -", "HaloImManger #subscribePushTopics success")
        }
    }

    fun subscribeWithMetadata(inboxId: String, topics: List<String>) {
        logV("subscribePushTopics")
        Log.i("XMTPPush -", "HaloImManger #subscribePushTopics ${inboxId} #### ${topics}")
        if (topics.isNotEmpty()) {
            if (xmtpPush == null) {
                throw XMTPException("Push server not registered")
            }
            val client = clients[inboxId] ?: throw XMTPException("No client")

            val hmacKeysResult = client.conversations.getHmacKeys()
            val subscriptions = topics.map {
                val hmacKeys = hmacKeysResult.hmacKeysMap
                val result = hmacKeys[it]?.valuesList?.map { hmacKey ->
                    Service.Subscription.HmacKey.newBuilder().also { sub_key ->
                        sub_key.key = hmacKey.hmacKey
                        sub_key.thirtyDayPeriodsSinceEpoch = hmacKey.thirtyDayPeriodsSinceEpoch
                    }.build()
                }
                Log.i("XMTPPush -", "result = ${result}")
                Service.Subscription.newBuilder().also { sub ->
                    sub.addAllHmacKeys(result)
                    if (!result.isNullOrEmpty()) {
                        sub.addAllHmacKeys(result)
                    }
                    sub.topic = it
                }.build()
            }
            xmtpPush?.subscribeWithMetadata(subscriptions)
            Log.i("XMTPPush -", "HaloImManger #subscribePushTopics success")
        }
    }

    fun getPushWithMetadata(inboxId: String, topics: List<String>): List<IMPushWithMetadata> {
        logV("getPushWithMetadata")
        Log.i("XMTPPush -", "HaloImManger #getPushWithMetadata ${inboxId} #### ${topics}")
        if (topics.isNotEmpty()) {
//            if (xmtpPush == null) {
//                throw XMTPException("Push server not registered")
//            }
            val client = clients[inboxId] ?: throw XMTPException("No client")

            val hmacKeysResult = client.conversations.getHmacKeys()
            val subscriptions = topics.map {
                val hmacKeys = hmacKeysResult.hmacKeysMap
                val result = hmacKeys[it]?.valuesList?.map { hmacKey ->
                    Service.Subscription.HmacKey.newBuilder().also { sub_key ->
                        sub_key.key = hmacKey.hmacKey
                        sub_key.thirtyDayPeriodsSinceEpoch = hmacKey.thirtyDayPeriodsSinceEpoch
                    }.build()
                }
                Service.Subscription.newBuilder().also { sub ->
                    if (!result.isNullOrEmpty()) {
                        sub.addAllHmacKeys(result)
                    }
                    sub.topic = it
                }.build()
            }
            val imp = subscriptions.map { itt ->
                val list = itt.hmacKeysList.map {
//                    Log.i("XMTPPush -", "getPushWithMetadata key = ${it.key.toByteArray().contentToString()}")
//                    var a = Base64.encodeToString(it.key.toByteArray(), NO_WRAP)
//                    Log.i("XMTPPush -", "getPushWithMetadata key base64 = $a")

                    IMPushHMACKeys(
                        thirtyDayPeriodsSinceEpoch = it.thirtyDayPeriodsSinceEpoch.toLong(),
                        key = Base64.encodeToString(it.key.toByteArray(), NO_WRAP),
                        key2 = it.key.toByteArray().contentToString(),
                    )
                }.toList()
                val metaDate = IMPushWithMetadata(topic = itt.topic, hmacKeys = list)
                metaDate
            }.toList()
            return imp
        }

        return listOf<IMPushWithMetadata>()
    }

    fun unsubscribePushTopics(topics: List<String>) {
        logV("unsubscribePushTopics")
        if (topics.isNotEmpty()) {
            if (xmtpPush == null) {
                throw XMTPException("Push server not registered")
            }
            xmtpPush?.unsubscribe(topics)
        }
    }

    fun deleteInstallation() {
        try {
            logV("deleteInstallation")
            val request = Service.DeleteInstallationRequest.newBuilder().also { request ->
                request.installationId = xmtpPush?.installationId
            }.build()
            xmtpPush?.client?.deleteInstallation(request)
            PushPreferences.clearAll(appContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * 解密消息,使用场景push信息等
     */
    suspend fun decodeMessage(
        inboxId: String,
        topic: String,
        encryptedMessage: String
    ): IMDecryptedMessage {
        logV("decodeMessage")
        val encryptedMessageData = Base64.decode(encryptedMessage, NO_WRAP)
        val envelope = EnvelopeBuilder.buildFromString(topic, Date(), encryptedMessageData)
        val conversation =
            findConversation(
                inboxId = inboxId,
                topic = topic
            )
                ?: throw XMTPException("no conversation found for $topic")
        val decodedMessage = conversation.decrypt(envelope)
        val contentM: Map<String, Any> = ContentJson(decodedMessage.encodedContent).toJsonMap()
        val dm = IMDecryptedMessage(
            id = decodedMessage.id,
            topic = decodedMessage.topic,
            contentTypeId = decodedMessage.encodedContent.type.description,
            content = contentM.toNullableMap(),
            senderAddress = decodedMessage.senderAddress,
            sent = decodedMessage.sentAt.time,
            deliveryStatus = decodedMessage.deliveryStatus.toString()
        )
        return dm
    }


    fun subscribeToAllMessages(inboxId: String, includeGroups: Boolean = false) {
        val client = clients[inboxId] ?: throw XMTPException("No client")

        subscriptions[getMessagesKey(inboxId)]?.cancel()
        subscriptions[getMessagesKey(inboxId)] = CoroutineScope(Dispatchers.IO).launch {
            client.conversations.streamAllMessages(includeGroups = includeGroups)
                .retry(5) { e ->
                    Log.d(TAG, "subscribeToAllMessages error")
                    (e is Exception).also {
                        if (it) {
                            e.printStackTrace()
                            delay(1000 * 10)
                        }
                    }
                }
                .catch { e ->
                    Log.d(TAG, "subscribeToAllGroupMessages error")
                    e.printStackTrace()
                }
                .collect { message ->
                    val contentM: Map<String, Any> = ContentJson(message.encodedContent).toJsonMap()
                    Log.d(TAG, "subscribeToMessages")
                    val dm = IMDecryptedMessage(
                        id = message.id,
                        topic = message.topic,
                        contentTypeId = message.encodedContent.type.description,
                        content = contentM.toNullableMap(),
                        senderAddress = message.senderAddress,
                        sent = message.sent.time,
                        deliveryStatus = message.deliveryStatus.toString()
                    )

                    imFlutterApiImpl.onSubscribeToAllMessages(inboxId, dm) {}

                }
        }
    }

    fun unsubscribeFromAllMessages(inboxId: String) {
        logV("unsubscribeFromAllMessages")
        subscriptions[getMessagesKey(inboxId)]?.cancel()
    }


    private fun getMessagesKey(inboxId: String): String {
        return "messages:$inboxId"
    }

    //Group

    private fun findGroup(
        inboxId: String,
        id: String,
    ): Group? {
        val client = clients[inboxId] ?: throw XMTPException("No client")

        val cacheKey = "${inboxId}:${id}"
        val cacheGroup = groups[cacheKey]
        if (cacheGroup != null) {
            return cacheGroup
        } else {
            val group = client.findGroup(id)
            if (group != null) {
                groups[group.cacheKey(inboxId)] = group
                return group
            }
        }
        return null
    }

    fun Group.cacheKey(inboxId: String): String {
        return "${inboxId}:${id}"
    }

    private fun getGroupMessagesKey(inboxId: String): String {
        return "groupMessages:$inboxId"
    }

    private fun getGroupsKey(inboxId: String): String {
        return "groups:$inboxId"
    }

    private suspend fun getPermissionOption(permissionString: String): PermissionOption {
        return when (permissionString) {
            "allow" -> PermissionOption.Allow
            "deny" -> PermissionOption.Deny
            "admin" -> PermissionOption.Admin
            "super_admin" -> PermissionOption.SuperAdmin
            else -> throw XMTPException("Invalid permission option: $permissionString")
        }
    }


    /**
     * 创建群
     */
    suspend fun createGroup(
        inboxId: String, peerAddresses: List<String>, permission: String, groupOptionsJson: String
    ): ImGroup {
        logV("createGroup")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val permissionLevel = when (permission) {
            "admin_only" -> GroupPermissionPreconfiguration.ADMIN_ONLY
            else -> GroupPermissionPreconfiguration.ALL_MEMBERS
        }
        val createGroupParams =
            CreateGroupParamsWrapper.createGroupParamsFromJson(groupOptionsJson)
        val group = client.conversations.newGroup(
            peerAddresses,
            permissionLevel,
            createGroupParams.groupName,
            createGroupParams.groupImageUrlSquare,
            createGroupParams.groupDescription,
            createGroupParams.groupPinnedFrameUrl
        )

        val imGroupInfo = group.let {
            val list = group.members().map {
                val permissionString = when (it.permissionLevel) {
                    PermissionLevel.MEMBER -> "member"
                    PermissionLevel.ADMIN -> "admin"
                    PermissionLevel.SUPER_ADMIN -> "super_admin"
                }
                ImMember(
                    inboxId = it.inboxId,
                    addresses = it.addresses,
                    permissionLevel = permissionString
                )
            }.toList()
            val imGroup = ImGroup(
                members = list,
                clientAddress = client.address,
                id = group.id,
                createdAt = group.createdAt.time,
                version = "GROUP",
                topic = group.topic,
                creatorInboxId = group.creatorInboxId(),
                isActive = group.isActive(),
                addedByInboxId = group.addedByInboxId(),
                name = group.name,
                imageUrlSquare = group.imageUrlSquare,
                description = group.description
            )
            imGroup
        }
        return imGroupInfo
    }

    suspend fun findGroupInfo(inboxId: String, groupId: String): ImGroup? {
        logV("findGroup")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = client.findGroup(groupId)

        val imGroupInfo = group?.let {
            val list = group.members().map {
                val permissionString = when (it.permissionLevel) {
                    PermissionLevel.MEMBER -> "member"
                    PermissionLevel.ADMIN -> "admin"
                    PermissionLevel.SUPER_ADMIN -> "super_admin"
                }
                ImMember(
                    inboxId = it.inboxId,
                    addresses = it.addresses,
                    permissionLevel = permissionString
                )
            }.toList()
            val imGroup = ImGroup(
                members = list,
                clientAddress = client.address,
                id = groupId,
                createdAt = group.createdAt.time,
                version = "GROUP",
                topic = group.topic,
                creatorInboxId = group.creatorInboxId(),
                isActive = group.isActive(),
                addedByInboxId = group.addedByInboxId(),
                name = group.name,
                imageUrlSquare = group.imageUrlSquare,
                description = group.description
            )
            imGroup
        }

        return imGroupInfo

    }


    fun groupMessages(
        inboxId: String,
        id: String,
        limit: Int?,
        before: Long?,
        after: Long?,
        direction: String?,
        deliveryStatus: String?
    ): List<IMDecryptedMessage> {
        logV("groupMessages")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val beforeDate = if (before != null) Date(before) else null
        val afterDate = if (after != null) Date(after) else null
        val group = findGroup(inboxId, id)
        val list = group?.decryptedMessages(
            limit = limit,
            before = beforeDate,
            after = afterDate,
            direction = MessageApiOuterClass.SortDirection.valueOf(
                direction ?: "SORT_DIRECTION_DESCENDING"
            ),
            deliveryStatus = MessageDeliveryStatus.valueOf(
                deliveryStatus ?: "ALL"
            )
        )?.map {
            val contentM = DecodedMessageWrapper.encodeMap(it)
            val dm = IMDecryptedMessage(
                id = it.id,
                topic = it.topic,
                contentTypeId = it.encodedContent.type.description,
                content = contentM.toNullableMap(),
                senderAddress = it.senderAddress,
                sent = it.sentAt.time,
                deliveryStatus = it.deliveryStatus.toString()
            )
            dm;
        }?.toList()
        return list ?: emptyList()
    }


    fun subscribeToAllGroupMessages(inboxId: String) {
        val client = clients[inboxId] ?: throw XMTPException("No client")

        subscriptions[getGroupMessagesKey(inboxId)]?.cancel()
        subscriptions[getGroupMessagesKey(inboxId)] = CoroutineScope(Dispatchers.IO).launch {
            try {
                client.conversations.streamAllGroupMessages()
                    .retry(5) { e ->
                        Log.d(TAG, "subscribeToAllGroupMessages error")
                        (e is Exception).also {
                            if (it) {
                                e.printStackTrace()
                                delay(1000 * 10)
                            }
                        }
                    }
                    .catch { e ->
                        Log.d(TAG, "subscribeToAllGroupMessages error")
                        e.printStackTrace()
                    }
                    .collect { message ->
                    val contentM: Map<String, Any> = ContentJson(message.encodedContent).toJsonMap()
                    val dm = IMDecryptedMessage(
                        id = message.id,
                        topic = message.topic,
                        contentTypeId = message.encodedContent.type.description,
                        content = contentM.toNullableMap(),
                        senderAddress = message.senderAddress,
                        sent = message.sent.time,
                        deliveryStatus = message.deliveryStatus.toString()
                    )

                    imFlutterApiImpl.onSubscribeToMessages(inboxId, dm)
                }
            } catch (e: Exception) {
                Log.e("XMTPModule", "Error in all group messages subscription: $e")
                subscriptions[getGroupMessagesKey(inboxId)]?.cancel()
            }
        }
    }

    fun unsubscribeToAllGroupMessages(inboxId: String) {
        val client = clients[inboxId] ?: throw XMTPException("No client")
        subscriptions[getGroupMessagesKey(inboxId)]?.cancel()
    }


    suspend fun allowGroups(inboxId: String, groupIds: List<String>) {
        logV("allowGroups")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        client.contacts.allowGroups(groupIds)
    }

    suspend fun denyGroups(inboxId: String, groupIds: List<String>) {
        logV("denyGroups")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        client.contacts.denyGroups(groupIds)
    }

    suspend fun isGroupAllowed(inboxId: String, groupId: String): Boolean {
        logV("isGroupAllowed")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        return client.contacts.isGroupAllowed(groupId)
    }

    suspend fun isGroupDenied(inboxId: String, groupId: String): Boolean {
        logV("isGroupDenied")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        return client.contacts.isGroupDenied(groupId)
    }

    suspend fun conversationConsentState(inboxId: String, conversationTopic: String): String {
        val conversation = findConversation(inboxId, conversationTopic)
            ?: throw XMTPException("no conversation found for $conversationTopic")
        return consentStateToString(conversation.consentState())
    }

    suspend fun groupConsentState(inboxId: String, groupId: String): String {
        val group = findGroup(inboxId, groupId)
            ?: throw XMTPException("no group found for $groupId")
        return consentStateToString(group.consentState())
    }


    suspend fun listGroupMembers(inboxId: String, groupId: String): List<String>? {
        logV("listGroupMembers")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, groupId)
        val list = group?.members()?.map { MemberWrapper.encode(it) }
        return list
    }

    suspend fun syncGroups(inboxId: String) {
        logV("syncGroups")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        client.conversations.syncGroups()
    }

    suspend fun syncGroup(inboxId: String, id: String) {
        logV("syncGroup")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        group?.sync()
    }

    suspend fun syncAllGroups(inboxId: String) {
        logV("syncAllGroups")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        client.conversations.syncAllGroups()
        // Expo Modules do not support UInt, so we need to convert to Int
        val numGroupsSyncedInt: Int = client.conversations.syncAllGroups()?.toInt()
            ?: throw IllegalArgumentException("Value cannot be null")
        numGroupsSyncedInt
    }

    suspend fun addGroupMembers(inboxId: String, id: String, peerAddresses: List<String>) {
        logV("addGroupMembers")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)

        group?.addMembers(peerAddresses)
    }

    suspend fun removeGroupMembers(inboxId: String, id: String, peerAddresses: List<String>) {
        logV("removeGroupMembers")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)

        group?.removeMembers(peerAddresses)
    }

    fun groupName(inboxId: String, id: String): String? {
        logV("groupName")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        return group?.name
    }

    suspend fun updateGroupName(inboxId: String, id: String, groupName: String) {
        logV("updateGroupName")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        group?.updateGroupName(groupName)
    }

    fun groupImageUrlSquare(inboxId: String, id: String) {
        logV("groupImageUrlSquare")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        group?.imageUrlSquare
    }

    suspend fun updateGroupImageUrlSquare(inboxId: String, id: String, groupImageUrl: String) {
        logV("updateGroupImageUrlSquare")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)

        group?.updateGroupImageUrlSquare(groupImageUrl)
    }

    fun groupDescription(inboxId: String, id: String): String? {
        logV("groupDescription")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        return group?.description
    }

    suspend fun updateGroupDescription(inboxId: String, id: String, groupDescription: String) {
        logV("updateGroupDescription")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        group?.updateGroupDescription(groupDescription)
    }


    fun groupPinnedFrameUrl(inboxId: String, id: String): String? {
        logV("groupPinnedFrameUrl")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        return group?.pinnedFrameUrl
    }

    suspend fun updateGroupPinnedFrameUrl(inboxId: String, id: String, pinnedFrameUrl: String) {
        logV("updateGroupPinnedFrameUrl")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        group?.updateGroupPinnedFrameUrl(pinnedFrameUrl)
    }


    fun isGroupActive(inboxId: String, id: String): Boolean? {
        logV("isGroupActive")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)

        return group?.isActive()
    }

    fun addedByInboxId(inboxId: String, id: String): String {
        logV("addedByInboxId")
        val group = findGroup(inboxId, id) ?: throw XMTPException("No group found")
        return group.addedByInboxId()
    }

    fun creatorInboxId(inboxId: String, id: String): String? {
        logV("creatorInboxId")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        return group?.creatorInboxId()
    }

    fun isAdmin(clientInboxId: String, id: String, inboxId: String): Boolean? {
        logV("isGroupAdmin")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        return group?.isAdmin(inboxId)
    }

    fun isSuperAdmin(clientInboxId: String, id: String, inboxId: String): Boolean? {
        logV("isSuperAdmin")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        return group?.isSuperAdmin(inboxId)
    }

    suspend fun listAdmins(inboxId: String, id: String): List<String>? {
        logV("listAdmins")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)

        return group?.listAdmins()
    }

    suspend fun listSuperAdmins(inboxId: String, id: String): List<String>? {
        logV("listSuperAdmins")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)
        return group?.listSuperAdmins()
    }

    suspend fun addAdmin(clientInboxId: String, id: String, inboxId: String) {
        logV("addAdmin")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)
        group?.addAdmin(inboxId)
    }

    suspend fun addSuperAdmin(clientInboxId: String, id: String, inboxId: String) {
        logV("addSuperAdmin")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)
        group?.addSuperAdmin(inboxId)
    }

    suspend fun removeAdmin(clientInboxId: String, id: String, inboxId: String) {
        logV("removeAdmin")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)
        group?.removeAdmin(inboxId)
    }

    suspend fun removeSuperAdmin(clientInboxId: String, id: String, inboxId: String) {
        logV("removeSuperAdmin")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)
        group?.removeSuperAdmin(inboxId)
    }

    suspend fun updateAddMemberPermission(
        clientInboxId: String,
        id: String,
        newPermission: String
    ) {
        logV("updateAddMemberPermission")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        group?.updateAddMemberPermission(getPermissionOption(newPermission))
    }

    suspend fun updateRemoveMemberPermission(
        clientInboxId: String,
        id: String,
        newPermission: String
    ) {
        logV("updateRemoveMemberPermission")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        group?.updateRemoveMemberPermission(getPermissionOption(newPermission))
    }

    suspend fun updateAddAdminPermission(clientInboxId: String, id: String, newPermission: String) {
        logV("updateAddAdminPermission")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        group?.updateAddAdminPermission(getPermissionOption(newPermission))
    }

    suspend fun updateRemoveAdminPermission(
        clientInboxId: String,
        id: String,
        newPermission: String
    ) {
        logV("updateRemoveAdminPermission")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        group?.updateRemoveAdminPermission(getPermissionOption(newPermission))
    }

    suspend fun updateGroupNamePermission(
        clientInboxId: String,
        id: String,
        newPermission: String
    ) {
        logV("updateGroupNamePermission")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        group?.updateGroupNamePermission(getPermissionOption(newPermission))

    }

    suspend fun updateGroupImageUrlSquarePermission(
        clientInboxId: String,
        id: String,
        newPermission: String
    ) {
        logV("updateGroupImageUrlSquarePermission")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        group?.updateGroupImageUrlSquarePermission(getPermissionOption(newPermission))
    }

    suspend fun updateGroupDescriptionPermission(
        clientInboxId: String,
        id: String,
        newPermission: String
    ) {
        logV("updateGroupDescriptionPermission")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        group?.updateGroupDescriptionPermission(getPermissionOption(newPermission))
    }

    suspend fun updateGroupPinnedFrameUrlPermission(
        clientInboxId: String,
        id: String,
        newPermission: String
    ) {
        logV("updateGroupPinnedFrameUrlPermission")
        val client = clients[clientInboxId] ?: throw XMTPException("No client")
        val group = findGroup(clientInboxId, id)

        group?.updateGroupPinnedFrameUrlPermission(getPermissionOption(newPermission))
    }

    fun permissionPolicySet(inboxId: String, id: String) {
        logV("groupImageUrlSquare")
        val client = clients[inboxId] ?: throw XMTPException("No client")
        val group = findGroup(inboxId, id)

        val permissionPolicySet = group?.permissionPolicySet()
        if (permissionPolicySet != null) {
            PermissionPolicySetWrapper.encodeToJsonString(permissionPolicySet)
        } else {
            throw XMTPException("Permission policy set not found for group: $id")
        }

    }


}


fun <K, V> Map<K, V>.toNullableMap(): Map<K?, V?> {
    return this.mapKeys { it.key }
        .mapValues { it.value }
}