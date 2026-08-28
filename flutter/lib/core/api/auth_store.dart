import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Guarda el token de sesión: almacenamiento seguro en móvil
/// (flutter_secure_storage) y shared_preferences en web, donde no existe
/// un equivalente del llavero pero localStorage cumple el mismo papel que la
/// cookie httponly original.
class AuthStore {
  static const _storage = FlutterSecureStorage();
  static const _tokenKey = 'bw_session_token';

  Future<String?> readToken() async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_tokenKey);
    }
    return _storage.read(key: _tokenKey);
  }

  Future<void> writeToken(String token) async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_tokenKey, token);
    } else {
      await _storage.write(key: _tokenKey, value: token);
    }
  }

  Future<void> clearToken() async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_tokenKey);
    } else {
      await _storage.delete(key: _tokenKey);
    }
  }
}