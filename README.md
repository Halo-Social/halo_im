# halo_im

A new Flutter project with XMTP

## Getting Started

### create client

```
var client = await IMHostApi().auth(
          address: eip55Address,
          environment: "production",
          appVersion: appVersion,
          enableV3: true,
          dbEncryptionKey: Uint8List.fromList(key),
        );
        var keyBundle = await IMHostApi().exportKeyBundle(v); //save keyBundle

```
### create client by KeyBundle
```
var client = await IMHostApi().createFromKeyBundle(
          keyBundle: savedKeyBundle!,
          environment: "production",
          appVersion: appVersion,
          enableV3: true,
          dbEncryptionKey: Uint8List.fromList(key),
        );
```

### create client

### getAllConversations

```
 List<IMConversation?> list = await IMHostApi().conversationList(inboxId)
```

### updatelMessages
```
List<IMDecryptedMessage?> msgList = await IMHostApi().loadBatchMessages(
      inboxId: inboxInfo.inbox,
      topics: topics,
      direction: "SORT_DIRECTION_ASCENDING",
    );
```
### updatelGroupMessages
```
IMHostApi().loadMessages(
          inboxId: inboxInfo.inbox,
          topic: topic,
          limit: 100,
          direction: "SORT_DIRECTION_ASCENDING",
          after: (lastSyncTime ?? conv.createdAt),
          before: DateTime.now().millisecondsSinceEpoch,
        )
```

### subscribeAllMessages
```
IMHostApi().subscribeToAllMessages(curInboxId);
```

### subscribeAllGroupMessages
```
IMHostApi().subscribeToAllGroupMessages(curInboxId);
```

### subscribeNewConversion
```
IMHostApi().subscribeToConversations(curInboxId);
```