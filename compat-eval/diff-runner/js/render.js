'use strict';
// Human-readable rendering of canonical typed trees (for reports).
function render(x, max = 400) {
    const s = r(x);
    return s.length > max ? s.slice(0, max) + '…' : s;
}
function r(x) {
    if (x === null) return 'null';
    if (typeof x === 'string') return JSON.stringify(x);
    if (typeof x === 'boolean') return String(x);
    if (!Array.isArray(x)) return JSON.stringify(x);
    switch (x[0]) {
        case 'u': return 'undefined';
        case 'n': return x[1] + (x[2] && x[2] !== 'L' && x[2] !== 'I' && x[2] !== 'D' ? '/' + x[2] : '');
        case 'a': return '[' + x[1].map(r).join(',') + ']';
        case 's': return '[' + x[1].map(r).join(',') + ']';
        case 'o': return '{' + x[1].map(e => JSON.stringify(e[0]) + ':' + r(e[1])).join(',') + '}';
        case 'f': return '<function:' + x[1] + '>';
        default: return '<' + x.join(':') + '>';
    }
}
function renderOutcome(res) {
    if (!res) return 'missing';
    if (res.status === 'ok') return render(res.result);
    const e = res.error || {};
    return res.status + (e.code ? ' ' + e.code : '') + (e.position !== null && e.position !== undefined ? '@' + e.position : '') +
        (e.code ? '' : ' ' + String(e.exception || '') + ': ' + String(e.message || '').slice(0, 160));
}
module.exports = { render, renderOutcome };
