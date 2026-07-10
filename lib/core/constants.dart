class AppConstants {
  static const int distractLimitSeconds = 20 * 60;  // 20 min de vigilancia
  static const int blockDurationSeconds = 15 * 60;  // 15 min de bloqueo
  static const int inactivityResetSeconds = 25 * 60;
  static const double studyRestRatio = 0.15;
  static const Duration maxStudyBlock = Duration(hours: 2);
  static const String nativeChannel = 'com.focus.blocker/native';
}