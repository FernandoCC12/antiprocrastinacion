import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'app.dart';
import 'core/services/storage.dart';
import 'core/services/native_bridge.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  FlutterError.onError = (FlutterErrorDetails details) {
    FlutterError.presentError(details);
  };
  PlatformDispatcher.instance.onError = (error, stack) {
    debugPrint('Error de plataforma: $error\n$stack');
    return true;
  };

  await Hive.initFlutter();
  await StorageService.init();

  // Sincronizar apps permitidas al nativo y auto-iniciar tracking
  await NativeBridge.syncAllowedAppsToNative();
  final distractingApps = StorageService.getDistractingApps();
  if (distractingApps.isNotEmpty) {
    await NativeBridge.startTracking();
  }

  runApp(const ProviderScope(child: FocusBlockerApp()));
}