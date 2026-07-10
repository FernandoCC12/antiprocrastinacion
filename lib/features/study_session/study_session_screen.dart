import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/services/session_manager.dart';
import '../../core/theme.dart';
import '../dashboard/dashboard_provider.dart';

class StudySetupScreen extends ConsumerStatefulWidget {
  const StudySetupScreen({super.key});

  @override
  ConsumerState<StudySetupScreen> createState() => _StudySetupScreenState();
}

class _StudySetupScreenState extends ConsumerState<StudySetupScreen> {
  double _hours = 2.0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Modo concentración')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: const BoxDecoration(
                color: AppColors.primarySurface,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.spa_rounded,
                color: AppColors.primary,
                size: 40,
              ),
            ),
            const SizedBox(height: 24),
            Text(
              '¿Cuántas horas deseas estudiar?',
              style: GoogleFonts.poppins(
                fontSize: 20,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Incluye descansos automáticos del 15%',
              style: GoogleFonts.nunito(
                fontSize: 14,
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 32),
            Text(
              '${_hours.toStringAsFixed(1)} h',
              style: GoogleFonts.poppins(
                fontSize: 52,
                fontWeight: FontWeight.w700,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(height: 8),
            Slider(
              value: _hours,
              min: 0.5,
              max: 12.0,
              divisions: 23,
              activeColor: AppColors.primary,
              onChanged: (v) => setState(() => _hours = v),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '30 min',
                  style: GoogleFonts.nunito(
                    fontSize: 12,
                    color: AppColors.textTertiary,
                  ),
                ),
                Text(
                  '12 h',
                  style: GoogleFonts.nunito(
                    fontSize: 12,
                    color: AppColors.textTertiary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 40),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () {
                  final totalMinutes = (_hours * 60).round();
                  ref.read(dashboardProvider.notifier).startStudyMode(totalMinutes);
                  Navigator.pop(context);
                },
                child: const Text('Comenzar'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class StudySessionScreen extends ConsumerWidget {
  const StudySessionScreen({super.key});

  String _getTreeEmoji(double progress) {
    if (progress < 0.25) return '🌱';
    if (progress < 0.50) return '🌿';
    if (progress < 0.75) return '🪴';
    return '🌳';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sessionState = ref.watch(sessionManagerProvider);
    if (sessionState == null) return const SizedBox.shrink();

    final hours = sessionState.remainingSeconds ~/ 3600;
    final minutes = (sessionState.remainingSeconds % 3600) ~/ 60;
    final seconds = sessionState.remainingSeconds % 60;

    final phaseText = sessionState.phase == StudyPhase.studying
        ? 'Estudiando'
        : sessionState.phase == StudyPhase.resting
            ? 'Descanso'
            : 'Completado';

    final blockInfo =
        'Bloque ${sessionState.currentBlock} de ${sessionState.totalBlocks}';

    if (sessionState.phase == StudyPhase.finished) {
      Future.microtask(() {
        ref.read(dashboardProvider.notifier).returnFromStudyMode();
        ref.read(sessionManagerProvider.notifier).cancel();
        if (context.mounted) {
          Navigator.popUntil(context, (route) => route.isFirst);
        }
      });
      return const SizedBox.shrink();
    }

    final treeEmoji = _getTreeEmoji(sessionState.progress);
    final treeSize = 40.0 + (60.0 * sessionState.progress);

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Color(0xFF4A7C59),
              Color(0xFF6B9E7A),
              Color(0xFF8FB99E),
            ],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      phaseText,
                      style: GoogleFonts.nunito(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    blockInfo,
                    style: GoogleFonts.nunito(
                      fontSize: 15,
                      fontWeight: FontWeight.w500,
                      color: Colors.white.withValues(alpha: 0.7),
                    ),
                  ),
                  const SizedBox(height: 24),
                  Text(
                    '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}',
                    style: GoogleFonts.poppins(
                      fontSize: 52,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                      letterSpacing: 2,
                    ),
                  ),
                  const SizedBox(height: 32),
                  Container(
                    height: 180,
                    width: 180,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Colors.white.withValues(alpha: 0.12),
                      border: Border.all(
                        color: Colors.white.withValues(alpha: 0.2),
                        width: 2,
                      ),
                    ),
                    child: Center(
                      child: Text(
                        treeEmoji,
                        style: TextStyle(fontSize: treeSize),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Crecimiento: ${(sessionState.progress * 100).toStringAsFixed(0)}%',
                    style: GoogleFonts.nunito(
                      color: Colors.white,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: 160,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: sessionState.progress,
                        backgroundColor: Colors.white.withValues(alpha: 0.2),
                        valueColor: const AlwaysStoppedAnimation<Color>(
                          Colors.white,
                        ),
                        minHeight: 6,
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),
                  if (sessionState.phase == StudyPhase.resting &&
                      sessionState.restRecommendation != null)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: Colors.white.withValues(alpha: 0.15),
                          width: 1,
                        ),
                      ),
                      child: Column(
                        children: [
                          Text(
                            'Descanso sugerido',
                            style: GoogleFonts.nunito(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: Colors.white.withValues(alpha: 0.8),
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            sessionState.restRecommendation!,
                            textAlign: TextAlign.center,
                            style: GoogleFonts.nunito(
                              fontSize: 15,
                              color: Colors.white,
                              fontStyle: FontStyle.italic,
                            ),
                          ),
                        ],
                      ),
                    ),
                  const SizedBox(height: 32),
                  TextButton.icon(
                    onPressed: () {
                      showDialog(
                        context: context,
                        builder: (_) => AlertDialog(
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                          ),
                          title: const Text('¿Cancelar sesión?'),
                          content: const Text('Perderás el progreso actual.'),
                          actions: [
                            TextButton(
                              onPressed: () => Navigator.pop(context),
                              child: const Text('Continuar'),
                            ),
                            TextButton(
                              onPressed: () {
                                ref
                                    .read(sessionManagerProvider.notifier)
                                    .cancel();
                                ref
                                    .read(dashboardProvider.notifier)
                                    .returnFromStudyMode();
                                Navigator.pop(context);
                                Navigator.popUntil(
                                  context,
                                  (route) => route.isFirst,
                                );
                              },
                              child: const Text(
                                'Cancelar',
                                style: TextStyle(color: AppColors.error),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                    icon: Icon(
                      Icons.cancel_outlined,
                      color: Colors.white.withValues(alpha: 0.7),
                    ),
                    label: Text(
                      'Cancelar',
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.7),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
