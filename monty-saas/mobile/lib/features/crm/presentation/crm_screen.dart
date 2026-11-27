import 'package:flutter/material.dart';
import '../../../core/network/api_client.dart';
import '../data/crm_repository.dart';
import '../domain/crm_api_models.dart';
class CrmScreen extends StatefulWidget {
  const CrmScreen({required this.repository, super.key});
  final CrmRepository repository;
  @override
  State<CrmScreen> createState() => _CrmScreenState();
}
class _CrmScreenState extends State<CrmScreen> {
  final _search = TextEditingController();
  List<CrmContact> _contacts = [];
  List<CrmFollowUp> _followUps = [];
  bool _loading = true, _saving = false;
  String? _error;
  @override
  void initState() { super.initState(); _load(); }
  @override
  void dispose() { _search.dispose(); super.dispose(); }
  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final results = await Future.wait([
        widget.repository.contacts(query: _search.text),
        widget.repository.automaticFollowUps(),
      ]);
      if (!mounted) return;
      setState(() {
        _contacts = results[0] as List<CrmContact>;
        _followUps = results[1] as List<CrmFollowUp>;
      });
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'Impossible de charger le CRM.');
    } finally { if (mounted) setState(() => _loading = false); }
  }
  Future<void> _createContact() async {
    final form = await showDialog<_NewContact>(context: context, builder: (_) => const _NewContactDialog());
    if (form == null) return;
    setState(() => _saving = true);
    try {
      await widget.repository.createContact(fullName: form.name, email: form.email,
        phone: form.phone, city: form.city, budgetEuro: form.budget, role: form.role);
      await _load();
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    } finally { if (mounted) setState(() => _saving = false); }
  }
  Future<void> _addExchange(CrmContact contact) async {
    final controller = TextEditingController();
    final summary = await showDialog<String>(context: context, builder: (context) => AlertDialog(
      title: Text('Nouvel échange — ${contact.fullName}'),
      content: TextField(controller: controller, autofocus: true, maxLines: 3,
        decoration: const InputDecoration(labelText: 'Compte rendu', hintText: 'Résumé de l’appel ou du rendez-vous')),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuler')),
        FilledButton(onPressed: () => Navigator.pop(context, controller.text), child: const Text('Enregistrer'))],
    ));
    controller.dispose();
    if (summary == null || summary.trim().length < 2) return;
    try { await widget.repository.addExchange(contact.id, summary); await _load(); }
    on ApiException catch (e) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message))); }
  }
  Future<void> _complete(CrmFollowUp task) async {
    try { await widget.repository.completeFollowUp(task.id); await _load(); }
    on ApiException catch (e) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message))); }
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('CRM & Contacts'), actions: [
      IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh), tooltip: 'Actualiser'),
    ]),
    floatingActionButton: FloatingActionButton.extended(onPressed: _saving ? null : _createContact,
      icon: const Icon(Icons.person_add_alt_1), label: const Text('Nouveau contact')),
    body: RefreshIndicator(onRefresh: _load, child: ListView(padding: const EdgeInsets.all(16), children: [
      TextField(controller: _search, textInputAction: TextInputAction.search,
        onSubmitted: (_) => _load(), decoration: InputDecoration(
          hintText: 'Rechercher un contact', prefixIcon: const Icon(Icons.search),
          suffixIcon: IconButton(onPressed: () { _search.clear(); _load(); }, icon: const Icon(Icons.clear)))),
      const SizedBox(height: 16),
      if (_error != null) Card(child: ListTile(leading: const Icon(Icons.error_outline, color: Colors.red),
        title: const Text('Chargement impossible'), subtitle: Text(_error!),
        trailing: IconButton(onPressed: _load, icon: const Icon(Icons.refresh)))),
      if (_loading) const Center(child: Padding(padding: EdgeInsets.all(28), child: CircularProgressIndicator())),
      if (!_loading && _error == null) ...[
        Row(children: [Text('Contacts', style: Theme.of(context).textTheme.titleLarge),
          const Spacer(), Text('${_contacts.length}', style: Theme.of(context).textTheme.titleMedium)]),
        if (_contacts.isEmpty) const Padding(padding: EdgeInsets.all(20), child: Text('Aucun contact pour le moment.')),
        ..._contacts.map((c) => Card(child: ListTile(
          leading: CircleAvatar(child: Text(c.fullName.isEmpty ? '?' : c.fullName.characters.first.toUpperCase())),
          title: Text(c.fullName), subtitle: Text([c.roleLabel, c.city, c.email, c.phone]
            .where((v) => v.isNotEmpty).join(' • ')),
          trailing: IconButton(tooltip: 'Ajouter un échange', onPressed: () => _addExchange(c),
            icon: const Icon(Icons.note_add_outlined)),
        ))),
        const SizedBox(height: 20),
        Text('Relances automatiques', style: Theme.of(context).textTheme.titleLarge),
        if (_followUps.isEmpty) const Padding(padding: EdgeInsets.all(16), child: Text('Aucune relance à traiter.')),
        ..._followUps.map((t) => Card(child: ListTile(
          leading: const Icon(Icons.notifications_active_outlined),
          title: Text(t.title), subtitle: Text(t.dueAt),
          trailing: IconButton(tooltip: 'Terminer', onPressed: () => _complete(t),
            icon: const Icon(Icons.check_circle_outline)),
        ))),
      ],
    ])),
  );
}
class _NewContact {
  const _NewContact(this.name, this.email, this.phone, this.city, this.budget, this.role);
  final String name, email, phone, city, role;
  final int budget;
}
class _NewContactDialog extends StatefulWidget {
  const _NewContactDialog();
  @override
  State<_NewContactDialog> createState() => _NewContactDialogState();
}
class _NewContactDialogState extends State<_NewContactDialog> {
  final _key = GlobalKey<FormState>();
  final _name = TextEditingController(), _email = TextEditingController(),
    _phone = TextEditingController(), _city = TextEditingController(), _budget = TextEditingController();
  String _role = 'LEAD';
  @override
  void dispose() { _name.dispose(); _email.dispose(); _phone.dispose(); _city.dispose(); _budget.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Créer un contact'),
    content: SizedBox(width: 440, child: SingleChildScrollView(child: Form(key: _key, child: Column(mainAxisSize: MainAxisSize.min, children: [
      TextFormField(controller: _name, autofocus: true, decoration: const InputDecoration(labelText: 'Nom complet *'),
        validator: (v) => v == null || v.trim().length < 2 ? 'Nom obligatoire (2 caractères minimum).' : null),
      TextFormField(controller: _email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'E-mail')),
      TextFormField(controller: _phone, keyboardType: TextInputType.phone, decoration: const InputDecoration(labelText: 'Téléphone')),
      TextFormField(controller: _city, decoration: const InputDecoration(labelText: 'Ville / secteur')),
      TextFormField(controller: _budget, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Budget (€)')),
      const SizedBox(height: 12),
      DropdownButtonFormField<String>(initialValue: _role, decoration: const InputDecoration(labelText: 'Type de contact'),
        items: const [DropdownMenuItem(value: 'LEAD', child: Text('Lead')), DropdownMenuItem(value: 'PROSPECT', child: Text('Prospect')),
          DropdownMenuItem(value: 'BUYER', child: Text('Acquéreur')), DropdownMenuItem(value: 'SELLER', child: Text('Vendeur')),
          DropdownMenuItem(value: 'PARTNER', child: Text('Partenaire'))],
        onChanged: (v) { if (v != null) setState(() => _role = v); }),
    ])))),
    actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuler')),
      FilledButton(onPressed: () {
        if (!_key.currentState!.validate()) return;
        final amount = int.tryParse(_budget.text.replaceAll(' ', '')) ?? 0;
        Navigator.pop(context, _NewContact(_name.text, _email.text, _phone.text, _city.text,
          amount.clamp(0, 100000000).toInt(), _role));
      }, child: const Text('Créer'))],
  );
}
