import 'dart:convert';
import 'package:http/http.dart' as http;
class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode});
  final String message;
  final int? statusCode;
  @override
  String toString() => message;
}
class ApiClient {
  ApiClient({required String baseUrl, http.Client? client})
      : _baseUri = Uri.parse(baseUrl.trim().replaceAll(RegExp(r'/+$'), '')),
        _client = client ?? http.Client();
  final Uri _baseUri;
  final http.Client _client;
  String? accessToken;
  Future<Map<String, dynamic>> login({required String email, required String password}) =>
      _request('POST', '/api/v1/auth/login',
          body: <String, Object>{'email': email.trim(), 'password': password});
  Future<Map<String, dynamic>> currentUser() => _request('GET', '/api/v1/auth/me', authenticated: true);
  Future<Map<String, dynamic>> getJson(String path, {Map<String, String>? query}) =>
      _request('GET', path, authenticated: true, query: query);
  Future<Map<String, dynamic>> postJson(String path, {Map<String, Object?>? body}) =>
      _request('POST', path, body: body, authenticated: true);
  Future<Map<String, dynamic>> _request(String method, String path,
      {Map<String, Object?>? body, bool authenticated = false,
      Map<String, String>? query}) async {
    final headers = <String, String>{'Accept': 'application/json'};
    if (body != null) headers['Content-Type'] = 'application/json';
    if (authenticated && (accessToken?.isNotEmpty ?? false)) {
      headers['Authorization'] = 'Bearer $accessToken';
    }
    late http.Response response;
    try {
      final resolved = _baseUri.resolve(path);
      final uri = query == null ? resolved : resolved.replace(queryParameters: query);
      response = method == 'POST'
          ? await _client.post(uri, headers: headers, body: jsonEncode(body))
          : await _client.get(uri, headers: headers);
    } on Exception {
      throw const ApiException('Serveur inaccessible. Vérifiez votre connexion.');
    }
    dynamic decoded;
    try {
      decoded = response.body.isEmpty ? <String, dynamic>{} : jsonDecode(response.body);
    } on FormatException {
      throw ApiException('Réponse serveur illisible.', statusCode: response.statusCode);
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final detail = decoded is Map ? decoded['detail'] : null;
      throw ApiException(detail is String ? detail : 'Erreur serveur.',
          statusCode: response.statusCode);
    }
    if (decoded is! Map<String, dynamic>) {
      throw ApiException('Format de réponse serveur inattendu.', statusCode: response.statusCode);
    }
    return decoded;
  }
  void close() => _client.close();
}
