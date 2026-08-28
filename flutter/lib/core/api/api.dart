import 'dart:convert';

import 'package:http/http.dart' as http;

import 'package:flutter/foundation.dart';

import '../domain/models.dart';

/// Configuración del backend. En desarrollo la API local corre en
/// `http://localhost:8787`. En producción se sobrescribe en el build:
///   flutter run --dart-define=API_BASE_URL=https://api.blackwatermacros.com
class ApiConfig {
  static const _defined = String.fromEnvironment('API_BASE_URL');

  /// URL base del backend Hono. Web en dev usa 8787; Android emulador usa la
  /// IP del host (10.0.2.2) para llegar a la máquina local.
  static Uri get baseUri {
    if (_defined.isNotEmpty) return Uri.parse(_defined);
    if (kIsWeb) return Uri.parse('http://localhost:8787');
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return Uri.parse('http://10.0.2.2:8787');
    }
    return Uri.parse('http://localhost:8787');
  }
}

class ApiException implements Exception {
  const ApiException(this.status, this.message);

  final int status;
  final String message;

  bool get isNetwork => status == 0;
  bool get isUnauthorized => status == 401;

  @override
  String toString() => 'ApiException($status): $message';
}

typedef AuthExpiredHandler = void Function();

/// Cliente HTTP mínimo con token Bearer, errores tipados y manejo global de
/// sesión caducada. El backend acepta tanto Bearer (apps nativas) como la
/// cookie bw_session (web heredada).
class ApiClient {
  ApiClient({this.onUnauthorized, this.tokenProvider});

  /// Forzado global de sesión caducada (falta de autenticación).
  final AuthExpiredHandler? onUnauthorized;

  /// Suministra el token de sesión vigente para cada petición, si existe.
  Future<String?> Function()? tokenProvider;

  Future<dynamic> request(
    String path, {
    String method = 'GET',
    Map<String, String>? headers,
    Map<String, String>? queryParameters,
    dynamic body,
  }) async {
    final request = http.Request(method, ApiConfig.baseUri.replace(
      path: path,
      queryParameters: queryParameters,
    ));
    final token = await tokenProvider?.call();
    request.headers['Accept'] = 'application/json';
    if (token != null && token.isNotEmpty) {
      request.headers['Authorization'] = 'Bearer $token';
    }
    if (body != null) {
      request.body = jsonEncode(body);
      request.headers['Content-Type'] = 'application/json';
    }
    request.headers.addAll(headers ?? const {});

    final client = http.Client();
    try {
      final streamed = await client.send(request);
      final bytes = await streamed.stream.toBytes();
      final raw = utf8.decode(bytes);
      final isJson =
          (streamed.headers['content-type'] ?? '').toLowerCase().contains('json');
      final decoded = isJson && raw.isNotEmpty ? jsonDecode(raw) : null;

      if (streamed.statusCode == 401 && !path.endsWith('/login')) {
        onUnauthorized?.call();
        throw const ApiException(401, 'No autenticado');
      }

      if (streamed.statusCode >= 400) {
        final message = (decoded is Map && decoded['error'] is String)
            ? decoded['error'] as String
            : 'Error ${streamed.statusCode}';
        throw ApiException(streamed.statusCode, message);
      }
      return decoded;
    } on http.ClientException {
      throw const ApiException(0, 'Error de red');
    } finally {
      client.close();
    }
  }

  /// GET que devuelve el cuerpo como texto plano (p. ej. los CSV de
  /// exportación), sin asumir JSON.
  Future<String> requestText(String path) async {
    final request = http.Request('GET', ApiConfig.baseUri.replace(path: path));
    request.headers['Accept'] = 'text/csv,text/plain';
    final token = await tokenProvider?.call();
    if (token != null && token.isNotEmpty) {
      request.headers['Authorization'] = 'Bearer $token';
    }

    final client = http.Client();
    try {
      final streamed = await client.send(request);
      final bytes = await streamed.stream.toBytes();
      final raw = utf8.decode(bytes);
      if (streamed.statusCode == 401) {
        onUnauthorized?.call();
        throw const ApiException(401, 'No autenticado');
      }
      if (streamed.statusCode >= 400) {
        throw ApiException(streamed.statusCode, 'Error ${streamed.statusCode}');
      }
      return raw;
    } on http.ClientException {
      throw const ApiException(0, 'Error de red');
    } finally {
      client.close();
    }
  }
}

/// API tipada espejo de `src/lib/api.ts` del frontend Next.js.
class Api {
  Api({ApiClient? client, this.onUnauthorized}) : _client = client ?? ApiClient();

  final ApiClient _client;
  final AuthExpiredHandler? onUnauthorized;

  /// Cliente subyacente, por si una pantalla necesita bajo nivel (CSV, etc.).
  ApiClient get client => _client;

  Future<String> login(String username, String password) async {
    final data = await _client.request(
      '/api/auth/login',
      method: 'POST',
      body: {'username': username, 'password': password},
    ) as Map<String, dynamic>;
    return data['token'] as String;
  }

  Future<void> logout() async {
    await _client.request('/api/auth/logout', method: 'POST');
  }

  Future<({String username, CalorieProfile profile})> session() async {
    final data = await _client.request('/api/auth/session') as Map<String, dynamic>;
    return (
      username: data['username'] as String,
      profile: CalorieProfile.fromJson(data['calorieProfile'] as Map<String, dynamic>),
    );
  }

  Future<List<Meal>> listMeals(String from, String to) async {
    final data = await _client.request(
      '/api/meals',
      queryParameters: {'from': from, 'to': to},
    ) as Map<String, dynamic>;
    return (data['meals'] as List<dynamic>)
        .map((item) => Meal.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<Meal> createMeal(Meal payload) async {
    final data = await _client.request(
      '/api/meals',
      method: 'POST',
      body: payload.toPayload(),
    ) as Map<String, dynamic>;
    return Meal.fromJson(data['meal'] as Map<String, dynamic>);
  }

  Future<Meal> updateMeal(String id, Meal payload) async {
    final data = await _client.request(
      '/api/meals/$id',
      method: 'PATCH',
      body: payload.toPayload(),
    ) as Map<String, dynamic>;
    return Meal.fromJson(data['meal'] as Map<String, dynamic>);
  }

  Future<void> deleteMeal(String id) async {
    await _client.request('/api/meals/$id', method: 'DELETE');
  }

  Future<void> reorderMeals(List<String> orderedIds) async {
    await _client.request(
      '/api/meals/reorder',
      method: 'PATCH',
      body: {'orderedIds': orderedIds},
    );
  }

  Future<List<MealTemplate>> listTemplates() async {
    final data = await _client.request('/api/templates') as Map<String, dynamic>;
    return (data['templates'] as List<dynamic>)
        .map((item) => MealTemplate.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<MealTemplate> createTemplate({
    required String name,
    required String title,
    String? notes,
    required List<Ingredient> ingredients,
  }) async {
    final data = await _client.request(
      '/api/templates',
      method: 'POST',
      body: {
        'name': name,
        'title': title,
        'notes': ?notes,
        'ingredients': ingredients.map((item) => item.toJson()).toList(),
      },
    ) as Map<String, dynamic>;
    return MealTemplate.fromJson(data['template'] as Map<String, dynamic>);
  }

  Future<void> deleteTemplate(String id) async {
    await _client.request('/api/templates/$id', method: 'DELETE');
  }

  Future<List<WeightEntry>> listWeights() async {
    final data = await _client.request('/api/weights') as Map<String, dynamic>;
    return (data['weights'] as List<dynamic>)
        .map((item) => WeightEntry.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<WeightEntry> createWeight(WeightEntry payload) async {
    final data = await _client.request(
      '/api/weights',
      method: 'POST',
      body: payload.toPayload(),
    ) as Map<String, dynamic>;
    return WeightEntry.fromJson(data['weight'] as Map<String, dynamic>);
  }

  Future<WeightEntry> updateWeight(String id, WeightEntry payload) async {
    final data = await _client.request(
      '/api/weights/$id',
      method: 'PATCH',
      body: payload.toPayload(),
    ) as Map<String, dynamic>;
    return WeightEntry.fromJson(data['weight'] as Map<String, dynamic>);
  }

  Future<void> deleteWeight(String id) async {
    await _client.request('/api/weights/$id', method: 'DELETE');
  }

  Future<StatsSummary> stats(StatsRange range, String today) async {
    final data = await _client.request(
      '/api/stats',
      queryParameters: {'range': range.json, 'today': today},
    ) as Map<String, dynamic>;
    return StatsSummary.fromJson(data);
  }

  Future<CalorieProfile> updateSettings(CalorieProfile profile) async {
    final data = await _client.request(
      '/api/settings',
      method: 'PUT',
      body: profile.toJson(),
    ) as Map<String, dynamic>;
    return CalorieProfile.fromJson(data['calorieProfile'] as Map<String, dynamic>);
  }
}