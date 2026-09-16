/**
 * Ligaya mascot engine.
 *
 * Derived from the Gemini rig: same layered artwork, parametric mouth, viseme model, eyelid curves and
 * secondary physics. Rebuilt to run inside the Android app's WebView:
 *
 *  - Renders into ONE <canvas>. Gemini's version composited 14 full-canvas image layers with CSS 3D
 *    transforms, which gives each layer its own GPU texture; inside an Android WebView that exceeds the
 *    tile memory budget ("tile memory limits exceeded, some content may not draw") and layers silently go
 *    missing. Each layer's 3D depth is reproduced exactly as its perspective scale, plus a depth-weighted
 *    parallax shift when her head turns.
 *  - Laid out in the artwork's native 1008x740 canvas and scaled as one unit, so pivots and motion
 *    amplitudes are the same at every screen size.
 *  - Periodic motion runs on the engine's own clock, which only advances while frames render, so nothing
 *    jumps when the app is paused and resumed.
 *  - Emotion is the base state; listening, speaking and a brief smile are overlays on top of it.
 *  - Frames whose pose hasn't changed aren't redrawn.
 */

const CANVAS_W = 1008;
const CANVAS_H = 740;
const MAX_YAW = 3;
const MAX_PITCH = 2.5;
const FRAME_TWEEN_MS = 380;
const PERSPECTIVE = 1200;
const ORIGIN = [540, 390];
const CAMERA_VIEWS = { front: 0, left_3_4: -2.6, right_3_4: 2.6 };

export const FRAMES = {
  bust: { x: 150, y: 22, w: 720, h: 718, fit: 'contain' },
  portrait: { x: 250, y: 30, w: 520, h: 560, fit: 'contain' },
  head: { x: 350, y: 110, w: 330, h: 330, fit: 'cover' },
};

// Each emotion is a whole face, kept distinct enough to read at a glance on a phone:
//   brows   [[dx, dy, rotation], ...] per brow (left, right as seen). Negative dy raises; rotation lifts or drops
//           the inner corners (left brow: negative lifts its inner end; right brow: positive does).
//   squint  lower lid rising (eye-smile), lid  resting upper-lid droop (0 = wide open), pupil  pupil size.
//   cheeks  flush, gaze  where the eyes rest, mouth  a MOUTHS shape.
//   pitch / tilt / yaw  the head: back(+)/in(-), tipped (deg, + = top of head to her left as seen), turned.
//   rate  how quickly the face moves into it. onset  a head gesture as it lands (+ pops back, - bobs down).
//   noBlink  arrive without the usual resetting blink. onsetAct  an idle behaviour to play on arrival.
const EMOTIONS = {
  neutral: { brows: [[0, 0, 0], [0, 0, 0]], squint: 0, lid: 0, pupil: 1, cheeks: 0, gaze: [0, 0], mouth: 'neutral', pitch: 0, tilt: 0 },
  // At ease: a gentle closed smile, warm cheeks, eyes open and soft — this is her resting face on Home and while
  // she listens, so the lids stay light; a heavy droop here reads as sleepy rather than relaxed.
  content: { brows: [[0, -1.8, 1.5], [0, -2.2, -1.5]], squint: 0.35, lid: 0.14, pupil: 1.05, cheeks: 0.55, gaze: [0, 0.15], mouth: 'smile', pitch: 0.2, tilt: -2.2, yaw: 0.3, rate: 7 },
  // A real smile reaches the eyes: eyes closed in upward crescents, cheeks flushed, big warm closed smile, small happy bob.
  happy: { brows: [[0, -5.5, 3], [0, -6, -3]], squint: 0.85, lid: 0.85, crescent: 1, pupil: 1.06, cheeks: 0.95, gaze: [0, 0], mouth: 'happy', pitch: -0.2, tilt: -1.6, rate: 11, onset: -3.5 },
  // One brow up, the other drawn in; eyes up and away; mouth pulled to one side; head tipped back.
  thinking: { brows: [[1.5, 2.0, -3.5], [0, -8.0, -5.0]], squint: 0.15, lid: 0.06, pupil: 1, cheeks: 0, gaze: [2.5, -2.2], mouth: 'thinking', pitch: 0.6, tilt: 2, yaw: 0.8, rate: 7, onset: 2 },
  // Apologetic: inner brow corners up, small sympathetic closed smile, head tilted in sympathy and leaning in. Settles slowly.
  concerned: { brows: [[1.5, -4.8, -9], [-1.5, -4.8, 9]], squint: 0.15, lid: 0.05, pupil: 1.02, cheeks: 0, gaze: [0, 0.4], mouth: 'concerned', pitch: -0.6, tilt: 2.6, rate: 6 },
  // Brows fly up, pupils tighten, mouth drops open, head pops back — fast, and without a blink.
  surprised: { brows: [[0, -9, 1.5], [0, -9.6, -1.5]], squint: 0, lid: 0, pupil: 0.84, cheeks: 0, gaze: [0, -0.3], mouth: 'surprised', pitch: 0.9, tilt: 0, rate: 18, onset: 7, noBlink: true },
  // Eyes open and on the person, brows lifted, lips just parted, leaning in.
  attentive: { brows: [[0, -2.8, 1], [0, -2.8, -1]], squint: 0, lid: 0, pupil: 1, cheeks: 0, gaze: [0, 0.3], mouth: 'attentive', pitch: -0.9, tilt: 0.8, rate: 9 },
  // Warm and steady: a soft closed smile, gently lifted inner brows, a small tilt towards the person.
  reassuring: { brows: [[0.8, -2.8, -4.5], [-0.8, -2.8, 4.5]], squint: 0.3, lid: 0.08, pupil: 1.04, cheeks: 0.35, gaze: [0, 0.3], mouth: 'smile', pitch: -0.3, tilt: 1.8, rate: 7 },
  // Focused and steady: brows drawn level, mouth closed and firm, gaze held on the person, idle motion
  // damped so she never pulls the eye away from emergency status and actions.
  emergency: { brows: [[1, 0.8, 1.5], [-1, 0.8, -1.5]], squint: 0.12, lid: 0.04, pupil: 0.94, cheeks: 0, gaze: [0, 0.2], mouth: 'firm', pitch: -0.5, tilt: 0, idle: 0.35, rate: 12, onset: -2 },
  // Relief: a soft closed smile, relaxed lids and an out-breath as it lands.
  resolved: { brows: [[0, -2.2, -2.5], [0, -2.2, 2.5]], squint: 0.38, lid: 0.15, pupil: 1.04, cheeks: 0.5, gaze: [0, 0.1], mouth: 'relief', pitch: 0.15, tilt: -1.2, rate: 5, onsetAct: 'sigh' },
  // Her sign-off ("Always here for you."): a wink — one eye arcs closed while the other stays open — over a soft
  // closed smile and warm cheeks.
  fond: { brows: [[0, -2.4, 1.2], [0, -3, -1.2]], squint: 0.3, lid: 0.05, wink: 1, pupil: 1.06, cheeks: 0.7, gaze: [0, 0.2], mouth: 'smile', pitch: 0.1, tilt: -1.8, yaw: 0.4, rate: 8 },
};

export const EMOTION_NAMES = Object.keys(EMOTIONS);

// Mouth shapes are authored along the mouth's own axis. Her head is tilted in the artwork, so "up" for a smile
// is against MOUTH_N, not screen-up. In mouthShape, a is the distance along the mouth from its centre and b the
// offset below the mouth line: corners [left, right], upper-lip controls, lower-lip controls (defaults to just
// under the upper line, i.e. a closed mouth).
const MOUTH_C = [544, 366];
const MOUTH_U = [0.956, -0.292];
const MOUTH_N = [0.292, 0.956];
function mouthShape({ w = 37, cw = 14, shift = 0, corners = [0, 0], upper = [2.4, 2.4], lower, open = 0, teeth = 0 }) {
  const at = (a, b) => [MOUTH_C[0] + (shift + a) * MOUTH_U[0] + b * MOUTH_N[0], MOUTH_C[1] + (shift + a) * MOUTH_U[1] + b * MOUTH_N[1]];
  const low = lower ?? [upper[0] + 2, upper[1] + 2];
  return { pL: at(-w, corners[0]), cUL: at(-cw, upper[0]), cUR: at(cw, upper[1]), pR: at(w, corners[1]), cLL: at(-cw, low[0]), cLR: at(cw, low[1]), openness: open, teeth };
}

const MOUTHS = {
  // Relaxed: the faintest curve, not a smile.
  neutral: mouthShape({ upper: [2.6, 2.6] }),
  // Lips just parted.
  attentive: mouthShape({ corners: [0.4, 0.2], upper: [1.8, 1.8], lower: [5.5, 5.5], open: 0.12 }),
  // Soft closed smile. Shorter than a neutral mouth and set a little higher, with the corners lifting into the
  // cheek rather than running flat across the face — measured against the reference art's resting smile.
  smile: mouthShape({ w: 33, corners: [-3.4, -4], upper: [5.6, 5.6], lower: [7.6, 7.6] }),
  // Relieved: a little wider and warmer than smile.
  relief: mouthShape({ corners: [-2.4, -3], upper: [5.6, 5.6], lower: [8, 8] }),
  // Closed and lopsided: one corner lifts higher.
  smirk: mouthShape({ corners: [1.5, -4.8], upper: [4.6, 3.4], lower: [6.6, 5.4] }),
  // Big warm closed smile, corners lifted high.
  happy: mouthShape({ w: 43, corners: [-4.8, -5.4], upper: [5.8, 5.8], lower: [7.8, 7.8] }),
  // Short, pulled to one side, one corner tucked.
  thinking: mouthShape({ w: 25, shift: 7, corners: [2.2, -1.6], upper: [0.8, 0.1], lower: [2.8, 2.1] }),
  // Small sympathetic closed smile (apologetic / "Oops...").
  concerned: mouthShape({ w: 32, corners: [-1.4, -1.8], upper: [3.4, 3.4], lower: [5.2, 5.2] }),
  // A small round "O".
  surprised: mouthShape({ w: 11, cw: 7, corners: [1.5, 1.5], upper: [-5.5, -5.5], lower: [3, 3], open: 0.3, teeth: 0.1 }),
  // Pressed flat.
  firm: mouthShape({ w: 33, corners: [1.3, 1.1], upper: [0.4, 0.4], lower: [2, 2] }),
};
const MOUTH_POINTS = ['pL', 'cUL', 'cUR', 'pR', 'cLL', 'cLR'];

const VISEMES = [
  { openness: 0.52, width: -0.4, lift: 0.85, min: 0.14, max: 0.22 },
  { openness: 0.44, width: -2.4, lift: 0.45, min: 0.13, max: 0.2 },
  { openness: 0.25, width: 2.2, lift: 0.65, min: 0.12, max: 0.18 },
  { openness: 0.26, width: -2.6, lift: 0.35, min: 0.12, max: 0.18 },
  { openness: 0.02, width: 0.4, lift: 0.08, min: 0.08, max: 0.13 },
  { openness: 0.13, width: 1.1, lift: 0.25, min: 0.09, max: 0.14 },
];

const Z = { hairBack: -22, neck: -8, body: -5, necklace: -3, face: 0, eyeWhites: 2, pupils: 3.5, eyelids: 4, brows: 4.5, mouth: 4, cheeks: 4.5, nose: 8, glasses: 12, hairFront: 18 };
// How far above the eye opening the painted upper lash reaches, in canvas px.
const LASH_BAND = 14;
const LAYER_FILES = ['hair_back', 'neck', 'body', 'necklace', 'face', 'eye_sclera', 'pupil_left', 'pupil_right', 'eyebrow_left', 'eyebrow_right', 'cheeks_smile', 'nose', 'glasses', 'hair_front', 'eye_skin'];

// Where each layer actually has pixels, as [x, y, w, h] in canvas px (padded a little; nose and glasses also
// cover their baked shadows). Drawing only that rectangle instead of the whole 1008x740 image cuts the
// per-frame fill to about a tenth — the difference between a steady 60fps and dropped frames in a WebView.
// Regenerate if a layer is repainted.
const LAYER_BOUNDS = {
  hair_back: [184, 36, 585, 691],
  // The neck layer also paints skin up behind her whole head. That part is always hidden at rest, but when her
  // head tilts it would peek out past the hair as a brown fringe, so drawing starts at y=300.
  neck: [260, 300, 483, 409],
  body: [214, 496, 642, 244],
  necklace: [524, 527, 96, 51],
  face: [237, 37, 516, 415],
  eye_sclera: [415, 243, 213, 91],
  pupil_left: [437, 287, 41, 40],
  pupil_right: [564, 248, 41, 42],
  eyebrow_left: [413, 228, 65, 39],
  eyebrow_right: [538, 197, 65, 24],
  cheeks_smile: [384, 284, 273, 88],
  nose: [507, 311, 55, 63],
  glasses: [364, 208, 305, 167],
  hair_front: [464, 147, 29, 42],
};

// Head tilts turn about the chin, so the face tips without sliding off the neck.
const LEAN_PIVOT = [575, 438];
// Each pupil scales about its own centre.
const PUPIL_CENTERS = [[456, 307], [584, 268]];

// Small unprompted behaviours that keep her alive between states, weighted per emotion (missing = never).
// `listening` ones also happen while she listens: quiet gestures that don't pull her attention away (no
// looking off, no sighing).
const ACTS = {
  glance: { dur: [0.9, 1.8], weight: { neutral: 3, content: 2, happy: 2, thinking: 4, reassuring: 1, resolved: 2 } },
  tilt: { dur: [2.2, 3.4], listening: true, weight: { neutral: 2, content: 3, happy: 2, thinking: 2, reassuring: 2, resolved: 2 } },
  smile: { dur: [1.6, 2.6], listening: true, weight: { neutral: 2, content: 3, happy: 1, reassuring: 2, resolved: 3 } },
  sigh: { dur: [2.6, 3.4], weight: { neutral: 1, content: 2, thinking: 1, resolved: 2 } },
  brow: { dur: [0.7, 1.0], listening: true, weight: { neutral: 1, content: 1, happy: 2, thinking: 1 } },
};
export const ACT_NAMES = Object.keys(ACTS);

const damp = (rate, dt) => 1 - Math.exp(-rate * dt);
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const rand = (lo, hi) => lo + Math.random() * (hi - lo);
const lerp = (a, b, t) => a + (b - a) * t;
const smoothstep = (t) => t * t * (3 - 2 * t);
const easeInOut = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
const f = (n) => n.toFixed(2);
const RAD = Math.PI / 180;

function loadImage(src) {
  const img = new Image();
  img.decoding = 'async';
  img.src = src;
  return img.decode().then(() => img);
}

// Nose and glasses cast a soft fixed shadow. Baked once: a canvas shadow redrawn every frame blurs a
// full-canvas image each time.
function bakeShadow(img, offsetY, blur, color) {
  const c = document.createElement('canvas');
  c.width = CANVAS_W;
  c.height = CANVAS_H;
  const g = c.getContext('2d');
  g.imageSmoothingEnabled = true;
  g.imageSmoothingQuality = 'high';
  g.shadowColor = color;
  g.shadowOffsetY = offsetY;
  g.shadowBlur = blur;
  g.drawImage(img, 0, 0);
  return c;
}

// Locates each eye (sclera alpha, split at the nose) and crops its painted-out skin from eye_skin.png.
function buildEyes(sclera, skin) {
  const probe = document.createElement('canvas');
  probe.width = CANVAS_W;
  probe.height = CANVAS_H;
  const pg = probe.getContext('2d', { willReadFrequently: true });
  const boxesOf = (img) => {
    pg.clearRect(0, 0, CANVAS_W, CANVAS_H);
    pg.drawImage(img, 0, 0);
    const { data } = pg.getImageData(0, 0, CANVAS_W, CANVAS_H);
    const boxes = [[1e9, 1e9, -1, -1], [1e9, 1e9, -1, -1]];
    for (let y = 0; y < CANVAS_H; y++) {
      for (let x = 0; x < CANVAS_W; x++) {
        if (data[(y * CANVAS_W + x) * 4 + 3] < 40) continue;
        const b = boxes[x < 522 ? 0 : 1];
        if (x < b[0]) b[0] = x;
        if (y < b[1]) b[1] = y;
        if (x > b[2]) b[2] = x;
        if (y > b[3]) b[3] = y;
      }
    }
    return boxes;
  };
  const eyes = boxesOf(sclera);
  pg.clearRect(0, 0, CANVAS_W, CANVAS_H);
  pg.drawImage(sclera, 0, 0);
  const sd = pg.getImageData(0, 0, CANVAS_W, CANVAS_H).data;
  const skins = boxesOf(skin);
  return [0, 1]
    .filter((i) => eyes[i][2] > eyes[i][0] && skins[i][2] > skins[i][0])
    .map((i) => {
      // The sclera layer extends a few pixels under the face so no edge can show; the eye itself is inset.
      const [l0, t0, r0, b0] = eyes[i];
      const [left, top, right, bottom] = [l0 + 4, t0 + 4, r0 - 4, b0 - 4];
      // The eye's own outline, column by column (inset by the same 4px), lightly smoothed. Lids follow it,
      // so a closing lid always matches the shape of the eye instead of cutting across it in a straight bar.
      const rawTop = [];
      const rawBot = [];
      const valid = [];
      for (let x = left; x <= right; x++) {
        let t = -1;
        let b = -1;
        for (let y = t0; y <= b0; y++) {
          if (sd[(y * CANVAS_W + x) * 4 + 3] >= 40) {
            if (t < 0) t = y;
            b = y;
          }
        }
        // Trust a column only if at least 6px of actual eye remains after the 4px inset top and bottom.
        const ok = t >= 0 && b - t >= 14;
        valid.push(ok);
        rawTop.push(ok ? t + 4 : 0);
        rawBot.push(ok ? b - 4 : 0);
      }
      // Near each corner the traced outline is unreliable (the sclera layer's rounded margin), so top and
      // bottom taper in a straight line to one corner point instead of spiking.
      const first = valid.indexOf(true);
      const last = valid.lastIndexOf(true);
      if (first >= 0) {
        const cornerL = (rawTop[first] + rawBot[first]) / 2;
        const cornerR = (rawTop[last] + rawBot[last]) / 2;
        for (let k = 0; k < first; k++) {
          const u = k / first;
          rawTop[k] = lerp(cornerL, rawTop[first], u);
          rawBot[k] = lerp(cornerL, rawBot[first], u);
        }
        const tail = rawTop.length - 1 - last;
        for (let k = last + 1; k < rawTop.length; k++) {
          const u = (rawTop.length - 1 - k) / tail;
          rawTop[k] = lerp(cornerR, rawTop[last], u);
          rawBot[k] = lerp(cornerR, rawBot[last], u);
        }
        for (let k = first + 1; k < last; k++) {
          if (!valid[k]) {
            rawTop[k] = rawTop[k - 1];
            rawBot[k] = rawBot[k - 1];
          }
        }
      } else {
        rawTop.fill((top + bottom) / 2);
        rawBot.fill((top + bottom) / 2);
      }
      const smooth = (arr, r) => arr.map((_, k) => {
        let sum = 0;
        let n = 0;
        for (let j = Math.max(0, k - r); j <= Math.min(arr.length - 1, k + r); j++) {
          sum += arr[j];
          n++;
        }
        return sum / n;
      });
      // A least-squares quadratic through each traced outline: the lids move along smooth arcs (a closed eye
      // is one clean curve from corner to corner) instead of every bump in the painted edge. The closed
      // position is pushed down by the largest amount the real outline dips below its arc (capped at 4px)
      // so no sliver of white can show beneath a closed lid.
      const fit = (arr) => {
        const m = arr.length;
        let s0 = 0, s1 = 0, s2 = 0, s3 = 0, s4 = 0, t0 = 0, t1 = 0, t2 = 0;
        for (let k = 0; k < m; k++) {
          const u = m > 1 ? k / (m - 1) : 0;
          const y = arr[k];
          s0 += 1; s1 += u; s2 += u * u; s3 += u * u * u; s4 += u * u * u * u;
          t0 += y; t1 += u * y; t2 += u * u * y;
        }
        const det = (a) => a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0]) + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]);
        const A = [[s0, s1, s2], [s1, s2, s3], [s2, s3, s4]];
        const D = det(A);
        if (Math.abs(D) < 1e-9) return arr.slice();
        const col = (i, v) => A.map((row, r) => row.map((x, c) => (c === i ? v[r] : x)));
        const rhs = [t0, t1, t2];
        const c0 = det(col(0, rhs)) / D;
        const c1 = det(col(1, rhs)) / D;
        const c2 = det(col(2, rhs)) / D;
        return arr.map((_, k) => {
          const u = m > 1 ? k / (m - 1) : 0;
          return c0 + c1 * u + c2 * u * u;
        });
      };
      const topFit = fit(smooth(rawTop, 2));
      const botFit = fit(smooth(rawBot, 2));
      const dip = Math.min(4, Math.max(0, ...rawBot.map((y, k) => y - botFit[k])));
      const contour = { x0: left, top: topFit, bot: botFit.map((y) => y + dip) };
      const [sl, st, sr, sb] = skins[i];
      const ox = sl - 2;
      const oy = st - 2;
      const w = sr - sl + 5;
      const h = sb - st + 5;
      const crop = document.createElement('canvas');
      crop.width = w;
      crop.height = h;
      crop.getContext('2d').drawImage(skin, -ox, -oy);
      return { left, top, right, bottom, ox, oy, w, h, skin: crop, contour, outer: i === 0 ? -1 : 1 };
    });
}

function cloneMouth(m) {
  const out = { openness: m.openness, teeth: m.teeth };
  for (const p of MOUTH_POINTS) out[p] = [m[p][0], m[p][1]];
  return out;
}

function frameState(frame) {
  return { x: frame.x, y: frame.y, w: frame.w, h: frame.h, cover: frame.fit === 'cover' ? 1 : 0 };
}

/**
 * @param {HTMLElement} container
 * @param {object} [options]
 * @param {string} [options.assetBase]  folder holding the layer PNGs
 * @param {'bust'|'portrait'|'head'} [options.frame]
 * @param {'system'|'calm'|'still'} [options.motion]
 * @param {boolean} [options.followPointer]  eyes and head track a mouse over the container
 * @param {number} [options.intensity]  expression strength, 1 = as drawn
 * @param {boolean} [options.debug]  exposes api.debug for deterministic visual testing
 */
export function createLigaya(container, options = {}) {
  const assetBase = options.assetBase ?? 'assets/ligaya';
  const listeners = new Map();

  const canvas = document.createElement('canvas');
  canvas.className = 'lg-canvas';
  canvas.setAttribute('aria-hidden', 'true');
  container.classList.add('lg-stage');
  container.appendChild(canvas);
  const ctx = canvas.getContext('2d', { alpha: true });
  // Every frame scales her 1008x740 artwork down to roughly a third of that. Chrome's default resampling is
  // 'low', which is what made her edges — hair, glasses rim, lashes — read soft next to the reference art.
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';

  const reducedQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  const s = {
    frame: frameState(FRAMES[options.frame] ?? FRAMES.bust),
    frameName: FRAMES[options.frame] ? options.frame : 'bust',
    frameFrom: null,
    frameTo: null,
    frameStart: 0,
    layout: { tx: 0, ty: 0, scale: 1, w: 0, h: 0, dpr: 1 },
    motion: options.motion ?? 'system',
    intensity: options.intensity ?? 1,
    calm: false,
    emotion: 'neutral',
    emotionIdle: 1,
    animationSpeed: clamp(options.animationSpeed ?? 1.0, 0.5, 2.0),
    depthStrength: clamp(options.depthStrength ?? 0.6, 0.0, 1.0),
    crescent: 0,
    crescentTarget: 0,
    wink: 0,
    winkTarget: 0,
    listening: false,
    speaking: false,
    smiling: false,
    smileTimer: 0,
    look: null,
    viewName: 'front',
    viewYaw: 0,
    pointer: { yaw: 0, pitch: 0, x: 0, y: 0, active: false },
    cam: { yaw: 0, pitch: 0, roll: 0 },
    brows: [[0, 0, 0], [0, 0, 0]],
    browsTarget: [[0, 0, 0], [0, 0, 0]],
    squint: 0,
    squintTarget: 0,
    cheeks: 0,
    cheeksTarget: 0,
    gaze: { x: 0, y: 0 },
    gazeTarget: { x: 0, y: 0 },
    saccade: { x: 0, y: 0 },
    nextSaccade: 0,
    pitchLean: 0,
    pitchLeanTarget: 0,
    mouth: cloneMouth(MOUTHS.neutral),
    mouthTarget: cloneMouth(MOUTHS.neutral),
    viseme: { openness: 0, width: 0, lift: 0 },
    visemeTarget: { openness: 0, width: 0, lift: 0 },
    syllable: 0,
    syllableDur: 0.16,
    pausing: false,
    syllables: 0,
    maxSyllables: 6,
    blink: { active: false, start: 0, dur: 260, progress: 0, next: 0 },
    necklace: { angle: 0, vel: 0 },
    hair: { x: 0, vel: 0 },
    lean: 0,
    lid: 0,
    lidTarget: 0,
    pupil: 1,
    pupilTarget: 1,
    tiltTarget: 0,
    yawBias: 0,
    yawBiasTarget: 0,
    faceRate: 9,
    nod: { amt: 0, vel: 0 },
    nextNod: 0,
    breathPhase: 0,
    act: { name: null, t: 0, dur: 0, next: 0, dx: 0, dy: 0, dir: 1, blinkAfter: false },
    clock: 0,
    idleAmp: 1,
    visible: !document.hidden,
    inView: true,
    raf: 0,
    last: 0,
    destroyed: false,
    art: null,
    lastKey: '',
    frozen: false,
    blinkOverride: null,
    visemeOverride: null,
    fps: 0,
    drawn: 0,
    fpsFrames: 0,
    fpsStart: 0,
    dts: new Float32Array(300),
    dtIdx: 0,
  };

  const isReduced = () => s.motion === 'still' || (s.motion === 'system' && reducedQuery.matches);
  const idleTarget = () => (isReduced() ? 0 : (s.calm || s.motion === 'calm' ? 0.3 : 1) * s.emotionIdle);

  function emit(type, detail) {
    (listeners.get(type) ?? []).forEach((fn) => fn(detail));
  }
  const snapshot = () => ({
    emotion: s.emotion,
    listening: s.listening,
    speaking: s.speaking,
    camera: s.viewName,
    frame: s.frameName,
    motion: s.motion,
    animationSpeed: s.animationSpeed,
    depthStrength: s.depthStrength,
  });

  function layout() {
    const W = container.clientWidth;
    const H = container.clientHeight;
    if (!W || !H) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 3);
    const bw = Math.round(W * dpr);
    const bh = Math.round(H * dpr);
    if (canvas.width !== bw || canvas.height !== bh) {
      canvas.width = bw;
      canvas.height = bh;
      // Resizing the backing store resets every context setting, including the resampling quality above.
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
    }
    const fr = s.frame;
    // Contain: whole region visible, placed 40% down any spare height so she reads as centred. Cover: fill,
    // keeping the top of the region in view. Blending both lets framing change smoothly between screens.
    const cs = Math.min(W / fr.w, H / fr.h);
    const os = Math.max(W / fr.w, H / fr.h);
    const scale = lerp(cs, os, fr.cover);
    const ty = lerp((H - fr.h * cs) * 0.4 - fr.y * cs, -fr.y * os, fr.cover);
    const tx = W / 2 - (fr.x + fr.w / 2) * scale;
    s.layout = { tx, ty, scale, w: W, h: H, dpr };
  }

  function scaledMouth(key, k) {
    const preset = MOUTHS[key] ?? MOUTHS.neutral;
    const base = MOUTHS.neutral;
    const out = cloneMouth(preset);
    for (const p of MOUTH_POINTS) {
      out[p] = [lerp(base[p][0], preset[p][0], k), lerp(base[p][1], preset[p][1], k)];
    }
    out.openness = preset.openness * k;
    out.teeth = preset.teeth * k;
    return out;
  }

  // Resolves every facial target from the base emotion plus the active overlays. The only place targets
  // are set, so no call can leave the face in a state the others don't know about.
  function applyTargets() {
    const e = EMOTIONS[s.emotion] ?? EMOTIONS.neutral;
    const k = s.intensity;
    s.browsTarget = e.brows.map(([x, y, r]) => [x * k, y * k, r * k]);
    s.squintTarget = e.squint * k;
    s.cheeksTarget = e.cheeks * k;
    s.gazeTarget = { x: e.gaze[0] * k, y: e.gaze[1] * k };
    s.pitchLeanTarget = e.pitch;
    s.mouthTarget = scaledMouth(e.mouth, (e.mouthScale ?? 1) * k);
    s.emotionIdle = e.idle ?? 1;
    s.lidTarget = (e.lid ?? 0) * k;
    s.pupilTarget = 1 + ((e.pupil ?? 1) - 1) * k;
    s.tiltTarget = (e.tilt ?? 0) * k;
    s.yawBiasTarget = (e.yaw ?? 0) * k;
    s.faceRate = e.rate ?? 9;
    s.crescentTarget = (e.crescent ?? 0) * k;
    s.winkTarget = (e.wink ?? 0) * k;
    if (s.listening) {
      // Attention on top of whatever she feels: brows lift a little, she leans in, gaze settles on the person.
      for (const b of s.browsTarget) b[1] -= 1.6 * k;
      s.pitchLeanTarget -= 0.8;
      s.gazeTarget = { x: s.gazeTarget.x * 0.5, y: s.gazeTarget.y * 0.5 + 0.4 * k };
      // Listening opens the eyes, whatever the resting lid.
      s.lidTarget = Math.min(s.lidTarget, 0.04);
    }
    if (s.speaking) {
      s.cheeksTarget = Math.max(s.cheeksTarget, 0.45 * k);
      if (e.mouth === 'neutral') s.mouthTarget = scaledMouth('smile', 0.8 * k);
    }
    if (s.smiling) {
      s.squintTarget = Math.max(s.squintTarget, 0.5 * k);
      s.cheeksTarget = Math.max(s.cheeksTarget, 0.85 * k);
      if (e.mouth !== 'happy') s.mouthTarget = scaledMouth('smile', k);
    }
  }

  function nextViseme() {
    const v = VISEMES[Math.floor(Math.random() * VISEMES.length)];
    s.visemeTarget = { openness: v.openness, width: v.width, lift: v.lift };
    s.syllableDur = rand(v.min, v.max);
    // Stressed syllables carry a small nod, the way people emphasise with their head as they talk.
    if (!isReduced() && v.openness > 0.4 && Math.random() < 0.4) s.nod.vel -= rand(2.5, 4.5);
  }

  function tickSpeech(dt) {
    if (s.visemeOverride) {
      s.viseme = { ...s.visemeOverride };
      return;
    }
    if (s.speaking) {
      s.syllable += dt;
      if (s.syllable >= s.syllableDur) {
        s.syllable = 0;
        if (s.pausing) {
          s.pausing = false;
          s.syllables = 0;
          s.maxSyllables = 5 + Math.floor(Math.random() * 5);
          nextViseme();
        } else if (++s.syllables >= s.maxSyllables) {
          // End of a phrase: a short natural rest before the next one.
          s.pausing = true;
          s.visemeTarget = { openness: 0, width: 0, lift: 0 };
          s.syllableDur = rand(0.22, 0.4);
        } else {
          nextViseme();
        }
      }
    }
    const target = s.speaking ? s.visemeTarget : { openness: 0, width: 0, lift: 0 };
    const e = damp(s.speaking ? 15 : 12, dt);
    s.viseme.openness += (target.openness - s.viseme.openness) * e;
    s.viseme.width += (target.width - s.viseme.width) * e;
    s.viseme.lift += (target.lift - s.viseme.lift) * e;
  }

  function startBlink(now) {
    if (s.blink.active) return;
    s.blink.active = true;
    s.blink.start = now;
    s.blink.dur = isReduced() ? 320 : 260;
  }

  function tickBlink(now) {
    const b = s.blink;
    if (s.blinkOverride !== null) {
      b.active = false;
      b.progress = s.blinkOverride;
      return;
    }
    if (!b.next) b.next = now + rand(1800, 4200);
    if (!b.active && !s.frozen && now > b.next) startBlink(now);
    if (!b.active) {
      b.progress = 0;
      return;
    }
    const t = (now - b.start) / b.dur;
    if (t < 0.42) b.progress = smoothstep(t / 0.42);
    else if (t < 1) b.progress = 1 - smoothstep((t - 0.42) / 0.58);
    else {
      b.progress = 0;
      b.active = false;
      // Irregular gaps, with an occasional quick double blink the way people actually blink.
      b.next = now + (Math.random() < 0.12 ? rand(180, 320) : rand(2200, 5200));
    }
  }

  // This frame's contribution from the running idle behaviour; every field is neutral when none is running.
  const actOut = { gx: 0, gy: 0, yaw: 0, pitch: 0, lean: 0, brow: 0, squint: 0, cheeks: 0, smile: 0, breathDepth: 1, breathRate: 1 };

  function startAct(name, now) {
    const def = ACTS[name];
    if (!def) return false;
    const a = s.act;
    a.name = name;
    a.t = 0;
    a.dur = rand(def.dur[0], def.dur[1]);
    a.dir = Math.random() < 0.5 ? -1 : 1;
    a.dx = rand(0.6, 1) * a.dir;
    // Thinking looks up and away; otherwise a glance drifts roughly level.
    a.dy = s.emotion === 'thinking' ? rand(-1, -0.6) : rand(-0.5, 0.4);
    a.blinkAfter = name === 'sigh' || (name === 'glance' && Math.random() < 0.5);
    if (name === 'glance' && Math.random() < 0.3) startBlink(now);
    return true;
  }

  function actWeight(def) {
    if (s.listening && !def.listening) return 0;
    return def.weight[s.emotion] ?? 0;
  }

  function pickAct(now) {
    let total = 0;
    for (const def of Object.values(ACTS)) total += actWeight(def);
    if (total <= 0) return;
    let r = Math.random() * total;
    for (const [name, def] of Object.entries(ACTS)) {
      r -= actWeight(def);
      if (r < 0) {
        startAct(name, now);
        return;
      }
    }
  }

  function tickActs(now, dt, still) {
    const o = actOut;
    o.gx = o.gy = o.yaw = o.pitch = o.lean = o.brow = o.squint = o.cheeks = o.smile = 0;
    o.breathDepth = 1;
    o.breathRate = 1;
    const a = s.act;
    if (still) {
      a.name = null;
      return o;
    }
    // While she listens: small, unhurried nods instead of wandering behaviours.
    if (s.listening && !s.frozen) {
      if (!s.nextNod) s.nextNod = now + rand(1200, 2400);
      if (now > s.nextNod) {
        s.nod.vel -= rand(1.8, 3);
        s.nextNod = now + rand(2200, 4200);
      }
    }
    if (!a.next) a.next = now + rand(1500, 3000);
    if (!a.name) {
      if (!s.frozen && !s.speaking && s.idleAmp > 0.5 && now > a.next) pickAct(now);
      if (!a.name) return o;
    }
    if (!s.frozen) a.t += dt;
    const u = clamp(a.t / a.dur, 0, 1);
    if (u >= 1) {
      if (a.blinkAfter) startBlink(now);
      a.name = null;
      a.next = now + rand(2600, 6500);
      return o;
    }
    const k = s.idleAmp;
    const env = smoothstep(clamp(u / 0.25, 0, 1)) * smoothstep(clamp((1 - u) / 0.3, 0, 1)) * k;
    switch (a.name) {
      case 'glance':
        o.gx = a.dx * 2.6 * env;
        o.gy = a.dy * 1.6 * env;
        break;
      case 'tilt':
        o.lean = a.dir * 1.6 * env;
        o.yaw = a.dir * 0.5 * env;
        break;
      case 'smile':
        o.smile = 0.9 * env;
        o.cheeks = 0.35 * env;
        o.squint = 0.35 * env;
        o.brow = -0.8 * env;
        break;
      case 'sigh':
        o.breathDepth = 1 + 1.3 * Math.sin(Math.PI * u) * k;
        o.breathRate = 0.6;
        o.pitch = -0.5 * env;
        o.squint = 0.3 * env;
        o.lean = -0.6 * env;
        break;
      case 'brow':
        o.brow = -3.2 * Math.pow(Math.sin(Math.PI * u), 1.5) * k;
        break;
    }
    return o;
  }

  function tickFrame(now) {
    if (!s.frameTo) return;
    const t = clamp((now - s.frameStart) / FRAME_TWEEN_MS, 0, 1);
    const e = easeInOut(t);
    for (const key of ['x', 'y', 'w', 'h', 'cover']) s.frame[key] = lerp(s.frameFrom[key], s.frameTo[key], e);
    if (t >= 1) s.frameTo = null;
    layout();
  }

  function tick(now) {
    s.raf = 0;
    if (s.destroyed) return;
    if (s.last) {
      s.dts[s.dtIdx % s.dts.length] = now - s.last;
      s.dtIdx++;
    }
    const rawDt = s.last ? Math.min((now - s.last) / 1000, 0.1) : 1 / 60;
    const dt = rawDt * s.animationSpeed;
    s.last = now;
    if (!s.frozen) s.clock += dt;
    const still = isReduced();
    s.idleAmp += (idleTarget() - s.idleAmp) * damp(still ? 5 : 2.5, dt);
    const idle = s.idleAmp;
    const t = s.clock;

    if (!s.fpsStart) s.fpsStart = now;
    s.fpsFrames++;
    if (now - s.fpsStart >= 1000) {
      s.fps = Math.round((s.fpsFrames * 1000) / (now - s.fpsStart));
      s.fpsFrames = 0;
      s.fpsStart = now;
    }

    tickFrame(now);
    const act = tickActs(now, dt, still);

    // Head: a held camera view, pointer or explicit look, her own gaze (the eyes lead and the head follows
    // part of the way), idle behaviours and a slow, uneven wander — clamped so the flat artwork never
    // visibly shears.
    const lookPitch = s.look ? -s.look.y * 1.2 : 0;
    const lookYaw = s.look ? s.look.x * 1.2 : 0;
    const followYaw = still ? 0 : s.pointer.yaw + lookYaw;
    const followPitch = still ? 0 : s.pointer.pitch + lookPitch;
    const gazeYaw = still ? 0 : s.gaze.x * 0.22;
    const gazePitch = still ? 0 : -s.gaze.y * 0.15;
    const wanderYaw = (Math.sin(t * 0.8) * 0.45 + Math.sin(t * 0.31 + 1.7) * 0.25) * idle;
    const wanderPitch = (Math.cos(t * 1.1) * 0.3 + Math.sin(t * 0.47 + 0.4) * 0.15) * idle;
    s.yawBias += (s.yawBiasTarget - s.yawBias) * damp(3, dt);
    const targetYaw = clamp(s.viewYaw + followYaw + gazeYaw + act.yaw + wanderYaw + s.yawBias, -MAX_YAW, MAX_YAW);
    const targetPitch = clamp(followPitch + gazePitch + act.pitch + s.nod.amt + wanderPitch + s.pitchLean, -MAX_PITCH, MAX_PITCH);
    // Slower than the eyes (they ease at 7), so a look reads as eyes first, then the head.
    const camEase = damp(4.5, dt);
    const prevYaw = s.cam.yaw;
    s.cam.yaw += (targetYaw - s.cam.yaw) * camEase;
    s.cam.pitch += (targetPitch - s.cam.pitch) * camEase;
    s.cam.roll += ((Math.sin(t * 0.6) * 0.25 + Math.sin(t * 0.23 + 0.9) * 0.18) * idle - s.cam.roll) * camEase;

    s.pitchLean += (s.pitchLeanTarget - s.pitchLean) * damp(4, dt);
    // Head tilt: the emotion's own tilt plus any idle behaviour (a tilt, a sigh), eased so it never snaps.
    s.lean += (s.tiltTarget + act.lean - s.lean) * damp(3.5, dt);
    // Nods: a damped spring that stressed syllables and listening kick.
    s.nod.vel += (-70 * s.nod.amt - 12 * s.nod.vel) * dt;
    s.nod.amt += s.nod.vel * dt;

    // Secondary motion: necklace pendulum and back-hair spring, driven by how fast the head turns. Left to
    // settle on their own rather than zeroed, so switching to still motion never snaps them.
    const lateral = ((s.cam.yaw - prevYaw) / Math.max(dt, 1e-3)) * 0.9;
    const n = s.necklace;
    n.vel += (-18 * n.angle - lateral * 4.5 - 4 * n.vel) * dt;
    n.angle = clamp(n.angle + n.vel * dt, -6, 6);
    const h = s.hair;
    h.vel += (-8 * h.x - lateral * 2.2 - 5 * h.vel) * dt;
    h.x = clamp(h.x + h.vel * dt, -4, 4);

    // Breathing moves the torso and, less, the neck and head. An uneven rhythm: the rate drifts, the inhale is
    // quicker than the exhale, and a sigh runs slower and deeper. Nothing is scaled.
    if (!s.frozen) s.breathPhase += dt * (1.45 + 0.22 * Math.sin(t * 0.11)) * act.breathRate;
    const bs = Math.sin(s.breathPhase);
    const happyBob = (!still && s.emotion === 'happy') ? Math.sin(t * 5.2) * 1.5 * idle : 0;
    const breath = (bs >= 0 ? Math.pow(bs, 0.8) : -Math.pow(-bs, 1.3)) * 2.2 * idle * act.breathDepth + happyBob;

    // Eyes: occasional small saccades while idle, so the gaze is never frozen.
    if (!still && !s.frozen && !s.pointer.active && !s.look && now > s.nextSaccade) {
      const settle = s.emotion === 'thinking' || s.emotion === 'emergency' || s.listening;
      s.saccade = settle ? { x: 0, y: 0 } : { x: rand(-0.9, 0.9), y: rand(-0.45, 0.45) };
      s.nextSaccade = now + rand(2500, 5500);
    }
    if (still || s.frozen) s.saccade = { x: 0, y: 0 };
    let gx = s.gazeTarget.x + s.saccade.x + act.gx;
    let gy = s.gazeTarget.y + s.saccade.y + act.gy;
    if (s.look) {
      gx = s.look.x * 3;
      gy = s.look.y * 2;
    } else if (s.pointer.active) {
      gx = s.pointer.x;
      gy = s.pointer.y;
    }
    const gEase = damp(still ? 12 : 7, dt);
    s.gaze.x += (gx - s.gaze.x) * gEase;
    s.gaze.y += (gy - s.gaze.y) * gEase;

    tickBlink(now);
    tickSpeech(dt);

    // Each emotion sets how fast the face moves into it: surprise snaps, concern and relief settle slowly.
    const e9 = damp(s.faceRate, dt);
    for (let i = 0; i < 2; i++) for (let j = 0; j < 3; j++) s.brows[i][j] += (s.browsTarget[i][j] + (j === 1 ? act.brow : 0) - s.brows[i][j]) * e9;
    s.squint += (clamp(s.squintTarget + act.squint, 0, 1) - s.squint) * e9;
    s.cheeks += (clamp(s.cheeksTarget + act.cheeks, 0, 1) - s.cheeks) * e9;
    s.lid += (s.lidTarget - s.lid) * e9;
    s.pupil += (s.pupilTarget - s.pupil) * damp(6, dt);
    s.crescent += (s.crescentTarget - s.crescent) * e9;
    s.wink += (s.winkTarget - s.wink) * e9;
    const e11 = damp(s.faceRate + 2, dt);
    // A passing smile from an idle behaviour bends the mouth towards her lopsided smile, never mid-speech.
    const sm = s.speaking ? 0 : act.smile;
    const smirk = MOUTHS.smirk;
    for (const p of MOUTH_POINTS) {
      s.mouth[p][0] += (lerp(s.mouthTarget[p][0], smirk[p][0], sm) - s.mouth[p][0]) * e11;
      s.mouth[p][1] += (lerp(s.mouthTarget[p][1], smirk[p][1], sm) - s.mouth[p][1]) * e11;
    }
    s.mouth.openness += (s.mouthTarget.openness - s.mouth.openness) * e11;
    s.mouth.teeth += (s.mouthTarget.teeth - s.mouth.teeth) * e11;

    if (s.art) draw(breath);
    schedule();
  }

  function mouthPaths() {
    const m = s.mouth;
    const k = s.intensity;
    const open = Math.min(1, m.openness + s.viseme.openness * k);
    const lift = s.viseme.lift * k;
    const lx = m.pL[0] + s.viseme.width * k;
    const rx = m.pR[0] - s.viseme.width * k;
    const ly = m.pL[1] - lift * 0.3;
    const ry = m.pR[1] - lift * 0.3;
    // How far the lips part at full openness, in canvas px. Measured on the device: at 5px a talking mouth changed
    // only a few hundred screen pixels and read as closed, and 12px was still a thin sliver. 24px (a syllable at
    // ~0.5 opens about 12px) reads clearly as speech at phone size.
    const uyL = m.cUL[1] - lift * 1.6;
    const uyR = m.cUR[1] - lift * 1.6;
    const dyL = m.cLL[1] + open * 24;
    const dyR = m.cLR[1] + open * 24;
    const upper = `M${f(lx)} ${f(ly)}C${f(m.cUL[0])} ${f(uyL)} ${f(m.cUR[0])} ${f(uyR)} ${f(rx)} ${f(ry)}`;
    const out = {
      upper,
      lower: `M${f(lx)} ${f(ly)}C${f(m.cLL[0])} ${f(dyL)} ${f(m.cLR[0])} ${f(dyR)} ${f(rx)} ${f(ry)}`,
      lowerAlpha: clamp(0.45 + open * 0.35, 0.35, 0.85),
      fill: null,
      fillAlpha: 0,
      teeth: null,
      teethAlpha: 0,
    };
    if (open > 0.05) {
      out.fill = `${upper}C${f(m.cLR[0])} ${f(dyR)} ${f(m.cLL[0])} ${f(dyL)} ${f(lx)} ${f(ly)}Z`;
      out.fillAlpha = Math.min(0.85, 0.25 + open * 1.2);
      const teeth = Math.max(m.teeth, open * 0.45);
      if (teeth > 0.08 && open > 0.22) {
        out.teeth = `M${f(lx + 4)} ${f(ly + 0.5)}C${f(m.cUL[0])} ${f(uyL + 2)} ${f(m.cUR[0])} ${f(uyR + 2)} ${f(rx - 4)} ${f(ry + 0.5)}C${f(m.cUR[0])} ${f(uyR)} ${f(m.cUL[0])} ${f(uyL)} ${f(lx + 4)} ${f(ly + 0.5)}Z`;
        out.teethAlpha = teeth * Math.min(0.7, open * 1.2);
      }
    }
    return out;
  }

  // Lids are her own skin. eye_skin.png is the face with each eye painted out from the surrounding skin,
  // so a lid revealed from it matches the face at every edge. Every lid edge follows the eye's own outline
  // (see buildEyes), is feathered over ~1.5px, and the blink lash is a crescent tapering to nothing at the
  // corners — a soft lid closing along the shape of the eye, not a bar being drawn across it.
  function drawLids(eye, blink, squint, target, crescent = 0) {
    const { ox, oy, w, h, contour } = eye;
    const { x0, top: T, bot: Bt } = contour;
    const n = T.length;
    // How tall this eye actually is. The smiling arch is a fraction of that rather than a fixed number of
    // pixels: her far eye is painted smaller, so a fixed rise collapsed it into a thin wink beside a full arc.
    let opening = 0;
    for (let i = 0; i < n; i++) opening += Bt[i] - T[i];
    const arch = Math.max(2.4, (opening / n) * 0.15);
    const edgeAt = (yOf) => {
      const steps = Math.min(n - 1, 28);
      const pts = [];
      for (let k = 0; k <= steps; k++) {
        const i = Math.round((k * (n - 1)) / steps);
        pts.push([x0 + i, yOf(i, i / (n - 1))]);
      }
      return pts;
    };
    const reveal = (pts, down, shift, alpha) => {
      const edgeY = down ? oy : oy + h;
      target.save();
      target.globalAlpha = alpha;
      target.beginPath();
      target.moveTo(ox, edgeY);
      target.lineTo(ox, pts[0][1] + shift);
      for (const [x, y] of pts) target.lineTo(x, y + shift);
      target.lineTo(ox + w, pts[pts.length - 1][1] + shift);
      target.lineTo(ox + w, edgeY);
      target.closePath();
      target.clip();
      target.drawImage(eye.skin, ox, oy);
      target.restore();
    };
    // The lid at full strength, plus two faint bands just past its edge: a soft rim instead of a cut.
    const softReveal = (pts, down) => {
      const dir = down ? 1 : -1;
      reveal(pts, down, dir * 1.4, 0.35);
      reveal(pts, down, dir * 0.7, 0.55);
      reveal(pts, down, 0, 1);
    };

    if (crescent > 0.02) {
      // Upward smiling crescent eyes (^ ^)
      const cr = crescent;
      const cLashPts = edgeAt((i, t) => lerp(T[i], Bt[i], 0.36) - arch * Math.sin(Math.PI * t));
      const upperPts = edgeAt((i, t) => {
        const normY = lerp(T[i] - (LASH_BAND - 3), Bt[i] + 1, blink);
        const archY = lerp(T[i], Bt[i], 0.36) - arch * Math.sin(Math.PI * t);
        return lerp(normY, archY, cr);
      });
      softReveal(upperPts, true);

      const lowerPts = edgeAt((i, t) => {
        const normY = lerp(Bt[i] + 1, T[i], squint * 0.5 * (0.55 + 0.45 * Math.sin(Math.PI * t)));
        const archY = lerp(T[i], Bt[i], 0.36) - arch * Math.sin(Math.PI * t);
        return lerp(normY, archY, cr);
      });
      softReveal(lowerPts, false);

      if (cr > 0.1) {
        const thick = lerp(2.8, 3.8, cr);
        target.beginPath();
        cLashPts.forEach(([x, y], k) => (k ? target.lineTo(x, y) : target.moveTo(x, y)));
        for (let k = cLashPts.length - 1; k >= 0; k--) {
          const t = k / (cLashPts.length - 1);
          target.lineTo(cLashPts[k][0], cLashPts[k][1] + thick * Math.pow(Math.sin(Math.PI * t), 0.7));
        }
        target.closePath();
        target.globalAlpha = Math.min(1, cr * 2);
        target.fillStyle = '#2f1d19';
        target.fill();

        // Her lash flick at the outer corner
        const [ex, ey] = eye.outer < 0 ? cLashPts[0] : cLashPts[cLashPts.length - 1];
        target.beginPath();
        target.moveTo(ex, ey + 0.5);
        target.quadraticCurveTo(ex + eye.outer * 6, ey - 2, ex + eye.outer * 10, ey - 6);
        target.strokeStyle = '#2f1d19';
        target.lineWidth = 2.4;
        target.lineCap = 'round';
        target.stroke();
        target.globalAlpha = 1;
      }
      return;
    }

    if (squint > 0.02) {
      // Eye-smile: the lower lid rises from the eye's bottom outline, arching most in the middle.
      const amt = squint * 0.5;
      softReveal(edgeAt((i, t) => lerp(Bt[i] + 1, T[i], amt * (0.55 + 0.45 * Math.sin(Math.PI * t)))), false);
    }

    if (blink > 0.01) {
      // From the painted lash line down to the eye's bottom outline.
      const pts = edgeAt((i) => lerp(T[i] - (LASH_BAND - 3), Bt[i] + 1, blink));
      softReveal(pts, true);
      const thick = lerp(4.2, 3.0, blink);
      target.beginPath();
      pts.forEach(([x, y], k) => (k ? target.lineTo(x, y) : target.moveTo(x, y)));
      for (let k = pts.length - 1; k >= 0; k--) {
        const t = k / (pts.length - 1);
        target.lineTo(pts[k][0], pts[k][1] + thick * Math.pow(Math.sin(Math.PI * t), 0.7));
      }
      target.closePath();
      target.globalAlpha = Math.min(1, blink * 4);
      target.fillStyle = '#2f1d19';
      target.fill();
      if (blink > 0.55) {
        // Her lash flick at the outer corner, so a closed eye still reads as hers.
        const [ex, ey] = eye.outer < 0 ? pts[0] : pts[pts.length - 1];
        target.globalAlpha = (blink - 0.55) / 0.45;
        target.beginPath();
        target.moveTo(ex, ey + 0.5);
        target.quadraticCurveTo(ex + eye.outer * 6, ey, ex + eye.outer * 10, ey - 5);
        target.strokeStyle = '#2f1d19';
        target.lineWidth = 2.2;
        target.lineCap = 'round';
        target.stroke();
      }
      target.globalAlpha = 1;
    }
  }

  // `snapshot` draws the current pose into another context at a fixed layout, without the unchanged-frame
  // skip — used to render the native loading poster from the engine itself.
  function draw(breath, c = ctx, L = s.layout, snapshot = false) {
    if (!L.w) return;
    const A = s.art;
    const { yaw, pitch, roll } = s.cam;
    const px = clamp(s.gaze.x - yaw * 0.15, -3.5, 3.5);
    const py = clamp(s.gaze.y + pitch * 0.08, -2.2, 2.2);
    const p = s.blink.progress;
    const mouth = mouthPaths();
    const [bl, br] = s.brows;

    if (!snapshot) {
      const key = [L.tx, L.ty, L.scale, c.canvas.width, c.canvas.height, yaw, pitch, roll, breath, s.hair.x, s.necklace.angle, px, py, p, s.squint, s.cheeks, s.lean, s.lid, s.pupil, s.crescent, s.wink, ...bl, ...br]
        .map((v) => v.toFixed(2))
        .join(',') + mouth.upper + mouth.lower + mouth.fillAlpha.toFixed(2);
      if (key === s.lastKey) return;
      s.lastKey = key;
      s.drawn++;
    }

    c.setTransform(1, 0, 0, 1, 0, 0);
    c.clearRect(0, 0, c.canvas.width, c.canvas.height);
    // Resizing a canvas resets its context state, so this is set on every frame rather than once.
    c.imageSmoothingEnabled = true;
    c.imageSmoothingQuality = 'high';
    const k = L.dpr * L.scale;
    const base = [k, 0, 0, k, L.dpr * L.tx, L.dpr * L.ty];

    // One layer: its perspective scale for depth z (scaled by depthStrength), a depth-weighted parallax shift,
    // its own offset, then an optional rotation about a pivot. `head` adds the head group's transform.
    const place = (z, dx, dy, rot, ox, oy, head, leanWeight = head ? 1 : 0) => {
      c.setTransform(...base);
      const effectiveZ = z * s.depthStrength;
      const d = PERSPECTIVE / (PERSPECTIVE - effectiveZ);
      c.translate(ORIGIN[0], ORIGIN[1]);
      c.scale(d, d);
      c.translate(-ORIGIN[0], -ORIGIN[1]);
      // Kept small: at the 3deg limit a stronger shift opened a visible gap between the face and the hair.
      c.translate(yaw * effectiveZ * 0.03, pitch * effectiveZ * 0.02);
      if (head) {
        c.translate(yaw * 0.4, breath * 0.45);
        c.translate(ORIGIN[0], ORIGIN[1]);
        c.rotate(roll * 0.5 * RAD);
        c.scale(1 - Math.abs(yaw) * 0.004, 1);
        c.translate(-ORIGIN[0], -ORIGIN[1]);
      }
      if (leanWeight && s.lean) {
        // A head tilt, turning about her chin.
        c.translate(LEAN_PIVOT[0], LEAN_PIVOT[1]);
        c.rotate(s.lean * leanWeight * RAD);
        c.translate(-LEAN_PIVOT[0], -LEAN_PIVOT[1]);
      }
      c.translate(dx, dy);
      if (rot) {
        c.translate(ox, oy);
        c.rotate(rot * RAD);
        c.translate(-ox, -oy);
      }
    };
    const image = (img, alpha = 1) => {
      if (alpha <= 0.004) return;
      c.globalAlpha = alpha;
      const b = A.bounds.get(img);
      if (b) c.drawImage(img, b[0], b[1], b[2], b[3], b[0], b[1], b[2], b[3]);
      else c.drawImage(img, 0, 0);
      c.globalAlpha = 1;
    };

    // The back hair tips with her head, or its silhouette would slide out from behind the painted hair.
    place(Z.hairBack, -yaw * 0.2 + s.hair.x, pitch * 0.12 + breath * 0.3, roll * 0.05, ORIGIN[0], ORIGIN[1], false, 1);
    image(A.hair_back);
    place(Z.neck, yaw * 0.04, breath * 0.6, roll * 0.15, 540, 481);
    image(A.neck);
    place(Z.body, -yaw * 0.08, breath, 0);
    image(A.body);
    place(Z.necklace, -yaw * 0.06, breath * 0.7, s.necklace.angle, 540, 490);
    image(A.necklace);

    // The eyes sit behind the face: its painted eye opening clips the whites and irises with the art's own
    // anti-aliased lash edge, so a moving iris never shows a hard or stepped boundary.
    place(Z.eyeWhites, 0, 0, 0, 0, 0, true);
    image(A.eye_sclera);
    place(Z.pupils, px, py, 0, 0, 0, true);
    // Pupil size carries feeling too: tight with surprise, a little fuller when she's warm. No catchlight is
    // drawn here: the artwork already paints one into each iris, and a second sat beside it as an obvious
    // double reflection (tried and reverted).
    for (const [img, pc] of [[A.pupil_left, PUPIL_CENTERS[0]], [A.pupil_right, PUPIL_CENTERS[1]]]) {
      c.save();
      c.translate(pc[0], pc[1]);
      c.scale(s.pupil, s.pupil);
      c.translate(-pc[0], -pc[1]);
      image(img);
      c.restore();
    }
    place(Z.face, 0, 0, 0, 0, 0, true);
    image(A.face);

    place(Z.eyelids, 0, 0, 0, 0, 0, true);
    // The upper lid rests at the emotion's droop and closes fully when she blinks. The lid path starts a little
    // above the eye, in the painted lash band (it reaches the eye at about 10% of its travel), so a droop of d
    // covers d of the eye.
    const lidP = Math.max(p, s.lid > 0.005 ? 0.1 + s.lid * 0.9 : 0);
    if (lidP > 0.01 || s.squint > 0.02 || s.crescent > 0.02 || s.wink > 0.02) {
      for (const eye of A.eyes) {
        // A crescent closes both eyes (a laugh); a wink closes only her left eye — the one on the right as seen,
        // the eye carrying outer = +1 — and leaves the other open.
        drawLids(eye, lidP, s.squint, c, Math.max(s.crescent, eye.outer > 0 ? s.wink : 0));
      }
    }

    const bx = yaw * 0.06;
    place(Z.brows, bx + bl[0], bl[1], bl[2], 455, 245, true);
    image(A.eyebrow_left);
    place(Z.brows, bx + br[0], br[1], br[2], 586, 205, true);
    image(A.eyebrow_right);

    place(Z.mouth, 0, 0, 0, 0, 0, true);
    c.lineCap = 'round';
    if (mouth.fill) {
      c.globalAlpha = mouth.fillAlpha;
      c.fillStyle = A.cavity;
      c.fill(new Path2D(mouth.fill));
      if (mouth.teeth) {
        c.globalAlpha = mouth.teethAlpha;
        c.fillStyle = '#FFF8F4';
        c.fill(new Path2D(mouth.teeth));
      }
    }
    c.globalAlpha = mouth.lowerAlpha;
    c.strokeStyle = 'rgba(180,85,80,0.55)';
    c.lineWidth = 2.6;
    c.stroke(new Path2D(mouth.lower));
    c.globalAlpha = 1;
    c.strokeStyle = '#6E332E';
    c.lineWidth = 3.2;
    c.stroke(new Path2D(mouth.upper));

    place(Z.cheeks, 0, 0, 0, 0, 0, true);
    image(A.cheeks_smile, s.cheeks);
    place(Z.nose, yaw * 0.18, 0, 0, 0, 0, true);
    image(A.nose);
    place(Z.glasses, yaw * 0.3, 0, 0, 0, 0, true);
    image(A.glasses);
    place(Z.hairFront, yaw * 0.4, 0, 0, 0, 0, true);
    image(A.hair_front);

    c.setTransform(1, 0, 0, 1, 0, 0);
  }

  function schedule() {
    if (!s.raf && !s.destroyed && s.visible && s.inView) s.raf = requestAnimationFrame(tick);
  }

  // Pointer tracking is opt-in and ignores touch: on a phone a face that chases your thumb reads as twitchy.
  function onPointerMove(ev) {
    if (ev.pointerType === 'touch') return;
    const r = container.getBoundingClientRect();
    const nx = clamp(((ev.clientX - r.left) / r.width) * 2 - 1, -1, 1);
    const ny = clamp(((ev.clientY - r.top) / r.height) * 2 - 1, -1, 1);
    s.pointer = { yaw: nx * 2.4, pitch: -ny * 1.6, x: nx * 3, y: ny * 2, active: true };
  }
  function onPointerLeave() {
    s.pointer = { yaw: 0, pitch: 0, x: 0, y: 0, active: false };
  }
  if (options.followPointer) {
    container.addEventListener('pointermove', onPointerMove);
    container.addEventListener('pointerleave', onPointerLeave);
  }

  const resizeObserver = new ResizeObserver(() => {
    layout();
    s.lastKey = '';
  });
  resizeObserver.observe(container);
  const intersectionObserver = new IntersectionObserver(([entry]) => {
    s.inView = entry.isIntersecting;
    s.last = 0;
    schedule();
  });
  intersectionObserver.observe(container);
  function onVisibility() {
    s.visible = !document.hidden;
    // The next frame restarts from a nominal dt instead of the whole time spent hidden.
    s.last = 0;
    schedule();
  }
  document.addEventListener('visibilitychange', onVisibility);

  const ready = Promise.all(LAYER_FILES.map((name) => loadImage(`${assetBase}/${name}.png`))).then((images) => {
    if (s.destroyed) return;
    const art = {};
    LAYER_FILES.forEach((name, i) => {
      art[name] = images[i];
    });
    art.nose = bakeShadow(art.nose, 1.8, 2.5, 'rgba(55,25,20,0.072)');
    art.glasses = bakeShadow(art.glasses, 2.8, 4, 'rgba(45,20,15,0.09)');
    art.eyes = buildEyes(art.eye_sclera, art.eye_skin);
    // Keyed by the final image objects, so the baked nose and glasses canvases get their padded bounds.
    art.bounds = new Map(Object.entries(LAYER_BOUNDS).map(([name, b]) => [art[name], b]));
    art.cavity = ctx.createLinearGradient(0, 362, 0, 386);
    art.cavity.addColorStop(0, '#5A2421');
    art.cavity.addColorStop(1, '#803833');
    s.art = art;
    layout();
    s.lastKey = '';
    container.classList.add('is-ready');
    emit('ready');
  });

  applyTargets();
  s.idleAmp = idleTarget();
  layout();
  schedule();

  const api = {
    ready,
    get state() {
      return snapshot();
    },

    /** Base emotion. 'listening' is accepted as a shorthand for startListening(). Unknown names are ignored. */
    setEmotion(name) {
      if (name === 'listening') return api.startListening();
      if (!EMOTIONS[name]) {
        console.warn(`Ligaya: unknown emotion "${name}". Expected one of: ${EMOTION_NAMES.join(', ')}, listening`);
        return api;
      }
      clearTimeout(s.smileTimer);
      s.smiling = false;
      if (s.emotion === name) {
        applyTargets();
        return api;
      }
      s.emotion = name;
      const e = EMOTIONS[name];
      // A change of feeling interrupts whatever she was idly doing. Most changes come with a blink, the way a
      // person resets; surprise doesn't (the eyes pop instead). Some carry a small head gesture as they land.
      s.act.name = null;
      if (!isReduced()) {
        const now = performance.now();
        if (!e.noBlink) startBlink(now);
        if (e.onset) s.nod.vel += e.onset;
        if (e.onsetAct) startAct(e.onsetAct, now);
      }
      applyTargets();
      emit('state', snapshot());
      return api;
    },

    startSpeaking() {
      if (s.speaking) return api;
      s.speaking = true;
      s.listening = false;
      s.syllable = 0;
      s.pausing = false;
      s.syllables = 0;
      s.maxSyllables = 5 + Math.floor(Math.random() * 4);
      nextViseme();
      applyTargets();
      emit('state', snapshot());
      return api;
    },

    stopSpeaking() {
      if (!s.speaking) return api;
      s.speaking = false;
      emit('state', snapshot());
      return api;
    },

    startListening() {
      if (s.listening) return api;
      s.listening = true;
      s.speaking = false;
      applyTargets();
      emit('state', snapshot());
      return api;
    },

    stopListening() {
      if (!s.listening) return api;
      s.listening = false;
      applyTargets();
      emit('state', snapshot());
      return api;
    },

    blink() {
      startBlink(performance.now());
      return api;
    },

    /** A brief genuine smile — mouth, cheeks and eye squint together — then back to the current emotion. */
    smile(duration = 2400) {
      clearTimeout(s.smileTimer);
      s.smiling = true;
      applyTargets();
      s.smileTimer = setTimeout(() => {
        s.smiling = false;
        applyTargets();
      }, duration);
      return api;
    },

    /** Point her eyes (and a little of her head) somewhere, in -1..1 container space. null releases. */
    lookAt(x, y) {
      s.look = x === null || x === undefined ? null : { x: clamp(x, -1, 1), y: clamp(y ?? 0, -1, 1) };
      return api;
    },

    /**
     * Hold a slight head turn. The artwork is flat, so 'left_3_4'/'right_3_4' are a few degrees of turn with
     * depth parallax, not a true three-quarter view.
     */
    setCameraView(name) {
      if (!(name in CAMERA_VIEWS)) {
        console.warn(`Ligaya: unknown camera view "${name}". Expected one of: ${Object.keys(CAMERA_VIEWS).join(', ')}`);
        return api;
      }
      if (s.viewName === name) return api;
      s.viewName = name;
      s.viewYaw = CAMERA_VIEWS[name];
      emit('state', snapshot());
      return api;
    },

    resetCamera() {
      s.look = null;
      return api.setCameraView('front');
    },

    /** Lower idle motion while the person is reading, without freezing her. */
    setCalm(calm) {
      s.calm = Boolean(calm);
      return api;
    },

    /** 'system' follows prefers-reduced-motion; 'calm' keeps idle motion small; 'still' removes it. */
    setMotion(mode) {
      s.motion = ['system', 'calm', 'still'].includes(mode) ? mode : 'system';
      return api;
    },

    setFrame(name, { animate = false } = {}) {
      if (!FRAMES[name]) return api;
      s.frameName = name;
      const target = frameState(FRAMES[name]);
      if (animate && !isReduced()) {
        s.frameFrom = { ...s.frame };
        s.frameTo = target;
        s.frameStart = performance.now();
      } else {
        s.frame = target;
        s.frameTo = null;
        layout();
      }
      return api;
    },

    /** Multiplier for animation playback speed (0.5 to 2.0). */
    setAnimationSpeed(multiplier) {
      const v = typeof multiplier === 'number' && !isNaN(multiplier) ? multiplier : 1.0;
      s.animationSpeed = clamp(v, 0.5, 2.0);
      emit('state', snapshot());
      return api;
    },

    /** Depth parallax strength (0.0 to 1.0). */
    setDepthStrength(strength) {
      const v = typeof strength === 'number' && !isNaN(strength) ? strength : 0.6;
      s.depthStrength = clamp(v, 0.0, 1.0);
      emit('state', snapshot());
      return api;
    },

    reset() {
      clearTimeout(s.smileTimer);
      s.smiling = false;
      s.listening = false;
      s.speaking = false;
      s.calm = false;
      s.look = null;
      s.viewName = 'front';
      s.viewYaw = 0;
      s.emotion = 'neutral';
      s.crescent = 0;
      s.crescentTarget = 0;
      s.wink = 0;
      s.winkTarget = 0;
      applyTargets();
      emit('state', snapshot());
      return api;
    },

    on(type, fn) {
      if (!listeners.has(type)) listeners.set(type, new Set());
      listeners.get(type).add(fn);
      return () => listeners.get(type).delete(fn);
    },

    destroy() {
      s.destroyed = true;
      cancelAnimationFrame(s.raf);
      clearTimeout(s.smileTimer);
      resizeObserver.disconnect();
      intersectionObserver.disconnect();
      document.removeEventListener('visibilitychange', onVisibility);
      container.removeEventListener('pointermove', onPointerMove);
      container.removeEventListener('pointerleave', onPointerLeave);
      canvas.remove();
      container.classList.remove('lg-stage', 'is-ready');
      listeners.clear();
      s.art = null;
    },
  };

  if (options.debug) {
    // Deterministic poses for visual testing: freeze the idle clock, hold a blink or mouth shape, read fps.
    api.debug = {
      freeze(on) {
        s.frozen = Boolean(on);
        return api;
      },
      setBlink(progress) {
        s.blinkOverride = progress === null || progress === undefined ? null : clamp(progress, 0, 1);
        return api;
      },
      setViseme(v) {
        s.visemeOverride = v ? { openness: v.openness ?? 0, width: v.width ?? 0, lift: v.lift ?? 0 } : null;
        return api;
      },
      fps: () => s.fps,
      /** Frame-interval stats over the last (up to) 300 frames, in ms. */
      frameStats() {
        const n = Math.min(s.dtIdx, s.dts.length);
        const v = Array.from(s.dts.slice(0, n)).sort((a, b) => a - b);
        if (!n) return null;
        return { frames: n, avg: +(v.reduce((a, b) => a + b, 0) / n).toFixed(2), p95: +v[Math.floor(n * 0.95)].toFixed(2), max: +v[n - 1].toFixed(2), over20ms: v.filter((x) => x > 20).length };
      },
      /** The current pose, unfaded, at `width` px wide (full 1008x740 artwork frame), as a PNG data URL. */
      snapshot(width = 504) {
        if (!s.art) return null;
        const out = document.createElement('canvas');
        out.width = width;
        out.height = Math.round((width * CANVAS_H) / CANVAS_W);
        draw(0, out.getContext('2d'), { tx: 0, ty: 0, scale: width / CANVAS_W, w: width, h: out.height, dpr: 1 }, true);
        return out.toDataURL('image/png');
      },
      /** Start one idle behaviour now (one of ACT_NAMES). */
      act(name) {
        startAct(name, performance.now());
        return api;
      },
      detail: () => ({ idleAmp: s.idleAmp, clock: s.clock, drawn: s.drawn, cam: { ...s.cam }, blink: s.blink.progress, viseme: { ...s.viseme }, layout: { ...s.layout }, lean: s.lean, lid: s.lid, pupil: s.pupil, crescent: s.crescent, act: s.act.name, fps: s.fps }),
    };
  }

  return api;
}

export const RIG_SIZE = { width: CANVAS_W, height: CANVAS_H };
