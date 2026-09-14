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

const EMOTIONS = {
  neutral: { brows: [[0, 0, 0], [0, 0, 0]], squint: 0, cheeks: 0, gaze: [0, 0], mouth: 'neutral', pitch: 0 },
  happy: { brows: [[0, -2.5, 1.5], [0, -2.5, -1.5]], squint: 0.65, cheeks: 0.85, gaze: [0, 0], mouth: 'happy', pitch: 0 },
  thinking: { brows: [[0, -3.5, 2.5], [0, 1.5, 3.5]], squint: 0, cheeks: 0, gaze: [2.2, -2.0], mouth: 'thinking', pitch: 0.4 },
  concerned: { brows: [[1, -2.8, -4], [-1, -2.8, 4]], squint: 0.2, cheeks: 0, gaze: [0, 0.3], mouth: 'concerned', pitch: -0.4 },
  surprised: { brows: [[0, -6.5, 1], [0, -6.5, -1]], squint: 0, cheeks: 0, gaze: [0, 0], mouth: 'surprised', pitch: 0.6 },
  attentive: { brows: [[0, -1.8, 0.6], [0, -1.8, -0.6]], squint: 0, cheeks: 0, gaze: [0, 0.4], mouth: 'neutral', pitch: -0.8 },
  reassuring: { brows: [[0.6, -1.8, -2.4], [-0.6, -1.8, 2.4]], squint: 0.15, cheeks: 0.25, gaze: [0, 0.3], mouth: 'smile', mouthScale: 0.7, pitch: -0.3 },
  // Focused and steady: brows drawn level, mouth closed and firm, gaze held on the person, idle motion
  // damped so she never pulls the eye away from emergency status and actions.
  emergency: { brows: [[0.8, -2.0, -2.6], [-0.8, -2.0, 2.6]], squint: 0.1, cheeks: 0, gaze: [0, 0.2], mouth: 'firm', pitch: -0.5, idle: 0.35 },
  // Relief: a soft closed smile and a light eye-smile — warmer than neutral, quieter than happy.
  resolved: { brows: [[0, -1.2, 0.8], [0, -1.2, -0.8]], squint: 0.35, cheeks: 0.5, gaze: [0, 0.1], mouth: 'smile', mouthScale: 0.85, pitch: 0.1 },
};

export const EMOTION_NAMES = Object.keys(EMOTIONS);

const MOUTHS = {
  neutral: { pL: [508, 377.5], cUL: [528, 376], cUR: [556, 368], pR: [580, 355], cLL: [528, 378], cLR: [556, 370], openness: 0, teeth: 0 },
  smile: { pL: [508, 372], cUL: [526, 377], cUR: [556, 367], pR: [580, 350], cLL: [526, 381], cLR: [556, 371], openness: 0, teeth: 0 },
  happy: { pL: [509, 371], cUL: [528, 373], cUR: [556, 364], pR: [580, 350], cLL: [526, 383], cLR: [558, 373], openness: 0.4, teeth: 0.5 },
  thinking: { pL: [514, 375], cUL: [530, 376], cUR: [554, 367], pR: [574, 357], cLL: [530, 377], cLR: [554, 368], openness: 0, teeth: 0 },
  concerned: { pL: [510, 379], cUL: [528, 375], cUR: [556, 365], pR: [579, 358], cLL: [528, 377], cLR: [556, 367], openness: 0, teeth: 0 },
  surprised: { pL: [528, 371], cUL: [534, 363], cUR: [552, 362], pR: [558, 368], cLL: [534, 381], cLR: [552, 381], openness: 0.68, teeth: 0.15 },
  firm: { pL: [510, 378], cUL: [528, 376.5], cUR: [556, 368.5], pR: [578, 357], cLL: [528, 377.5], cLR: [556, 369.5], openness: 0, teeth: 0 },
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
    if (s.listening) {
      // Attention on top of whatever she feels: brows lift a little, she leans in, gaze settles on the person.
      for (const b of s.browsTarget) b[1] -= 1.6 * k;
      s.pitchLeanTarget -= 0.8;
      s.gazeTarget = { x: s.gazeTarget.x * 0.5, y: s.gazeTarget.y * 0.5 + 0.4 * k };
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
      b.next = now + (Math.random() < 0.12 ? rand(180, 320) : rand(2800, 6800));
    }
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
    const dt = s.last ? Math.min((now - s.last) / 1000, 0.1) : 1 / 60;
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

    // Head: a held camera view, pointer or explicit look, plus a slow wander — clamped so the flat artwork
    // never visibly shears.
    const lookPitch = s.look ? -s.look.y * 1.2 : 0;
    const lookYaw = s.look ? s.look.x * 1.2 : 0;
    const followYaw = still ? 0 : s.pointer.yaw + lookYaw;
    const followPitch = still ? 0 : s.pointer.pitch + lookPitch;
    const targetYaw = clamp(s.viewYaw + followYaw + Math.sin(t * 0.8) * 0.45 * idle, -MAX_YAW, MAX_YAW);
    const targetPitch = clamp(followPitch + Math.cos(t * 1.1) * 0.3 * idle + s.pitchLean, -MAX_PITCH, MAX_PITCH);
    const camEase = damp(6, dt);
    const prevYaw = s.cam.yaw;
    s.cam.yaw += (targetYaw - s.cam.yaw) * camEase;
    s.cam.pitch += (targetPitch - s.cam.pitch) * camEase;
    s.cam.roll += (Math.sin(t * 0.6) * 0.25 * idle - s.cam.roll) * camEase;
    s.pitchLean += (s.pitchLeanTarget - s.pitchLean) * damp(4, dt);

    // Secondary motion: necklace pendulum and back-hair spring, driven by how fast the head turns. Left to
    // settle on their own rather than zeroed, so switching to still motion never snaps them.
    const lateral = ((s.cam.yaw - prevYaw) / Math.max(dt, 1e-3)) * 0.9;
    const n = s.necklace;
    n.vel += (-18 * n.angle - lateral * 4.5 - 4 * n.vel) * dt;
    n.angle = clamp(n.angle + n.vel * dt, -6, 6);
    const h = s.hair;
    h.vel += (-8 * h.x - lateral * 2.2 - 5 * h.vel) * dt;
    h.x = clamp(h.x + h.vel * dt, -4, 4);

    // Breathing moves the torso and, less, the neck and head. Nothing is scaled.
    const breath = Math.sin(t * 1.6) * 2.2 * idle;

    // Eyes: occasional small saccades while idle, so the gaze is never frozen.
    if (!still && !s.frozen && !s.pointer.active && !s.look && now > s.nextSaccade) {
      const settle = s.emotion === 'thinking' || s.emotion === 'emergency' || s.listening;
      s.saccade = settle ? { x: 0, y: 0 } : { x: rand(-0.9, 0.9), y: rand(-0.45, 0.45) };
      s.nextSaccade = now + rand(2500, 5500);
    }
    if (still || s.frozen) s.saccade = { x: 0, y: 0 };
    let gx = s.gazeTarget.x + s.saccade.x;
    let gy = s.gazeTarget.y + s.saccade.y;
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

    const e9 = damp(9, dt);
    for (let i = 0; i < 2; i++) for (let j = 0; j < 3; j++) s.brows[i][j] += (s.browsTarget[i][j] - s.brows[i][j]) * e9;
    s.squint += (s.squintTarget - s.squint) * e9;
    s.cheeks += (s.cheeksTarget - s.cheeks) * e9;
    const e11 = damp(11, dt);
    for (const p of MOUTH_POINTS) {
      s.mouth[p][0] += (s.mouthTarget[p][0] - s.mouth[p][0]) * e11;
      s.mouth[p][1] += (s.mouthTarget[p][1] - s.mouth[p][1]) * e11;
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
    const uyL = m.cUL[1] - lift * 0.9;
    const uyR = m.cUR[1] - lift * 0.9;
    const dyL = m.cLL[1] + open * 5.2;
    const dyR = m.cLR[1] + open * 5.2;
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
      out.fillAlpha = Math.min(0.68, open * 1.15);
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
  function drawLids(eye, blink, squint, target) {
    const { ox, oy, w, h, contour } = eye;
    const { x0, top: T, bot: Bt } = contour;
    const n = T.length;
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

  // `snapshot` draws the current pose into another context at a fixed layout, without the bottom fade or
  // the unchanged-frame skip — used to render the native loading poster from the engine itself.
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
      const key = [L.tx, L.ty, L.scale, c.canvas.width, c.canvas.height, yaw, pitch, roll, breath, s.hair.x, s.necklace.angle, px, py, p, s.squint, s.cheeks, ...bl, ...br]
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

    // One layer: its perspective scale for depth z (exactly what CSS translateZ under perspective:1200px
    // produced), a depth-weighted parallax shift, its own offset, then an optional rotation about a pivot.
    // `head` adds the head group's transform between the depth scale and the layer's own.
    const place = (z, dx, dy, rot, ox, oy, head) => {
      c.setTransform(...base);
      const d = PERSPECTIVE / (PERSPECTIVE - z);
      c.translate(ORIGIN[0], ORIGIN[1]);
      c.scale(d, d);
      c.translate(-ORIGIN[0], -ORIGIN[1]);
      // Kept small: at the 3deg limit a stronger shift opened a visible gap between the face and the hair.
      c.translate(yaw * z * 0.03, pitch * z * 0.02);
      if (head) {
        c.translate(yaw * 0.4, breath * 0.45);
        c.translate(ORIGIN[0], ORIGIN[1]);
        c.rotate(roll * 0.5 * RAD);
        c.scale(1 - Math.abs(yaw) * 0.004, 1);
        c.translate(-ORIGIN[0], -ORIGIN[1]);
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
      c.drawImage(img, 0, 0);
      c.globalAlpha = 1;
    };

    place(Z.hairBack, -yaw * 0.2 + s.hair.x, pitch * 0.12 + breath * 0.3, roll * 0.05, ORIGIN[0], ORIGIN[1]);
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
    image(A.pupil_left);
    image(A.pupil_right);
    place(Z.face, 0, 0, 0, 0, 0, true);
    image(A.face);

    place(Z.eyelids, 0, 0, 0, 0, 0, true);
    if (p > 0.01 || s.squint > 0.02) for (const eye of A.eyes) drawLids(eye, p, s.squint, c);

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
    if (snapshot) return;
    // The artwork stops on a hard edge at canvas y=706: fade the bottom out so that edge is never visible.
    const cut = Math.min(L.h, 706 * L.scale + L.ty) * L.dpr;
    const fade = c.createLinearGradient(0, cut - L.h * 0.16 * L.dpr, 0, cut);
    fade.addColorStop(0, 'rgba(0,0,0,1)');
    fade.addColorStop(1, 'rgba(0,0,0,0)');
    c.globalCompositeOperation = 'destination-in';
    c.fillStyle = fade;
    c.fillRect(0, 0, c.canvas.width, c.canvas.height);
    c.globalCompositeOperation = 'source-over';
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
      detail: () => ({ idleAmp: s.idleAmp, clock: s.clock, drawn: s.drawn, cam: { ...s.cam }, blink: s.blink.progress, viseme: { ...s.viseme }, layout: { ...s.layout } }),
    };
  }

  return api;
}

export const RIG_SIZE = { width: CANVAS_W, height: CANVAS_H };
