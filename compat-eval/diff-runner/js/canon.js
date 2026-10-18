'use strict';
// Canonical typed tree - identical to compat.diff.Json.writeCanon on the JVM side:
// null | true | false | "string" | ["n", lexeme] | ["a", [...]] | ["s", [...]] (sequence)
// | ["o", [[key, value], ...]] | ["u"] (undefined) | ["f", kind] | ["x", description]

function numberLexeme(n) {
    if (Object.is(n, -0)) return '-0';
    return String(n);
}

function canon(v, seen) {
    seen = seen || new Set();
    if (v === undefined) return ['u'];
    if (v === null) return null;
    switch (typeof v) {
        case 'string': return v;
        case 'boolean': return v;
        case 'number': return ['n', numberLexeme(v)];
        case 'function': return ['f', 'native'];
        case 'bigint': return ['x', 'bigint:' + v];
        case 'symbol': return ['x', 'symbol'];
    }
    if (seen.has(v)) return ['x', 'cycle'];
    if (v && v._jsonata_lambda) return ['f', 'lambda'];
    if (v && v._jsonata_function) return ['f', 'native'];
    if (v instanceof RegExp) return ['f', 'regex'];
    seen.add(v);
    let r;
    if (Array.isArray(v)) {
        r = [v.sequence === true ? 's' : 'a', v.map(x => canon(x, seen))];
    } else if (typeof v.then === 'function') {
        r = ['x', 'promise'];
    } else {
        r = ['o', Object.keys(v).map(k => [k, canon(v[k], seen)])];
    }
    seen.delete(v);
    return r;
}

// JSON.stringify with all non-ASCII escaped as UTF-16 code units (same as the JVM writer).
function stringify(v) {
    return JSON.stringify(v).replace(/[\u007f-￿]/g, c => '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0'));
}

// neutral JSON value -> plain JS (inputs/bindings). Numbers are JS doubles anyway.
module.exports = { canon, stringify, numberLexeme };
