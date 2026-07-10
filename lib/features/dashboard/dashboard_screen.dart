import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/constants.dart';
import '../../core/services/native_bridge.dart';
import '../../core/services/session_manager.dart';
import '../../core/theme.dart';
import '../app_selector/app_selector_screen.dart';
import '../study_session/study_session_screen.dart';
import 'dashboard_provider.dart';

final hasUsageStatsPermissionProvider = FutureProvider<bool>((ref) async {
  return NativeBridge.hasUsageStatsPermission();
});

final canDrawOverlaysProvider = FutureProvider<bool>((ref) async {
  return NativeBridge.canDrawOverlays();
});

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(dashboardProvider);
    final isStudyActive = ref.watch(sessionManagerProvider) != null;
    final hasUsagePermAsync = ref.watch(hasUsageStatsPermissionProvider);

    if (isStudyActive) {
      return const StudySessionScreen();
    }

    final bool showProgressive = !state.isBlocked;
    final double progress;
    final int displayMinutes;
    final int displaySeconds;
    final String timerLabel;

    if (showProgressive) {
      progress = state.accumulatedSeconds / AppConstants.distractLimitSeconds;
      displayMinutes = state.accumulatedSeconds ~/ 60;
      displaySeconds = state.accumulatedSeconds % 60;
      timerLabel = '/ ${AppConstants.distractLimitSeconds ~/ 60} min';
    } else {
      progress = state.blockRemainingSeconds / AppConstants.blockDurationSeconds;
      displayMinutes = state.blockRemainingSeconds ~/ 60;
      displaySeconds = state.blockRemainingSeconds % 60;
      timerLabel = '/ ${AppConstants.blockDurationSeconds ~/ 60} min';
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildHeader(context),
              const SizedBox(height: 28),
              _buildProgressCard(
                context: context,
                progress: progress,
                displayMinutes: displayMinutes,
                displaySeconds: displaySeconds,
                isBlocked: state.isBlocked,
                timerLabel: timerLabel,
              ),
              const SizedBox(height: 20),
              if (state.isBlocked) _buildBlockedBanner(),
              if (state.isBlocked) const SizedBox(height: 20),
              _buildPermissionsSection(
                context: context,
                hasUsagePermAsync: hasUsagePermAsync,
              ),
              if (!state.isBlocked && progress < 1.0) ...[
                const SizedBox(height: 20),
                _buildStartTrackingButton(context, hasUsagePermAsync),
              ],
              const SizedBox(height: 28),
              _buildStudyModeCard(context),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: AppColors.primarySurface,
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(
            Icons.eco_rounded,
            color: AppColors.primary,
            size: 24,
          ),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Focus Blocker',
                style: GoogleFonts.poppins(
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                  color: AppColors.textPrimary,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                'Tu espacio de enfoque',
                style: GoogleFonts.nunito(
                  fontSize: 14,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
        ),
        IconButton(
          onPressed: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const AppSelectorScreen()),
            );
          },
          icon: const Icon(
            Icons.settings_rounded,
            color: AppColors.textSecondary,
            size: 22,
          ),
        ),
      ],
    );
  }

  Widget _buildProgressCard({
    required BuildContext context,
    required double progress,
    required int displayMinutes,
    required int displaySeconds,
    required bool isBlocked,
    required String timerLabel,
  }) {
    final clampedProgress = progress.clamp(0.0, 1.0);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isBlocked
              ? AppColors.error.withValues(alpha: 0.3)
              : AppColors.divider,
          width: 1,
        ),
      ),
      child: Column(
        children: [
          SizedBox(
            height: 180,
            width: 180,
            child: Stack(
              fit: StackFit.expand,
              children: [
                CircularProgressIndicator(
                  value: clampedProgress,
                  strokeWidth: 10,
                  backgroundColor: AppColors.card,
                  valueColor: AlwaysStoppedAnimation<Color>(
                    isBlocked ? AppColors.error : AppColors.primary,
                  ),
                ),
                Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        '${displayMinutes.toString().padLeft(2, '0')}:${displaySeconds.toString().padLeft(2, '0')}',
                        style: GoogleFonts.poppins(
                          fontSize: 36,
                          fontWeight: FontWeight.w700,
                          color: isBlocked
                              ? AppColors.error
                              : AppColors.textPrimary,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        timerLabel,
                        style: GoogleFonts.nunito(
                          fontSize: 14,
                          color: AppColors.textTertiary,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: isBlocked
                  ? AppColors.error.withValues(alpha: 0.1)
                  : AppColors.primarySurface,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  isBlocked ? Icons.warning_rounded : Icons.shield_rounded,
                  size: 16,
                  color: isBlocked ? AppColors.error : AppColors.primary,
                ),
                const SizedBox(width: 6),
                Text(
                  isBlocked ? 'Bloqueado' : 'Monitoreando',
                  style: GoogleFonts.nunito(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: isBlocked ? AppColors.error : AppColors.primary,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBlockedBanner() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.error.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: AppColors.error.withValues(alpha: 0.2),
          width: 1,
        ),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.error.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(
              Icons.lock_rounded,
              color: AppColors.error,
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Límite excedido',
                  style: GoogleFonts.nunito(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: AppColors.error,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  'Solo apps blandas disponibles durante el bloqueo',
                  style: GoogleFonts.nunito(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionsSection({
    required BuildContext context,
    required AsyncValue<bool> hasUsagePermAsync,
  }) {
    return hasUsagePermAsync.when(
      data: (hasPerm) {
        if (hasPerm) return const SizedBox.shrink();
        return Column(
          children: [
            _buildPermissionButton(
              context: context,
              icon: Icons.bar_chart_rounded,
              title: 'Estadísticas de uso',
              subtitle: 'Permite monitorear el tiempo de apps',
              onTap: () => NativeBridge.openUsageAccessSettings(),
            ),
            const SizedBox(height: 12),
          ],
        );
      },
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
    );
  }

  Widget _buildPermissionButton({
    required BuildContext context,
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: onTap,
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.border, width: 1),
          ),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppColors.warning.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(icon, color: AppColors.warning, size: 22),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: GoogleFonts.nunito(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: GoogleFonts.nunito(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(
                Icons.chevron_right_rounded,
                color: AppColors.textTertiary,
                size: 22,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStartTrackingButton(
    BuildContext context,
    AsyncValue<bool> hasUsagePermAsync,
  ) {
    return hasUsagePermAsync.when(
      data: (hasPerm) {
        if (!hasPerm) return const SizedBox.shrink();
        return SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: () async {
              try {
                await NativeBridge.startTracking();
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        'Monitoreo iniciado',
                        style: GoogleFonts.nunito(),
                      ),
                      backgroundColor: AppColors.success,
                      behavior: SnackBarBehavior.floating,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        'Error al iniciar: $e',
                        style: GoogleFonts.nunito(),
                      ),
                      backgroundColor: AppColors.error,
                      behavior: SnackBarBehavior.floating,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  );
                }
              }
            },
            icon: const Icon(Icons.play_arrow_rounded, size: 22),
            label: const Text('Iniciar monitoreo'),
          ),
        );
      },
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
    );
  }

  Widget _buildStudyModeCard(BuildContext context) {
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const StudySetupScreen()),
          );
        },
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.divider, width: 1),
          ),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.primarySurface,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.spa_rounded,
                  color: AppColors.primary,
                  size: 28,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Modo Concentración',
                      style: GoogleFonts.poppins(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Prepara tu sesión de estudio con descansos inteligentes',
                      style: GoogleFonts.nunito(
                        fontSize: 13,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(
                Icons.arrow_forward_ios_rounded,
                color: AppColors.textTertiary,
                size: 16,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
