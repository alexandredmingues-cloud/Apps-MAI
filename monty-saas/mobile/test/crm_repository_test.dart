import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mon_agent_mobile/core/network/api_client.dart';
import 'package:mon_agent_mobile/features/crm/data/crm_repository.dart';
void main() {
  test('le CRM lit les contacts via le tenant authentifié', () async {
    final client = ApiClient(baseUrl: 'https://api.example.test', client: MockClient((request) async {
      expect(request.url.path, '/api/v1/crm/contacts');
      expect(request.headers['authorization'], 'Bearer jwt');
      return http.Response(jsonEncode({'count': 1, 'contacts': [{
        'id': 'c-1', 'full_name': 'Camille Martin', 'role': 'BUYER',
        'city': 'Montpellier', 'budget_euro': 250000,
      }]}), 200);
    }));
    client.accessToken = 'jwt';
    final contacts = await CrmRepository(client).contacts();
    expect(contacts.single.fullName, 'Camille Martin');
    expect(contacts.single.roleLabel, 'Acquéreur');
    client.close();
  });
  test('la création transmet les champs au format API snake_case', () async {
    final client = ApiClient(baseUrl: 'https://api.example.test', client: MockClient((request) async {
      expect(request.method, 'POST');
      expect(request.url.path, '/api/v1/crm/contacts');
      final body = jsonDecode(request.body) as Map<String, dynamic>;
      expect(body['full_name'], 'Alex Agent');
      expect(body['role'], 'SELLER');
      expect(body['consent_granted'], true);
      return http.Response(jsonEncode({'id':'c-2','full_name':'Alex Agent','role':'SELLER'}), 201);
    }));
    client.accessToken = 'jwt';
    final contact = await CrmRepository(client).createContact(fullName: 'Alex Agent', role: 'SELLER');
    expect(contact.id, 'c-2');
    client.close();
  });
}
