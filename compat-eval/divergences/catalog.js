'use strict';
// Divergence catalog: jsonata-js 2.2.2 vs JVM engine.
// Every entry has minimal cases (diff-runner format, without id) that reproduce it.
// `build.js` turns this into cases/divergences.jsonl and, after a diff-runner run, into divergences.md.
//
// solution: engine   - fix in the engine (behaviour of JS 2.2.2 is clearly right / reference)
//           rewrite  - close by automatic rewriting of the expression (migration tool)
//           exception- agreed exception with migration instruction
//           question - needs a decision by the owners (see REPORT.md "Вопросы")
// complexity: S (<0.5 d), M (0.5-3 d), L (>3 d)
// severity: high = silently different value in plausible expressions; medium = error/crash instead
//           of value or vice versa, or plausible-but-rare value differences; low = error details, exotic input

const H = (name, impl, signature) => ({ name, impl, signature });

module.exports = [
    // ------------------------------------------------------------------ API / evaluation model
    {
        id: 'API-01', fixedBy: 'CompatJs222Test.api01_*', cat: 'api', severity: 'high', solution: 'engine', complexity: 'S',
        title: 'Общее окружение между вызовами evaluate без Frame: `:=` на верхнем уровне и `$$` утекают, параллельные вызовы дают неверный результат',
        js: 'каждый evaluate получает новый frame (исправлено в 2.2.2, df63a7c)',
        jvm: 'без Frame используется общий `environment` скомпилированного выражения',
        evidence: 'ApiProbe API-1, API-2 (209 неверных результатов из 400 при 8 потоках)',
        cases: [],
    },
    {
        id: 'API-02', fixedBy: 'CompatJs222Test.api02_callLambdaFromOtherThread', cat: 'api', severity: 'medium', solution: 'engine', complexity: 'M',
        title: 'Лямбды можно вызвать из хоста только в потоке вычисления (Functions.funcApply опирается на ThreadLocal)',
        js: '`fn.apply(self, args)` работает всегда (Promise)',
        jvm: 'NPE при вызове из другого потока, в т.ч. лямбды, возвращённой как результат',
        evidence: 'ApiProbe API-5b, API-5d',
        cases: [],
    },
    {
        id: 'API-03', cat: 'api', severity: 'medium', solution: 'engine', complexity: 'S',
        title: 'Функции хоста: частичное применение `$f(?, x)` и передача функции без сигнатуры в HOF',
        cases: [
            { expr: "$ap($ap(?, 'x'), function($s){$s & $s})", functions: [H('ap', 'apply')] },
            { expr: "$map(['a','b'], $hecho)", functions: [H('hecho', 'echo')] },
        ],
    },
    {
        id: 'API-04', cat: 'api', severity: 'medium', solution: 'engine', complexity: 'S',
        title: 'Guardrails (опции jsonata 2.2): NPE при `timeout`/`stack` без `sequence`; `$append` и `*` не проверяют `sequence`; у D2015 нет позиции',
        cases: [
            { expr: '[1..5]', options: { timeout: 1000 } },
            { expr: '[1..5]', options: { stack: 100 } },
            { expr: '$append([1,2,3],[4,5,6])', options: { sequence: 5 } },
            { expr: "{'a':[1,2],'b':[3,4]}.*", options: { sequence: 3 } },
        ],
    },
    {
        id: 'API-05', cat: 'api', severity: 'medium', solution: 'engine', complexity: 'S',
        title: '`Frame.setRuntimeBounds`: ошибка без кода U1001 (код = текст сообщения), время отсчитывается от создания Frame, а не от начала evaluate',
        evidence: 'ApiProbe API-7; test-suite tail-recursion/case002,005,006',
        cases: [
            { expr: '($f := function($n){$n=0?0:1+$f($n-1)}; $f(50))', bounds: { timelimit: 1000, depth: 30 } },
        ],
    },
    {
        id: 'API-06', cat: 'api', severity: 'low', solution: 'question', complexity: 'M',
        title: 'Модель значений на границе с хостом: JSON null приходит в функции хоста как `Jsonata.NULL_VALUE`, вернуть null можно только как NULL_VALUE (Java null = undefined); числа приходят как Integer/Long/Double, последовательности — как JList',
        evidence: 'ApiProbe API-3, API-4, API-10, API-11',
        cases: [],
    },
    // ------------------------------------------------------------------ null / undefined
    {
        id: 'UND-01', cat: 'null', severity: 'high', solution: 'engine', complexity: 'M',
        title: 'JSON null, переданный аргументом функции, превращается в undefined',
        cases: [
            { expr: '$count(null)' },
            { expr: '$count(owner)', input: { owner: null } },
            { expr: '$map(null, function($v){$type($v)})' },
            { expr: '$sort(null)' },
            { expr: '$reduce(null, function($a,$b){$a})' },
            { expr: '$zip(null)' },
            { expr: '$max(null)' },
        ],
    },
    {
        id: 'UND-02', cat: 'null', severity: 'medium', solution: 'engine', complexity: 'S',
        title: 'Проверка сигнатур пропускает null для параметров `s`, `n`, `o`…: вместо T0410 — исключение Java или undefined (в т.ч. для функций хоста с сигнатурой)',
        cases: [
            { expr: '$uppercase(null)' },
            { expr: '$length(a)', input: { a: null } },
            { expr: '$hu(null)', functions: [H('hu', 'upper', '<s:s>')] },
            { expr: '$sift(null, function($v){true})' },
        ],
    },
    // ------------------------------------------------------------------ numbers
    {
        id: 'NUM-01', cat: 'number', severity: 'high', solution: 'engine', complexity: 'S',
        title: 'Глубокое сравнение (`=`, `!=`, `in`, `$distinct`) массивов и объектов зависит от Java-типа числа (Long/Integer/Double)',
        cases: [
            { expr: 'a = [1,2]', input: { a: [1, 2] } },
            { expr: "o = {'x': 1}", input: { o: { x: 1 } } },
            { expr: 'a != [1.5,2]', input: { a: [1.5, 2] } },
            { expr: '$distinct([1, 1.0, n])', input: { n: 1 } },
            { expr: '[1] in [[1]]' }, { expr: "{'a':1} in [{'a':1}]" },
        ],
    },
    {
        id: 'NUM-02', cat: 'number', severity: 'medium', solution: 'engine', complexity: 'S',
        title: 'Строковое представление чисел ≥1e20 и с >15 значащими цифрами (`$string`, `&`): JVM даёт экспоненту там, где JS пишет целое',
        cases: [
            { expr: '$string(1e20)' }, { expr: "12345678901234567890 & ''" }, { expr: '$string($power(2, 64))' },
        ],
    },
    {
        id: 'NUM-03', cat: 'number', severity: 'medium', solution: 'engine', complexity: 'M',
        title: '`$formatNumber` реализован через java.text.DecimalFormat: другие результаты для `#.#`, округления, экспоненты, второй части шаблона; ошибки шаблона — исключения Java или их отсутствие',
        cases: [
            { expr: "$formatNumber(0.125, '#.#')" }, { expr: "$formatNumber(0.005, '0.00')" },
            { expr: "$formatNumber(0.234, '#.00e0')" }, { expr: "$formatNumber(2392.14*(-36.58), '000,000.000###;###,###.000###')" },
            { expr: "$formatNumber(20, '#0%%')" }, { expr: "$formatNumber(42, '---')" },
            { expr: "$formatNumber(-1, '0%', {'percent': 'pc'})" },
        ],
    },
    {
        id: 'NUM-04', cat: 'number', severity: 'medium', solution: 'engine', complexity: 'M',
        title: '`$formatInteger`/`$parseInteger`: переполнение long (слова для >9.2e18), цифры других систем, разбор `i`/`I` (NPE), знак, порядковые `#;o`',
        cases: [
            { expr: "$formatInteger(1e22, 'w')" }, { expr: "$parseInteger('ten billion trillion trillion trillion', 'w')" },
            { expr: "$parseInteger('١٢٣٤٠', '###١')" }, { expr: "$parseInteger('ab', 'i')" },
            { expr: "$parseInteger('-5', '1')" }, { expr: "$formatInteger(22, '#;o')" }, { expr: "$parseInteger('12', '1')" },
        ],
    },
    {
        id: 'NUM-05', cat: 'number', severity: 'low', solution: 'engine', complexity: 'S',
        title: '`$number`: принимает `"+5"`, `"1."`; ошибки — NumberFormatException или D1001 вместо D3030',
        cases: [
            { expr: "$number('+5')" }, { expr: "$number('1.')" }, { expr: "$number('Hello')" }, { expr: "$number('1e400')" },
        ],
    },
    {
        id: 'NUM-06', cat: 'number', severity: 'low', solution: 'engine', complexity: 'S',
        title: '`$formatBase` и диапазоны с числами вне long: JS считает в double',
        cases: [{ expr: '$formatBase(1e22)' }, { expr: '[1e20..5] ~> $count' }, { expr: '[1e22..2.5]' }],
    },
    {
        id: 'NUM-07', cat: 'number', severity: 'low', solution: 'engine', complexity: 'S',
        title: '`$sum` суммирует не так, как JS (не простое последовательное сложение double)',
        cases: [{ expr: '$sum([0.1, 0.2, 0.3])' }],
    },
    {
        id: 'NUM-08', cat: 'number', severity: 'low', solution: 'exception', complexity: 'S',
        title: 'Целые > 2^53 во входных данных и привязках: JVM хранит точный long, JS округляет до double',
        evidence: 'cases/extensibility.jsonl: ext-input-large-int, ext-bind-large-int-string (в catalog.js такие числа не выразить: литерал JS уже округлён)',
        cases: [],
        note: 'JVM точнее. Для эквивалентности хост может читать JSON в double (настройка парсера). Выражения переписывать не нужно',
    },
    {
        id: 'NUM-09', cat: 'number', severity: 'low', solution: 'exception', complexity: 'S',
        title: 'Отрицательный ноль: JS сохраняет -0 (`-0`, `0*-1`, `$floor(-0)`…), JVM даёт 0',
        cases: [{ expr: '-0' }, { expr: '0 * -1' }, { expr: "$number('-0')" }],
        note: 'Видно только через JSON с -0 или 1/x; JSON.stringify(-0) = "0". Считать исключением',
    },
    {
        id: 'NUM-10', cat: 'number', severity: 'low', solution: 'exception', complexity: 'S',
        title: 'Бесконечность как результат: JS возвращает Infinity/NaN (`1/0`, `$eval("1/0")`, `x % 0`), JVM — ошибку D1001/D3121',
        cases: [{ expr: '1/0' }, { expr: "$eval('1/0')" }, { expr: '5 % 0' }, { expr: '$string(1/0)' }],
        note: 'Результат JS не сериализуется в JSON. Ошибка в JVM полезнее; считать исключением',
    },
    {
        id: 'NUM-11', cat: 'number', severity: 'low', solution: 'exception', complexity: 'S',
        title: 'Последние биты `$power`/`$sqrt` (разные libm V8 и Java)',
        cases: [{ expr: '$power(0.30000000000000004, 3)' }],
    },
    // ------------------------------------------------------------------ strings
    {
        id: 'STR-01', cat: 'string', severity: 'medium', solution: 'engine', complexity: 'S',
        title: '`$substring` считает в UTF-16 (режет суррогатные пары; при отрицательном start — исключение), в JS — по кодовым точкам',
        cases: [{ expr: "$substring('a😀b😀c', 1, 3)" }, { expr: "$substring('a😀b😀c', -2)" }, { expr: "$substring('😀', -3)" }],
    },
    {
        id: 'STR-02', cat: 'string', severity: 'medium', solution: 'engine', complexity: 'S',
        title: 'URL-функции: `$encodeUrl` не кодирует пробел, `$decodeUrl*` превращает `+` в пробел, ошибки — исключения Java или код «Malformed URL» вместо D3140',
        cases: [
            { expr: "$encodeUrl('a b')" }, { expr: "$decodeUrlComponent('a+b')" }, { expr: "$decodeUrl('%')" },
            { expr: "$encodeUrlComponent('\\ud800')" },
        ],
    },
    {
        id: 'STR-03', cat: 'string', severity: 'low', solution: 'question', complexity: 'S',
        title: '`$base64encode/decode`: JS (Node) кодирует строку как latin1 (обрезая символы >255), JVM — как UTF-8; некорректный base64 — исключение Java',
        cases: [{ expr: "$base64encode('é')" }, { expr: "$base64decode('w6k=')" }, { expr: "$base64decode('!!!')" }],
        note: 'Для ASCII совпадает. Повторять поведение JS означает портить не-латинские данные',
    },
    {
        id: 'STR-04', cat: 'string', severity: 'low', solution: 'engine', complexity: 'S',
        title: '`$string`: символы U+0080–U+009F и U+2000–U+20FF (кавычки “”, тире, €, …) экранируются как \\uXXXX; пустой объект при pretty-print печатается как `{\\n  }`',
        cases: [{ expr: "$string({'a':{}}, true)" }, { expr: "$string(['x\\u2028y'])" }, { expr: "$string({'a': '€ “q” – …'})" }],
    },
    // ------------------------------------------------------------------ regex
    {
        id: 'RE-01', cat: 'regex', severity: 'medium', solution: 'engine', complexity: 'M',
        title: 'Синтаксис regex: JVM передаёт шаблон в java.util.regex как есть: JS-синтаксис `[^]`, `[\\b]`, `\\0` падает, Java-only `a++`, `(?i)`, `\\Z`, `\\h`, `\\R` принимается (в JS — SyntaxError)',
        cases: [
            { expr: "$replace('ab', /[^]/, 'x')" }, { expr: "$contains('a', /\\0/)" }, { expr: "$match('a\\bb', /[\\b]/)" },
            { expr: "$contains('aa', /a++/)" }, { expr: "$contains('ABC', /(?i)abc/)" },
        ],
    },
    {
        id: 'RE-02', cat: 'regex', severity: 'medium', solution: 'engine', complexity: 'M',
        title: 'Семантика regex: `\\s` (в JS включает NBSP, U+2028, BOM…), `$` перед финальным `\\n`, регистронезависимость для не-ASCII, `.`/классы на суррогатных парах',
        cases: [
            { expr: "$replace('a\\u00a0b', /\\s/, '_')" }, { expr: "$contains('abc\\n', /c$/)" }, { expr: "$contains('é', /É/i)" },
        ],
    },
    {
        id: 'RE-03', cat: 'regex', severity: 'medium', solution: 'engine', complexity: 'S',
        title: 'Совпадение нулевой длины: JS бросает D1004 в `$match`/`$replace`/`$split`, JVM возвращает результат (иногда с лишними пустыми элементами)',
        cases: [
            { expr: "$match('ab', /x*/)" }, { expr: "$replace('abracadabra', /.*?/, '$1')" }, { expr: "$split('ab', /x*/)" },
            { expr: "$split('42', /.*/)" }, { expr: "$match('aaa', /.*/)" },
        ],
    },
    {
        id: 'RE-04', cat: 'regex', severity: 'medium', solution: 'engine', complexity: 'M',
        title: 'Объект совпадения при `str ~> /re/` и regex как функция: `groups` содержит всё совпадение, другой порядок ключей; `$map(arr, /re/)` падает; функции-матчеры не поддерживаются',
        cases: [
            { expr: "'abc' ~> /b/" }, { expr: "$map(['a','b'], /a/)" },
            { expr: "$match('aXbX', function($s, $o){ $o = 0 ? {'match':'X','start':1,'end':2,'groups':[], 'next': function(){undefined}} })" },
        ],
    },
    {
        id: 'RE-05', cat: 'regex', severity: 'medium', solution: 'engine', complexity: 'S',
        title: '`$match(str, re, limit)`: limit игнорируется',
        cases: [{ expr: "$match('a1b2c3', /\\d/, 2).match" }],
    },
    {
        id: 'RE-06', cat: 'regex', severity: 'medium', solution: 'engine', complexity: 'S',
        title: '`$replace`: `$$` в строке замены — исключение; функция замены, вернувшая не строку, — NPE/ClassCast вместо D3012; коды D3010/D3011 — текстом; непойманные группы — null вместо undefined',
        cases: [
            { expr: "$replace('abc', /b/, '$$')" }, { expr: "$replace('abc', /b/, function($m){42})" },
            { expr: "$replace('abc', '', 'x')" }, { expr: "$replace('abc', 'b', 'x', -1)" }, { expr: "$replace('a.b', '.', 'x', 1)" },
        ],
    },
    // ------------------------------------------------------------------ dates
    {
        id: 'DT-01', cat: 'date', severity: 'medium', solution: 'engine', complexity: 'M',
        title: '`$toMillis(str)` без шаблона разбирает через java.time: принимает `20180201`, отвергает `2018-02`, `2018-02-30` даёт 28 февраля вместо 2 марта; ошибка — DateTimeParseException вместо D3110',
        cases: [
            { expr: "$toMillis('2018-02')" }, { expr: "$toMillis('20180201')" }, { expr: "$toMillis('2018-02-30')" },
            { expr: "$toMillis('foo')" },
        ],
    },
    {
        id: 'DT-02', cat: 'date', severity: 'medium', solution: 'engine', complexity: 'M',
        title: '`$toMillis(str, picture)`: фиксированная ширина полей (не перенесён 2dd49c6), `[f]` меньше 3 цифр падает, ошибки шаблона — исключения Java вместо D3132–D3136',
        cases: [
            { expr: "$toMillis('1022024', '[D1][M01][Y0001]')" }, { expr: "$toMillis('2026-04-08T19:05:04.5', '[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f1]')" },
            { expr: "$toMillis('2018-05-22', '[Y]-[M]-[q]')" }, { expr: "$toMillis('2018-22', '[Y]-[D]')" },
        ],
    },
    {
        id: 'DT-03', cat: 'date', severity: 'medium', solution: 'engine', complexity: 'S',
        title: '`$fromMillis`: `[X]` (ISO-год недели) выводится как `[Y]`, `[w]`/`[x]` (неделя месяца) считаются иначе',
        cases: [
            { expr: "$fromMillis($toMillis('2005-01-01'), '[X0001]-W[W01]-[F1]')" },
            { expr: "$fromMillis(1359720000000, 'Week: [w] of [xNn]')" },
        ],
    },
    {
        id: 'DT-04', cat: 'date', severity: 'low', solution: 'engine', complexity: 'S',
        title: '`$fromMillis`: ошибки шаблона (D3133–D3135) — исключения Java; часовой пояс не в формате ±HHMM (`Z`, `EST`, `+01:00`) — NumberFormatException',
        cases: [
            { expr: "$fromMillis(0, '[YN]-[M]-[D]')" }, { expr: "$fromMillis(0, '[Y]', 'Z')" }, { expr: "$fromMillis(0, '[H]', '+01:00')" },
        ],
    },
    {
        id: 'DT-05', cat: 'date', severity: 'low', solution: 'exception', complexity: 'S',
        title: 'Отрицательный часовой пояс с минутами (`-0530`): JS сдвигает на час больше (-06:30), это ошибка JS; JVM считает верно',
        cases: [{ expr: "$fromMillis(0, '[H01]:[m01] [Z]', '-0530')" }],
        note: 'Затрагивает только пояса вроде -03:30 (Ньюфаундленд), -09:30. Повторять ошибку JS не рекомендуется',
    },
    // ------------------------------------------------------------------ order
    {
        id: 'ORD-01', cat: 'order', severity: 'medium', solution: 'question', complexity: 'M',
        title: 'Порядок ключей вида целого числа (`"1"`, `"10"`): объекты JS ставят их первыми по возрастанию (и во входном JSON, и в создаваемых объектах), JVM сохраняет порядок вставки. Видно в `$keys`, `$each`, `$spread`, `$string`, `.*`, группировке `{}`',
        cases: [
            { expr: "{'b':1,'2':2,'1':3}" }, { expr: "$keys({'b':1,'2':2,'1':3})" },
            { expr: 'items{k: n}', input: { items: [{ k: 'b', n: 1 }, { k: '2', n: 2 }, { k: '1', n: 3 }] } },
            { expr: "$string({'b':1,'1':2})" }, { expr: "{'b':1,'1':2}.*" },
        ],
    },
    // ------------------------------------------------------------------ sort
    {
        id: 'SORT-01', cat: 'sort', severity: 'high', solution: 'engine', complexity: 'S',
        title: '`$sort` по умолчанию падает с ClassCastException на смеси Integer/Double (`[2, 0.5]`) и вместо D3070 на объектах/смеси типов',
        cases: [{ expr: '[2, 0.5] ~> $sort' }, { expr: '$sort(n)', input: { n: [3, 1.5, 2] } }, { expr: "$sort([1,'a'])" }, { expr: "$sort([{'a':1},{'a':2}])" }],
    },
    {
        id: 'SORT-02', cat: 'sort', severity: 'medium', solution: 'engine', complexity: 'S',
        title: '`$sort` с компаратором, возвращающим true на равных (`>=`, константа): другой порядок, чем у merge sort из JS',
        cases: [
            { expr: '$sort(items, function($l,$r){$l.k >= $r.k}).n', input: { items: [{ k: 2, n: 'a' }, { k: 1, n: 'b' }, { k: 2, n: 'c' }, { k: 1, n: 'd' }, { k: 3, n: 'e' }, { k: 1, n: 'f' }] } },
            { expr: '$sort([3,1,2], function($l,$r){true})' },
        ],
    },
    {
        id: 'SORT-03', cat: 'sort', severity: 'low', solution: 'engine', complexity: 'S',
        title: 'Сортировка на несравнимых значениях: код (T2007 смесь типов / T2008 не число и не строка) зависит от того, какая пара сравнивается первой, то есть от алгоритма сортировки (см. SORT-02)',
        cases: [{ expr: 'items^(k)', input: { items: [{ k: 1 }, { k: '9' }, { k: null }, { k: '10' }, { k: 2 }, { n: 'nok' }] } }],
    },
    {
        id: 'SEQ-01', cat: 'sequence', severity: 'low', solution: 'engine', complexity: 'S',
        title: '`$distinct` от массива с одним уникальным значением возвращает скаляр вместо массива',
        cases: [{ expr: '$distinct([1,1])' }],
    },
    // ------------------------------------------------------------------ errors
    {
        id: 'ERR-01', cat: 'error', severity: 'low', solution: 'engine', complexity: 'M',
        title: 'Нет позиции (0 или пусто) у ошибок времени выполнения T1003, T1006, T2001–T2004, T2009, T2010, D1002, D1009, T0412…',
        cases: [{ expr: "items{k: n}", input: { items: [{ k: 1, n: 'a' }] } }, { expr: '($x := 1; $x())' }, { expr: "null + 1" }, { expr: "{'x': 1, 'x': 2}" }],
    },
    {
        id: 'ERR-02', cat: 'error', severity: 'low', solution: 'engine', complexity: 'S',
        title: 'Другой код ошибки: `$number("10e500")` D1001 вместо D3030, `$string(1/0)` D1001 вместо D3001, частичное применение T1008 вместо T1007, `$type(age) = …` «Unexpected» вместо D3030',
        cases: [{ expr: "$number('10e500')" }, { expr: 'substring(?, 0, ?)' }],
    },
    {
        id: 'ERR-03', cat: 'error', severity: 'low', solution: 'question', complexity: 'S',
        title: 'Структура ошибки: у JException нет раздельных `token`/`value` (одно поле `current`), сообщения отличаются кавычками; «Expected ) before end» — token `)` вместо `(end)`',
        cases: [{ expr: 'foo(' }, { expr: '$uppercase(1)' }],
    },
    // ------------------------------------------------------------------ found while fixing
    {
        id: 'API-07', fixedBy: 'CompatJs222Test.api07_evaluationTimePerExpression', cat: 'api', severity: 'high', solution: 'engine', complexity: 'S',
        title: 'Общий ThreadLocal `Jsonata.current` для всех выражений: `$now()`/`$millis()` закэшированного выражения возвращают время чужого вычисления (устаревшее), вложенное вычисление другого выражения из функции хоста сбивает контекст',
        evidence: 'CompatJs222Test.api07_evaluationTimePerExpression (до исправления: $millis() устарел на 1,5 с)',
        cases: [],
    },
    {
        id: 'PART-01', cat: 'api', severity: 'high', solution: 'engine', complexity: 'S',
        title: 'Частичное применение: значение токена `?` — Character, проверка заполнителя не срабатывала; любые undefined/недостающие аргументы становились параметрами (`$map(arr, $substring(?, 1))` получал индекс как length)',
        cases: [
            { expr: "$map(['abc','def'], $substring(?, 1))" }, { expr: "($p := function($a,$b,$c){[$a,$b,$c]}(?, 2); $map([1,2], $p))" },
            { expr: "$map(['ab','cd'], $match(?, /b/))" },
        ],
    },
    {
        id: 'ERR-04', cat: 'error', severity: 'low', solution: 'engine', complexity: 'M',
        title: 'Тексты сообщений: в шаблонах порта «function» заменено на «Object», аргументы подставлялись не в те места и всегда в кавычках (совпадало 28% сообщений)',
        evidence: 'выборка 2158 ошибок с одинаковым кодом: до исправления 28% одинаковых сообщений, после — см. REPORT.md',
        cases: [{ expr: '$min(2, 3)' }, { expr: "1 < 'a'" }, { expr: "$average(['1','2'])" }],
    },
    {
        id: 'RE-07', cat: 'regex', severity: 'low', solution: 'exception', complexity: 'L',
        title: 'Regex и символы вне BMP (эмодзи): JS без флага u сопоставляет кодовые единицы UTF-16 (`.` берёт половину суррогатной пары), java.util.regex — кодовые точки',
        cases: [{ expr: "$match('a😀b', /./).match" }, { expr: "$replace('😀', /[😀]/, 'x')" }],
        note: 'Повторить поведение JS в java.util.regex нельзя без собственного regex-движка. Детектор миграции (RE-07) находит regex-литералы с такими символами',
    },
    {
        id: 'UND-03', cat: 'null', severity: 'low', solution: 'exception', complexity: 'S',
        title: 'Непойманная группа regex: в JS элемент `groups` равен undefined, в JVM — null (Java-список не хранит undefined; после JSON-сериализации одинаково)',
        cases: [{ expr: "$match('b', /(a)|(b)/).groups" }, { expr: "$match('ac', /a(b)?c/).groups" }],
    },
    {
        id: 'AHEAD-01', cat: 'api', severity: 'low', solution: 'exception', complexity: 'S',
        title: 'JVM опережает 2.2.2: `$split`/`$replace` с undefined-шаблоном возвращают undefined (в JS 2.2.2 TypeError), `$pad` с шириной > 1e7 — D2016 (в JS строка любой длины). Оба исправлены в jsonata-js после 2.2.2',
        cases: [{ expr: "$split('a,b', nothing)" }, { expr: "$length($pad('x', 20000000))" }],
        note: 'Выражения, падающие в JS, не могут быть в рабочем корпусе; $pad > 1e7 находит детектор (PAD-01)',
    },
];
