import React, { useState } from 'react';
import { searchByReachability, TRAVEL_MODES, TRANSIT_TYPES } from './mon_agent_v3_reachability.js';

const MODE_LABELS = [
  [TRAVEL_MODES.CAR, 'Voiture', '🚗'],
  [TRAVEL_MODES.BIKE, 'Vélo', '🚲'],
  [TRAVEL_MODES.WALK, 'À pied', '🚶'],
  [TRAVEL_MODES.TRANSIT, 'Transports en commun', '🚆']
];
const TRANSIT_LABELS = { bus: 'Bus', tram: 'Tram', train: 'Train', metro: 'Métro' };

/**
 * Recherche connectée à un backend d'itinéraires. `onResults` reçoit la réponse
 * vérifiée du serveur. Aucun temps de trajet n'est inventé dans le navigateur.
 */
export default function ReachabilitySearch({ onResults, endpoint = '/api/search/reachability' }) {
  const [criteria, setCriteria] = useState({
    originAddress: '', radiusKm: '', maxTravelMinutes: '30',
    travelModes: [TRAVEL_MODES.CAR, TRAVEL_MODES.TRANSIT],
    transitTypes: ['bus', 'tram', 'train'], matchAnyMode: true, departureAt: ''
  });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const set = (key, value) => setCriteria(current => ({ ...current, [key]: value }));
  const toggle = (key, value) => setCriteria(current => ({ ...current, [key]: current[key].includes(value) ? current[key].filter(x => x !== value) : [...current[key], value] }));

  const submit = async event => {
    event.preventDefault(); setBusy(true); setMessage('Calcul des zones accessibles…');
    try {
      const result = await searchByReachability({ endpoint, criteria });
      onResults?.(result);
      setMessage(`${result.results.length} bien(s) trouvé(s).`);
    } catch (error) { setMessage(error.message || 'Impossible de calculer cette recherche.'); }
    finally { setBusy(false); }
  };

  return <section className="mx-auto max-w-5xl space-y-4 rounded-2xl bg-white p-4 text-slate-900 shadow">
    <header><p className="text-xs font-semibold uppercase tracking-wider text-emerald-800">Recherche intelligente</p><h2 className="text-2xl font-bold">Chercher autour d'une adresse</h2><p className="text-sm text-slate-600">Définissez une zone par distance ou par temps de trajet réel.</p></header>
    <form onSubmit={submit} className="space-y-4">
      <label className="block space-y-1 text-sm"><span className="font-semibold">Adresse de référence</span><input required minLength={5} value={criteria.originAddress} onChange={e => set('originAddress', e.target.value)} placeholder="Ex. : 45 avenue de Toulouse, Montpellier" className="w-full rounded-lg border px-3 py-3" /><span className="text-xs text-slate-500">Domicile, travail, gare, école ou toute autre adresse.</span></label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block space-y-1 text-sm"><span className="font-semibold">Rayon kilométrique</span><select value={criteria.radiusKm} onChange={e => set('radiusKm', e.target.value)} className="w-full rounded-lg border px-3 py-3"><option value="">Pas de limite de distance</option>{[2,5,10,15,20,30,50,100].map(v => <option key={v} value={v}>{v} km</option>)}</select></label>
        <label className="block space-y-1 text-sm"><span className="font-semibold">Temps de trajet maximum</span><select value={criteria.maxTravelMinutes} onChange={e => set('maxTravelMinutes', e.target.value)} className="w-full rounded-lg border px-3 py-3"><option value="">Pas de limite de temps</option>{[10,15,20,30,45,60,90,120,180,240].map(v => <option key={v} value={v}>{v} minutes</option>)}</select></label>
      </div>
      <div className="space-y-2"><p className="text-sm font-semibold">Modes de déplacement</p><div className="grid grid-cols-2 gap-2 sm:grid-cols-4">{MODE_LABELS.map(([value,label,emoji]) => <label key={value} className={`flex cursor-pointer items-center gap-2 rounded-lg border p-3 text-sm ${criteria.travelModes.includes(value) ? 'border-emerald-700 bg-emerald-50' : ''}`}><input type="checkbox" checked={criteria.travelModes.includes(value)} onChange={() => toggle('travelModes', value)} />{emoji} {label}</label>)}</div></div>
      {criteria.travelModes.includes(TRAVEL_MODES.TRANSIT) && <div className="space-y-2 rounded-xl bg-slate-50 p-3"><p className="text-sm font-semibold">Transports à prendre en compte</p><div className="flex flex-wrap gap-4">{TRANSIT_TYPES.map(type => <label key={type} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={criteria.transitTypes.includes(type)} onChange={() => toggle('transitTypes', type)} />{TRANSIT_LABELS[type]}</label>)}</div></div>}
      <div className="space-y-2"><p className="text-sm font-semibold">Combinaison des modes</p><label className="flex items-start gap-2 text-sm"><input type="radio" checked={criteria.matchAnyMode} onChange={() => set('matchAnyMode', true)} />Un bien est retenu si au moins un mode respecte le temps choisi.</label><label className="flex items-start gap-2 text-sm"><input type="radio" checked={!criteria.matchAnyMode} onChange={() => set('matchAnyMode', false)} />Un bien est retenu uniquement si tous les modes sélectionnés respectent le temps choisi.</label></div>
      <label className="block space-y-1 text-sm"><span className="font-semibold">Heure de départ (facultatif)</span><input type="datetime-local" value={criteria.departureAt} onChange={e => set('departureAt', e.target.value)} className="w-full rounded-lg border px-3 py-3" /><span className="text-xs text-slate-500">Utile pour les horaires de bus, tram et train. Sans horaire, le serveur doit appliquer une règle clairement indiquée.</span></label>
      <button disabled={busy} className="w-full rounded-lg bg-emerald-800 px-5 py-3 font-semibold text-white disabled:opacity-50">{busy ? 'Recherche en cours…' : 'Afficher les biens accessibles'}</button>
      {message && <p role="status" className="rounded-lg bg-slate-50 p-3 text-sm">{message}</p>}
    </form>
  </section>;
}
