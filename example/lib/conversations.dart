import 'package:flutter/material.dart';

class Conversations extends StatefulWidget {
  const Conversations({super.key});

  @override
  State<StatefulWidget> createState() => _ConversationsState();
}

class _ConversationsState extends State<Conversations> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Conversations'),
      ),
      body: ListView.builder(
          itemCount: 20,
          itemBuilder: (context, index) {
            return Text(index.toString());
          }),
    );
  }
}
