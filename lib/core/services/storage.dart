import 'package:hive/hive.dart';

class StorageService {
  static const _distractingKey = 'distractingApps';
  static const _allowedKey = 'allowedApps';
  static late Box _box;

  static Future<void> init() async {
    _box = await Hive.openBox('settings');
  }

  static List<String> getDistractingApps() =>
      _box.get(_distractingKey, defaultValue: <String>[])!.cast<String>();

  static setDistractingApps(List<String> apps) =>
      _box.put(_distractingKey, apps);

  static List<String> getAllowedApps() =>
      _box.get(_allowedKey, defaultValue: <String>[])!.cast<String>();

  static setAllowedApps(List<String> apps) =>
      _box.put(_allowedKey, apps);
}