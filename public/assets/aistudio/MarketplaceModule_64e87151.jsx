import React, { useMemo, useState } from 'react';
import {
  draftPropertyListing, estimateSaleTime, createListingRecord,
  searchMarketplaceListings, LISTING_SOURCES, generateListingWithAI
} from './mon_agent_v3_annonces.js';

const money = value => new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(Number(value || 0));

/** Écran de dépôt vendeur. La persistance réelle est déléguée à onSubmitListing (backend). */
export function SellerListingComposer({ user, marketData, onSubmitListing, aiEndpoint }) {
  const [form, setForm] = useState({
    title: '', propertyType: 'Appartement', city: 'Montpellier', district: '', address: '',
    askingPrice: '', marketValue: '', area: '', rooms: '', bedrooms: '', dpe: '',
    condition: 'Bon état', exterieur: '', stationnement: '', transport: '',
    neighborhoodDescription: '', highlightsText: '', description: '',
    ownerConfirmedAccuracy: false, legalPublicationConsent: false
  });
  const [photos, setPhotos] = useState([]);
  const [photoObservations, setPhotoObservations] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [saleEstimate, setSaleEstimate] = useState(null);
  const [preview, setPreview] = useState(null);
  const set = (key, value) => setForm(current => ({ ...current, [key]: value }));
  const photoUrls = useMemo(() => photos.map(file => ({ file, url: URL.createObjectURL(file) })), [photos]);

  const prepareText = () => {
    const result = draftPropertyListing({ ...form, highlights: form.highlightsText.split('\n').filter(Boolean), photos });
    set('title', result.title);
    set('description', result.description);
    setPreview(result);
    setNotice(result.missingFields.length ? `À compléter : ${result.missingFields.join(', ')}` : 'Brouillon généré. Relisez et vérifiez chaque information.');
  };

  const createWithAI = async () => {
    setBusy(true); setNotice('Analyse en cours…');
    try {
      const result = await generateListingWithAI({ endpoint: aiEndpoint, property: { ...form, highlights: form.highlightsText.split('\n').filter(Boolean) }, photoFiles: photos });
      setForm(current => ({ ...current, title: result.title, description: result.description }));
      setPreview(result);
      setNotice('Proposition IA reçue. Vérification humaine obligatoire avant publication.');
    } catch (error) { setNotice(error.message); }
    finally { setBusy(false); }
  };

  const calculateDelay = () => {
    const result = estimateSaleTime({ ...form, photoCount: photos.length, documentsComplete: false }, marketData);
    setSaleEstimate(result);
  };

  const submit = async event => {
    event.preventDefault();
    const record = {
      ...form,
      area: Number(form.area), askingPrice: Number(form.askingPrice),
      rooms: Number(form.rooms), bedrooms: Number(form.bedrooms),
      photos: photos.map(file => ({ name: file.name, file })),
      highlights: form.highlightsText.split('\n').filter(Boolean)
    };
    const result = createListingRecord(record, user);
    if (!result.validation.valid) { setNotice(result.validation.errors.join(' ')); return; }
    if (!onSubmitListing) { setNotice('Annonce préparée, mais aucun service serveur de publication n’est configuré.'); return; }
    setBusy(true);
    try { await onSubmitListing(result.listing); setNotice('Annonce envoyée pour vérification avant publication.'); }
    catch (error) { setNotice(error.message || 'Échec de l’enregistrement.'); }
    finally { setBusy(false); }
  };

  const field = (label, key, type = 'text', extra = {}) => <label className="block text-sm space-y-1"><span className="font-medium">{label}</span><input type={type} value={form[key]} onChange={e => set(key, e.target.value)} {...extra} className="w-full rounded-lg border border-slate-300 px-3 py-2" /></label>;

  return <section className="mx-auto max-w-4xl space-y-5 rounded-2xl bg-white p-4 text-slate-900 shadow">
    <header><p className="text-xs font-semibold uppercase tracking-wider text-emerald-800">Espace vendeur</p><h2 className="text-2xl font-bold">Publier une annonce</h2><p className="text-sm text-slate-600">Particulier ou professionnel : créez votre annonce, préparez le texte et soumettez-la à vérification.</p></header>
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        {field('Titre de l’annonce', 'title')}
        <label className="block text-sm space-y-1"><span className="font-medium">Type de bien</span><select value={form.propertyType} onChange={e => set('propertyType', e.target.value)} className="w-full rounded-lg border px-3 py-2"><option>Appartement</option><option>Maison</option><option>Villa</option><option>Terrain</option><option>Immeuble</option><option>Local commercial</option></select></label>
        {field('Ville', 'city')}{field('Quartier / secteur', 'district')}{field('Adresse (privée par défaut)', 'address')}
        {field('Prix demandé (€)', 'askingPrice', 'number', { min: 1 })}{field('Valeur de marché estimée (€)', 'marketValue', 'number', { min: 1 })}
        {field('Surface (m²)', 'area', 'number', { min: 1 })}{field('Nombre de pièces', 'rooms', 'number', { min: 1 })}{field('Chambres', 'bedrooms', 'number', { min: 0 })}
        <label className="block text-sm space-y-1"><span className="font-medium">DPE</span><select value={form.dpe} onChange={e => set('dpe', e.target.value)} className="w-full rounded-lg border px-3 py-2"><option value="">À renseigner</option>{['A','B','C','D','E','F','G'].map(v => <option key={v}>{v}</option>}</select></label>
        {field('État général', 'condition')}{field('Extérieur', 'exterieur')}{field('Stationnement', 'stationnement')}{field('Transports', 'transport')}
      </div>
      <label className="block text-sm space-y-1"><span className="font-medium">Description de l’environnement</span><textarea value={form.neighborhoodDescription} onChange={e => set('neighborhoodDescription', e.target.value)} rows={2} className="w-full rounded-lg border px-3 py-2" placeholder="Commerces, écoles, espaces verts, transports…" /></label>
      <label className="block text-sm space-y-1"><span className="font-medium">Atouts vérifiés (un par ligne)</span><textarea value={form.highlightsText} onChange={e => set('highlightsText', e.target.value)} rows={3} className="w-full rounded-lg border px-3 py-2" placeholder="Cuisine rénovée\nTerrasse exposée sud" /></label>
      <label className="block text-sm space-y-1"><span className="font-medium">Photos du bien (minimum 3)</span><input type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={e => setPhotos(Array.from(e.target.files || []))} className="block w-full text-sm" /></label>
      {!!photoUrls.length && <div className="grid grid-cols-3 gap-2">{photoUrls.map(({ file, url }) => <figure key={`${file.name}-${file.lastModified}`}><img src={url} alt={file.name} className="h-28 w-full rounded-lg object-cover" /><figcaption className="truncate text-xs text-slate-500">{file.name}</figcaption></figure>)}</div>}
      <div className="flex flex-wrap gap-2"><button type="button" onClick={prepareText} className="rounded-lg border px-4 py-2 text-sm font-semibold">Aide à la rédaction</button><button type="button" onClick={createWithAI} disabled={busy || !aiEndpoint} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">Rédaction IA + photos</button><button type="button" onClick={calculateDelay} className="rounded-lg border px-4 py-2 text-sm font-semibold">Estimer le délai de vente</button></div>
      {saleEstimate && <div className="rounded-xl bg-emerald-50 p-3 text-sm"><b>Délai indicatif :</b> {saleEstimate.status === 'estimated' ? `${saleEstimate.lowDays} à ${saleEstimate.highDays} jours (estimation centrale : ${saleEstimate.likelyDays} jours)` : 'Données locales insuffisantes.'}<p className="text-xs text-slate-600">Fiabilité : {saleEstimate.confidence}. {saleEstimate.warnings.join(' ')}</p>{saleEstimate.factors.map(f => <p key={f.label} className="text-xs">• {f.label} : {f.explanation}</p>)}</div>}
      <label className="block text-sm"><input type="checkbox" checked={form.ownerConfirmedAccuracy} onChange={e => set('ownerConfirmedAccuracy', e.target.checked)} className="mr-2" />Je confirme l’exactitude des informations et des photos.</label>
      <label className="block text-sm"><input type="checkbox" checked={form.legalPublicationConsent} onChange={e => set('legalPublicationConsent', e.target.checked)} className="mr-2" />J’autorise la publication de cette annonce et accepte les conditions de diffusion.</label>
      <label className="block text-sm space-y-1"><span className="font-medium">Texte de l’annonce</span><textarea value={form.description} onChange={e => set('description', e.target.value)} rows={9} className="w-full rounded-lg border px-3 py-2" /></label>
      <button disabled={busy} className="rounded-lg bg-emerald-800 px-5 py-3 font-semibold text-white disabled:opacity-40">Envoyer pour vérification</button>
      {notice && <p role="status" className="rounded-lg bg-amber-50 p-3 text-sm">{notice}</p>}
      {preview?.disclaimer && <p className="text-xs text-slate-500">{preview.disclaimer}</p>}
    </form>
  </section>;
}

/** Catalogue commun acquéreur. Le serveur doit fournir uniquement les annonces publiées. */
export function BuyerMarketplace({ listings = [] }) {
  const [filters, setFilters] = useState({ query: '', sources: [LISTING_SOURCES.PRIVATE, LISTING_SOURCES.AGENCY], city: '', propertyType: '', minPrice: '', maxPrice: '', minArea: '' });
  const results = useMemo(() => searchMarketplaceListings(listings, filters), [listings, filters]);
  const toggleSource = source => setFilters(current => ({ ...current, sources: current.sources.includes(source) ? current.sources.filter(x => x !== source) : [...current.sources, source] }));
  return <section className="mx-auto max-w-6xl space-y-4 p-4 text-slate-900">
    <header><p className="text-xs font-semibold uppercase tracking-wider text-emerald-800">Toutes les annonces</p><h2 className="text-2xl font-bold">Trouvez votre prochain logement</h2><p className="text-sm text-slate-600">Annonces de particuliers et d’agences réunies dans une même recherche.</p></header>
    <div className="grid gap-3 rounded-xl bg-white p-3 shadow sm:grid-cols-3">
      <input value={filters.query} onChange={e => setFilters({ ...filters, query: e.target.value })} placeholder="Mot-clé, quartier, ville…" className="rounded-lg border px-3 py-2" />
      <input value={filters.city} onChange={e => setFilters({ ...filters, city: e.target.value })} placeholder="Ville" className="rounded-lg border px-3 py-2" />
      <select value={filters.propertyType} onChange={e => setFilters({ ...filters, propertyType: e.target.value })} className="rounded-lg border px-3 py-2"><option value="">Tous les types</option>{['Appartement','Maison','Villa','Terrain','Immeuble','Local commercial'].map(x => <option key={x}>{x}</option>)}</select>
      <input type="number" placeholder="Prix min (€)" value={filters.minPrice} onChange={e => setFilters({ ...filters, minPrice: e.target.value })} className="rounded-lg border px-3 py-2" />
      <input type="number" placeholder="Prix max (€)" value={filters.maxPrice} onChange={e => setFilters({ ...filters, maxPrice: e.target.value })} className="rounded-lg border px-3 py-2" />
      <input type="number" placeholder="Surface min. (m²)" value={filters.minArea} onChange={e => setFilters({ ...filters, minArea: e.target.value })} className="rounded-lg border px-3 py-2" />
      <div className="flex flex-wrap gap-4 text-sm sm:col-span-3"><label><input type="checkbox" checked={filters.sources.includes(LISTING_SOURCES.PRIVATE)} onChange={() => toggleSource(LISTING_SOURCES.PRIVATE)} className="mr-2" />Particuliers</label><label><input type="checkbox" checked={filters.sources.includes(LISTING_SOURCES.AGENCY)} onChange={() => toggleSource(LISTING_SOURCES.AGENCY)} className="mr-2" />Agences</label></div>
    </div>
    <p className="text-sm text-slate-600">{results.length} annonce(s)</p>
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{results.map(item => <article key={item.id} className="overflow-hidden rounded-xl bg-white shadow"><div className="flex h-44 items-center justify-center bg-slate-100 text-slate-400">{item.coverUrl ? <img src={item.coverUrl} alt={item.title} className="h-full w-full object-cover" /> : 'Photo non disponible'}</div><div className="space-y-2 p-4"><div className="flex justify-between gap-2"><h3 className="font-semibold">{item.title}</h3><span className="h-fit rounded-full bg-slate-100 px-2 py-1 text-xs">{item.source === LISTING_SOURCES.AGENCY ? 'Agence' : 'Particulier'}</span></div><p className="text-sm text-slate-600">{item.district ? `${item.district}, ` : ''}{item.city}</p><p className="text-lg font-bold">{money(item.askingPrice)}</p><p className="text-sm text-slate-600">{item.area} m² · {item.rooms || '—'} pièces · DPE {item.dpe || 'NC'}</p><p className="line-clamp-3 text-sm">{item.description}</p><button onClick={() => window.dispatchEvent(new CustomEvent('monagent:open-listing', { detail: { id: item.id } }))} className="w-full rounded-lg border px-3 py-2 text-sm font-semibold">Voir l’annonce</button></div></article>)}</div>
    {results.length === 0 && <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-600">Aucune annonce ne correspond à ces critères.</p>}
  </section>;
}
