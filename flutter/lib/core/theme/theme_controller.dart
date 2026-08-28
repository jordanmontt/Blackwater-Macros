import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Controla el modo de tema (claro/oscuro/sistema) persistido localmente.
class ThemeController extends ChangeNotifier {
  static const _key = 'bw_theme_mode';
  ThemeMode _mode = ThemeMode.system;

  ThemeMode get mode => _mode;

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    _mode = ThemeMode.values.asNameMap()[prefs.getString(_key)] ?? ThemeMode.system;
    notifyListeners();
  }

  Future<void> setMode(ThemeMode mode) async {
    _mode = mode;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, mode.name);
  }
}

/// Referencia global del controlador de tema (la usan las pantallas de
/// ajustes), registrada en main()/app.dart.
ThemeController? appThemeController;