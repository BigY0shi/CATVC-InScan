# InScan UI rebuild: design skill pipeline

**Status:** draft. Nothing here is locked until Yoshi says "lock it".
**Pairs with:** `landing-nav-ui-prompt.md`. The prompt stays builder-agnostic; the skills run around it.
**Already locked:**
- palette v4 true charcoal (`#1E1F21`)
- the 5-slot nav: Home · Stock · [Scan] · Tickets (TBA) · More
- depth B: tactile controls only
- PINs are exactly 4 digits
**Rule:** where two skills overlap, one is chosen and named. The other is listed only as a fallback.

---

## Phase 0. Direction (before any mockup)

| Skill | Job | Notes |
|---|---|---|
| `mobbin:search` | Reference screens for home/launcher, bottom nav with a center action, inventory lists, scan flows | Required by the repo handoff §5.1. Reference only, never copy. |
| `frontend-design` | Set the aesthetic direction and avoid templated defaults | — |
| aura `generate-reference-inspired-brand-worlds` | Build a visual family from reference images | **Needs references from Yoshi:** shop photos, Country ATV signage or logo. It's built for image generation (campaign worlds), so use it for a moodboard, not screens. Skip it if there are no references. |

## Phase 1. Tighten the prompt

| Skill | Job |
|---|---|
| aura `design-first-ui-prompting` | Check `landing-nav-ui-prompt.md` for structure, constraints and variation rules |

## Phase 2. Variants

| Skill | Job | Why this one |
|---|---|---|
| **`prototype`** (chosen) | Build several genuinely different versions behind a picker, so you flip through them and promote one | Runs in this session. Only runs when explicitly invoked. |
| `superdesign` (fallback) | Multi-model canvas comparison | Runs through its own CLI (`npx @superdesign/cli`) on the Superdesign canvas. Use it only if you want to compare models side by side. |
| `21st-ui` (skip) | — | It's a marketplace of React/Tailwind web components. InScan is Jetpack Compose. Its `get_inspiration` and `search` tools could serve as a web-component reference, but they need the 21st MCP server and an API key (`API_KEY_21ST`), and that server isn't connected in this session. Mobbin covers mobile app screens better. |

## Phase 3. Polish

| Skill | Job | Why this one |
|---|---|---|
| **`emil-design-eng`** (chosen) | Motion, press states, component polish | The deeper of the two polish skills. |
| `better-ui` (fallback) | Quick polish checklist | Overlaps with `emil-design-eng`. |
| aura `no-ai-design-slop` | Passive guardrail during the build | — |

## Phase 4. Gate (nothing is "done" until all of these pass)

| Skill | Job |
|---|---|
| `vectorlab-ux-skills:anti-slop` | Slop matrix (S1–S15) run against the mockup code. It replaces UXKIN, on Yoshi's call. |
| `/slopmonster` | **Not found** in this session or on the linked PC. Until it's located, the fallback is aura `audit-ai-design-slop`. |
| aura `audit-ai-design-slop` | Evidence-backed review for AI-design clichés |
| `design-critique` | Heuristics, UX laws, information architecture, UX writing |
| `ux-ui-audit` | Measured numbers on the HTML mockups in a browser: contrast, tap-target sizes, focus rings |

Phase 4 runs in a code session, against the mockup code, not screenshots.
| `agentic-council:council` | Adversarial seats: Skeptic, Advocate, Artisan, Pathfinder, Craftsman. Every finding is fixed or rejected in writing. |
| Adversarial UITest / UXTest | Required by the repo handoff §5.1. **Still not located.** Check the Desktop PC. |

---

## Acceptance bar

Adapted from aura `build-awwwards-quality-sites` §6. Only the parts that apply to an Android app are kept; the GSAP, smooth-scroll and Three.js sections are web-only and are dropped.

- One distinct, art-directed idea, carried through every screen.
- The first screen is the strongest authored moment and is usable instantly.
- Typography, spacing and image crops are disciplined.
- Every control has finished states: default, pressed, focus, loading, disabled, error, offline and reduced-motion.
- Reject:
  - generic gradient blobs
  - ornamental bento grids
  - glass effects
  - stock component layouts
  - fake data presented as real
  - motion that has no job
- No placeholder copy and no invented claims in anything Yoshi signs off on.

**Not used:** design-drift's `awwwards-ui-skills`. It is a copy of awwwards.com's own UI: light mode, a required `#E4E4E4` page background and Inter. Applying it would override the locked charcoal palette, and it sets no quality bar.

---

## Dropped: `high-contrast-skeuomorphic-clean`

Dropped by Yoshi on 2026-10-29. The skill itself is out, but one idea from it is locked as **depth option B: tactile controls only.** Only things you press get depth: Scan, tiles, chips, the selected nav pill, and the Find field as an inset well. Information surfaces stay flat. The exact values are in the prompt, section 6.
