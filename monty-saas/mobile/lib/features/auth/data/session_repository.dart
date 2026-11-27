import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../core/network/api_client.dart';
class SessionRepository {
  SessionRepository({required this.api, FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();
  static const _tokenKey = 'mon_agent_access_token';
  final ApiClient api;
  final FlutterSecureStorage _storage;
  Future<Map<String, dynamic>> signIn(String email, String password) async {
    final session = await api.login(email: email, password: password);
    final token = session['access_token'];
    if (token is! String || token.isEmpty) {
      throw const ApiException('Le serveur n’a pas fourni de jeton de session.');
    }
    await _storage.write(key: _tokenKey, value: token);
    api.accessToken = token;
    return session;
  }
  Future<Map<String, dynamic>?> restore() async {
    final token = await _storage.read(key: _tokenKey);
    if (token == null || token.isEmpty) return null;
    api.accessToken = token;
    try {
      return await api.currentUser();
    } on ApiException catch (error) {
      if (error.statusCode == 401 || error.statusCode == 403) {
        await signOut();
        return null;
      }
      rethrow;
    }
  }
  Future<void> signOut() async {
    api.accessToken = null;
    await _storage.delete(key: _tokenKey);
  }
}
