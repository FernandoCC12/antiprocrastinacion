import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/services/native_bridge.dart';
import '../../core/services/storage.dart';

final distractingAppsProvider =
    StateNotifierProvider<AppListNotifier, List<String>>((ref) {
  return AppListNotifier(
    initial: StorageService.getDistractingApps(),
    onSave: StorageService.setDistractingApps,
  );
});

final allowedAppsProvider =
    StateNotifierProvider<AppListNotifier, List<String>>((ref) {
  return AppListNotifier(
    initial: StorageService.getAllowedApps(),
    onSave: StorageService.setAllowedApps,
  );
});

class AppListNotifier extends StateNotifier<List<String>> {
  final void Function(List<String>) onSave;
  AppListNotifier({required List<String> initial, required this.onSave})
      : super(initial);

  void toggle(String package) {
    if (state.contains(package)) {
      state = state.where((p) => p != package).toList();
    } else {
      state = [...state, package];
    }
    onSave(state);

    // Sincronizar con el lado nativo y arrancar el tracking si hay distractoras
    if (onSave == StorageService.setDistractingApps) {
      NativeBridge.updateDistractingApps(state);
      if (state.isNotEmpty) {
        NativeBridge.startTracking();
      }
    } else if (onSave == StorageService.setAllowedApps) {
      NativeBridge.updateAllowedApps(state);
    }
  }
}