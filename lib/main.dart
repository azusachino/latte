import 'package:flutter/material.dart';

void main() {
  runApp(const LatteApp());
}

class LatteApp extends StatelessWidget {
  const LatteApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Latte',
      theme: ThemeData(colorScheme: .fromSeed(seedColor: Colors.deepPurple)),
      home: const Scaffold(body: Center(child: Text('Latte'))),
    );
  }
}
