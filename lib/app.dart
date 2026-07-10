import 'package:flutter/material.dart';
import 'core/theme.dart';
import 'features/dashboard/dashboard_screen.dart';

class FocusBlockerApp extends StatelessWidget {
  const FocusBlockerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Focus Blocker',
      debugShowCheckedModeBanner: false,
      theme: warmMelonTheme,
      home: const DashboardScreen(),
    );
  }
}