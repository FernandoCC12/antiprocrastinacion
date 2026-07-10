import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../constants.dart';
import 'native_bridge.dart';
import 'storage.dart';

enum StudyPhase { studying, resting, finished }

class SessionState {
  final int totalDurationSeconds;
  final int remainingSeconds;
  final StudyPhase phase;
  final double progress;
  final int currentBlock;
  final int totalBlocks;
  final String? restRecommendation;

  const SessionState({
    required this.totalDurationSeconds,
    required this.remainingSeconds,
    required this.phase,
    required this.progress,
    required this.currentBlock,
    required this.totalBlocks,
    this.restRecommendation,
  });
}

final sessionManagerProvider =
    StateNotifierProvider<SessionManager, SessionState?>((ref) {
  return SessionManager();
});

class SessionManager extends StateNotifier<SessionState?> {
  Timer? _timer;
  int _studySecondsLeft = 0;
  int _restSecondsLeft = 0;
  int _totalStudySeconds = 0;
  int _blockCount = 0;
  int _currentBlock = 0;
  int _currentBlockDuration = 0;
  String _restRecommendation = '';
  List<String> _allowedApps = [];

  static const _restMessages = [
    'Respira profundo 5 veces 🧘',
    'Toma un vaso con agua 💧',
    'Estira brazos y cuello 🙆',
    'Camina un par de minutos 🚶',
    'Mira por la ventana 2 min 🌳',
    'Haz 5 sentadillas 🏋️',
    'Cierra los ojos 30 seg 😌',
    'Rueda los hombros 🔄',
    'Aprieta y suelta los puños 🤲',
    'Haz una respiración diafragmática 🌬️',
  ];

  SessionManager() : super(null);

  void startSession(int totalMinutes) {
    final totalSeconds = totalMinutes * 60;
    final restSeconds = (totalSeconds * AppConstants.studyRestRatio).round();
    final studySeconds = totalSeconds - restSeconds;

    _blockCount = (studySeconds / AppConstants.maxStudyBlock.inSeconds).ceil();
    _studySecondsLeft = studySeconds;
    _restSecondsLeft = restSeconds;
    _totalStudySeconds = studySeconds;
    _currentBlock = 0;
    _allowedApps = StorageService.getAllowedApps();

    _updateConcentrationMode(StudyPhase.studying);

    state = SessionState(
      totalDurationSeconds: totalSeconds,
      remainingSeconds: totalSeconds,
      phase: StudyPhase.studying,
      progress: 0.0,
      currentBlock: 1,
      totalBlocks: _blockCount,
    );
    _tickStudy();
  }

  void _updateConcentrationMode(StudyPhase phase) {
    final isRest = phase == StudyPhase.resting;
    final remainingForBlock = _currentBlockDuration;
    final total = state?.totalDurationSeconds ?? 0;
    final elapsed = total - (state?.remainingSeconds ?? 0);

    if (isRest) {
      _restRecommendation =
          _restMessages[(_currentBlock) % _restMessages.length];
    }

    NativeBridge.setConcentrationMode(
      active: true,
      phase: isRest ? 'resting' : 'studying',
      currentBlock: _currentBlock + 1,
      totalBlocks: _blockCount,
      blockDurationSeconds: remainingForBlock,
      progress: total > 0 ? (elapsed / total).clamp(0.0, 1.0) : 0.0,
      restRecommendation: _restRecommendation,
      allowedApps: _allowedApps,
    );
  }

  void _tickStudy() {
    _timer?.cancel();
    final totalSeconds = state!.totalDurationSeconds;
    final blockDuration = AppConstants.maxStudyBlock.inSeconds;
    _currentBlockDuration = _studySecondsLeft > blockDuration
        ? blockDuration
        : _studySecondsLeft;

    _updateConcentrationMode(StudyPhase.studying);

    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_studySecondsLeft <= 0) {
        timer.cancel();
        _finishSession();
        return;
      }
      _studySecondsLeft--;
      _currentBlockDuration--;
      final remaining = _studySecondsLeft + _restSecondsLeft;
      state = SessionState(
        totalDurationSeconds: totalSeconds,
        remainingSeconds: remaining,
        phase: StudyPhase.studying,
        progress: 1.0 - (remaining / totalSeconds),
        currentBlock: _currentBlock + 1,
        totalBlocks: _blockCount,
      );

      final studyPerBlock = _totalStudySeconds ~/ _blockCount;
      if (_studySecondsLeft <=
          _totalStudySeconds - (_currentBlock + 1) * studyPerBlock) {
        _currentBlock++;
        if (_currentBlock < _blockCount) {
          _startRest();
        }
      }
    });
  }

  void _startRest() {
    _timer?.cancel();
    final restPerBlock = _blockCount > 1
        ? _restSecondsLeft ~/ (_blockCount - _currentBlock)
        : _restSecondsLeft;

    _currentBlockDuration = restPerBlock;
    _updateConcentrationMode(StudyPhase.resting);

    int restRemaining = restPerBlock;
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (restRemaining <= 0) {
        timer.cancel();
        _restSecondsLeft -= restPerBlock;
        _tickStudy();
        return;
      }
      restRemaining--;
      _currentBlockDuration--;
      final total = state!.totalDurationSeconds;
      final remaining = _studySecondsLeft + restRemaining;
      state = SessionState(
        totalDurationSeconds: total,
        remainingSeconds: remaining,
        phase: StudyPhase.resting,
        progress: 1.0 - (remaining / total),
        currentBlock: _currentBlock,
        totalBlocks: _blockCount,
        restRecommendation: _restRecommendation,
      );
    });
  }

  void _finishSession() {
    _timer?.cancel();
    NativeBridge.setConcentrationMode(
      active: false,
      phase: 'finished',
      currentBlock: 0,
      totalBlocks: 0,
      blockDurationSeconds: 0,
      progress: 1.0,
      allowedApps: _allowedApps,
    );
    state = SessionState(
      totalDurationSeconds: state!.totalDurationSeconds,
      remainingSeconds: 0,
      phase: StudyPhase.finished,
      progress: 1.0,
      currentBlock: _blockCount,
      totalBlocks: _blockCount,
    );
  }

  void cancel() {
    _timer?.cancel();
    NativeBridge.setConcentrationMode(
      active: false,
      phase: 'cancelled',
      currentBlock: 0,
      totalBlocks: 0,
      blockDurationSeconds: 0,
      progress: 0.0,
      allowedApps: _allowedApps,
    );
    state = null;
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}
