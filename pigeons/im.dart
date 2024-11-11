import 'package:pigeon/pigeon.dart';

//dart run pigeon --input pigeons/im.dart
// https://github.com/flutter/packages/blob/main/packages/pigeon/example/README.md
@ConfigurePigeon(PigeonOptions(
  dartOut: 'lib/plugin/halo_im_pigeon.g.dart',
  kotlinOut: 'android/src/main/kotlin/halo/social/halo_im/gen/HaloIM.g.kt',
  // kotlinOptions: KotlinOptions(
  //   // https://github.com/fluttercommunity/wakelock_plus/issues/18
  //   errorClassName: "ImFlutterError",
  // ),
  swiftOut: 'ios/Classes/gen/HaloIM.g.swift',
))
enum Env { local, dev, production }

class ClientData {
  ClientData({required this.inboxID, required this.address, required this.installationID, required this.dbPath});

  String inboxID;
  String address;
  String installationID;
  String dbPath;
}

@HostApi()
abstract class IMHostApi {
  ///用于测试
  void abc();

  @async
  String createFromKeyBundle(
      {required String keyBundle,
      required String environment,
      String? appVersion,
      bool? hasCreateIdentityCallback,
      bool? hasEnableIdentityCallback,
      bool? hasPreAuthenticateToInboxCallback,
      bool? enableV3,
      Uint8List? dbEncryptionKey,
      String? dbDirectory,
      String? historySyncUrl});

  @async
  String auth(
      {required String address,
      required String environment,
      String? appVersion,
      bool? hasCreateIdentityCallback,
      bool? hasEnableIdentityCallback,
      bool? hasPreAuthenticateToInboxCallback,
      bool? enableV3,
      Uint8List? dbEncryptionKey,
      String? dbDirectory,
      String? historySyncUrl});

  ///创建会话
  @async
  IMConversation createConversation(String inboxId, String address);

  ///发送消息
  ///TODO 目前只支持文字类型，其他类型还未支持
  @async
  String sendMessage(String inboxId, String topic, String body);

  ///订阅新的会话
  void subscribeToConversations(String inboxId);

  ///取消订阅新的会话
  void unsubscribeToConversations(String inboxId);

  ///订阅会话新消息
  void subscribeToMessages(String inboxId, String topic);

  ///取消订阅会话新消息
  void unsubscribeToMessages(String inboxId, String topic);

  ///订阅所有会话新消息
  void subscribeToAllMessages(String inboxId);

  ///取消订阅所有会话新消息
  void unsubscribeToAllMessages(String inboxId);

  ///订阅所有群新消息
  void subscribeToAllGroupMessages(String inboxId);

  ///取消订阅所有群新消息
  void unsubscribeToAllGroupMessages(String inboxId);

  ///是否可以与[address]发消息
  @async
  bool canMessage(String inboxId, String address);

  ///[address]是否激活
  @async
  bool staticCanMessage(String address, String appVersion);

  ///获取会话列表
  @async
  List<IMConversation> conversationList(String inboxId);

  ///获取会话历史消息
  @async
  List<IMDecryptedMessage> loadMessages(
      {required String inboxId,
      required String topic,
      int? limit = 20,
      int? before,
      int? after,
      String? direction = 'SORT_DIRECTION_DESCENDING'});

  @async
  List<IMDecryptedMessage> loadBatchMessages({
    required String inboxId,
    required List<IMMessageReq> topics,
    String? direction = 'SORT_DIRECTION_DESCENDING',
  });

  @async
  void allowContacts(String inboxId, List<String> addresses);

  @async
  void denyContacts(String inboxId, List<String> addresses);

  @async
  bool isAllowed(String inboxId, String address);

  @async
  bool isDenied(String inboxId, String address);

  @async
  void allowGroups(String inboxId, List<String> groupIds);

  @async
  void denyGroups(String inboxId, List<String> groupIds);

  @async
  bool isGroupAllowed(String inboxId, String groupId);

  @async
  bool isGroupDenied(String inboxId, String groupId);

  void refreshConsentList(String inboxId);

  ///创建注册push
  ///[token] device id
  ///返回 installationId
  @async
  String registerPushToken(String pushServer, String token);

  ///订阅推送Topics
  void subscribePushTopics(String inboxId, List<String> topics);

  ///订阅推送Topics
  void subscribePushWithMetadata(String inboxId, List<String> topics);

  @async
  List<IMPushWithMetadata> getPushWithMetadata(String inboxId, List<String> topics);

  ///注销推送，注销或切换帐户时使用
  ///PS：删除installationId
  void deleteInstallationPush();

  ///解密消息（用于推送接收等场景）
  @async
  IMDecryptedMessage decodeMessage(String inboxId, String topic, String encryptedMessage);

  ///静音（取消订阅推送Topics）
  void unsubscribePushTopics(List<String> topics);

  void receiveSignature(String requestId, String signature);

  @async
  String exportKeyBundle(String inboxId);

  ///是否可以使用群聊
  @async
  Map<String, bool> canGroupMessage(String inboxId, List<String> peerAddresses);

  ///创建群
  @async
  ImGroup createGroup(String inboxId, List<String> peerAddresses, String permission, String groupOptionsJson);

  @async
  ImGroup? findGroup(String inboxId, String groupId);

  @async
  bool isAdmin(String clientInboxId, String groupId, String inboxId);

  @async
  bool isSuperAdmin(String clientInboxId, String groupId, String inboxId);

  @async
  void updateGroupName(String clientInboxId, String groupId, String groupName);

  @async
  void updateGroupImageUrlSquare(String clientInboxId, String groupId, String groupImageUrl);

  @async
  void syncGroup(String clientInboxId, String groupId);

  @async
  void addGroupMembers(String clientInboxId, String groupId, List<String> peerAddresses);

  @async
  void addAdmin({required String clientInboxId, required String groupId, required String inboxId});

  @async
  void removeAdmin({required String clientInboxId, required String groupId, required String inboxId});

  @async
  void removeGroupMembers({required String clientInboxId, required String groupId, required List<String> addresses});

  @async
  String conversationConsentState (String clientInboxId, String conversationTopic);

  @async
  String groupConsentState (String clientInboxId, String groupId);
}

@FlutterApi()
abstract class IMFlutterApi {
  ///用于测试
  void flutterApiTest(String test);

  ///新会话响应
  void onSubscribeToConversations(String inboxId, IMConversation conversation);

  ///会话新消息响应
  void onSubscribeToMessages(String inboxId, IMDecryptedMessage message);

  ///所有新消息响应
  void onSubscribeToAllMessages(String inboxId, IMDecryptedMessage message);

  ///消息签名
  void onSignMessage(String requestId, String message);
}

class IMDecryptedMessage {
  String id;
  String topic;
  String contentTypeId;
  Map<String?, Object?> content;
  String senderAddress;
  int sent;
  String deliveryStatus;

  IMDecryptedMessage(
      this.id, this.topic, this.contentTypeId, this.content, this.senderAddress, this.sent, this.deliveryStatus);
}

class IMConversation {
  String clientAddress;
  String topic;
  String peerAddress;
  String version;
  int createdAt;
  String conversationID;
  String keyMaterial;
  String consentProof;
  bool isAllowed;
  bool isDenied;

  //group
  String? groupName;
  String? groupDescription;
  String? groupId;
  String? groupIcon;

  IMConversation(this.clientAddress, this.topic, this.peerAddress, this.version, this.createdAt, this.conversationID,
      this.keyMaterial, this.consentProof, this.isAllowed, this.isDenied,
      {this.groupName, this.groupId, this.groupDescription, this.groupIcon});
}

class IMMessageReq {
  String topic;
  int? limit;
  int? before;
  int? after;
  IMMessageReq({required this.topic, this.limit = 20, this.before, this.after});
}

class IMPushWithMetadata {
  List<IMPushHMACKeys?> hmacKeys;
  String topic;

  IMPushWithMetadata(this.hmacKeys, this.topic);
}

class IMPushHMACKeys {
  String key;
  String key2;
  int thirtyDayPeriodsSinceEpoch;

  IMPushHMACKeys(this.key, this.key2, this.thirtyDayPeriodsSinceEpoch);
}

class ImGroup {
  String clientAddress;
  String id;
  int createdAt;
  String version;
  String topic;
  String creatorInboxId;
  bool isActive;
  String addedByInboxId;
  String name;
  String imageUrlSquare;
  String description;
  List<ImMember?> members;

  ImGroup(this.clientAddress, this.id, this.createdAt, this.version, this.topic, this.creatorInboxId, this.isActive,
      this.addedByInboxId, this.name, this.imageUrlSquare, this.description, this.members);
}

class ImMember {
  String inboxId;
  List<String?> addresses;

  ///[member],[admin],[super_admin]
  String permissionLevel;

  ImMember(this.inboxId, this.addresses, this.permissionLevel);
}
