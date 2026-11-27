import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mon_agent_mobile/main.dart';

void main() {
  testWidgets('affiche l’identité MON-AGENT et les trois cibles', (tester) async {
    await tester.pumpWidget(const MonAgentApp());

    expect(find.text('MON-AGENT'), findsOneWidget);
    expect(find.text('Android'), findsOneWidget);
    expect(find.text('iOS'), findsOneWidget);
    expect(find.text('Web'), findsOneWidget);
  });
}
