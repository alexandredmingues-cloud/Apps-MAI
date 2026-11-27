import '../../../core/network/api_client.dart';
import '../domain/crm_api_models.dart';
class CrmRepository {
  const CrmRepository(this.api);
  final ApiClient api;
  Future<List<CrmContact>> contacts({String query = ''}) async {
    final result = await api.getJson('/api/v1/crm/contacts',
      query: query.trim().isEmpty ? null : {'query': query.trim()});
    final rows = result['contacts'];
    if (rows is! List) throw const ApiException('Liste des contacts invalide.');
    return rows.whereType<Map<String, dynamic>>().map(CrmContact.fromJson).toList();
  }
  Future<CrmContact> createContact({
    required String fullName, String email = '', String phone = '',
    String city = '', int budgetEuro = 0, String role = 'LEAD',
    bool consentGranted = true,
  }) async {
    final result = await api.postJson('/api/v1/crm/contacts', body: {
      'full_name': fullName.trim(), 'email': email.trim(), 'phone': phone.trim(),
      'city': city.trim(), 'budget_euro': budgetEuro, 'role': role,
      'consent_granted': consentGranted,
    });
    return CrmContact.fromJson(result);
  }
  Future<void> addExchange(String contactId, String summary, {String channel = 'CALL'}) async {
    await api.postJson('/api/v1/crm/contacts/$contactId/exchanges',
      body: {'channel': channel, 'summary': summary.trim()});
  }
  Future<List<CrmFollowUp>> automaticFollowUps() async {
    final result = await api.getJson('/api/v1/crm/followups/auto');
    final rows = result['tasks'];
    if (rows is! List) throw const ApiException('Liste des relances invalide.');
    return rows.whereType<Map<String, dynamic>>().map(CrmFollowUp.fromJson).toList();
  }
  Future<void> completeFollowUp(String id) async {
    await api.postJson('/api/v1/crm/followups/$id/complete');
  }
}
