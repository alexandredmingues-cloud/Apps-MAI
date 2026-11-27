import 'package:flutter/material.dart';
import 'core/network/api_client.dart';
import 'features/auth/data/session_repository.dart';
const _brand = Color(0xFF174B3A);
const _apiBaseUrl = String.fromEnvironment('API_BASE_URL');
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MonAgentApp());
}
class MonAgentApp extends StatelessWidget {
  const MonAgentApp({super.key});
  @override
  Widget build(BuildContext context) {
    final sessions = SessionRepository(api: ApiClient(
      baseUrl: _apiBaseUrl.isEmpty ? 'http://localhost:8000' : _apiBaseUrl,
    ));
    return MaterialApp(
      title: 'MON-AGENT',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: _brand),
        scaffoldBackgroundColor: const Color(0xFFF7F8F6),
        inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
      ),
      home: AuthGate(sessions: sessions, apiConfigured: _apiBaseUrl.isNotEmpty),
    );
  }
}
class AuthGate extends StatefulWidget {
  const AuthGate({required this.sessions, required this.apiConfigured, super.key});
  final SessionRepository sessions;
  final bool apiConfigured;
  @override
  State<AuthGate> createState() => _AuthGateState();
}
class _AuthGateState extends State<AuthGate> {
  Map<String, dynamic>? _user;
  bool _loading = true;
  String? _error;
  @override
  void initState() {
    super.initState();
    widget.sessions.restore().then((user) {
      if (mounted) setState(() => _user = user);
    }).catchError((Object error) {
      if (mounted) setState(() => _error = error.toString());
    }).whenComplete(() {
      if (mounted) setState(() => _loading = false);
    });
  }
  void _signedIn(Map<String, dynamic> session) => setState(() { _user = session; _error = null; });
  Future<void> _signOut() async {
    await widget.sessions.signOut();
    if (mounted) setState(() => _user = null);
  }
  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    if (_user != null) return HomeScreen(user: _user!, onSignOut: _signOut);
    return LoginScreen(sessions: widget.sessions, apiConfigured: widget.apiConfigured,
        initialError: _error, onSignedIn: _signedIn);
  }
}
class LoginScreen extends StatefulWidget {
  const LoginScreen({required this.sessions, required this.apiConfigured,
    required this.onSignedIn, this.initialError, super.key});
  final SessionRepository sessions;
  final bool apiConfigured;
  final String? initialError;
  final ValueChanged<Map<String, dynamic>> onSignedIn;
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}
class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;
  @override
  void dispose() { _email.dispose(); _password.dispose(); super.dispose(); }
  Future<void> _submit() async {
    if (!widget.apiConfigured) {
      setState(() => _error = 'API_BASE_URL doit être configurée pour connecter MON-AGENT.');
      return;
    }
    if (!_formKey.currentState!.validate()) return;
    setState(() { _busy = true; _error = null; });
    try {
      final session = await widget.sessions.signIn(_email.text, _password.text);
      if (mounted) widget.onSignedIn(session);
    } on ApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'Connexion impossible. Réessayez.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 460),
      child: SingleChildScrollView(padding: const EdgeInsets.all(28),
        child: Form(key: _formKey, child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Icon(Icons.apartment_rounded, size: 52, color: _brand),
          const SizedBox(height: 12),
          Text('MON-AGENT', textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(color: _brand, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          const Text('L’humain avant tout.', textAlign: TextAlign.center),
          const SizedBox(height: 36),
          Text('Connexion', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 16),
          TextFormField(controller: _email, keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.username, AutofillHints.email],
            decoration: const InputDecoration(labelText: 'Adresse e-mail'),
            validator: (v) => v == null || !v.contains('@') ? 'Adresse e-mail invalide.' : null),
          const SizedBox(height: 12),
          TextFormField(controller: _password, obscureText: true,
            autofillHints: const [AutofillHints.password],
            decoration: const InputDecoration(labelText: 'Mot de passe'),
            validator: (v) => v == null || v.length < 8 ? '8 caractères minimum.' : null,
            onFieldSubmitted: (_) => _submit()),
          if (_error != null || widget.initialError != null) ...[
            const SizedBox(height: 12),
            Text(_error ?? widget.initialError!, style: const TextStyle(color: Colors.red)),
          ],
          const SizedBox(height: 20),
          FilledButton(onPressed: _busy ? null : _submit, child: _busy
            ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
            : const Text('Se connecter')),
          const SizedBox(height: 24),
          const Text('Accès sécurisé • Android • iOS • Web', textAlign: TextAlign.center,
            style: TextStyle(color: Colors.black54)),
        ])),
      ),
    )),
  );
}
class HomeScreen extends StatelessWidget {
  const HomeScreen({required this.user, required this.onSignOut, super.key});
  final Map<String, dynamic> user;
  final Future<void> Function() onSignOut;
  @override
  Widget build(BuildContext context) {
    final name = user['display_name'] as String? ?? user['email'] as String? ?? 'Utilisateur';
    final role = user['role'] as String? ?? 'Compte';
    return Scaffold(
      appBar: AppBar(title: const Text('MON-AGENT'),
        actions: [IconButton(tooltip: 'Déconnexion', onPressed: onSignOut, icon: const Icon(Icons.logout))]),
      body: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 900),
        child: ListView(padding: const EdgeInsets.all(24), children: [
          Text('Bonjour $name', style: Theme.of(context).textTheme.headlineMedium),
          Text(role, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 24),
          const Text('Votre espace immobilier', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
          const _ModuleTile(Icons.people_alt_outlined, 'CRM & Contacts', 'Prospects, clients et suivi commercial'),
          const _ModuleTile(Icons.home_work_outlined, 'Biens immobiliers', 'Portefeuille, mandats et visites'),
          const _ModuleTile(Icons.calculate_outlined, 'Estimation & DVF', 'Valeurs, comparables et analyse de marché'),
          const _ModuleTile(Icons.calendar_month_outlined, 'Agenda & Relances', 'Actions à venir et échéances'),
          const _ModuleTile(Icons.folder_open_outlined, 'Documents', 'Dossiers vendeurs et acquéreurs'),
          const SizedBox(height: 12),
          const Text('Les modules métier seront activés progressivement après raccordement et tests des API.',
            style: TextStyle(color: Colors.black54)),
        ]))),
    );
  }
}
class _ModuleTile extends StatelessWidget {
  const _ModuleTile(this.icon, this.title, this.detail);
  final IconData icon;
  final String title;
  final String detail;
  @override
  Widget build(BuildContext context) => Card(child: ListTile(
    leading: Icon(icon, color: _brand), title: Text(title), subtitle: Text(detail),
    trailing: const Icon(Icons.chevron_right),
  ));
}
