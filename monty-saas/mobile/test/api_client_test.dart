import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mon_agent_mobile/core/network/api_client.dart';
void main() {
  test('login respecte le contrat FastAPI', () async {
    final client = ApiClient(baseUrl: 'https://api.example.test/', client: MockClient((request) async {
      expect(request.url.path, '/api/v1/auth/login');
      expect(request.method, 'POST');
      expect(jsonDecode(request.body), {'email': 'agent@example.test', 'password': 'password-123'});
      return http.Response(jsonEncode({'access_token': 'signed.jwt.token', 'user_id': 'user-1',
        'agency_id': 'agency-1', 'role': 'AGENT', 'permissions': ['crm:read']}), 200);
    }));
    final response = await client.login(email: ' agent@example.test ', password: 'password-123');
    expect(response['access_token'], 'signed.jwt.token');
    client.accessToken = response['access_token'] as String;
    expect(client.accessToken, 'signed.jwt.token');
    client.close();
  });
  test('current user envoie le bearer et signale une session expirée', () async {
    final client = ApiClient(baseUrl: 'https://api.example.test', client: MockClient((request) async {
      expect(request.headers['authorization'], 'Bearer expired-token');
      return http.Response('{"detail":"Session invalide"}', 401);
    }));
    client.accessToken = 'expired-token';
    await expectLater(client.currentUser(),
      throwsA(isA<ApiException>().having((e) => e.statusCode, 'statusCode', 401)));
    client.close();
  });
}
