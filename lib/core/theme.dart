import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppColors {
  AppColors._();

  static const Color primary = Color(0xFF5B8C5A);
  static const Color primaryDark = Color(0xFF3D6B3D);
  static const Color primaryLight = Color(0xFFA8D5A7);
  static const Color primarySurface = Color(0xFFE8F3E8);

  static const Color secondary = Color(0xFFD4956B);
  static const Color secondaryDark = Color(0xFFB87A52);
  static const Color secondaryLight = Color(0xFFF0D0BE);
  static const Color secondarySurface = Color(0xFFFDF0E8);

  static const Color accent = Color(0xFFC9A96E);
  static const Color accentLight = Color(0xFFF5EBD6);

  static const Color background = Color(0xFFF8F5F0);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color card = Color(0xFFF3EDE6);

  static const Color textPrimary = Color(0xFF2D2219);
  static const Color textSecondary = Color(0xFF7A6E62);
  static const Color textTertiary = Color(0xFFAEA49A);
  static const Color textOnPrimary = Color(0xFFFFFFFF);

  static const Color error = Color(0xFFC75C4A);
  static const Color success = Color(0xFF5B8C5A);
  static const Color warning = Color(0xFFD4A76A);

  static const Color divider = Color(0xFFE8E2DB);
  static const Color border = Color(0xFFDDD6CC);
}

const melon = AppColors.secondaryLight;
const melonDeep = AppColors.secondary;
const melonLight = AppColors.secondaryLight;
const cream = AppColors.background;
const darkBrown = AppColors.textPrimary;
const golden = AppColors.accent;

final warmMelonTheme = ThemeData(
  useMaterial3: true,
  brightness: Brightness.light,
  colorScheme: ColorScheme.light(
    primary: AppColors.primary,
    onPrimary: AppColors.textOnPrimary,
    primaryContainer: AppColors.primarySurface,
    onPrimaryContainer: AppColors.primaryDark,
    secondary: AppColors.secondary,
    onSecondary: AppColors.textOnPrimary,
    secondaryContainer: AppColors.secondarySurface,
    onSecondaryContainer: AppColors.secondaryDark,
    surface: AppColors.surface,
    onSurface: AppColors.textPrimary,
    error: AppColors.error,
    onError: AppColors.textOnPrimary,
  ),
  scaffoldBackgroundColor: AppColors.background,
  textTheme: GoogleFonts.nunitoTextTheme().copyWith(
    headlineLarge: GoogleFonts.poppins(
      fontSize: 28,
      fontWeight: FontWeight.w700,
      color: AppColors.textPrimary,
    ),
    headlineMedium: GoogleFonts.poppins(
      fontSize: 22,
      fontWeight: FontWeight.w600,
      color: AppColors.textPrimary,
    ),
    headlineSmall: GoogleFonts.poppins(
      fontSize: 18,
      fontWeight: FontWeight.w600,
      color: AppColors.textPrimary,
    ),
    titleLarge: GoogleFonts.poppins(
      fontSize: 16,
      fontWeight: FontWeight.w600,
      color: AppColors.textPrimary,
    ),
    titleMedium: GoogleFonts.nunito(
      fontSize: 15,
      fontWeight: FontWeight.w700,
      color: AppColors.textPrimary,
    ),
    bodyLarge: GoogleFonts.nunito(
      fontSize: 16,
      fontWeight: FontWeight.w400,
      color: AppColors.textPrimary,
    ),
    bodyMedium: GoogleFonts.nunito(
      fontSize: 14,
      fontWeight: FontWeight.w400,
      color: AppColors.textSecondary,
    ),
    bodySmall: GoogleFonts.nunito(
      fontSize: 12,
      fontWeight: FontWeight.w400,
      color: AppColors.textTertiary,
    ),
    labelLarge: GoogleFonts.nunito(
      fontSize: 14,
      fontWeight: FontWeight.w700,
      color: AppColors.textOnPrimary,
    ),
    labelMedium: GoogleFonts.nunito(
      fontSize: 12,
      fontWeight: FontWeight.w600,
      color: AppColors.textSecondary,
    ),
  ),
  appBarTheme: AppBarTheme(
    backgroundColor: AppColors.surface,
    foregroundColor: AppColors.textPrimary,
    elevation: 0,
    centerTitle: false,
    titleTextStyle: GoogleFonts.poppins(
      fontSize: 20,
      fontWeight: FontWeight.w700,
      color: AppColors.textPrimary,
    ),
  ),
  cardTheme: CardThemeData(
    color: AppColors.surface,
    elevation: 0,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(16),
      side: const BorderSide(color: AppColors.divider, width: 1),
    ),
    margin: EdgeInsets.zero,
  ),
  elevatedButtonTheme: ElevatedButtonThemeData(
    style: ElevatedButton.styleFrom(
      backgroundColor: AppColors.primary,
      foregroundColor: AppColors.textOnPrimary,
      elevation: 0,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
      ),
      textStyle: GoogleFonts.nunito(
        fontSize: 15,
        fontWeight: FontWeight.w700,
      ),
    ),
  ),
  outlinedButtonTheme: OutlinedButtonThemeData(
    style: OutlinedButton.styleFrom(
      foregroundColor: AppColors.primary,
      side: const BorderSide(color: AppColors.primary, width: 1.5),
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
      ),
      textStyle: GoogleFonts.nunito(
        fontSize: 15,
        fontWeight: FontWeight.w700,
      ),
    ),
  ),
  inputDecorationTheme: InputDecorationTheme(
    filled: true,
    fillColor: AppColors.surface,
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: AppColors.border),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: AppColors.border),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: AppColors.primary, width: 2),
    ),
    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    hintStyle: GoogleFonts.nunito(
      color: AppColors.textTertiary,
      fontSize: 14,
    ),
  ),
  chipTheme: ChipThemeData(
    backgroundColor: AppColors.card,
    selectedColor: AppColors.primarySurface,
    labelStyle: GoogleFonts.nunito(
      fontSize: 14,
      fontWeight: FontWeight.w600,
    ),
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(10),
      side: const BorderSide(color: AppColors.border),
    ),
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
  ),
  dividerTheme: const DividerThemeData(
    color: AppColors.divider,
    thickness: 1,
    space: 1,
  ),
  sliderTheme: SliderThemeData(
    activeTrackColor: AppColors.primary,
    inactiveTrackColor: AppColors.primaryLight,
    thumbColor: AppColors.primary,
    overlayColor: AppColors.primarySurface,
    trackHeight: 6,
    thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
    overlayShape: const RoundSliderOverlayShape(overlayRadius: 20),
  ),
);
