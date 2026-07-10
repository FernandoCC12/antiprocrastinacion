import 'package:flutter/services.dart';
import '../constants.dart';
import 'storage.dart';

class NativeBridge {
  static const _channel = MethodChannel(AppConstants.nativeChannel);

  static Future<void> syncAllowedAppsToNative() async {
    final apps = StorageService.getAllowedApps();
    await _channel.invokeMethod('updateAllowedApps', apps);
  }

  static Future<Map<String, dynamic>> getTrackingState() async {
    final result = await _channel.invokeMethod('getTrackingState');
    return Map<String, dynamic>.from(result ?? {});
  }

  static Future<void> startTracking() async {
    await syncAllowedAppsToNative();
    await _channel.invokeMethod('startTracking');
  }

  static Future<void> stopTracking() async {
    await _channel.invokeMethod('stopTracking');
  }

  static Future<void> pauseTracking() async {
    await _channel.invokeMethod('pauseTracking');
  }

  static Future<void> resumeTracking() async {
    await _channel.invokeMethod('resumeTracking');
  }

  static Future<List<Map<String, dynamic>>> getInstalledApps() async {
    final List<dynamic>? result = await _channel.invokeMethod('getInstalledApps');
    if (result == null) return [];
    return result.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  static Future<void> updateDistractingApps(List<String> apps) async {
    await _channel.invokeMethod('updateDistractingApps', apps);
  }

  static Future<void> updateAllowedApps(List<String> apps) async {
    await _channel.invokeMethod('updateAllowedApps', apps);
  }

  static Future<bool> hasUsageStatsPermission() async {
    final result = await _channel.invokeMethod('hasUsageStatsPermission');
    return result as bool? ?? false;
  }

  static Future<void> openUsageAccessSettings() async {
    await _channel.invokeMethod('openUsageAccessSettings');
  }

  static Future<bool> canDrawOverlays() async {
    final result = await _channel.invokeMethod('canDrawOverlays');
    return result as bool? ?? false;
  }

  static Future<void> openOverlaySettings() async {
    await _channel.invokeMethod('openOverlaySettings');
  }

  static Future<void> setConcentrationMode({
    required bool active,
    required String phase,
    required int currentBlock,
    required int totalBlocks,
    required int blockDurationSeconds,
    required double progress,
    String restRecommendation = '',
    List<String> allowedApps = const [],
  }) async {
    await _channel.invokeMethod('setConcentrationMode', {
      'active': active,
      'phase': phase,
      'currentBlock': currentBlock,
      'totalBlocks': totalBlocks,
      'blockDurationSeconds': blockDurationSeconds,
      'progress': progress,
      'restRecommendation': restRecommendation,
      'allowedApps': allowedApps,
    });
  }
}