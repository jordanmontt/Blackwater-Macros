import 'package:flutter/material.dart';

import 'app.dart';
import 'core/auth/auth_controller.dart';
import 'core/theme/theme_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final theme = ThemeController();
  await theme.load();
  final auth = AuthController();
  runApp(BlackwaterApp(auth: auth, theme: theme));
}