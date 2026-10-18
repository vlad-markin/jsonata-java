#!/usr/bin/env node
'use strict';
// Differential fuzzing case generator for JSONata (grammar + risk-zone templates).
//   node fuzz/gen.js --seed 1 --count 20000 [--zones numbers,regex,...] > cases.jsonl
// Deterministic for a given seed. Each case carries tags ["fuzz", "zone:<zone>", "fn:<name>"...].
const { stringify } = require('../diff-runner/js/canon');

// ------------------------------------------------------------------ PRNG
function mulberry32(a) {
    return function () {
        a |= 0; a = a + 0x6D2B79F5 | 0;
        let t = Math.imul(a ^ a >>> 15, 1 | a);
        t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
        return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
}
let rnd = mulberry32(1);
const int = (n) => Math.floor(rnd() * n);
const pick = (a) => a[int(a.length)];
const chance = (p) => rnd() < p;
const q = (s) => JSON.stringify(s); // JSONata string literal (JSON syntax is valid JSONata)

// ------------------------------------------------------------------ value pools
const NUMS = ['0', '-0', '1', '-1', '2', '3', '7', '10', '0.1', '0.2', '0.5', '1.5', '2.5', '-2.5', '3.5', '0.125',
    '1.005', '2.675', '1.45', '3.14159', '123.456', '1234.5678', '-1234.5678', '0.000001234', '1e-7', '1e-6', '5e-7',
    '1e20', '1e21', '1e22', '123456789012345680000', '9007199254740991', '9007199254740993', '18014398509481985',
    '1e300', '1.7976931348623157e308', '5e-324', '255', '65535', '1000000', '99.995', '0.30000000000000004',
    '100', '1e15', '1e16', '12345678901234567', '4.35', '1.1', '2.2', '0.7', '-0.4', '-0.5', '0.49999999999999994'];
const STRS = ['', 'a', 'abc', 'Hello World', 'hello world', 'héllo', 'HÉLLO', 'Straße', 'İstanbul', 'ǅ', 'ﬁ',
    '😀', 'a😀b', '😀😀😀', 'e\u0301', 'naïve café', '  padded  ', '\t tab\n', 'a,b,,c', 'a.b.c', '1,234.5', '42',
    '  42 ', '0x1F', '1e3', '-0', 'true', 'null', 'αβγ', 'Ωmega', '日本語テキスト', '\u00a0nbsp\u00a0', 'x\u2028y',
    'line1\nline2', 'a\\b', 'quote"d', 'mixed CASE Words', 'abcabcabc', 'aaa', 'the cat sat on the mat',
    'ID-001', 'ID-0042', 'svc.payment-api', 'v1.2.3', 'C:\\path', '%E2%82%AC', '€100', '\ud83d', 'abc\udc00'];
const REGEXES = ['/a/', '/a+/', '/[aeiou]/', '/\\d+/', '/\\w+/', '/\\s+/', '/\\b\\w/', '/^a/', '/c$/', '/a/i', '/^l/m',
    '/(\\w)(\\w)/', '/(a)|(b)/', '/(?<n>\\d)/', '/\\p{L}+/', '/[^]/', '/[\\w-]+/', '/(?<=a)b/', '/(?<!a)b/', '/(a)\\1/',
    '/./', '/.*/', '/.*?/', '/x*/', '/\\u00e9/', '/é/', '/😀/', '/[😀]/', '/\\./', '/[.]/', '/\\//', '/[a-z]{2,}/',
    '/(?:ab)+/', '/a{0}/', '/\\Bt/', '/\\cJ/', '/[\\b]/', '/\\x41/', '/\\0/', '/$^/', '/(?=c)/', '/[[:alpha:]]/',
    '/a++/', '/(?i)abc/', '/\\h/', '/\\Z/', '/\\A/', '/[a&&b]/', '/\\R/', '/\\k<n>/', '/(?<n>a)\\k<n>/', '/\\d{2,3}?/'];
const REPLS = ['"x"', '"$0"', '"$1"', '"$2"', '"$$"', '"[$0]"', '"$&"', '"$10"', '"\\\\"', '""', '"$"', '"$1$1"', '"${1}"',
    'function($m){$uppercase($m.match)}', 'function($m){$m.groups[0]}', 'function($m){$string($m.index)}'];
const PICT_NUM = ['"#,##0.00"', '"0.000"', '"#0.#"', '"#%"', '"0%"', '"#‰"', '"00"', '"#,###"', '"0.0#"', '"#,##0.###"',
    '"0.00e0"', '"#.##e0"', '"00.0e00"', '"#"', '"0"', '"###,##0.00;(###,##0.00)"', '"#,##,##0"', '"0,000.00"',
    '"#.000"', '"$#,##0.00"', '"#0.00 EUR"', '"0.###############"', '"#E0"'];
const PICT_INT = ['"w"', '"W"', '"Ww"', '"i"', '"I"', '"a"', '"A"', '"#,##0"', '"0001"', '"1"', '"#0"', '"1;o"', '"w;o"',
    '"#;o"', '"0;o"', '"000,000"', '"##0"', '"Ⅰ"', '"①"', '"٠"'];
const PICT_DATE = ['"[Y0001]-[M01]-[D01]"', '"[D1o] [MNn] [Y]"', '"[FNn], [D] [MNn] [Y]"', '"[H01]:[m01]:[s01].[f001][Z]"',
    '"[h]:[m01] [P]"', '"[h]:[m01] [PN]"', '"[Y,2]"', '"[YI]"', '"[Mi]"', '"[MI]"', '"[W]"', '"[w]"', '"[X]-W[W01]-[F1]"',
    '"[x]"', '"[d]"', '"[F1]"', '"[FN,3-3]"', '"[MN,3-3]"', '"[E]"', '"[z]"', '"[Z0]"', '"[Z01:01]"', '"[ZN]"', '"[Z0101t]"',
    '"[Dw]"', '"[DW]"', '"[YW]"', '"[Y0001]-[d001]"', '"[f1]"', '"[f01]"', '"[f0001]"', '"[s01].[f01]"', '"[H]h[m]"',
    '"[Y]-[M]-[D]T[H]:[m]:[s]"', '"[Y0001][M01][D01]"', '"[D01]/[M01]/[Y0001] [H01]:[m01]"', '"[MNn,*-3]"',
    '"[Y9,999,*]"', '"[C]"', '"[Y01]"', '"[M1,2]"'];
const TZ = ['"+0100"', '"-0530"', '"0000"', '"+1400"', '"-1200"', '"+0545"', '"Z"', '"+01:00"', '"EST"'];
const MILLIS = ['0', '1', '-1', '86399999', '951782400000', '1104537600000', '1230757500000', '1419940800000', '1483272000000',
    '1533038400000', '1609459199999', '1709208000000', '-62135596800000', '253402300799999', '1e15', '1.5', '-86400000'];
const DATESTR = ['"2018-02-01T09:42:13.123Z"', '"2018-02-01"', '"2018-02-01T09:42:13+01:00"', '"2018-02-01T09:42"',
    '"2018-02-01T09:42:13.123456Z"', '"2018"', '"2018-02"', '"2018-13-01"', '"2018-02-30"', '"2018-02-01 09:42:13"',
    '"20180201"', '"2018-02-01T09:42:13.5Z"', '"2018-02-01T24:00:00Z"', '"2018-W05-4"', '"1970-01-01T00:00:00.000Z"',
    '"2018-02-01T09:42:13,123Z"', '"+002018-02-01"', '"2018-02-01T09:42:13.123-05:30"'];

// ------------------------------------------------------------------ random data documents
const KEYS = ['id', 'name', 'type', 'tags', 'items', 'value', 'price', 'qty', 'owner', 'children', 'a', 'b', 'c',
    'Name', 'deps', 'meta', '10', '2', '1', 'x y', '$weird', 'é'];
function randValue(depth) {
    const r = rnd();
    if (depth <= 0 || r < 0.35) return randScalar();
    if (r < 0.65) { const n = int(5); const a = []; for (let i = 0; i < n; i++) a.push(randValue(depth - 1)); return a; }
    return randObject(depth - 1);
}
function randScalar() {
    const r = rnd();
    if (r < 0.35) return Number(pick(NUMS));
    if (r < 0.75) return pick(STRS);
    if (r < 0.85) return chance(0.5);
    return null;
}
function randObject(depth) {
    const o = {};
    const n = 1 + int(5);
    for (let i = 0; i < n; i++) o[pick(KEYS)] = randValue(depth);
    return o;
}
function archDoc() {
    // architecture-like document
    const sys = [];
    const ns = 2 + int(3);
    for (let s = 0; s < ns; s++) {
        const comps = [];
        const nc = 1 + int(4);
        for (let c = 0; c < nc; c++) comps.push({
            id: 'c' + s + c, name: pick(['api', 'db', 'Web', 'queue', 'cache', 'Auth', 'éditeur', 'svc-' + c]),
            type: pick(['service', 'database', 'queue', null]), cost: Number(pick(NUMS.slice(0, 30))),
            tags: [pick(['prod', 'dev', 'Prod', 'beta']), pick(['eu', 'us', 'eu'])].slice(0, int(3)),
            deps: chance(0.5) ? ['c' + int(ns) + int(3)] : [],
        });
        sys.push({ id: 's' + s, name: 'System ' + s, owner: chance(0.8) ? { name: pick(['Ann', 'Bob', 'Čeněk']), team: pick(['A', 'B']) } : null, components: comps });
    }
    return { systems: sys, version: pick(['1.0', 1, 1.5]), meta: { '2': 'two', b: 'bee', '10': 'ten', a: 'ay', '1': 'one' } };
}

// ------------------------------------------------------------------ generic grammar
const PATH_FIELDS = ['systems', 'components', 'name', 'id', 'type', 'cost', 'tags', 'deps', 'owner', 'team', 'meta', 'version'];
const AGG = ['$sum', '$count', '$max', '$min', '$average'];
const STRF = ['$string', '$length', '$uppercase', '$lowercase', '$trim', '$substring', '$substringBefore', '$substringAfter',
    '$pad', '$contains', '$split', '$join', '$replace', '$match', '$base64encode', '$encodeUrlComponent'];
function path(depth) {
    let p = pick(['systems', 'systems.components', '$.systems', 'systems[0]', '**', 'meta', '*', 'systems.owner']);
    const n = int(3);
    for (let i = 0; i < n; i++) {
        const r = rnd();
        if (r < 0.4) p += '.' + pick(PATH_FIELDS);
        else if (r < 0.55) p += '[' + pred(depth - 1) + ']';
        else if (r < 0.62) p += '[' + pick(['0', '-1', '1', '[0,1]', '[0..1]', '99']) + ']';
        else if (r < 0.7) p += '@$x';
        else if (r < 0.77) p += '#$i';
        else if (r < 0.82) p += '.%';
        else if (r < 0.87) p += '^(' + pick(['name', '>cost', 'id', '$']) + ')';
        else if (r < 0.92) p += '.*';
        else p += '.' + pick(['`10`', '`x y`', '"name"', '$string()', '$keys()']);
    }
    return p;
}
function pred(depth) {
    return pick([
        () => pick(['type', 'cost', 'name', 'tags']) + pick(['=', '!=', '>', '<', '>=', '<=', ' in ']) + atom(depth),
        () => '$contains(name, ' + pick(['"a"', '/a/i', '"é"']) + ')',
        () => '$exists(' + pick(['owner', 'deps', 'nope']) + ')',
        () => pick(['0', '-1', 'true', 'false', '$i>0', '"x"']),
        () => expr(depth),
    ])();
}
function atom(depth) {
    return pick([
        () => pick(NUMS), () => q(pick(STRS)), () => pick(['true', 'false', 'null']), () => pick(['$x', '$i', '$v', '$', '$$']),
        () => path(depth), () => '[' + Array.from({ length: int(4) }, () => atom(depth - 1)).join(',') + ']',
        () => '(' + expr(depth - 1) + ')',
    ])();
}
function expr(depth) {
    if (depth <= 0) return atom(0);
    return pick([
        () => atom(depth),
        () => atom(depth - 1) + ' ' + pick(['+', '-', '*', '/', '%', '&', '=', '!=', '<', '>', 'and', 'or', 'in', '?:', '??']) + ' ' + atom(depth - 1),
        () => pick(AGG) + '(' + path(depth - 1) + ')',
        () => pick(STRF) + '(' + atom(depth - 1) + (chance(0.5) ? ', ' + atom(depth - 1) : '') + ')',
        () => '$map(' + atom(depth - 1) + ', function($v, $i){' + expr(depth - 1) + '})',
        () => '$filter(' + atom(depth - 1) + ', function($v){' + expr(depth - 1) + '})',
        () => '$reduce(' + atom(depth - 1) + ', function($a, $b){$a ' + pick(['+', '&', '*']) + ' $b}' + (chance(0.5) ? ', ' + atom(0) : '') + ')',
        () => '$sort(' + atom(depth - 1) + (chance(0.5) ? ', function($l, $r){$l ' + pick(['>', '<']) + ' $r}' : '') + ')',
        () => path(depth - 1) + '{' + pick(['type', 'name', '$string(cost)', 'tags', 'id']) + ': ' + pick(AGG.concat(['$'])) + '(' + pick(['cost', 'name', '$', 'id']) + ')}',
        () => '{' + pick(['"k"', 'name', 'type', '"10"', '"2"']) + ': ' + atom(depth - 1) + '}',
        () => '(' + '$x := ' + atom(depth - 1) + '; ' + expr(depth - 1) + ')',
        () => atom(depth - 1) + ' ? ' + atom(depth - 1) + ' : ' + atom(depth - 1),
        () => path(depth - 1) + ' ~> ' + pick(['$count', '$sum', '$string', '$keys', '$distinct', '$reverse', '$sort', '$type']),
        () => '$ ~> |' + pick(['systems', 'systems.components', '**']) + '|{' + pick(['"x":1', '"name":"n"', '"cost":cost*2']) + '}' + (chance(0.4) ? ', ["' + pick(['id', 'deps', 'tags']) + '"]' : '') + '|',
        () => pick(['$keys', '$spread', '$each', '$sift', '$merge', '$distinct', '$reverse', '$shuffleNot', '$zip', '$append', '$flatten', '$single', '$exists', '$type', '$boolean', '$not', '$lookup']) + '(' + atom(depth - 1) + (chance(0.4) ? ', ' + atom(depth - 1) : '') + ')',
    ])();
}

// ------------------------------------------------------------------ risk-zone templates
const ZONES = {
    numbers: () => pick([
        () => '$string(' + pick(NUMS) + ')',
        () => '$string(' + pick(NUMS) + ' ' + pick(['+', '-', '*', '/', '%']) + ' ' + pick(NUMS) + ')',
        () => pick(NUMS) + ' ' + pick(['+', '-', '*', '/', '%']) + ' ' + pick(NUMS),
        () => '$round(' + pick(NUMS) + (chance(0.7) ? ', ' + pick(['0', '1', '2', '3', '-1', '-2', '10', '1.5']) : '') + ')',
        () => '$formatNumber(' + pick(NUMS) + ', ' + pick(PICT_NUM) + ')',
        () => '$formatNumber(' + pick(NUMS) + ', ' + pick(PICT_NUM) + ', ' + pick(['{"decimal-separator": ",", "grouping-separator": "."}', '{"percent": "pc"}', '{"zero-digit": "٠"}', '{"exponent-separator": "E"}', '{"minus-sign": "~"}', '{"infinity": "inf", "NaN": "nan"}']) + ')',
        () => '$formatInteger(' + pick(NUMS.filter(n => !n.includes('e-'))) + ', ' + pick(PICT_INT) + ')',
        () => '$parseInteger(' + pick(['"12"', '"twelve"', '"one hundred and one"', '"xii"', '"MMXXVI"', '"1,234"', '"0042"', '"ab"', '"-5"', '"twenty-first"', '"3rd"']) + ', ' + pick(PICT_INT) + ')',
        () => '$formatBase(' + pick(NUMS) + (chance(0.7) ? ', ' + pick(['2', '8', '16', '36', '1', '37', '10.5']) : '') + ')',
        () => '$number(' + pick(['"1e3"', '"0x1F"', '"0b11"', '"0o17"', '"  42 "', '"-0"', '"1."', '".5"', '"+5"', '"Infinity"', '"1_000"', '"١٢"', 'true', 'false', '"1e400"', '"-1e-400"', '"9007199254740993"', '"12345678901234567890"', '"0.1"']) + ')',
        () => '[' + Array.from({ length: 2 + int(4) }, () => pick(NUMS)).join(',') + '] ~> ' + pick(['$sum', '$max', '$min', '$average', '$string', '$sort', '$distinct']),
        () => '$floor(' + pick(NUMS) + ')', () => '$ceil(' + pick(NUMS) + ')', () => '$abs(' + pick(NUMS) + ')',
        () => '$sqrt(' + pick(NUMS) + ')', () => '$power(' + pick(NUMS) + ', ' + pick(['0.5', '2', '-1', '3', '1e3', '0']) + ')',
        () => pick(NUMS) + ' = ' + pick(NUMS), () => pick(NUMS) + ' < ' + pick(NUMS),
        () => '{"n": ' + pick(NUMS) + '}.n', () => '$string({"n": ' + pick(NUMS) + ', "a": [' + pick(NUMS) + ']})',
        () => '[' + pick(NUMS) + '..' + pick(['3', '5', '1e3', '2.5', '-2']) + '] ~> $count',
        () => '$string(' + pick(NUMS) + ', true)', () => '$ ~> $string', () => 'value * 2',
    ])(),
    regex: () => pick([
        () => '$match(' + q(pick(STRS)) + ', ' + pick(REGEXES) + (chance(0.3) ? ', ' + pick(['0', '1', '2', '-1']) : '') + ')',
        () => '$replace(' + q(pick(STRS)) + ', ' + pick(REGEXES) + ', ' + pick(REPLS) + (chance(0.2) ? ', ' + pick(['0', '1', '2']) : '') + ')',
        () => '$replace(' + q(pick(STRS)) + ', ' + q(pick(['a', 'l', '.', '$', '\\', 'é', '😀', ''])) + ', ' + pick(REPLS.filter(r => !r.startsWith('function'))) + ')',
        () => '$split(' + q(pick(STRS)) + ', ' + pick(REGEXES.concat(['""', '","', '" "', '"😀"'])) + (chance(0.3) ? ', ' + pick(['0', '1', '2', '-1', '1.5']) : '') + ')',
        () => '$contains(' + q(pick(STRS)) + ', ' + pick(REGEXES) + ')',
        () => q(pick(STRS)) + ' ~> ' + pick(REGEXES),
        () => '[' + q(pick(STRS)) + ', ' + q(pick(STRS)) + '][$ ~> ' + pick(REGEXES) + ']',
        () => '$map([' + q(pick(STRS)) + '], ' + pick(REGEXES) + ')',
    ])(),
    dates: () => pick([
        () => '$fromMillis(' + pick(MILLIS) + ')',
        () => '$fromMillis(' + pick(MILLIS) + ', ' + pick(PICT_DATE) + (chance(0.5) ? ', ' + pick(TZ) : '') + ')',
        () => '$toMillis(' + pick(DATESTR) + ')',
        () => '$toMillis(' + pick(['"2018-02-01"', '"01/02/2018"', '"1st February 2018"', '"Thursday, 1 February 2018"', '"2018-032"', '"12:30 pm"', '"2018-W05-4"', '"MMXVIII"', '"18"', '"2018-2-1"', '"20180201"']) + ', ' + pick(PICT_DATE) + ')',
        () => '$toMillis($fromMillis(' + pick(MILLIS) + ', ' + pick(PICT_DATE) + '), ' + pick(PICT_DATE) + ')',
        () => '$fromMillis($toMillis(' + pick(DATESTR) + '), ' + pick(PICT_DATE) + ', ' + pick(TZ) + ')',
        () => '$type($now())', () => '$length($now()) > 20', () => '$now(' + pick(PICT_DATE) + ') ~> $type',
        () => '$millis() > 0', () => '$now() = $now()', () => '$type($toMillis($now()))',
    ])(),
    undefnull: () => pick([
        () => pick(['null', 'nothing', '[]', '{}', '[null]', '""', '$x']) + ' ' + pick(['+', '&', '=', '!=', '<', 'and', 'or', 'in', '?:', '??', '-']) + ' ' + pick(['null', 'nothing', '1', '"a"', '[null]', 'true', '[]']),
        () => pick(['$count', '$sum', '$exists', '$string', '$boolean', '$type', '$not', '$length', '$keys', '$reverse', '$distinct', '$sort', '$append', '$join', '$max', '$uppercase', '$number', '$abs', '$split', '$lookup', '$spread', '$each', '$merge', '$sift', '$single', '$flatten', '$zip']) + '(' + pick(['null', 'nothing', '[null]', '[null, 1]', '[]', '{}', '{"a":null}', '[nothing]', '[[],[null]]']) + ')',
        () => '[' + pick(['null', 'nothing', '1']) + ', ' + pick(['null', 'nothing', '[]', '[null]']) + ']',
        () => '{"a": ' + pick(['null', 'nothing', '[]', '[nothing]']) + ', "b": 1}',
        () => 'a' + pick(['', '.b', '[0]', '[b=null]', '.b.c']) , () => '$.a = null', () => '$exists(a.b)',
        () => '[a, b, c]', () => 'a ? "t" : "f"', () => '$map([1,null,3], function($v){$v})', () => '$filter([null, 0, "", false], function($v){$v = null})',
    ])(),
    sort: () => pick([
        () => '$sort(' + pick(['items', 'systems.components', '[3,1,2,1,3]', '["b","a","B","é","e","A","10","9"]', '[1, "a"]', '[null, 1]', '[[1],[0]]', '[{"a":1},{"a":0}]', '[0, -0, 0.1, -1]', '["😀","a","\uffff"]']) + (chance(0.6) ? ', ' + pick(['function($l,$r){$l.k > $r.k}', 'function($l,$r){$l > $r}', 'function($l,$r){$l.k >= $r.k}', 'function($l,$r){false}', 'function($l,$r){true}', 'function($l,$r){$l.k < $r.k}', 'function($l,$r){$string($l) > $string($r)}']) : '') + ')',
        () => 'items^(' + pick(['k', '>k', '<k', 'k, >n', '$string(k)', 'v', 'n']) + ')',
        () => 'items^(' + pick(['k', '>k']) + ').n',
        () => '$sort(items, function($l,$r){$l.k > $r.k}).n',
    ])(),
    grouping: () => pick([
        () => 'items{' + pick(['k', '$string(k)', 'n', 'g', '"x"', 'k & ""']) + ': ' + pick(['n', '$count(n)', '$sum(v)', '$', '[n]', '{"c": $count($)}', '$max(v)']) + '}',
        () => 'items.{' + pick(['k', 'n', '"x"']) + ': ' + pick(['n', 'v', '$']) + '}',
        () => '{' + pick(['"b": 1, "a": 2, "10": 3, "2": 4', '"z": 1, "1": 2', '"x": 1, "x": 2', '"-1": 1, "01": 2, "1": 3, "1.5": 4', '"4294967295": 1, "4294967294": 2, "a": 3']) + '}',
        () => 'items{k: n}.*', () => '$keys(items{k: n})', () => 'items@$i{$i.k: $count($i)}',
        () => '$each(items{k: $sum(v)}, function($v, $k){$k & "=" & $v})',
        () => 'items{g: {k: $sum(v)}}', () => 'items{g: $}', () => 'items[0]{k:n}',
        () => 'items{n: k}', () => '$string(items{k: $count(n)})',
    ])(),
    keys: () => pick([
        () => pick(['$keys', '$spread', '$merge', '$sift', '$each', '$lookup', '$string', '$type', '$distinct']) + '(' + pick(['meta', 'obj', '[obj, meta]', '{"b":1,"a":2,"1":3}', 'items', '[{"a":1},{"a":2,"b":3}]', '{}']) + (chance(0.3) ? ', ' + pick(['"a"', 'function($v,$k){$k}', 'function($v){$v}']) : '') + ')',
        () => '$merge([obj, ' + pick(['{"a": 0}', '{"10": 1}', '{"z": null}', 'meta']) + '])',
        () => 'obj.*', () => 'meta.*', () => '$keys(meta)', () => '$string(meta)', () => 'meta ~> $each(function($v,$k){$k})',
        () => '$spread(meta)', () => '$sift(meta, function($v,$k){$k > "1"})', () => 'obj ~> |$|{"new": 1}|',
        () => 'obj ~> |$|{"a": 9}, ["b"]|', () => '$lookup([obj, meta], "a")', () => '**.a', () => '$keys(**)',
    ])(),
    strings: () => pick([
        () => '$length(' + q(pick(STRS)) + ')',
        () => '$substring(' + q(pick(STRS)) + ', ' + pick(['0', '1', '-1', '-3', '2', '1.5', '100', '-100']) + (chance(0.6) ? ', ' + pick(['0', '1', '2', '-1', '1.5', '100']) : '') + ')',
        () => pick(['$uppercase', '$lowercase', '$trim', '$base64encode', '$encodeUrl', '$encodeUrlComponent', '$string', '$length', '$split']) + '(' + q(pick(STRS)) + ')',
        () => '$base64decode(' + q(pick(['aGVsbG8=', 'w6k=', '8J+YgA==', 'aGVsbG8', '!!!', ''])) + ')',
        () => '$decodeUrl(' + q(pick(['%E2%82%AC', '%zz', 'a+b', '%F0%9F%98%80', '%'])) + ')',
        () => '$pad(' + q(pick(STRS)) + ', ' + pick(['5', '-5', '10', '0', '2.7', '-2.7']) + (chance(0.6) ? ', ' + q(pick(['*', '😀', '-=', ''])) : '') + ')',
        () => pick(['$substringBefore', '$substringAfter', '$contains', '$split']) + '(' + q(pick(STRS)) + ', ' + q(pick(['a', 'l', ' ', '😀', '', 'é', '\ud83d'])) + ')',
        () => '$join(' + pick(['["a","b"]', '[]', '["😀"]', '"single"', '[1,2]']) + (chance(0.5) ? ', ' + q(pick([',', '', '😀'])) : '') + ')',
        () => '$string(' + pick(['{"a":"é😀\\n\\t\\"/"}', '["\\u0000"]', '"\\u2028"', '{"k": [1.5, 2, "x"]}', '{"a":{"b":[]}}', 'function(){1}', '$uppercase', '[1e21, 1e-7, -0]']) + (chance(0.4) ? ', true' : '') + ')',
        () => q(pick(STRS)) + ' ' + pick(['<', '>', '=', '&']) + ' ' + q(pick(STRS)),
        () => '$uppercase(' + q(pick(['ß', 'ǆ', 'ŉ', 'ﬃ', 'i', 'ı'])) + ')', () => '$lowercase(' + q(pick(['İ', 'Σ', 'ΑΣ', 'Ǆ'])) + ')',
    ])(),
    eval: () => pick([
        () => '$eval(' + q(pick(['1+1', '[1,2,3]', '$', '$$', 'a', '$x', '(', '{"a":', 'foo(', '$sum([1,2])', '1/0', 'function(){1}', '"x" & 1', '$eval("2")', ''])) + (chance(0.4) ? ', ' + pick(['{"a": 5}', '[1,2]', 'null', 'items']) : '') + ')',
        () => '$assert(' + pick(['true', 'false', '1=1', '1=2', 'null', '"x"', '0']) + (chance(0.6) ? ', ' + q(pick(['custom message', '', 'x'])) : '') + ')',
        () => '$error(' + pick(['"boom"', '', '""', '{"a":1}', '42']) + ')',
        () => '(' + pick(['$x := 1', '$f := function($a){$a*2}']) + '; ' + pick(['$x()', '$f(1,2)', '$f()', '$g(1)', '$f.x', 'x()', '$x(1)', '1()', '"s"()']) + ')',
        () => pick(['1 + "a"', '"a" - 1', '[1] + 1', '{} * 2', 'true + 1', '1 > "a"', '"a" < true', '-"a"', '$sum("a")', '$sum(["a"])', '$max(["a"])', '$average([true])', '1..3', '[1..1e7]', '[1..1.5]', '[1.."2"]', '$x.y.z()', '$keys(1, 2)', '$count()', '$substring()', 'a.b.', '1 +', '$', '"unterminated', '/unterminated', '{"a":1 "b":2}', 'a[', '$lookup(1,"a")', '$spread("x")', '$string(1,2,3)', '$join([1,2])', '$split("a", 1)', '$uppercase(1)', '$sort([1,"a"])', 'λ($x)<n:n>{$x}("a")', 'function($x)<s>{$x}(1)', '$map(1)', '$reduce([1], 1)', '1 in 2', 'a ~> 1', '$formatNumber(1)', '$toMillis(1)', '$fromMillis("a")', '$number([1])', '$boolean(function(){1})']),
    ])(),
};

// data for zone templates
function zoneData(zone) {
    if (zone === 'sort' || zone === 'grouping') {
        const items = [];
        const n = 3 + int(6);
        for (let i = 0; i < n; i++) items.push({ k: pick([1, 2, 2, 'a', 'b', 'B', 10, 9, null, 1.5, '10', '9']), n: 'n' + i, v: Number(pick(NUMS.slice(0, 25))), g: pick(['x', 'y', '2', '10']) });
        if (chance(0.15)) items.push({ n: 'nok' });
        return { items };
    }
    if (zone === 'keys') return { obj: { b: 1, a: 2, '10': 3, '2': 4, z: null, 'é': 5 }, meta: { y: 1, '1': 2, x: [1, 2] }, items: [{ a: 1 }, { b: 2, a: 3 }] };
    if (zone === 'numbers') return chance(0.5) ? { value: Number(pick(NUMS)) } : Number(pick(NUMS));
    if (zone === 'undefnull') return chance(0.5) ? { a: pick([null, { b: null }, { b: { c: 1 } }, [null, { b: 1 }], 0, '']), b: null, c: [] } : null;
    if (zone === 'eval') return { a: 1, items: [1, 2] };
    return chance(0.5) ? archDoc() : randObject(3);
}

// ------------------------------------------------------------------ main
function parseArgs() {
    const a = { seed: 1, count: 1000, zones: Object.keys(ZONES).concat(['grammar']) };
    const v = process.argv;
    for (let i = 2; i < v.length; i++) {
        if (v[i] === '--seed') a.seed = Number(v[++i]);
        else if (v[i] === '--count') a.count = Number(v[++i]);
        else if (v[i] === '--zones') a.zones = v[++i].split(',');
        else if (v[i] === '--prefix') a.prefix = v[++i];
    }
    return a;
}
function fnTags(expr) {
    const m = expr.match(/\$[a-zA-Z]+(?=\()/g) || [];
    return [...new Set(m)].slice(0, 6).map(f => 'fn:' + f);
}
function main() {
    const a = parseArgs();
    rnd = mulberry32(a.seed);
    const out = [];
    for (let i = 0; i < a.count; i++) {
        const zone = a.zones[i % a.zones.length];
        let expr, input;
        if (zone === 'grammar') { expr = expr0(); input = chance(0.7) ? archDoc() : randObject(3); }
        else { expr = ZONES[zone](); input = zoneData(zone); }
        const c = { id: (a.prefix || 's' + a.seed) + '-' + i, expr, tags: ['fuzz', 'zone:' + zone].concat(fnTags(expr)) };
        if (input !== undefined) c.input = input;
        if (chance(0.1)) c.bindings = { x: randScalar(), v: Number(pick(NUMS)) };
        out.push(stringify(c));
    }
    process.stdout.write(out.join('\n') + '\n');
}
function expr0() { return expr(2 + int(2)); }
main();
