'use strict';
// Host function registry; semantics identical to compat.diff.HostFunctions (JVM).
// `this` is the jsonata-js call context ({environment, input}).

function typeOf(o) {
    if (o === undefined) return 'undefined';
    if (o === null) return 'null';
    if (Array.isArray(o)) return 'array';
    if (typeof o === 'function' || (o && (o._jsonata_lambda || o._jsonata_function))) return 'function';
    if (o instanceof RegExp) return 'function';
    return typeof o;
}
function str(o) {
    if (o === undefined) return 'undefined';
    if (typeof o === 'number') return Object.is(o, -0) ? '-0' : String(o);
    return String(o);
}
async function callFn(self, fn, args) {
    if (fn && typeof fn.apply === 'function') return await fn.apply(self, args);
    throw new Error('host: argument is not callable from host: ' + typeOf(fn));
}

const registry = {
    echo: function (x) { return x; },
    upper: function (s) { return typeof s === 'string' ? s.toUpperCase() : undefined; },
    concat: function (a, b) { return str(a) + str(b); },
    add: function (a, b) { return a + b; },
    typeOf: function (x) { return typeOf(x); },
    argCount: function (...a) { return a.length; },
    argTypes: function (...a) { return a.map(typeOf); },
    asyncEcho: function (x) { return new Promise(r => setTimeout(() => r(x), 1)); },
    asyncUpper: function (s) {
        return new Promise(r => setTimeout(() => r(typeof s === 'string' ? s.toUpperCase() : undefined), 1));
    },
    apply: async function (fn, x) { return await callFn(this, fn, [x]); },
    apply2: async function (fn, a, b) { return await callFn(this, fn, [a, b]); },
    mapAll: async function (fn, arr) {
        const r = [];
        if (Array.isArray(arr)) for (const o of arr) r.push(await callFn(this, fn, [o]));
        return r;
    },
    contextInput: function () { return this.input; },
    throwError: function (m) { throw new Error(str(m)); },
    makeObject: function () { return { z: 1, a: 2, m: null }; },
};

function get(name) {
    const f = registry[name];
    if (!f) throw new Error('unknown host function ' + name);
    return f;
}
module.exports = { get, registry };
