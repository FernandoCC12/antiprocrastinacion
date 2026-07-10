import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/services/native_bridge.dart';
import '../../core/services/session_manager.dart';
import '../../core/services/storage.dart';

class DashboardState {
  final int accumulatedSeconds;
  final bool isBlocked;
  final bool concentrationActive;
  final int blockRemainingSeconds;
  const DashboardState({
    required this.accumulatedSeconds,
    this.isBlocked = false,
    this.concentrationActive = false,
    this.blockRemainingSeconds = 0,
  });
}

final dashboardProvider =
    StateNotifierProvider<DashboardNotifier, DashboardState>((ref) {
  return DashboardNotifier(ref);
});

class DashboardNotifier extends StateNotifier<DashboardState> {
  Timer? _pollTimer;
  final Ref _ref;

  DashboardNotifier(this._ref)
      : super(const DashboardState(accumulatedSeconds: 0)) {
    _startPolling();
  }

  void _startPolling() {
    _pollTimer = Timer.periodic(const Duration(seconds: 1), (_) async {
      try {
        final data = await NativeBridge.getTrackingState();
        if (!mounted) return;
        final seconds = (data['accumulatedSeconds'] as int?) ?? 0;
        final blocked = (data['isBlocked'] as bool?) ?? false;
        final concentrationActive =
            (data['concentrationActive'] as bool?) ?? false;
        final blockRemaining = (data['blockRemainingSeconds'] as int?) ?? 0;

        final prevConcActive = state.concentrationActive;
        state = DashboardState(
          accumulatedSeconds: seconds,
          isBlocked: blocked,
          concentrationActive: concentrationActive,
          blockRemainingSeconds: blockRemaining,
        );

        // Si el modo concentración se desactivó externamente (cancel desde overlay)
        // y el SessionManager aún está activo → cancelar la sesión Dart
        if (prevConcActive && !concentrationActive) {
          final sm = _ref.read(sessionManagerProvider);
          if (sm != null && sm.phase != StudyPhase.finished) {
            _ref.read(sessionManagerProvider.notifier).cancel();
            await NativeBridge.resumeTracking();
          }
        }
      } catch (_) {}
    });
  }

  Future<void> startStudyMode(int totalMinutes) async {
    await NativeBridge.pauseTracking();
    _ref.read(sessionManagerProvider.notifier).startSession(totalMinutes);
  }

  Future<void> returnFromStudyMode() async {
    await NativeBridge.resumeTracking();
  }

  Future<void> ensureTracking() async {
    final distracting = StorageService.getDistractingApps();
    if (distracting.isNotEmpty) {
      await NativeBridge.startTracking();
    }
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }
}
