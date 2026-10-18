'use strict';
// JS side of the diff-runner: reads cases (JSONL) on stdin, writes results (JSONL) on stdout.
// Engine: npm jsonata (version pinned in compat-eval/package.json, must be 2.2.2).
const readline = require('readline');
const path = require('path');
const jsonata = require(path.join(__dirname, '..', '..', 'node_modules', 'jsonata'));
const version = require(path.join(__dirname, '..', '..', 'node_modules', 'jsonata', 'package.json')).version;
const { canon, stringify } = require('./canon');
const host = require('./hostfns');
const ENGINE = 'jsonata-js-' + version;

function timebox(expr, timeout, maxDepth) {
    // identical to jsonata-js test/run-test-suite.js
    let depth = 0;
    const time = Date.now();
    const check = function () {
        if (maxDepth > 0 && depth > maxDepth) {
            throw { message: 'Stack overflow error: Check for non-terminating recursive function.  Consider rewriting as tail-recursive.', code: 'U1001' };
        }
        if (Date.now() - time > timeout) {
            throw { message: 'Expression evaluation timeout: Check for infinite loop', code: 'U1001' };
        }
    };
    expr.assign(Symbol.for('jsonata.__evaluate_entry'), function (e, i, env) { if (env.isParallelCall) return; depth++; check(); });
    expr.assign(Symbol.for('jsonata.__evaluate_exit'), function (e, i, env) { if (env.isParallelCall) return; depth--; check(); });
}

async function bindingValue(v) {
    if (v && typeof v === 'object' && !Array.isArray(v)) {
        const keys = Object.keys(v);
        if (keys.length === 1 && typeof v.$expr === 'string') return await jsonata(v.$expr).evaluate(undefined);
        if (keys.length === 1 && typeof v.$host === 'string') return host.get(v.$host);
        if (keys.length === 1 && '$undefined' in v) return undefined;
    }
    return v;
}

function errorInfo(e) {
    const isJsonata = e && typeof e === 'object' && typeof e.code === 'string';
    const info = {
        code: isJsonata ? e.code : null,
        position: e && typeof e.position === 'number' ? e.position : null,
        token: e && e.token !== undefined && e.token !== null ? String(e.token) : null,
        message: e && e.message !== undefined ? String(e.message) : String(e),
        exception: e && e.constructor && e.constructor.name ? e.constructor.name : typeof e,
    };
    if (e && e.value !== undefined) { try { info.value = canon(e.value); } catch (x) { /* ignore */ } }
    return { info, crash: !isJsonata };
}

async function runCase(c) {
    const t0 = process.hrtime.bigint();
    let phase = 'setup';
    try {
        const bindings = {};
        for (const k of Object.keys(c.bindings || {})) bindings[k] = await bindingValue(c.bindings[k]);
        phase = 'compile';
        const expr = jsonata(c.expr, c.options || undefined);
        for (const f of c.functions || []) expr.registerFunction(f.name, host.get(f.impl), f.signature);
        if (c.bounds && 'timelimit' in c.bounds && 'depth' in c.bounds) timebox(expr, c.bounds.timelimit, c.bounds.depth);
        phase = 'evaluate';
        const input = 'input' in c ? c.input : undefined;
        const result = await expr.evaluate(input, Object.keys(bindings).length ? bindings : undefined);
        return { id: c.id, engine: ENGINE, status: 'ok', result: canon(result), us: Number((process.hrtime.bigint() - t0) / 1000n) };
    } catch (e) {
        const { info, crash } = errorInfo(e);
        return { id: c.id, engine: ENGINE, status: crash ? 'crash' : 'error', phase, error: info, us: Number((process.hrtime.bigint() - t0) / 1000n) };
    }
}

async function main() {
    const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
    for await (const raw of rl) {
        const line = raw.trim();
        if (!line || line.startsWith('//')) continue;
        let c;
        try { c = JSON.parse(line); } catch (e) {
            process.stdout.write(stringify({ id: null, engine: ENGINE, status: 'bad-case', error: { message: String(e) } }) + '\n');
            continue;
        }
        const r = await runCase(c);
        process.stdout.write(stringify(r) + '\n');
    }
}

if (require.main === module) main();
module.exports = { runCase, ENGINE };
