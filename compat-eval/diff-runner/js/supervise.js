'use strict';
// Runs a JSONL case worker (JS or JVM) as a child process, feeding cases on stdin and collecting
// result lines from stdout. If a case produces no result within `timeoutMs`, the child is killed,
// the case is recorded as "timeout" and the worker is restarted with the remaining cases.
const { spawn } = require('child_process');
const readline = require('readline');

function runSupervised({ cmd, args, cases, timeoutMs = 20000, startupMs = 15000, engineName, env, onResult }) {
    return new Promise((resolve) => {
        const results = new Array(cases.length);
        let next = 0; // index of first case whose result is pending
        let restarts = 0;

        const start = () => {
            if (next >= cases.length) return resolve({ results, restarts });
            const child = spawn(cmd, args, { stdio: ['pipe', 'pipe', 'pipe'], env: Object.assign({}, process.env, env || {}) });
            let stderr = '';
            child.stderr.on('data', d => { stderr = (stderr + d.toString()).slice(-4000); });
            let timer = null;
            let done = false;
            const arm = (ms) => {
                clearTimeout(timer);
                timer = setTimeout(() => {
                    if (done) return;
                    done = true;
                    const c = cases[next];
                    results[next] = { id: c.id, engine: engineName, status: 'timeout', error: { message: 'no result within ' + timeoutMs + ' ms (process killed)' } };
                    if (onResult) onResult(results[next], next);
                    next++;
                    restarts++;
                    child.kill('SIGKILL');
                    start();
                }, ms);
            };
            const rl = readline.createInterface({ input: child.stdout, crlfDelay: Infinity });
            rl.on('line', (line) => {
                if (done) return;
                let r;
                try { r = JSON.parse(line); } catch (e) { return; } // ignore stray output
                if (!r || typeof r !== 'object' || !('status' in r)) return;
                results[next] = r;
                if (onResult) onResult(r, next);
                next++;
                if (next >= cases.length) { done = true; clearTimeout(timer); child.stdin.end(); return; }
                arm(timeoutMs);
            });
            child.on('exit', (code) => {
                clearTimeout(timer);
                if (done) { if (next >= cases.length) resolve({ results, restarts }); return; }
                done = true;
                // died without timeout: record the current case as crashed, continue
                if (next < cases.length) {
                    const c = cases[next];
                    results[next] = { id: c.id, engine: engineName, status: 'died', error: { message: 'worker exited with code ' + code + ': ' + stderr.slice(-1000) } };
                    if (onResult) onResult(results[next], next);
                    next++;
                    restarts++;
                }
                start();
            });
            child.stdin.on('error', () => { /* child died; handled by exit */ });
            arm(timeoutMs + startupMs);
            for (let i = next; i < cases.length; i++) child.stdin.write(cases[i].__line + '\n');
            child.stdin.end();
        };
        start();
    });
}
module.exports = { runSupervised };
