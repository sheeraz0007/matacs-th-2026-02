// Deterministic half of the triage design: pass/fail checks, weighted scoring, ranking.
//
// The AI step (fact extraction) is described in PROMPTS.md. This program starts from the
// extracted facts, so the same facts and the same criteria always give the same ranking.
//
// Run (Node 18 or newer, no dependencies):  node score.js

// ---- criteria (an admin would edit these; here they are fixed for the sample category) ----
const BUDGET_CEILING_CAD = 260000;
const MAX_WEEKS = 16;
const EXPERIENCE_FULL_MARKS_AT = 14;
const WORKMANSHIP_FULL_MARKS_AT_YEARS = 12;
const WEIGHTS = { experience: 30, price: 25, timeline: 15, warranty: 15, team: 15 };

// Facts a verified extraction step would produce. Entered by hand for this prototype.
const PROPOSALS = [
  { id: 'A', licenceHeld: true,  wsibAndInsurance: true, installs100kw5y: 14, priceCad: 238000, weeks: 12,   workmanshipYears: 10, namedLead: true,  crew: true,  safetyPlan: true,  monitoring: true,  injectionAttempt: false },
  { id: 'B', licenceHeld: false, wsibAndInsurance: true, installs100kw5y: 6,  priceCad: 189500, weeks: 10,   workmanshipYears: 5,  namedLead: false, crew: false, safetyPlan: false, monitoring: false, injectionAttempt: false },
  { id: 'C', licenceHeld: true,  wsibAndInsurance: true, installs100kw5y: 9,  priceCad: 244000, weeks: 14,   workmanshipYears: 10, namedLead: true,  crew: true,  safetyPlan: false, monitoring: false, injectionAttempt: true },
  { id: 'D', licenceHeld: true,  wsibAndInsurance: true, installs100kw5y: 11, priceCad: 231000, weeks: null, workmanshipYears: 10, namedLead: true,  crew: false, safetyPlan: true,  monitoring: false, injectionAttempt: false },
  { id: 'E', licenceHeld: true,  wsibAndInsurance: true, installs100kw5y: 8,  priceCad: 242500, weeks: 13,   workmanshipYears: 12, namedLead: true,  crew: true,  safetyPlan: true,  monitoring: true,  injectionAttempt: false },
];

function failures(f) {
  const out = [];
  if (!f.licenceHeld) out.push('licence not held by bidder');
  if (!f.wsibAndInsurance) out.push('WSIB/insurance not confirmed');
  if (f.priceCad > BUDGET_CEILING_CAD) out.push('over budget');
  if (f.weeks !== null && f.weeks > MAX_WEEKS) out.push('over schedule limit');
  return out;
}

function criterionScores(f, bestPrice, bestWeeks) {
  const teamItems = [f.namedLead, f.crew, f.safetyPlan, f.monitoring].filter(Boolean).length;
  return {
    experience: Math.min(f.installs100kw5y / EXPERIENCE_FULL_MARKS_AT, 1) * 100,
    price: (bestPrice / f.priceCad) * 100,
    timeline: f.weeks === null ? 0 : (bestWeeks / f.weeks) * 100,
    warranty: Math.min(f.workmanshipYears / WORKMANSHIP_FULL_MARKS_AT_YEARS, 1) * 100,
    team: (teamItems / 4) * 100,
  };
}

function total(scores) {
  const t = Object.entries(WEIGHTS).reduce((sum, [k, w]) => sum + (scores[k] * w) / 100, 0);
  return Math.round(t * 10) / 10;
}

if (Object.values(WEIGHTS).reduce((a, b) => a + b, 0) !== 100) {
  throw new Error('weights must add up to 100');
}

// price and timeline are relative to the best eligible bid; the rest use fixed caps
const eligible = PROPOSALS.filter((f) => failures(f).length === 0);
const bestPrice = Math.min(...eligible.map((f) => f.priceCad));
const bestWeeks = Math.min(...eligible.filter((f) => f.weeks !== null).map((f) => f.weeks));

const ranked = [];
const others = [];
const shadow = {};

for (const f of PROPOSALS) {
  const failed = failures(f);
  if (failed.length > 0) {
    others.push({ proposal: f.id, rank: null, status: 'ineligible', score: null, reason: `Fails check: ${failed.join(', ')}.` });
    continue;
  }
  const parts = criterionScores(f, bestPrice, bestWeeks);
  if (f.injectionAttempt) {
    shadow[f.id] = total(parts); // for the reviewer only, never used for ranking
    others.push({ proposal: f.id, rank: null, status: 'flagged', score: null, reason: 'Contains text aimed at the review system asking for a top rank; held for a human, not auto-ranked.' });
    continue;
  }
  ranked.push({ f, parts, total: total(parts) });
}
ranked.sort((a, b) => b.total - a.total || a.f.id.localeCompare(b.f.id));

const out = ranked.map((s, i) => {
  const best = Object.keys(WEIGHTS).reduce((a, b) => (s.parts[b] * WEIGHTS[b] > s.parts[a] * WEIGHTS[a] ? b : a));
  const note = s.f.weeks === null ? '; no completion time stated, timeline scored 0' : '';
  return { proposal: s.f.id, rank: i + 1, status: 'ranked', score: s.total, reason: `Total ${s.total.toFixed(1)}; strongest on ${best}${note}.` };
}).concat(others);

for (const r of out) console.log(JSON.stringify(r));
for (const [id, sc] of Object.entries(shadow)) console.log(`shadow score for ${id} (reviewer only): ${sc}`);
