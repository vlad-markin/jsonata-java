# Upstream delta: jsonata-js v2.0.0..v2.2.2 → jsonata-java

Цель: эквивалентность jsonata-java (main, HEAD `7dd27a5`, 2026-10-09) с jsonata-js **v2.2.2** (`6c7e95f`).
Метод: для каждого JS-коммита, затрагивающего `src/` или `test/`, прочитан diff и найден соответствующий Java-код; спорные места проверены запуском JS 2.2.2 (`/home/user/jsonata/src`) и Java (`target/classes`, `setOutputConvertNulls(false)`).

Примечание: клон jsonata-js неглубокий (graft на `6f1fc88` = v2.0.6), поэтому коммиты v2.0.0..v2.0.5 взяты из истории тегов, а v2.0.5→v2.0.6 — одной строкой (squash-коммит релиза).

## Итоги

| Класс | Кол-во | Коммиты |
|---|---|---|
| PORTED | 14 | b520b88, 7a7f2b5, 4446161, 6f1fc88, 09dba37, 9e1523d, 6092352, ff36f0b, fd47061, accd4d3, f9632e0, 47c0e58, fec95de, f3e15f2 |
| NOT PORTED | 4 | **2dd49c6** (regex парсера даты), **d6ffc17** (ISO 8601 / D3110), **91532d4** (лимит `$append`), **df63a7c** (`$$` race / общий frame) |
| PARTIAL | 3 | **80ba95d** (`[f]` < 3 цифр падает), **9588fb4** (D3086 в `$formatNumber`), **0a7aa35** (guardrails: NPE, позиции) |
| N/A-JS-ONLY | 11 | c907b5e, 335d38f, e53b83c, ec0f8cf, d1958d0, d4e4133, 597e5ee, 4c5f4ad, 59e2514, f174348, 82e1597 |
| UNCLEAR | 0 | — |

Java опережает 2.2.2 в двух местах (бэкпорты коммитов после v2.2.2): `d77b8b5` (`$split`/`$replace` с undefined pattern) и `98f23c5` (лимит `$pad` 1e7, D2016) — см. последний раздел.

### С какой версии jsonata-js изначально портирован код

Оценка: **jsonata-js 2.0.3 (середина 2023), исходники позже подтянуты до уровня 2.0.5**; от 2.0.6 есть только изменение `keepArray` для `~>`.
- README ссылается на прогон CI от 07.2023 (actions run 5717119540) — в это время актуальной была 2.0.3 (2023-04).
- В порте есть `isClosingSlash` из b520b88 (2.0.3) и am/pm `[PN]` из 7a7f2b5 (вошло в 2.0.4); изменения 2.0.4/2.0.5 либо только для JS (D1010), либо в Java выполняются сами собой (`$keys`/`$lookup` у функций).
- Первый указатель сабмодуля — `d0afd35` = v2.0.5.
- `keepArray` для `apply` из v2.0.6 добавлен отдельно (Java `4e12922`, 2024-09, rayokota).
- `?:`/`??` (2.1.0) и обрезка дробной части в `$pad` (2.1.0) добавлены бэкпортами 2025-07 (`b411529`, `7a9f0c2`). Значит, на момент портирования их в исходниках не было.

## Таблица

Обозначения: `undefined` — нет результата; `ERR code` — ошибка JSONata; `EXC` — Java-исключение без кода JSONata.

| JS sha | дата | заголовок | класс | Java evidence | demo | JS 2.2.2 | Java |
|---|---|---|---|---|---|---|---|
| b520b88 | 2023-03-17 | Fix regex termination lexer (#623) | PORTED | Tokenizer.java:110 `isClosingSlash` (есть с первоначального порта) | — | — | — |
| 7a7f2b5 | 2023-08-05 | Upper/lower am/pm in fromMillis | PORTED | DateTimeUtils.java:859-863 | `$fromMillis(0,'[h]:[m01] [PN]')` | `"12:00 AM"` | `"12:00 AM"` |
| c907b5e | 2024-02-27 | Prevent access to `__proto__` (D1010) | N/A-JS-ONLY | у Java-Map нет прототипа; удалено в JS в 4c5f4ad | — | — | — |
| 335d38f | 2024-02-29 | Check for constructor property | N/A-JS-ONLY | то же | — | — | — |
| 4446161 | 2024-05-09 | Prevent lookup of function object properties | PORTED (само собой) | функции в Java — `Symbol`/`JFunction`, не `Map` | `$keys($sum)` / `$lookup($sum,'x')` | `undefined` | `undefined` |
| 6f1fc88 | 2024-11-29 | Release v2.0.6 (squash: `Symbol.for` callbacks, `__createFrame_push`, `keepArray` для `~>`) | PORTED (`keepArray`); callbacks — N/A | Parser.java:1218 (Java `4e12922`) | `( $data := [1]; $square := function($x){$x*$x}; $data ~> $map($square)[] )` | `[1]` | `[1]` |
| 2dd49c6 | 2025-01-10 | Improve regex generation for DateTime parser | **NOT PORTED** | DateTimeUtils.java:1197-1216: свои regex по компонентам (`Y`→`[0-9]{2,4}`, `MDHhms`→`[0-9]{1,2}`); `parseWidth` у соседних целых полей нет | `$toMillis('1022024','[D1][M01][Y0001]') ~> $fromMillis()` | `"2024-02-01T00:00:00.000Z"` | `EXC DateTimeException … MonthOfYear: 22` |
| 〃 | | | | | `$toMillis('2024-01-05 93000','[Y0001]-[M01]-[D01] [H1][m01][s01]') ~> $fromMillis()` | `"2024-01-05T09:30:00.000Z"` | `EXC DateTimeException … HourOfDay: 93` |
| 〃 | | | | | `$toMillis('20241015','[Y0001][M1][D01]') ~> $fromMillis()` | `"2024-01-15T00:00:00.000Z"` | `"2024-10-15T00:00:00.000Z"` |
| 09dba37 | 2025-01-14 | `$pad` truncates fractional width | PORTED | Java `7a9f0c2`; Functions.java:580 `_width.intValue()` | `$pad("x",3.7)` | `"x  "` | `"x  "` |
| 9e1523d | 2025-07-08 | default `?:` / coalescing `??` operators | PORTED | Java `b411529`; Tokenizer.java:64-65,248-253; Parser.java:498,812. `$boolean(function)` = false само собой (Functions.java:1476) | `null ?? 1`; `{} ?: 1`; `$sum ?: 1` | `null`; `1`; `1` | `null`; `1`; `1` |
| e53b83c | 2025-05-25 | Await array elements | N/A-JS-ONLY | async/Promise | — | — | — |
| ec0f8cf | 2025-05-25 | Better test name (test/async-function.js) | N/A-JS-ONLY | только тест | — | — | — |
| d1958d0 | 2025-05-25 | Formatting (test/async-function.js) | N/A-JS-ONLY | только тест | — | — | — |
| 6092352 | 2025-11-06 | Precision fix for `$string` | PORTED (результат тот же) | Functions.java:198-200: целые double выводятся без `toPrecision(15)` (отдельного бэкпорта нет) | `$string(5890840712243076)` | `"5890840712243076"` | `"5890840712243076"` |
| ff36f0b | 2025-11-06 | Signature fix for repeating arguments | PORTED | Java `62b3d54` (+`d7fa65a`); Signature.java:348,388 | `λ($a,$b)<a<n>+:o>{{'a':$a,'b':$b}}([1,2],[3,4],[5,6])` (+ case035-040) | `{"a":[1,2],"b":[3,4]}` | то же (все 6 кейсов совпадают) |
| d4e4133 | 2026-03-16 | move function signature tests | N/A-JS-ONLY | перенос тестов | — | — | — |
| fd47061 | 2026-03-18 | Fix `??` with array predicate on LHS | PORTED | Java `c6e6822`; Parser.java:509 `Parser.clone(left)` | `foo.blah[5] ?? 42` (`{"foo":{"blah":[{}]}}`); `[1,2,3][-1] ?? 42` | `42`; `3` | `42`; `3` |
| 597e5ee | 2026-03-22 | removing unused nextFunction | N/A (мёртвый код) | Parser.java:1010 в Java всё ещё ставит `nextFunction`, но поле нигде не читается. На поведение не влияет | — | — | — |
| accd4d3 | 2026-04-29 | Fix `?:` with array predicate on LHS | PORTED | Java `c6e6822`; Parser.java:819 | `-5 ?: 99`; `[true][-1] ?: "no"` | `-5`; `true` | `-5`; `true` |
| 80ba95d | 2026-04-30 | `$toMillis` with >3 digit fractional seconds | **PARTIAL** | Java `4f68f66`; DateTimeUtils.java:1111-1115: `Integer.parseInt(value.substring(0,3))` вместо `parseFloat('0.'+v.substring(0,3))*1000` — при 1-2 цифрах выход за границы строки | `$toMillis('2026-04-08T19:05:04.5','[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f1]') ~> $fromMillis()` | `"2026-04-08T19:05:04.500Z"` | `EXC StringIndexOutOfBoundsException` |
| 〃 | | | | | то же с `.01987` (тест из коммита) | `"…04.019Z"` | `"…04.019Z"` |
| 9588fb4 | 2026-05-05 | fix picture string parsing for `$formatNumber` | **PARTIAL** (по сути не портировано) | Java `e8b852d`: добавлен только override в test-overrides.json. `$formatNumber` в Java построен на `java.text.DecimalFormat` (Functions.java:1118), D3086 никогда не выдаётся | `$formatNumber(42,'---')` | `ERR D3086` | `"---42"` |
| 〃 | | | | | `$formatNumber(42,'%%')` | `ERR D3086` | `EXC IllegalArgumentException` (без кода) |
| 〃 | | | | | `$formatNumber(42,'abc')` | `ERR D3086` | `"abc42"` |
| f9632e0 | 2026-05-11 | `$formatNumber` infinite loop on zero/negative with exponent | PORTED (результат тот же за счёт DecimalFormat) | Functions.java:1118 (бесконечного цикла в Java нет и не было) | `$formatNumber(0,'0e0')`; `$formatNumber(-42,'0e0')` | `"0e0"`; `"-4e1"` | `"0e0"`; `"-4e1"` |
| 〃 | | | | (побочное, к коммиту не относится) | `$formatNumber(0,'#e0')` | `"0.e0"` | `"e0"` |
| d6ffc17 | 2026-05-14 | fix iso8601 regex pattern | **NOT PORTED** (другая реализация) | Functions.java:2231-2262: ISO-разбор через `java.time`; regex нет, D3110 не выдаётся нигде (есть только текст в Jsonata.java:2493) | `$toMillis('2026-04-08-08-08')` | `ERR D3110` | `EXC DateTimeParseException` |
| 〃 | | | | | `$toMillis('2026-04-08T10:00:00T10:00:00')` | `ERR D3110` | `EXC DateTimeParseException` |
| 4c5f4ad | 2026-05-14 | prevent `$lookup` accessing prototype; удаление D1010 | N/A-JS-ONLY | прототипов в Java нет | `$lookup({}, 'constructor')` | `undefined` | `undefined` |
| 0a7aa35 | 2026-05-14 | Create guardrails (timeout/stack/sequence, D1011/D1012/D2015, D2014 1e6→1e7) | **PARTIAL** | Java `fae1a75`; Jsonata.java:120-147 (Frame), 1252, 1259, 2546-2570 (`Options`). **Ошибка:** Jsonata.java:1259 `size > options.sequence` при `options.sequence == null` даёт NPE. Позиция ошибок `-1` (в JS — позиция токена) | `[1..5]` с `{timeout:1000}` (или `{stack:100}`) | `[1,2,3,4,5]` | `EXC NullPointerException` |
| 〃 | | | | | `[0..1001]` с `{sequence:1000}` | `ERR D2015` pos 4, «…of 1002…» | `ERR D2015` pos -1, «…of "1002"…» |
| 〃 | | | | | `($ack := …; $ack(4,4))` с `{stack:500}`; `$inf()` с `{timeout:300}` | `D1011`; `D1012` | `D1011`; `D1012` (совпадает) |
| 59e2514 | 2026-05-19 | Prevent object prototype pollution | N/A-JS-ONLY | `Object.create(null)`, `Object.keys` — в Java не нужно | `{"__proto__": 1}.__proto__` | `1` | `1` |
| 47c0e58 | 2026-05-19 | Wildcards should not unwrap function objects | PORTED (само собой) | функции не `Map` (Jsonata.java:755, 833) | `$sum.*`; `[$sum,$count].*`; `/a/.*` | `undefined` ×3 | `undefined` ×3 |
| 91532d4 | 2026-05-19 | Append limit (D2015 в `$append`, wildcard, конструкторе массива, group) | **NOT PORTED** | Functions.java:2091-2115 `append()` копирует в `new JList<>(arg1)` без maxSize, проверки размера нет | `$append([0..600],[0..600]) ~> $count()` с `{sequence:1000}` | `ERR D2015` | `1202` |
| 〃 | | | | | `[1,2,3,4,5,6]` с `{sequence:5}` | `ERR D2015` | `[1,2,3,4,5,6]` |
| 〃 | | | | | `{'a':[1,2],'b':[3,4]}.*` с `{sequence:3}` | `ERR D2015` | `[1,2,3,4]` |
| f174348 | 2026-05-19 | Prevent object constructor setting internal flags (D1013) | N/A-JS-ONLY (ограничение самой JS-реализации), но поведение видимо отличается | Java не проверяет; подделать лямбду через Map нельзя (T1006) | `{"_jsonata_lambda": true}` | `ERR D1013` | `{"_jsonata_lambda":true}` |
| 〃 | | | | | `($fn = {"_jsonata_function": true}; $fn())` | `ERR D1013` | `ERR T1006` |
| fec95de | 2026-07-15 | Fix `$contains` when token is undefined | PORTED | Java `a11d3fb`; Functions.java:701-703 | `$contains("Hello World", nothing)` | `undefined` | `undefined` |
| 82e1597 | 2026-07-15 | regression looking up keys of an object (`utils.keys`) | N/A-JS-ONLY (регрессия была только в JS 2.2.1) | Java не затронута | `$each(input.data, function($v,$k){$v})` (`{"input":{}}`) | `undefined` | `undefined` |
| df63a7c | 2026-07-15 | race condition when `$$` used concurrently | **NOT PORTED** | Jsonata.java:2687-2697: без bindings `exec_env = environment` — общий для всех вызовов frame. В нём же `bind("$", input)`, а также `base/depth/time/options` | один `Jsonata` для `$y := ($exists($y) ? $y + 1 : 1)`, `evaluate(null)` вызван дважды | `1`, `1` | `1`, `2` (переменная остаётся между вызовами) |
| 〃 | | | | | `[1..20000].($$.id) ~> $distinct()`: 200 параллельных `evaluate({"id":i})` на 8 потоках | всегда `i` | 199/200 неверных (напр. `[7, 8]`) |
| f3e15f2 | 2026-07-15 | correctly handle empty joins | PORTED | Java `717ee64`; Jsonata.java:1110 | `library-joins012.jsonata` (dataset library) | `{}` | `{}` |

### Что исправить (кратко)
1. **df63a7c**: в `Jsonata.evaluate(Object, Frame)` всегда создавать `exec_env = createFrame(environment)`. Иначе `$$`, guardrail-счётчик `depth`, `time` и `options` общие для всех потоков.
2. **0a7aa35**: Jsonata.java:1259 — `options!=null && options.sequence!=null && size > options.sequence`; по возможности передавать позицию в D2015/D1011/D1012.
3. **91532d4**: в `Functions.append` проверять `arg1.size()+arg2.size() > options.sequence` → D2015. Либо сохранять `maxSize` при копировании `JList`. Это покроет wildcard, конструктор массива и group.
4. **80ba95d**: `'f'` → `(int)(Double.parseDouble("0." + value.substring(0, Math.min(3, value.length()))) * 1000)`.
5. **2dd49c6**: перенести логику `parseWidth` (точная ширина предыдущего целого поля без разделителя) вместо фиксированных `{1,2}`/`{2,4}`.
6. **d6ffc17**: при неразобранной ISO-строке бросать `JException("D3110", …)` и проверять тот же regex, что в JS. Попутно: JS принимает `'2026-04'`, Java падает.
7. **9588fb4**: проверять picture (passive между active → D3086) до `DecimalFormat`. Исключения `DecimalFormat` преобразовывать в коды D3xxx.

## Java опережает 2.2.2 (поведение отличается от эталона v2.2.2)

| upstream (после v2.2.2) | Java | demo | JS 2.2.2 | Java |
|---|---|---|---|---|
| d77b8b5 (2026-09-10) `$split`/`$replace` возвращают undefined при undefined pattern | `9f1c960` (Functions.java:909, 1080) | `$split('a,b', nothing)`; `$replace('a,b', nothing, 'x')` | ошибка без кода: `Cannot read properties of undefined (reading 'apply')` | `undefined` |
| 98f23c5 (2026-09-10) лимит `$pad` 1e7, D2016 | `d0eacb3` (Functions.java:582-590, Jsonata.java:2466) | `$pad('x', 20000000)` | строка из 20 000 000 символов | `ERR D2016` |

Также отличается от 2.2.2, но это особенности самой Java (upstream этого не было):
- `test/test-overrides.json` пропускает или подменяет кейсы `formatNumber` (экспонента, picture), ISO week date и др.
- Есть собственный класс `Timebox` (таймаут и глубина через callbacks) — параллельно guardrails.
- Thread-safety через `EvalContext` (`900c653`) — не покрывает общий `environment` (см. df63a7c).

## Попутно замечено (не связано с коммитами диапазона)
- `$toMillis('2026-04')`: JS → `1775001600000`, Java → `EXC DateTimeParseException`.
- 2-значный год: `$toMillis('240101','[Y01][M01][D01]')`: JS → 1924-01-01, Java → 2401-01-01.
- Значения в сообщениях ошибок Java берёт в кавычки (`of "1002"` вместо `of 1002`).
- `$string(/a/)`: JS → `""`, Java → `IllegalArgumentException`.
