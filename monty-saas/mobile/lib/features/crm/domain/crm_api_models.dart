class CrmContact {
  const CrmContact({
    required this.id,
    required this.fullName,
    required this.role,
    this.email = '',
    this.phone = '',
    this.city = '',
    this.budgetEuro = 0,
    this.consentGranted = false,
    this.retentionUntil = '',
    this.lastContactAt = '',
    this.isAnonymized = false,
  });
  final String id, fullName, role, email, phone, city, retentionUntil, lastContactAt;
  final int budgetEuro;
  final bool consentGranted, isAnonymized;
  factory CrmContact.fromJson(Map<String, dynamic> json) => CrmContact(
    id: json['id'] as String? ?? '',
    fullName: json['full_name'] as String? ?? 'Contact sans nom',
    role: json['role'] as String? ?? 'LEAD',
    email: json['email'] as String? ?? '',
    phone: json['phone'] as String? ?? '',
    city: json['city'] as String? ?? '',
    budgetEuro: (json['budget_euro'] as num?)?.toInt() ?? 0,
    consentGranted: json['consent_granted'] as bool? ?? false,
    retentionUntil: json['retention_until'] as String? ?? '',
    lastContactAt: json['last_contact_at'] as String? ?? '',
    isAnonymized: json['is_anonymized'] as bool? ?? false,
  );
  String get roleLabel => switch (role) {
    'BUYER' => 'Acquéreur', 'SELLER' => 'Vendeur', 'PROSPECT' => 'Prospect',
    'PARTNER' => 'Partenaire', _ => 'Contact',
  };
}
class CrmFollowUp {
  const CrmFollowUp({required this.id, required this.title, this.dueAt = '', this.priority = 'MEDIUM'});
  final String id, title, dueAt, priority;
  factory CrmFollowUp.fromJson(Map<String, dynamic> json) => CrmFollowUp(
    id: json['id']?.toString() ?? '',
    title: json['title'] as String? ?? 'Relance',
    dueAt: json['due_at'] as String? ?? json['due_date'] as String? ?? '',
    priority: json['priority'] as String? ?? 'MEDIUM',
  );
}
