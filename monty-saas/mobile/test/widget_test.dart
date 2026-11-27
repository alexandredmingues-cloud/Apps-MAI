import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mon_agent_mobile/main.dart';
import 'package:mon_agent_mobile/core/network/api_client.dart';
import 'package:mon_agent_mobile/features/auth/data/session_repository.dart';
class _FakeSessions extends SessionRepository {
  _FakeSessions() : super(api: ApiClient(baseUrl: 'https://example.test'));
  @override
  Future<Map<String, dynamic>?> restore() async => null;
}
void main() {
  testWidgets('affiche l’écran de connexion MON-AGENT', (tester) async {
    await tester.pumpWidget(MaterialApp(home: AuthGate(
      sessions: _FakeSessions(), apiConfigured: true,
    )));
    await tester.pumpAndSettle();
    expect(find.text('MON-AGENT'), findsOneWidget);
    expect(find.text('Connexion'), findsOneWidget);
    expect(find.text('Adresse e-mail'), findsOneWidget);
    expect(find.text('Mot de passe'), findsOneWidget);
    expect(find.text('Se connecter'), findsOneWidget);
  });
  testWidgets('bloque une connexion sans URL API configurée', (tester) async {
    await tester.pumpWidget(MaterialApp(home: AuthGate(
      sessions: _FakeSessions(), apiConfigured: false,
    )));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Se connecter'));
    await tester.pump();
    expect(find.textContaining('API_BASE_URL'), findsOneWidget);
  });
}
