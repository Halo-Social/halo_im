package halo.social.halo_im.api

import FlutterError
import IMConversation
import IMDecryptedMessage
import IMHostApi
import IMMessageReq
import IMPushWithMetadata
import ImGroup
import android.content.Context
import android.util.Log
import halo.social.halo_im.xmtp.HaloImManger
import io.flutter.embedding.engine.plugins.FlutterPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class IMHostApiImpl(
    private val flutterPluginBinding: FlutterPlugin.FlutterPluginBinding,
    context: Context
) : IMHostApi {

    private val haloImManger = HaloImManger(context)

    override fun abc() {
        IMFlutterApiImpl().flutterTest("${Date()}")
    }

    override fun createFromKeyBundle(
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
        callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inboxId = haloImManger.createFromKeyBundle(
                    keyBundle,
                    environment,
                    appVersion,
                    hasCreateIdentityCallback,
                    hasEnableIdentityCallback,
                    hasPreAuthenticateToInboxCallback,
                    enableV3 = enableV3,
                    dbEncryptionKey = dbEncryptionKey,
                    dbDirectory = dbDirectory,
                    historySyncUrl = historySyncUrl,
                )
                callback(Result.success(inboxId))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }

        }
    }

    override fun exportKeyBundle(inboxId: String, callback: (Result<String>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val keyBundle = haloImManger.exportKeyBundle(inboxId)
                callback(Result.success(keyBundle))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun canGroupMessage(
        inboxId: String,
        peerAddresses: List<String>,
        callback: (Result<Map<String, Boolean>>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.canGroupMessage(inboxId, peerAddresses)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }

    }

    override fun createGroup(
        inboxId: String,
        peerAddresses: List<String>,
        permission: String,
        groupOptionsJson: String,
        callback: (Result<ImGroup>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result =
                    haloImManger.createGroup(inboxId, peerAddresses, permission, groupOptionsJson)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun findGroup(inboxId: String, groupId: String, callback: (Result<ImGroup?>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.findGroupInfo(
                    inboxId = inboxId,
                    groupId = groupId,
                )
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun isAdmin(
        clientInboxId: String,
        groupId: String,
        inboxId: String,
        callback: (Result<Boolean>) -> Unit
    ) {
        val result = haloImManger.isAdmin(clientInboxId, groupId, inboxId)
        callback(Result.success(result ?: false))
    }

    override fun isSuperAdmin(
        clientInboxId: String,
        groupId: String,
        inboxId: String,
        callback: (Result<Boolean>) -> Unit
    ) {
        val result = haloImManger.isSuperAdmin(clientInboxId, groupId, inboxId)
        callback(Result.success(result ?: false))
    }

    override fun updateGroupName(
        clientInboxId: String,
        groupId: String,
        groupName: String,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.updateGroupName(
                    inboxId = clientInboxId,
                    id = groupId,
                    groupName = groupName
                )
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun updateGroupImageUrlSquare(
        clientInboxId: String,
        groupId: String,
        groupImageUrl: String,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.updateGroupImageUrlSquare(
                    inboxId = clientInboxId,
                    id = groupId,
                    groupImageUrl = groupImageUrl
                )
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun syncGroup(
        clientInboxId: String,
        groupId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.syncGroup(inboxId = clientInboxId, id = groupId)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun addGroupMembers(
        clientInboxId: String,
        groupId: String,
        peerAddresses: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.addGroupMembers(
                    inboxId = clientInboxId,
                    id = groupId,
                    peerAddresses = peerAddresses
                )
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun addAdmin(
        clientInboxId: String,
        groupId: String,
        inboxId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.addAdmin(clientInboxId, id = groupId, inboxId = inboxId)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun removeAdmin(
        clientInboxId: String,
        groupId: String,
        inboxId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.removeAdmin(clientInboxId, id = groupId, inboxId = inboxId)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun removeGroupMembers(
        clientInboxId: String,
        groupId: String,
        addresses: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.removeGroupMembers(clientInboxId, id = groupId, addresses)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun conversationConsentState(
        clientInboxId: String,
        conversationTopic: String,
        callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.conversationConsentState(clientInboxId, conversationTopic)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun groupConsentState(
        clientInboxId: String,
        groupId: String,
        callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.groupConsentState(clientInboxId, groupId)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun auth(
        address: String,
        environment: String,
        appVersion: String?,
        hasCreateIdentityCallback: Boolean?,
        hasEnableIdentityCallback: Boolean?,
        hasPreAuthenticateToInboxCallback: Boolean?,
        enableV3: Boolean?,
        dbEncryptionKey: ByteArray?,
        dbDirectory: String?,
        historySyncUrl: String?,
        callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inboxId = haloImManger.auth(
                    address = address,
                    environment = environment,
                    appVersion = appVersion,
                    hasCreateIdentityCallback = hasCreateIdentityCallback,
                    hasEnableIdentityCallback = hasEnableIdentityCallback,
                    hasPreAuthenticateToInboxCallback = hasPreAuthenticateToInboxCallback,
                    enableV3 = enableV3,
                    dbEncryptionKey = dbEncryptionKey,
                    dbDirectory = dbDirectory,
                    historySyncUrl = historySyncUrl
                )
                withContext(Dispatchers.Main) {
                    callback(Result.success(inboxId))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(
                        Result.failure(
                            FlutterError(
                                "-1",
                                "${e.message}",
                                "${e.message}"
                            )
                        )
                    )
                }
            }
        }
    }

    override fun createConversation(
        inboxId: String,
        address: String,
        callback: (Result<IMConversation>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val topic = haloImManger.createConversation(inboxId, address)
                withContext(Dispatchers.Main) {
                    callback(Result.success(topic))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(
                        Result.failure(
                            FlutterError(
                                "-1",
                                "${e.message}",
                                "${e.message}"
                            )
                        )
                    )
                }
            }
        }

    }

    override fun sendMessage(
        inboxId: String,
        topic: String,
        body: String,
        callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val msgId = haloImManger.sendMessage(inboxId, conversationTopic = topic, body)
                withContext(Dispatchers.Main) {
                    callback(Result.success(msgId))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(
                        Result.failure(
                            FlutterError(
                                "-1",
                                "${e.message}",
                                "${e.message}"
                            )
                        )
                    )
                }
                e.printStackTrace()
            }
        }
    }

    override fun subscribeToConversations(inboxId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                haloImManger.subscribeToConversations(inboxId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun unsubscribeToConversations(inboxId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                haloImManger.unsubscribeFromConversations(inboxId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun subscribeToMessages(inboxId: String, topic: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.subscribeToMessages(inboxId, topic)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun unsubscribeToMessages(inboxId: String, topic: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.unsubscribeFromMessages(inboxId, topic)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun subscribeToAllMessages(inboxId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.subscribeToAllMessages(inboxId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun unsubscribeToAllMessages(inboxId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.unsubscribeFromAllMessages(inboxId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun subscribeToAllGroupMessages(inboxId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.subscribeToAllGroupMessages(inboxId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun unsubscribeToAllGroupMessages(inboxId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.unsubscribeToAllGroupMessages(inboxId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun loadMessages(
        inboxId: String,
        topic: String,
        limit: Long?,
        before: Long?,
        after: Long?,
        direction: String?,
        callback: (Result<List<IMDecryptedMessage>>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val list = haloImManger.loadMessages(
                    inboxId = inboxId,
                    topic = topic,
                    limit = limit?.toInt(),
                    before = before,
                    after = after,
                    direction
                )
                callback(Result.success(list))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun loadBatchMessages(
        inboxId: String,
        topics: List<IMMessageReq>,
        direction: String?,
        callback: (Result<List<IMDecryptedMessage>>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val list = haloImManger.loadBatchMessages(inboxId, topics, direction)
                callback(Result.success(list))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun allowContacts(
        inboxId: String,
        addresses: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.allowContacts(inboxId, addresses)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun denyContacts(
        inboxId: String,
        addresses: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.denyContacts(inboxId, addresses)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun isAllowed(inboxId: String, address: String, callback: (Result<Boolean>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.isAllowed(inboxId, address)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun isDenied(inboxId: String, address: String, callback: (Result<Boolean>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.isDenied(inboxId, address)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun allowGroups(
        inboxId: String,
        groupIds: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.allowGroups(inboxId, groupIds)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun denyGroups(
        inboxId: String,
        groupIds: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.denyGroups(inboxId, groupIds)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun isGroupAllowed(
        inboxId: String,
        groupId: String,
        callback: (Result<Boolean>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.isGroupAllowed(inboxId, groupId)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun isGroupDenied(
        inboxId: String,
        groupId: String,
        callback: (Result<Boolean>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.isGroupDenied(inboxId, groupId)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun refreshConsentList(inboxId: String) {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                haloImManger.refreshConsentList(inboxId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun registerPushToken(
        pushServer: String,
        token: String,
        callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.i(
                    "XMTPPush -",
                    "IMHostApiImpl #registerPushToken ${pushServer} #### ${token}"
                )
                val result = haloImManger.registerPushToken(pushServer, token) ?: ""
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

//    override fun registerPushToken(pushServer: String, token: String) {
//        CoroutineScope(Dispatchers.Main).launch {
//            try {
//                Log.i("XMTPPush -", "IMHostApiImpl #registerPushToken ${pushServer} #### ${token}")
//                haloImManger.registerPushToken(pushServer, token)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }

    override fun subscribePushTopics(inboxId: String, topics: List<String>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                haloImManger.subscribePushTopics(inboxId, topics)
            } catch (e: Exception) {
                //e.printStackTrace()
            }
        }
    }

    override fun subscribePushWithMetadata(inboxId: String, topics: List<String>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                haloImManger.subscribeWithMetadata(inboxId, topics)
            } catch (e: Exception) {
                //e.printStackTrace()
            }
        }
    }

    override fun getPushWithMetadata(
        inboxId: String,
        topics: List<String>,
        callback: (Result<List<IMPushWithMetadata>>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.getPushWithMetadata(inboxId, topics)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun deleteInstallationPush() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                haloImManger.deleteInstallation()
            } catch (e: Exception) {
                //e.printStackTrace()
            }
        }
    }

    override fun decodeMessage(
        inboxId: String,
        topic: String,
        encryptedMessage: String,
        callback: (Result<IMDecryptedMessage>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = haloImManger.decodeMessage(inboxId, topic, encryptedMessage)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

    override fun unsubscribePushTopics(topics: List<String>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                haloImManger.unsubscribePushTopics(topics)
            } catch (e: Exception) {
                //e.printStackTrace()
            }
        }
    }

    override fun receiveSignature(requestId: String, signature: String) {
        CoroutineScope(Dispatchers.IO).launch {
            haloImManger.receiveSignature(requestId, signature)
        }
    }

    override fun canMessage(
        inboxId: String,
        address: String,
        callback: (Result<Boolean>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val canMessage = haloImManger.canMessage(inboxId, address)
                callback(Result.success(canMessage))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun staticCanMessage(
        address: String,
        appVersion: String,
        callback: (Result<Boolean>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val canMessage = haloImManger.staticCanMessage(address, appVersion)
                callback(Result.success(canMessage))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
            }
        }
    }

    override fun conversationList(
        inboxId: String,
        callback: (Result<List<IMConversation>>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val list = haloImManger.listConversations(inboxId)
                callback(Result.success(list))
            } catch (e: Exception) {
                callback(
                    Result.failure(
                        FlutterError(
                            "-1",
                            "${e.message}",
                            "${e.message}"
                        )
                    )
                )
                e.printStackTrace()
            }
        }
    }

}