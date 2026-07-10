import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/services/native_bridge.dart';
import '../../core/theme.dart';
import 'app_selector_provider.dart';

final installedAppsProvider = FutureProvider<List<Map<String, dynamic>>>((ref) {
  return NativeBridge.getInstalledApps();
});

class AppSelectorScreen extends ConsumerStatefulWidget {
  const AppSelectorScreen({super.key});

  @override
  ConsumerState<AppSelectorScreen> createState() => _AppSelectorScreenState();
}

class _AppSelectorScreenState extends ConsumerState<AppSelectorScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final installed = ref.watch(installedAppsProvider);
    final distracting = ref.watch(distractingAppsProvider);
    final allowed = ref.watch(allowedAppsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Configurar apps'),
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppColors.primary,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.primary,
          indicatorWeight: 3,
          labelStyle: GoogleFonts.nunito(
            fontWeight: FontWeight.w700,
            fontSize: 14,
          ),
          unselectedLabelStyle: GoogleFonts.nunito(
            fontWeight: FontWeight.w600,
            fontSize: 14,
          ),
          tabs: const [
            Tab(text: 'Distractoras'),
            Tab(text: 'Blandas'),
          ],
        ),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: TextField(
              decoration: InputDecoration(
                hintText: 'Buscar app...',
                prefixIcon: const Icon(Icons.search_rounded, size: 20),
                suffixIcon: _searchQuery.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear_rounded, size: 18),
                        onPressed: () {
                          setState(() => _searchQuery = '');
                        },
                      )
                    : null,
              ),
              onChanged: (value) {
                setState(() => _searchQuery = value.toLowerCase().trim());
              },
            ),
          ),
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                _buildAppList(
                  installed: installed,
                  selectedApps: distracting,
                  notifier: ref.read(distractingAppsProvider.notifier),
                ),
                _buildAppList(
                  installed: installed,
                  selectedApps: allowed,
                  notifier: ref.read(allowedAppsProvider.notifier),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppList({
    required AsyncValue<List<Map<String, dynamic>>> installed,
    required List<String> selectedApps,
    required AppListNotifier notifier,
  }) {
    return installed.when(
      data: (apps) {
        final filteredApps = apps
            .where((a) =>
                a['package'] != 'com.example.antiprocrastinacion')
            .toList()
          ..sort((a, b) {
            final labelA = (a['label'] as String? ??
                    a['package'] as String? ??
                    '')
                .toLowerCase();
            final labelB = (b['label'] as String? ??
                    b['package'] as String? ??
                    '')
                .toLowerCase();
            return labelA.compareTo(labelB);
          });

        final displayedApps = _searchQuery.isEmpty
            ? filteredApps
            : filteredApps.where((app) {
                final label = (app['label'] as String? ??
                        app['package'] as String? ??
                        '')
                    .toLowerCase();
                final pkg =
                    (app['package'] as String? ?? '').toLowerCase();
                return label.contains(_searchQuery) ||
                    pkg.contains(_searchQuery);
              }).toList();

        if (displayedApps.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(
                  Icons.search_off_rounded,
                  size: 48,
                  color: AppColors.textTertiary,
                ),
                const SizedBox(height: 12),
                Text(
                  'No se encontraron apps',
                  style: GoogleFonts.nunito(
                    fontSize: 16,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          );
        }

        return ListView.builder(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
          itemCount: displayedApps.length,
          itemBuilder: (context, index) {
            final app = displayedApps[index];
            final pkg = app['package'] as String;
            final label = app['label'] as String?;
            final iconData = app['icon'] as Uint8List?;
            final displayName =
                (label != null && label.isNotEmpty) ? label : pkg;
            final isSelected = selectedApps.contains(pkg);

            return Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Material(
                color: isSelected
                    ? AppColors.primarySurface
                    : AppColors.surface,
                borderRadius: BorderRadius.circular(14),
                child: InkWell(
                  borderRadius: BorderRadius.circular(14),
                  onTap: () => notifier.toggle(pkg),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 12,
                    ),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: isSelected
                            ? AppColors.primary.withValues(alpha: 0.4)
                            : AppColors.divider,
                        width: isSelected ? 1.5 : 1,
                      ),
                    ),
                    child: Row(
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(10),
                          child: iconData != null
                              ? Image.memory(
                                  iconData,
                                  width: 42,
                                  height: 42,
                                  fit: BoxFit.cover,
                                )
                              : Container(
                                  width: 42,
                                  height: 42,
                                  decoration: BoxDecoration(
                                    color: AppColors.card,
                                    borderRadius:
                                        BorderRadius.circular(10),
                                  ),
                                  child: const Icon(
                                    Icons.apps_rounded,
                                    size: 22,
                                    color: AppColors.textTertiary,
                                  ),
                                ),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Text(
                            displayName,
                            style: GoogleFonts.nunito(
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                              color: AppColors.textPrimary,
                            ),
                          ),
                        ),
                        if (isSelected)
                          Container(
                            padding: const EdgeInsets.all(4),
                            decoration: const BoxDecoration(
                              color: AppColors.primary,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.check_rounded,
                              size: 16,
                              color: Colors.white,
                            ),
                          )
                        else
                          const Icon(
                            Icons.add_circle_outline_rounded,
                            size: 22,
                            color: AppColors.textTertiary,
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          },
        );
      },
      loading: () => const Center(
        child: CircularProgressIndicator(color: AppColors.primary),
      ),
      error: (e, _) => Center(
        child: Text(
          'Error al cargar apps: $e',
          style: GoogleFonts.nunito(color: AppColors.error),
        ),
      ),
    );
  }
}
