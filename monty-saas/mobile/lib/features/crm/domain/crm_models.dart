/// CRM domain rules ported from app/.../SaasModels.kt.
///
/// This file contains client-side domain representation only. Authorization,
/// tenant isolation and persistence remain enforced by the FastAPI backend.
enum SaasPlan {
  individual('INDIVIDUAL', 'Formule Individuelle',
      'Agent indépendant • 1 poste • CRM complet, relances & export Excel',
      '39 € HT / mois'),
  agency('AGENCY', 'Formule Agence',
      'Multi-collaborateurs (jusqu\'à 25) • Rôles RBAC, répartition & audit',
      '149 € HT / mois');

  const SaasPlan(this.code, this.label, this.subtitle, this.priceMonthlyLabel);

  final String code;
  final String label;
  final String subtitle;
  final String priceMonthlyLabel;

  static SaasPlan fromCode(String? code) {
    for (final plan in SaasPlan.values) {
      if (plan.code == code) return plan;
    }
    return SaasPlan.individual;
  }
}

enum AgencyRole {
  admin('ADMIN', 'Administrateur'),
  director('DIRECTOR', 'Responsable d\'agence'),
  agent('AGENT', 'Agent immobilier');

  const AgencyRole(this.code, this.labelFr);

  final String code;
  final String labelFr;

  bool get isManagerOrAdmin => this == admin || this == director;

  static AgencyRole fromCode(String? code) {
    for (final role in AgencyRole.values) {
      if (role.code == code) return role;
    }
    return AgencyRole.agent;
  }
}

abstract final class CrmCatalog {
  static const contactTypes = <String>[
    'Acquéreur', 'Vendeur', 'Propriétaire', 'Locataire',
    'Investisseur', 'Partenaire', 'Autre',
  ];
  static const contactStatuses = <String>[
    'Nouveau', 'À qualifier', 'Qualifié', 'Actif',
    'En attente', 'Converti', 'Perdu',
  ];
  static const priorities = <String>['Faible', 'Normal', 'Élevé', 'Urgent'];
  static const sources = <String>[
    'Manuel', 'Import Excel', 'Portail', 'Site web',
    'Recommandation', 'Vitrine', 'Autre',
  ];
  static const financingModes = <String>[
    'Crédit immobilier', 'Comptant', 'Mixte (Apport + Prêt)',
    'Prêt relais', 'À définir',
  ];
  static const timelines = <String>[
    'Immédiat (< 1 mois)', 'Sous 3 mois', '3 à 6 mois',
    '6 à 12 mois', 'Veille / Réflexion',
  ];
  static const propertyTypes = <String>[
    'Appartement', 'Maison', 'Villa', 'Terrain',
    'Immeuble / Investissement', 'Local commercial', 'Autre',
  ];
  static const exchangeChannels = <String>['Appel', 'SMS', 'Email', 'Note'];
}

List<String> computeViewerUids(
  List<String> directorUids,
  String assignedToUid,
) {
  return <String>[...directorUids, assignedToUid]
      .map((uid) => uid.trim())
      .where((uid) => uid.isNotEmpty)
      .toSet()
      .take(25)
      .toList(growable: false);
}

class ContactExchangeEntry {
  const ContactExchangeEntry({
    this.id = '',
    this.dateTime = '',
    this.channel = 'Note',
    this.authorName = '',
    this.summary = '',
  });

  final String id;
  final String dateTime;
  final String channel;
  final String authorName;
  final String summary;

  Map<String, Object> toMap() => <String, Object>{
        'id': _limit(id, 64),
        'dateTime': _limit(dateTime, 40),
        'channel': _limit(channel, 30),
        'authorName': _limit(authorName, 120),
        'summary': _limit(summary, 800),
      };
}

class CrmContact {
  const CrmContact({
    this.id = '',
    this.agencyId = '',
    this.createdByUid = '',
    this.assignedToUid = '',
    this.assignedToName = '',
    this.firstName = '',
    this.lastName = '',
    this.fullName = '',
    this.email = '',
    this.phone = '',
    this.postalAddress = '',
    this.contactType = 'Acquéreur',
    this.status = 'Nouveau',
    this.priority = 'Normal',
    this.budgetMin = 0,
    this.budgetMax = 0,
    this.sector = '',
    this.desiredPropertyType = '',
    this.desiredSurfaceMin = 0,
    this.desiredRoomsMin = 0,
    this.financingMode = '',
    this.projectTimeline = '',
    this.source = 'Manuel',
    this.notes = '',
    this.lastContactedAt = '',
    this.nextFollowUpAt = '',
    this.linkedPropertyId = '',
    this.linkedPropertyTitle = '',
    this.archived = false,
    this.exchangeLogs = const <ContactExchangeEntry>[],
    this.viewerUids = const <String>[],
    this.rgpdConsentGranted = false,
    this.rgpdConsentDate = '',
    this.rgpdLegalBasis = 'Consentement explicite (Art. 6.1.a)',
    this.rgpdRetentionUntil = '',
  });

  final String id;
  final String agencyId;
  final String createdByUid;
  final String assignedToUid;
  final String assignedToName;
  final String firstName;
  final String lastName;
  final String fullName;
  final String email;
  final String phone;
  final String postalAddress;
  final String contactType;
  final String status;
  final String priority;
  final int budgetMin;
  final int budgetMax;
  final String sector;
  final String desiredPropertyType;
  final int desiredSurfaceMin;
  final int desiredRoomsMin;
  final String financingMode;
  final String projectTimeline;
  final String source;
  final String notes;
  final String lastContactedAt;
  final String nextFollowUpAt;
  final String linkedPropertyId;
  final String linkedPropertyTitle;
  final bool archived;
  final List<ContactExchangeEntry> exchangeLogs;
  final List<String> viewerUids;
  final bool rgpdConsentGranted;
  final String rgpdConsentDate;
  final String rgpdLegalBasis;
  final String rgpdRetentionUntil;

  bool isRgpdRetentionExpired(String todayIso) {
    final clean = rgpdRetentionUntil.trim().substring(
      0,
      rgpdRetentionUntil.trim().length.clamp(0, 10).toInt(),
    );
    return clean.length >= 10 && clean.compareTo(todayIso) < 0;
  }

  Map<String, Object> toCreateMap() => <String, Object>{
        'agencyId': _limit(agencyId, 128),
        'createdByUid': _limit(createdByUid, 128),
        'assignedToUid': _limit(assignedToUid, 128),
        'assignedToName': _limit(assignedToName, 120),
        'firstName': _limit(firstName, 80),
        'lastName': _limit(lastName, 80),
        'fullName': _limit(fullName, 140),
        'email': _limit(email, 160),
        'phone': _limit(phone, 40),
        'postalAddress': _limit(postalAddress, 220),
        'contactType': normalizeContactType(contactType),
        'status': _limit(status, 60),
        'priority': normalizePriority(priority),
        'budgetMin': budgetMin.clamp(0, 50000000),
        'budgetMax': budgetMax.clamp(0, 50000000),
        'sector': _limit(sector, 160),
        'desiredPropertyType': _limit(desiredPropertyType, 80),
        'desiredSurfaceMin': desiredSurfaceMin.clamp(0, 5000),
        'desiredRoomsMin': desiredRoomsMin.clamp(0, 30),
        'financingMode': _limit(financingMode, 80),
        'projectTimeline': _limit(projectTimeline, 80),
        'source': _limit(source, 60),
        'notes': _limit(notes, 2500),
        'lastContactedAt': _limit(lastContactedAt, 40),
        'nextFollowUpAt': _limit(nextFollowUpAt, 40),
        'linkedPropertyId': _limit(linkedPropertyId, 128),
        'linkedPropertyTitle': _limit(linkedPropertyTitle, 160),
        'archived': archived,
        'exchangeLogs': exchangeLogs.take(60).map((entry) => entry.toMap()).toList(),
        'viewerUids': viewerUids.toSet().take(25).toList(),
        'rgpdConsentGranted': rgpdConsentGranted,
        'rgpdConsentDate': _limit(rgpdConsentDate, 30),
        'rgpdLegalBasis': _limit(rgpdLegalBasis, 80),
        'rgpdRetentionUntil': _limit(rgpdRetentionUntil, 20),
      };

  static String normalizeContactType(String raw) =>
      CrmCatalog.contactTypes.contains(raw) || raw == 'Mixte'
          ? raw
          : 'Acquéreur';

  static String normalizePriority(String raw) =>
      CrmCatalog.priorities.contains(raw) ? raw : 'Normal';
}

class FollowUpTask {
  const FollowUpTask({
    this.id = '',
    this.agencyId = '',
    this.contactId = '',
    this.contactName = '',
    this.createdByUid = '',
    this.assignedToUid = '',
    this.assignedToName = '',
    this.title = '',
    this.channel = 'Appel',
    this.dueDate = '',
    this.dueTime = '10:00',
    this.priority = 'Normale',
    this.status = 'À faire',
    this.autoGenerated = false,
    this.viewerUids = const <String>[],
  });

  final String id;
  final String agencyId;
  final String contactId;
  final String contactName;
  final String createdByUid;
  final String assignedToUid;
  final String assignedToName;
  final String title;
  final String channel;
  final String dueDate;
  final String dueTime;
  final String priority;
  final String status;
  final bool autoGenerated;
  final List<String> viewerUids;

  bool isDueToday(String todayIso) => status != 'Terminée' && dueDate == todayIso;

  bool isOverdue(String todayIso, String currentTimeHHmm) {
    if (status == 'Terminée' || dueDate.isEmpty) return false;
    if (dueDate.compareTo(todayIso) < 0) return true;
    if (dueDate == todayIso) {
      final time = dueTime.isEmpty ? '23:59' : dueTime.substring(0, dueTime.length.clamp(0, 5).toInt());
      return time.compareTo(currentTimeHHmm) < 0;
    }
    return false;
  }

  Map<String, Object> toCreateMap() => <String, Object>{
        'agencyId': _limit(agencyId, 128),
        'contactId': _limit(contactId, 128),
        'contactName': _limit(contactName, 140),
        'createdByUid': _limit(createdByUid, 128),
        'assignedToUid': _limit(assignedToUid, 128),
        'assignedToName': _limit(assignedToName, 120),
        'title': _limit(title, 200),
        'channel': const <String>{'Email', 'SMS', 'Visite'}.contains(channel)
            ? channel
            : 'Appel',
        'dueDate': _limit(dueDate, 40),
        'dueTime': _limit(dueTime, 10),
        'priority': const <String>{
          'Haute', 'Urgente', 'Normale', 'Faible', 'Basse',
        }.contains(priority)
            ? priority
            : 'Normale',
        'status': const <String>{'En cours', 'Terminée'}.contains(status)
            ? status
            : 'À faire',
        'autoGenerated': autoGenerated,
        'viewerUids': viewerUids.toSet().take(25).toList(),
      };
}

class CrmDashboardMetrics {
  const CrmDashboardMetrics({
    this.totalContacts = 0,
    this.newContacts = 0,
    this.toQualifyContacts = 0,
    this.activeProspects = 0,
    this.todayFollowUps = 0,
    this.overdueFollowUps = 0,
    this.convertedContacts = 0,
    this.conversionRatePercent = 0,
  });

  final int totalContacts;
  final int newContacts;
  final int toQualifyContacts;
  final int activeProspects;
  final int todayFollowUps;
  final int overdueFollowUps;
  final int convertedContacts;
  final double conversionRatePercent;
}

CrmDashboardMetrics computeCrmDashboardMetrics({
  required List<CrmContact> contacts,
  required List<FollowUpTask> tasks,
  required String todayIso,
  required String currentTimeHHmm,
}) {
  final activeContacts = contacts.where((contact) => !contact.archived).toList();
  final total = activeContacts.length;
  final converted = activeContacts.where((contact) => contact.status == 'Converti').length;
  final todayCount = tasks.where((task) => task.isDueToday(todayIso)).length;
  final overdueCount = tasks.where((task) => task.isOverdue(todayIso, currentTimeHHmm)).length;

  return CrmDashboardMetrics(
    totalContacts: total,
    newContacts: activeContacts.where((contact) => contact.status == 'Nouveau').length,
    toQualifyContacts: activeContacts.where((contact) => contact.status == 'À qualifier').length,
    activeProspects: activeContacts.where((contact) =>
        const <String>{'Actif', 'Qualifié', 'Visite planifiée'}.contains(contact.status)).length,
    todayFollowUps: todayCount,
    overdueFollowUps: overdueCount,
    convertedContacts: converted,
    conversionRatePercent: total == 0 ? 0 : (converted * 1000 / total).round() / 10,
  );
}

String _limit(String value, int maxLength) =>
    value.length <= maxLength ? value : value.substring(0, maxLength);
