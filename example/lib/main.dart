import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:halo_im/plugin/halo_im_pigeon.g.dart';

void main() {
  runApp(MaterialApp(navigatorKey: GlobalKey<NavigatorState>(), home: const MyApp()));
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Plugin example app'),
      ),
      body: Center(
        child: InkWell(
          onTap: () async {
            // var demo = await ExampleHostApi().add(1, 21);
            // print('ExampleHostApi - $demo');
            // var demo2 = await ExampleHostApi().getHostLanguage();
            // print('ExampleHostApi2 - $demo2');
            Uint8List dbEncryptionKey = Uint8List.fromList([
              233,
              120,
              198,
              96,
              154,
              65,
              132,
              17,
              132,
              96,
              250,
              40,
              103,
              35,
              125,
              64,
              166,
              83,
              208,
              224,
              254,
              44,
              205,
              227,
              175,
              49,
              234,
              129,
              74,
              252,
              135,
              145
            ]);
            var demo3 = await IMHostApi().createFromPrivateKey(
                '0x23d39760b3bf25f674ff11bcb3d849f9f8583a5b564b6ea1faab793101a0bbe6',
                Env.dev.name,
                'XMTP_RN_EX/0.0.1',
                true,
                true,
                true,
                true,
                dbEncryptionKey,
                null,
                null);
            print('ExampleHostApi3 - ${demo3.address} ${demo3.inboxID} ${demo3.installationID}');
            // Navigator.push(context, MaterialPageRoute(builder: (context) => const Conversations()));
          },
          child: Text('conversations'),
        ),
      ),
    );
  }
}
