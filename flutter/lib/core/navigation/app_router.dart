import 'package:go_router/go_router.dart';

import '../auth/auth_controller.dart';
import '../../shared/widgets/main_shell.dart';
import '../../features/auth/login_screen.dart';
import '../../features/hoy/hoy_screen.dart';
import '../../features/peso/peso_screen.dart';
import '../../features/estadisticas/estadisticas_screen.dart';
import '../../features/ajustes/ajustes_screen.dart';
import '../../features/metodologia/metodologia_screen.dart';

/// Construye el router ligado al estado de sesión y al tema: al cambiar la
/// autenticación se refresca y redirige entre /login y la app.
GoRouter createAppRouter(AuthController auth) {
  return GoRouter(
    initialLocation: '/hoy',
    refreshListenable: auth,
    redirect: (context, state) {
      if (auth.booting) return null; // aún restaurando la sesión almacenada
      final loggedIn = auth.isAuthenticated;
      final onLoginPage = state.matchedLocation == '/login';
      if (!loggedIn && !onLoginPage) return '/login';
      if (loggedIn && onLoginPage) return '/hoy';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            MainShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/hoy', builder: (context, state) => const HoyScreen()),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/peso', builder: (context, state) => const PesoScreen()),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/estadisticas', builder: (context, state) => const EstadisticasScreen()),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/ajustes', builder: (context, state) => const AjustesScreen()),
              GoRoute(path: '/metodologia', builder: (context, state) => const MetodologiaScreen()),
            ],
          ),
        ],
      ),
    ],
  );
}