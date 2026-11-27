import 'package:flutter_test/flutter_test.dart';
import 'package:mon_agent_mobile/features/crm/domain/crm_models.dart';

void main() {
  group('Agency roles and plans', () {
    test('maps known codes and uses the legacy safe defaults', () {
      expect(AgencyRole.fromCode('DIRECTOR'), AgencyRole.director);
      expect(AgencyRole.fromCode('unexpected'), AgencyRole.agent);
      expect(AgencyRole.admin.isManagerOrAdmin, isTrue);
      expect(SaasPlan.fromCode('AGENCY'), SaasPlan.agency);
      expect(SaasPlan.fromCode(null), SaasPlan.individual);
    });
  });

  group('CRM domain rules', () {
    test('deduplicates viewer UIDs and limits exposure list', () {
      expect(
        computeViewerUids(<String>[' director ', 'agent', 'director'], ' agent '),
        <String>['director', 'agent'],
      );
    });

    test('normalizes contact type and priority and bounds sensitive input', () {
      final contact = CrmContact(
        contactType: 'unexpected',
        priority: 'critical',
        budgetMax: 90000000,
        notes: List<String>.filled(2600, 'x').join(),
      );
      final payload = contact.toCreateMap();
      expect(payload['contactType'], 'Acquéreur');
      expect(payload['priority'], 'Normal');
      expect(payload['budgetMax'], 50000000);
      expect((payload['notes']! as String).length, 2500);
    });

    test('flags expired RGPD retention dates without accepting blanks', () {
      expect(
        const CrmContact(rgpdRetentionUntil: '2026-01-01').isRgpdRetentionExpired('2026-02-01'),
        isTrue,
      );
      expect(const CrmContact().isRgpdRetentionExpired('2026-02-01'), isFalse);
    });

    test('calculates follow-up status using date and time', () {
      const overdue = FollowUpTask(dueDate: '2026-11-26', status: 'À faire');
      const dueNow = FollowUpTask(dueDate: '2026-11-27', dueTime: '09:30');
      const done = FollowUpTask(dueDate: '2026-11-20', status: 'Terminée');
      expect(overdue.isOverdue('2026-11-27', '10:00'), isTrue);
      expect(dueNow.isDueToday('2026-11-27'), isTrue);
      expect(dueNow.isOverdue('2026-11-27', '09:00'), isFalse);
      expect(done.isOverdue('2026-11-27', '10:00'), isFalse);
    });

    test('builds dashboard KPIs excluding archived contacts', () {
      final result = computeCrmDashboardMetrics(
        contacts: const <CrmContact>[
          CrmContact(status: 'Nouveau'),
          CrmContact(status: 'Converti'),
          CrmContact(status: 'Actif', archived: true),
        ],
        tasks: const <FollowUpTask>[
          FollowUpTask(dueDate: '2026-11-27'),
          FollowUpTask(dueDate: '2026-11-26'),
        ],
        todayIso: '2026-11-27',
        currentTimeHHmm: '10:00',
      );
      expect(result.totalContacts, 2);
      expect(result.newContacts, 1);
      expect(result.convertedContacts, 1);
      expect(result.conversionRatePercent, 50);
      expect(result.todayFollowUps, 1);
      expect(result.overdueFollowUps, 1);
    });
  });
}
