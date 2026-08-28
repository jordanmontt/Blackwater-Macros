import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'core/auth/auth_controller.dart';
import 'core/navigation/app_router.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/theme_controller.dart';

/// Raíz de la app: escucha los cambios de sesión y tema y construye el router
/// una vez restaurada la sesión almacenada.
class BlackwaterApp extends StatefulWidget {
  const BlackwaterApp({
    super.key,
    required this.auth,
    required this.theme,
  });

  final AuthController auth;
  final ThemeController theme;

  @override
  State<BlackwaterApp> createState() => _BlackwaterAppState();
}

class _BlackwaterAppState extends State<BlackwaterApp> {
  late final GoRouter _router = createAppRouter(widget.auth);

  @override
  void initState() {
    super.initState();
    appAuthController = widget.auth;
    appThemeController = widget.theme;
    widget.auth.bootstrap();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([widget.auth, widget.theme]),
      builder: (context, _) {
        return MaterialApp.router(
          title: 'Blackwater Macros',
          debugShowCheckedModeBanner: false,
          theme: buildTheme(Brightness.light),
          darkTheme: buildTheme(Brightness.dark),
          themeMode: widget.theme.mode,
          routerConfig: _router,
        );
      },
    );
  }
}