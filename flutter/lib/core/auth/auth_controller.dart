import 'package:flutter/foundation.dart';

import '../api/api.dart';
import '../api/auth_store.dart';
import '../domain/models.dart';

/// Estado de sesión global: token, usuario y perfil de calorías. También se
/// encarga del redireccionamiento tras 401 (sesión caducada en cualquier
/// pantalla), igual que el frontend Next.js que hacía `window.location = /login`.
class AuthController extends ChangeNotifier {
  AuthController({Api? api, AuthStore? store})
      : _store = store ?? AuthStore(),
        _api = api ??
            Api(
              onUnauthorized: () => unauthorized(),
            ) {
    _api.client.tokenProvider = _store.readToken;
  }

  final AuthStore _store;
  final Api _api;

  String? _token;
  String? _username;
  CalorieProfile _profile = CalorieProfile.empty;
  bool _booting = true;

  String? get token => _token;
  String? get username => _username;
  CalorieProfile get profile => _profile;
  bool get isAuthenticated => _token != null;

  /// true mientras se restaura la sesión almacenada al arrancar.
  bool get booting => _booting;

  /// API pública, ya conectada al store de token y al manejo de 401.
  Api get api => _api;

  /// Restaura la sesión guardada (si el token sigue siendo válido).
  Future<void> bootstrap() async {
    _booting = true;
    notifyListeners();
    try {
      final stored = await _store.readToken();
      if (stored != null && stored.isNotEmpty) {
        _token = stored;
        await _refreshSession();
      }
    } finally {
      _booting = false;
      notifyListeners();
    }
  }

  Future<void> login(String username, String password) async {
    final token = await _api.login(username, password);
    _token = token;
    await _store.writeToken(token);
    await _refreshSession();
    notifyListeners();
  }

  Future<void> logout() async {
    final token = _token;
    _token = null;
    _username = null;
    _profile = CalorieProfile.empty;
    await _store.clearToken();
    if (token != null) {
      try {
        await _api.logout();
      } on ApiException {
        // La sesión puede haber caducado ya; el token local se limpia igual.
      }
    }
    notifyListeners();
  }

  Future<void> _refreshSession() async {
    final session = await _api.session();
    _username = session.username;
    _profile = session.profile;
  }

  void updateProfile(CalorieProfile profile) {
    _profile = profile;
    notifyListeners();
  }

  /// Respuesta global a cualquier 401: la sesión ya no es válida.
  static Future<void> unauthorized() async {
    final controller = appAuthController;
    if (controller != null) {
      await controller.forceLogout();
    }
  }

  /// Limpieza brusca sin llamar al backend (el token ya fue rechazado).
  Future<void> forceLogout() async {
    _token = null;
    _username = null;
    _profile = CalorieProfile.empty;
    await _store.clearToken();
    notifyListeners();
  }
}

/// Referencia global débil para el manejo asíncrono de 401 (evita import
/// circular): se registra en main().
AuthController? appAuthController;