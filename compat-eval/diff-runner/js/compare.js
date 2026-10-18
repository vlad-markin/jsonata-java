'use strict';
// Comparison of canonical results (see canon.js / compat.diff.Json.writeCanon).
// Produces a list of difference kinds; see diff-runner/README.md for definitions.

const SEVERITY = [
    // most severe first
    'JS_TIMEOUT', 'JVM_TIMEOUT', 'JVM_CRASH', 'JVM_DIED', 'JVM_ERROR', 'JVM_NO_ERROR', 'ERROR_CODE',
    'VALUE', 'NUMBER', 'UNDEF_NULL', 'SINGLETON', 'NUMBER_PRECISION', 'NEG_ZERO', 'KEY_ORDER',
    'ERROR_POSITION', 'ERROR_TOKEN', 'CRASH_BOTH', 'JS_CRASH', 'BOTH_TIMEOUT',
];
const INFO = ['JAVA_DOUBLE_INTEGRAL', 'SEQ_FLAG', 'ERROR_MESSAGE', 'FUNCTION_KIND'];

function isNum(x) { return Array.isArray(x) && x[0] === 'n'; }
function isArr(x) { return Array.isArray(x) && (x[0] === 'a' || x[0] === 's'); }
function isObj(x) { return Array.isArray(x) && x[0] === 'o'; }
function isUndef(x) { return Array.isArray(x) && x[0] === 'u'; }
function isFn(x) { return Array.isArray(x) && x[0] === 'f'; }

function numEq(la, lb) {
    const a = Number(la), b = Number(lb);
    return { a, b };
}

// Strip ["u"]-valued keys (JS drops undefined object values when serialising; we treat them as absent)
function objEntries(x) { return x[1].filter(e => !isUndef(e[1])); }

function diff(a, b, path, out, info) {
    if (isNum(a) && isNum(b)) {
        if (b[2] === 'D' && /^-?\d+$/.test(b[1])) info.add('JAVA_DOUBLE_INTEGRAL');
        if (a[1] === b[1]) return;
        const set = new Set([a[1], b[1]]);
        if (set.has('-0') && set.has('0')) { out.push({ kind: 'NEG_ZERO', path }); return; }
        const { a: x, b: y } = numEq(a[1], b[1]);
        if (isFinite(x) && isFinite(y) && Math.abs(x - y) <= 1e-12 * Math.max(Math.abs(x), Math.abs(y))) {
            out.push({ kind: 'NUMBER_PRECISION', path, js: a[1], jvm: b[1] });
        } else {
            out.push({ kind: 'NUMBER', path, js: a[1], jvm: b[1] });
        }
        return;
    }
    if (isUndef(a) && b === null || a === null && isUndef(b)) { out.push({ kind: 'UNDEF_NULL', path }); return; }
    if (isFn(a) && isFn(b)) { if (a[1] !== b[1]) info.add('FUNCTION_KIND'); return; }
    if (isArr(a) && isArr(b)) {
        if (a[0] !== b[0]) info.add('SEQ_FLAG');
        if (a[1].length !== b[1].length) {
            out.push({ kind: 'VALUE', path, note: 'array length ' + a[1].length + ' vs ' + b[1].length });
            return;
        }
        for (let i = 0; i < a[1].length; i++) diff(a[1][i], b[1][i], path + '[' + i + ']', out, info);
        return;
    }
    if (isArr(a) && a[1].length === 1 && !isArr(b)) {
        const sub = []; diff(a[1][0], b, path, sub, info);
        if (sub.every(d => d.kind !== 'VALUE')) { out.push({ kind: 'SINGLETON', path, note: 'JS array of 1, JVM scalar' }); out.push(...sub); return; }
    }
    if (isArr(b) && b[1].length === 1 && !isArr(a)) {
        const sub = []; diff(a, b[1][0], path, sub, info);
        if (sub.every(d => d.kind !== 'VALUE')) { out.push({ kind: 'SINGLETON', path, note: 'JS scalar, JVM array of 1' }); out.push(...sub); return; }
    }
    if (isObj(a) && isObj(b)) {
        const ea = objEntries(a), eb = objEntries(b);
        const ka = ea.map(e => e[0]), kb = eb.map(e => e[0]);
        const sa = new Set(ka), sb = new Set(kb);
        const missing = ka.filter(k => !sb.has(k)), extra = kb.filter(k => !sa.has(k));
        if (missing.length || extra.length) {
            out.push({ kind: 'VALUE', path, note: 'keys only in JS: ' + JSON.stringify(missing) + ', only in JVM: ' + JSON.stringify(extra) });
            return;
        }
        const mb = new Map(eb);
        for (const [k, v] of ea) diff(v, mb.get(k), path + '.' + k, out, info);
        if (ka.join('\u0000') !== kb.join('\u0000')) out.push({ kind: 'KEY_ORDER', path, js: ka, jvm: kb });
        return;
    }
    if (JSON.stringify(a) === JSON.stringify(b)) return;
    out.push({ kind: 'VALUE', path });
}

function primary(kinds) {
    for (const k of SEVERITY) if (kinds.includes(k)) return k;
    return kinds.length ? kinds[0] : 'MATCH';
}

// Compare a JS result line with a JVM result line.
function compareResults(js, jvm) {
    const details = [];
    const info = new Set();
    const st = (r) => r ? r.status : 'missing';
    const sj = st(js), sv = st(jvm);
    const jsT = sj === 'timeout' || sj === 'died';
    if (jsT && (sv === 'timeout' || sv === 'died')) details.push({ kind: 'BOTH_TIMEOUT' });
    else {
        if (jsT) details.push({ kind: 'JS_TIMEOUT' });
        if (sv === 'timeout') details.push({ kind: 'JVM_TIMEOUT' });
        if (sv === 'died') details.push({ kind: 'JVM_DIED' });
    }
    if (details.length === 0) {
        if (sj === 'ok' && sv === 'ok') {
            diff(js.result, jvm.result, '$', details, info);
        } else if (sj === 'ok' && sv === 'error') {
            details.push({ kind: 'JVM_ERROR' });
        } else if (sv === 'crash') {
            details.push({ kind: sj === 'crash' ? 'CRASH_BOTH' : 'JVM_CRASH' });
        } else if (sj === 'error' && sv === 'ok') {
            details.push({ kind: 'JVM_NO_ERROR' });
        } else if (sj === 'error' && sv === 'error') {
            if (js.error.code !== jvm.error.code) details.push({ kind: 'ERROR_CODE', js: js.error.code, jvm: jvm.error.code });
            else {
                if (js.error.position !== jvm.error.position) details.push({ kind: 'ERROR_POSITION', js: js.error.position, jvm: jvm.error.position });
                if ((js.error.token || null) !== (jvm.error.token || null)) details.push({ kind: 'ERROR_TOKEN', js: js.error.token, jvm: jvm.error.token });
                if (js.error.message !== jvm.error.message) info.add('ERROR_MESSAGE');
            }
        } else if (sj === 'crash') {
            details.push({ kind: 'JS_CRASH' });
        } else {
            details.push({ kind: 'VALUE', note: 'status ' + sj + ' vs ' + sv });
        }
    }
    const kinds = [...new Set(details.map(d => d.kind))];
    return { category: primary(kinds), kinds, details, info: [...info] };
}

// Build an expected-result line from a jsonata test-suite case ("expected" field of a case).
function canonPlain(v) {
    if (v === undefined) return ['u'];
    if (v === null || typeof v === 'string' || typeof v === 'boolean') return v;
    if (typeof v === 'number') return ['n', Object.is(v, -0) ? '-0' : String(v)];
    if (Array.isArray(v)) return ['a', v.map(canonPlain)];
    return ['o', Object.keys(v).map(k => [k, canonPlain(v[k])])];
}

// Strict suite check: engine result vs. expectation of the jsonata test-suite.
// Mirrors run-test-suite.js (chai deep.equal ignores key order) but also checks error codes,
// "token" and fields of "error" objects.
function checkExpected(exp, r) {
    if (!r || r.status === 'timeout' || r.status === 'died') return { pass: false, why: 'timeout/died' };
    if (exp.code) {
        if (r.status !== 'error') return { pass: false, why: 'expected error ' + exp.code + ', got ' + r.status };
        if (r.error.code !== exp.code) return { pass: false, why: 'error code ' + r.error.code + ' != ' + exp.code };
        if (exp.token !== undefined && r.error.token !== exp.token) return { pass: false, why: 'token ' + r.error.token + ' != ' + exp.token, soft: true };
        if (exp.position !== undefined && r.error.position !== exp.position) return { pass: false, why: 'position ' + r.error.position + ' != ' + exp.position, soft: true };
        return { pass: true };
    }
    if (r.status !== 'ok') return { pass: false, why: 'unexpected ' + r.status + ' ' + (r.error && (r.error.code || r.error.message)) };
    const want = exp.undefinedResult ? ['u'] : canonPlain(exp.result);
    const details = []; const info = new Set();
    diff(want, r.result, '$', details, info);
    const bad = details.filter(d => d.kind !== 'KEY_ORDER');
    if (bad.length) return { pass: false, why: bad.map(d => d.kind + '@' + d.path + (d.js !== undefined ? ' ' + d.js + ' vs ' + d.jvm : '') + (d.note ? ' ' + d.note : '')).join('; ') };
    return { pass: true };
}

module.exports = { compareResults, checkExpected, canonPlain, SEVERITY, INFO };
