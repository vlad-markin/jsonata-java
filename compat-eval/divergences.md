# Каталог расхождений jsonata-js 2.2.2 ↔ JVM-движок (форк dashjoin)

Файл генерируется командой `node divergences/build.js report <run>` из `divergences/catalog.js`
и прогона diff-runner по `cases/divergences.jsonl`. Колонки JS/JVM — фактические результаты,
статус пересчитывается после исправлений в движке.

Источники: строгий прогон test-suite 2.2.2 (фаза 1), дифф-фаззинг (42 000 случаев,
`fuzz/gen.js` seeds 1–3), случаи расширяемости (`cases/extensibility.jsonl`, 110 шт.),
точечные проверки (`cases/targeted.jsonl`, 261 шт.), проба API хоста
(`phase2/api-probe.md`). Каждый кластер фаззинга сведён к минимальным примерам.

Обозначения результатов: `error CODE@pos` — ошибка JSONata; `crash <класс>` — исключение, которое
не является ошибкой JSONata; `undefined` — нет результата.
Категория — главный вид расхождения по diff-runner (см. `diff-runner/README.md`).

Не считаются расхождениями (сравнение их не учитывает): различия только в поле `token`
(см. ERR-03), функции, отличающиеся лишь видом, флаг sequence у массивов.

## Сводка

| id | категория | серьёзность | решение | сложность | статус | кратко |
|---|---|---|---|---|---|---|
| [API-01](#api-01) | api | high | исправить движок | S | см. evidence | Общее окружение между вызовами evaluate без Frame: `:=` на верхнем уровне и `$$` утекают, параллельные вызовы … |
| [API-02](#api-02) | api | medium | исправить движок | M | см. evidence | Лямбды можно вызвать из хоста только в потоке вычисления (Functions.funcApply опирается на ThreadLocal) |
| [API-03](#api-03) | api | medium | исправить движок | S | воспроизводится 2/2 | Функции хоста: частичное применение `$f(?, x)` и передача функции без сигнатуры в HOF |
| [API-04](#api-04) | api | medium | исправить движок | S | воспроизводится 4/4 | Guardrails (опции jsonata 2.2): NPE при `timeout`/`stack` без `sequence`; `$append` и `*` не проверяют `sequen… |
| [API-05](#api-05) | api | medium | исправить движок | S | воспроизводится 1/1 | `Frame.setRuntimeBounds`: ошибка без кода U1001 (код = текст сообщения), время отсчитывается от создания Frame… |
| [API-06](#api-06) | api | low | решение за вами (см. «Вопросы») | M | см. evidence | Модель значений на границе с хостом: JSON null приходит в функции хоста как `Jsonata.NULL_VALUE`, вернуть null… |
| [UND-01](#und-01) | null | high | исправить движок | M | воспроизводится 7/7 | JSON null, переданный аргументом функции, превращается в undefined |
| [UND-02](#und-02) | null | medium | исправить движок | S | воспроизводится 4/4 | Проверка сигнатур пропускает null для параметров `s`, `n`, `o`…: вместо T0410 — исключение Java или undefined … |
| [NUM-01](#num-01) | number | high | исправить движок | S | воспроизводится 4/4 | Глубокое сравнение (`=`, `!=`, `in`, `$distinct`) массивов и объектов зависит от Java-типа числа (Long/Integer… |
| [NUM-02](#num-02) | number | medium | исправить движок | S | воспроизводится 3/3 | Строковое представление чисел ≥1e20 и с >15 значащими цифрами (`$string`, `&`): JVM даёт экспоненту там, где J… |
| [NUM-03](#num-03) | number | medium | исправить движок | M | воспроизводится 7/7 | `$formatNumber` реализован через java.text.DecimalFormat: другие результаты для `#.#`, округления, экспоненты,… |
| [NUM-04](#num-04) | number | medium | исправить движок | M | воспроизводится 7/7 | `$formatInteger`/`$parseInteger`: переполнение long (слова для >9.2e18), цифры других систем, разбор `i`/`I` (… |
| [NUM-05](#num-05) | number | low | исправить движок | S | воспроизводится 4/4 | `$number`: принимает `"+5"`, `"1."`; ошибки — NumberFormatException или D1001 вместо D3030 |
| [NUM-06](#num-06) | number | low | исправить движок | S | воспроизводится 3/3 | `$formatBase` и диапазоны с числами вне long: JS считает в double |
| [NUM-07](#num-07) | number | low | исправить движок | S | воспроизводится 1/1 | `$sum` суммирует не так, как JS (не простое последовательное сложение double) |
| [NUM-08](#num-08) | number | low | согласованное исключение | S | см. evidence | Целые > 2^53 во входных данных и привязках: JVM хранит точный long, JS округляет до double |
| [NUM-09](#num-09) | number | low | согласованное исключение | S | воспроизводится 3/3 | Отрицательный ноль: JS сохраняет -0 (`-0`, `0*-1`, `$floor(-0)`…), JVM даёт 0 |
| [NUM-10](#num-10) | number | low | согласованное исключение | S | воспроизводится 3/3 | Бесконечность как результат: JS возвращает Infinity/NaN (`1/0`, `$eval("1/0")`, `x % 0`), JVM — ошибку D1001/D… |
| [NUM-11](#num-11) | number | low | согласованное исключение | S | воспроизводится 1/1 | Последние биты `$power`/`$sqrt` (разные libm V8 и Java) |
| [STR-01](#str-01) | string | medium | исправить движок | S | воспроизводится 3/3 | `$substring` считает в UTF-16 (режет суррогатные пары; при отрицательном start — исключение), в JS — по кодовы… |
| [STR-02](#str-02) | string | medium | исправить движок | S | воспроизводится 4/4 | URL-функции: `$encodeUrl` не кодирует пробел, `$decodeUrl*` превращает `+` в пробел, ошибки — исключения Java … |
| [STR-03](#str-03) | string | low | решение за вами (см. «Вопросы») | S | воспроизводится 3/3 | `$base64encode/decode`: JS (Node) кодирует строку как latin1 (обрезая символы >255), JVM — как UTF-8; некоррек… |
| [STR-04](#str-04) | string | low | исправить движок | S | воспроизводится 2/2 | `$string(x, true)`: пустой объект печатается как `{\n  }`; U+2028/U+2029 экранируются |
| [RE-01](#re-01) | regex | medium | исправить движок | M | воспроизводится 5/5 | Синтаксис regex: JVM передаёт шаблон в java.util.regex как есть: JS-синтаксис `[^]`, `[\b]`, `\0` падает, Java… |
| [RE-02](#re-02) | regex | medium | исправить движок | M | воспроизводится 5/5 | Семантика regex: `\s` (в JS включает NBSP, U+2028, BOM…), `$` перед финальным `\n`, регистронезависимость для … |
| [RE-03](#re-03) | regex | medium | исправить движок | S | воспроизводится 5/5 | Совпадение нулевой длины: JS бросает D1004 в `$match`/`$replace`/`$split`, JVM возвращает результат (иногда с … |
| [RE-04](#re-04) | regex | medium | исправить движок | M | воспроизводится 3/3 | Объект совпадения при `str ~> /re/` и regex как функция: `groups` содержит всё совпадение, другой порядок ключ… |
| [RE-05](#re-05) | regex | medium | исправить движок | S | воспроизводится 1/1 | `$match(str, re, limit)`: limit игнорируется |
| [RE-06](#re-06) | regex | medium | исправить движок | S | воспроизводится 5/5 | `$replace`: `$$` в строке замены — исключение; функция замены, вернувшая не строку, — NPE/ClassCast вместо D30… |
| [DT-01](#dt-01) | date | medium | исправить движок | M | воспроизводится 4/4 | `$toMillis(str)` без шаблона разбирает через java.time: принимает `20180201`, отвергает `2018-02`, `2018-02-30… |
| [DT-02](#dt-02) | date | medium | исправить движок | M | воспроизводится 4/4 | `$toMillis(str, picture)`: фиксированная ширина полей (не перенесён 2dd49c6), `[f]` меньше 3 цифр падает, ошиб… |
| [DT-03](#dt-03) | date | medium | исправить движок | S | воспроизводится 2/2 | `$fromMillis`: `[X]` (ISO-год недели) выводится как `[Y]`, `[w]`/`[x]` (неделя месяца) считаются иначе |
| [DT-04](#dt-04) | date | low | исправить движок | S | воспроизводится 3/3 | `$fromMillis`: ошибки шаблона (D3133–D3135) — исключения Java; часовой пояс не в формате ±HHMM (`Z`, `EST`, `+… |
| [DT-05](#dt-05) | date | low | согласованное исключение | S | воспроизводится 1/1 | Отрицательный часовой пояс с минутами (`-0530`): JS сдвигает на час больше (-06:30), это ошибка JS; JVM считае… |
| [ORD-01](#ord-01) | order | medium | решение за вами (см. «Вопросы») | M | воспроизводится 5/5 | Порядок ключей вида целого числа (`"1"`, `"10"`): объекты JS ставят их первыми по возрастанию (и во входном JS… |
| [SORT-01](#sort-01) | sort | high | исправить движок | S | воспроизводится 4/4 | `$sort` по умолчанию падает с ClassCastException на смеси Integer/Double (`[2, 0.5]`) и вместо D3070 на объект… |
| [SORT-02](#sort-02) | sort | medium | исправить движок | S | воспроизводится 2/2 | `$sort` с компаратором, возвращающим true на равных (`>=`, константа): другой порядок, чем у merge sort из JS |
| [SORT-03](#sort-03) | sort | low | исправить движок | S | воспроизводится 1/1 | Сортировка на несравнимых значениях: код (T2007 смесь типов / T2008 не число и не строка) зависит от того, как… |
| [SEQ-01](#seq-01) | sequence | low | исправить движок | S | воспроизводится 1/1 | `$distinct` от массива с одним уникальным значением возвращает скаляр вместо массива |
| [ERR-01](#err-01) | error | low | исправить движок | M | воспроизводится 4/4 | Нет позиции (0 или пусто) у ошибок времени выполнения T1003, T1006, T2001–T2004, T2009, T2010, D1002, D1009, T… |
| [ERR-02](#err-02) | error | low | исправить движок | S | воспроизводится 3/3 | Другой код ошибки: `$number("10e500")` D1001 вместо D3030, `$string(1/0)` D1001 вместо D3001, частичное примен… |
| [ERR-03](#err-03) | error | low | решение за вами (см. «Вопросы») | S | воспроизводится 2/2 | Структура ошибки: у JException нет раздельных `token`/`value` (одно поле `current`), сообщения отличаются кавы… |

## Подробно

### API-01

**Общее окружение между вызовами evaluate без Frame: `:=` на верхнем уровне и `$$` утекают, параллельные вызовы дают неверный результат**

Категория: api; серьёзность: high; решение: **исправить движок**; сложность: S; статус: см. evidence.

- JS 2.2.2: каждый evaluate получает новый frame (исправлено в 2.2.2, df63a7c)
- JVM: без Frame используется общий `environment` скомпилированного выражения
- Доказательство: ApiProbe API-1, API-2 (209 неверных результатов из 400 при 8 потоках)

### API-02

**Лямбды можно вызвать из хоста только в потоке вычисления (Functions.funcApply опирается на ThreadLocal)**

Категория: api; серьёзность: medium; решение: **исправить движок**; сложность: M; статус: см. evidence.

- JS 2.2.2: `fn.apply(self, args)` работает всегда (Promise)
- JVM: NPE при вызове из другого потока, в т.ч. лямбды, возвращённой как результат
- Доказательство: ApiProbe API-5b, API-5d

### API-03

**Функции хоста: частичное применение `$f(?, x)` и передача функции без сигнатуры в HOF**

Категория: api; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 2/2.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$ap($ap(?, 'x'), function($s){$s & $s})` | host: ap | "xx" | error T1006@0 | JVM_ERROR |
| `$map(['a','b'], $hecho)` | host: hecho | ["a","b"] | crash java.lang.NullPointerException: java.lang.NullPointerException: Cannot invoke "com.dashjoin.jsonata.utils.Signature.getMinNumberOfArgs | JVM_CRASH |

### API-04

**Guardrails (опции jsonata 2.2): NPE при `timeout`/`stack` без `sequence`; `$append` и `*` не проверяют `sequence`; у D2015 нет позиции**

Категория: api; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `[1..5]` | options {"timeout":1000} | [1,2,3,4,5] | crash java.lang.NullPointerException: java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.environment | JVM_CRASH |
| `[1..5]` | options {"stack":100} | [1,2,3,4,5] | crash java.lang.NullPointerException: java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "this.environment | JVM_CRASH |
| `$append([1,2,3],[4,5,6])` | options {"sequence":5} | error D2015@8 | [1,2,3,4,5,6] | JVM_NO_ERROR |
| `{'a':[1,2],'b':[3,4]}.*` | options {"sequence":3} | error D2015 | [1,2,3,4] | JVM_NO_ERROR |

### API-05

**`Frame.setRuntimeBounds`: ошибка без кода U1001 (код = текст сообщения), время отсчитывается от создания Frame, а не от начала evaluate**

Категория: api; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 1/1.

- Доказательство: ApiProbe API-7; test-suite tail-recursion/case002,005,006

| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `($f := function($n){$n=0?0:1+$f($n-1)}; $f(50))` | bounds {"timelimit":1000,"depth":30} | error U1001@32 | error Stack overflow error: Check for non-terminating recursive function.  Consider rewriting as tail-recursive. Depth=31 max=30@32 | ERROR_CODE |

### API-06

**Модель значений на границе с хостом: JSON null приходит в функции хоста как `Jsonata.NULL_VALUE`, вернуть null можно только как NULL_VALUE (Java null = undefined); числа приходят как Integer/Long/Double, последовательности — как JList**

Категория: api; серьёзность: low; решение: **решение за вами (см. «Вопросы»)**; сложность: M; статус: см. evidence.

- Доказательство: ApiProbe API-3, API-4, API-10, API-11

### UND-01

**JSON null, переданный аргументом функции, превращается в undefined**

Категория: null; серьёзность: high; решение: **исправить движок**; сложность: M; статус: воспроизводится 7/7.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$count(null)` |  | 1 | 0 | NUMBER |
| `$count(owner)` | {"owner":null} | 1 | 0 | NUMBER |
| `$map(null, function($v){$type($v)})` |  | "null" | undefined | VALUE |
| `$sort(null)` |  | [null] | undefined | UNDEF_NULL |
| `$reduce(null, function($a,$b){$a})` |  | null | undefined | UNDEF_NULL |
| `$zip(null)` |  | [[null]] | [] | VALUE |
| `$max(null)` |  | error T0412@5 | undefined | JVM_NO_ERROR |

### UND-02

**Проверка сигнатур пропускает null для параметров `s`, `n`, `o`…: вместо T0410 — исключение Java или undefined (в т.ч. для функций хоста с сигнатурой)**

Категория: null; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$uppercase(null)` |  | error T0410@11 | crash java.lang.Exception: java.lang.Exception: Argument error calling function uppercase | JVM_CRASH |
| `$length(a)` | {"a":null} | error T0410@8 | crash java.lang.Exception: java.lang.Exception: Argument error calling function length | JVM_CRASH |
| `$hu(null)` | host: hu<s:s> | error T0410@4 | undefined | JVM_NO_ERROR |
| `$sift(null, function($v){true})` |  | error T0410@6 | crash java.lang.Exception: java.lang.Exception: Argument error calling function sift | JVM_CRASH |

### NUM-01

**Глубокое сравнение (`=`, `!=`, `in`, `$distinct`) массивов и объектов зависит от Java-типа числа (Long/Integer/Double)**

Категория: number; серьёзность: high; решение: **исправить движок**; сложность: S; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `a = [1,2]` | {"a":[1,2]} | true | false | VALUE |
| `o = {'x': 1}` | {"o":{"x":1}} | true | false | VALUE |
| `a != [1.5,2]` | {"a":[1.5,2]} | false | true | VALUE |
| `$distinct([1, 1.0, n])` | {"n":1} | [1] | [1,1] | VALUE |

### NUM-02

**Строковое представление чисел ≥1e20 и с >15 значащими цифрами (`$string`, `&`): JVM даёт экспоненту там, где JS пишет целое**

Категория: number; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 3/3.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$string(1e20)` |  | "100000000000000000000" | "1e+20" | VALUE |
| `12345678901234567890 & ''` |  | "12345678901234567000" | "1.23456789012346e+19" | VALUE |
| `$string($power(2, 64))` |  | "18446744073709552000" | "1.84467440737096e+19" | VALUE |

### NUM-03

**`$formatNumber` реализован через java.text.DecimalFormat: другие результаты для `#.#`, округления, экспоненты, второй части шаблона; ошибки шаблона — исключения Java или их отсутствие**

Категория: number; серьёзность: medium; решение: **исправить движок**; сложность: M; статус: воспроизводится 7/7.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$formatNumber(0.125, '#.#')` |  | ".1" | "0.1" | VALUE |
| `$formatNumber(0.005, '0.00')` |  | "0.00" | "0.01" | VALUE |
| `$formatNumber(0.234, '#.00e0')` |  | "0.23e0" | ".234e0" | VALUE |
| `$formatNumber(2392.14*(-36.58), '000,000.000###;###,###.000###')` |  | "87,504.4812" | "-087,504.4812" | VALUE |
| `$formatNumber(20, '#0%%')` |  | error D3082@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Too many percent/per mille characters in pattern "#0%%" | JVM_CRASH |
| `$formatNumber(42, '---')` |  | error D3086@14 | "---42" | JVM_NO_ERROR |
| `$formatNumber(-1, '0%', {'percent': 'pc'})` |  | "-1%" | crash java.lang.RuntimeException: java.lang.RuntimeException | JVM_CRASH |

### NUM-04

**`$formatInteger`/`$parseInteger`: переполнение long (слова для >9.2e18), цифры других систем, разбор `i`/`I` (NPE), знак, порядковые `#;o`**

Категория: number; серьёзность: medium; решение: **исправить движок**; сложность: M; статус: воспроизводится 7/7.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$formatInteger(1e22, 'w')` |  | "ten billion trillion" | "nine million, two hundred and twenty-three thousand, three hundred and seventy-two trillion, thirty-six billion, eight hundred and fifty-fo | VALUE |
| `$parseInteger('ten billion trillion trillion trillion', 'w')` |  | 1e+46 | 8023796054858137600 | NUMBER |
| `$parseInteger('١٢٣٤٠', '###١')` |  | 12340 | undefined | VALUE |
| `$parseInteger('ab', 'i')` |  | NaN | crash java.lang.NullPointerException: java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because the return value  | JVM_CRASH |
| `$parseInteger('-5', '1')` |  | -5 | undefined | VALUE |
| `$formatInteger(22, '#;o')` |  | error D3130@15 | crash java.lang.RuntimeException: java.lang.RuntimeException: Formatting or parsing an integer as a sequence starting with # is not supporte | JVM_CRASH |
| `$parseInteger('12', '1')` |  | 12 | 2 | NUMBER |

### NUM-05

**`$number`: принимает `"+5"`, `"1."`; ошибки — NumberFormatException или D1001 вместо D3030**

Категория: number; серьёзность: low; решение: **исправить движок**; сложность: S; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$number('+5')` |  | error D3030@8 | 5 | JVM_NO_ERROR |
| `$number('1.')` |  | error D3030@8 | 1 | JVM_NO_ERROR |
| `$number('Hello')` |  | error D3030@8 | crash java.lang.NumberFormatException: java.lang.NumberFormatException: For input string: "Hello" | JVM_CRASH |
| `$number('1e400')` |  | error D3030@8 | error D1001@0 | ERROR_CODE |

### NUM-06

**`$formatBase` и диапазоны с числами вне long: JS считает в double**

Категория: number; серьёзность: low; решение: **исправить движок**; сложность: S; статус: воспроизводится 3/3.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$formatBase(1e22)` |  | "1e+22" | "9223372036854775807" | VALUE |
| `[1e20..5] ~> $count` |  | 0 | error T2003 | JVM_ERROR |
| `[1e22..2.5]` |  | error T2004@7 | error T2003 | ERROR_CODE |

### NUM-07

**`$sum` суммирует не так, как JS (не простое последовательное сложение double)**

Категория: number; серьёзность: low; решение: **исправить движок**; сложность: S; статус: воспроизводится 1/1.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$sum([0.1, 0.2, 0.3])` |  | 0.6000000000000001 | 0.6 | NUMBER_PRECISION |

### NUM-08

**Целые > 2^53 во входных данных и привязках: JVM хранит точный long, JS округляет до double**

Категория: number; серьёзность: low; решение: **согласованное исключение**; сложность: S; статус: см. evidence.

- Доказательство: cases/extensibility.jsonl: ext-input-large-int, ext-bind-large-int-string (в catalog.js такие числа не выразить: литерал JS уже округлён)
- Примечание: JVM точнее. Для эквивалентности хост может читать JSON в double (настройка парсера). Выражения переписывать не нужно

### NUM-09

**Отрицательный ноль: JS сохраняет -0 (`-0`, `0*-1`, `$floor(-0)`…), JVM даёт 0**

Категория: number; серьёзность: low; решение: **согласованное исключение**; сложность: S; статус: воспроизводится 3/3.

- Примечание: Видно только через JSON с -0 или 1/x; JSON.stringify(-0) = "0". Считать исключением

| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `-0` |  | -0 | 0 | NEG_ZERO |
| `0 * -1` |  | -0 | 0 | NEG_ZERO |
| `$number('-0')` |  | -0 | 0 | NEG_ZERO |

### NUM-10

**Бесконечность как результат: JS возвращает Infinity/NaN (`1/0`, `$eval("1/0")`, `x % 0`), JVM — ошибку D1001/D3121**

Категория: number; серьёзность: low; решение: **согласованное исключение**; сложность: S; статус: воспроизводится 3/3.

- Примечание: Результат JS не сериализуется в JSON. Ошибка в JVM полезнее; считать исключением

| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `1/0` |  | Infinity | error D1001@0 | JVM_ERROR |
| `$eval('1/0')` |  | Infinity | error D3121@6 | JVM_ERROR |
| `5 % 0` |  | NaN | undefined | VALUE |

### NUM-11

**Последние биты `$power`/`$sqrt` (разные libm V8 и Java)**

Категория: number; серьёзность: low; решение: **согласованное исключение**; сложность: S; статус: воспроизводится 1/1.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$power(0.30000000000000004, 3)` |  | 0.02700000000000001 | 0.027000000000000014 | NUMBER_PRECISION |

### STR-01

**`$substring` считает в UTF-16 (режет суррогатные пары; при отрицательном start — исключение), в JS — по кодовым точкам**

Категория: string; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 3/3.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$substring('a😀b😀c', 1, 3)` |  | "😀b😀" | "😀b\ud83d" | VALUE |
| `$substring('a😀b😀c', -2)` |  | "😀c" | crash java.lang.IndexOutOfBoundsException: java.lang.IndexOutOfBoundsException | JVM_CRASH |
| `$substring('😀', -3)` |  | "😀" | crash java.lang.IndexOutOfBoundsException: java.lang.IndexOutOfBoundsException | JVM_CRASH |

### STR-02

**URL-функции: `$encodeUrl` не кодирует пробел, `$decodeUrl*` превращает `+` в пробел, ошибки — исключения Java или код «Malformed URL» вместо D3140**

Категория: string; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$encodeUrl('a b')` |  | "a%20b" | "a+b" | VALUE |
| `$decodeUrlComponent('a+b')` |  | "a+b" | "a b" | VALUE |
| `$decodeUrl('%')` |  | error D3140@11 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern | JVM_CRASH |
| `$encodeUrlComponent('\ud800')` |  | error D3140@20 | error Malformed URL@0 | ERROR_CODE |

### STR-03

**`$base64encode/decode`: JS (Node) кодирует строку как latin1 (обрезая символы >255), JVM — как UTF-8; некорректный base64 — исключение Java**

Категория: string; серьёзность: low; решение: **решение за вами (см. «Вопросы»)**; сложность: S; статус: воспроизводится 3/3.

- Примечание: Для ASCII совпадает. Повторять поведение JS означает портить не-латинские данные

| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$base64encode('é')` |  | "6Q==" | "w6k=" | VALUE |
| `$base64decode('w6k=')` |  | "Ã©" | "é" | VALUE |
| `$base64decode('!!!')` |  | "" | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Illegal base64 character 21 | JVM_CRASH |

### STR-04

**`$string(x, true)`: пустой объект печатается как `{\n  }`; U+2028/U+2029 экранируются**

Категория: string; серьёзность: low; решение: **исправить движок**; сложность: S; статус: воспроизводится 2/2.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$string({'a':{}}, true)` |  | "{\n  \"a\": {}\n}" | "{\n  \"a\": {\n  }\n}" | VALUE |
| `$string(['x\u2028y'])` |  | "[\"x y\"]" | "[\"x\\u2028y\"]" | VALUE |

### RE-01

**Синтаксис regex: JVM передаёт шаблон в java.util.regex как есть: JS-синтаксис `[^]`, `[\b]`, `\0` падает, Java-only `a++`, `(?i)`, `\Z`, `\h`, `\R` принимается (в JS — SyntaxError)**

Категория: regex; серьёзность: medium; решение: **исправить движок**; сложность: M; статус: воспроизводится 5/5.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$replace('ab', /[^]/, 'x')` |  | "xx" | crash java.util.regex.PatternSyntaxException: java.util.regex.PatternSyntaxException: Unclosed character class near index 2\n[^]\n  ^ | JVM_CRASH |
| `$contains('a', /\0/)` |  | false | crash java.util.regex.PatternSyntaxException: java.util.regex.PatternSyntaxException: Illegal octal escape sequence near index 2\n\0 | JVM_CRASH |
| `$match('a\bb', /[\b]/)` |  | {"match":"\b","index":1,"groups":[]} | crash java.util.regex.PatternSyntaxException: java.util.regex.PatternSyntaxException: Illegal/unsupported escape sequence near index 2\n[\b] | JVM_CRASH |
| `$contains('aa', /a++/)` |  | crash SyntaxError: Invalid regular expression: /a++/g: Nothing to repeat | true | JS_CRASH |
| `$contains('ABC', /(?i)abc/)` |  | crash SyntaxError: Invalid regular expression: /(?i)abc/g: Invalid group | true | JS_CRASH |

### RE-02

**Семантика regex: `\s` (в JS включает NBSP, U+2028, BOM…), `$` перед финальным `\n`, регистронезависимость для не-ASCII, `.`/классы на суррогатных парах**

Категория: regex; серьёзность: medium; решение: **исправить движок**; сложность: M; статус: воспроизводится 5/5.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$replace('a\u00a0b', /\s/, '_')` |  | "a_b" | "a b" | VALUE |
| `$contains('abc\n', /c$/)` |  | false | true | VALUE |
| `$contains('é', /É/i)` |  | true | false | VALUE |
| `$match('a😀b', /./).match` |  | ["a","\ud83d","\ude00","b"] | ["a","😀","b"] | VALUE |
| `$replace('😀', /[😀]/, 'x')` |  | "xx" | "x" | VALUE |

### RE-03

**Совпадение нулевой длины: JS бросает D1004 в `$match`/`$replace`/`$split`, JVM возвращает результат (иногда с лишними пустыми элементами)**

Категория: regex; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 5/5.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$match('ab', /x*/)` |  | error D1004@7 | [{"match":"","index":0,"groups":[]},{"match":"","index":1,"groups":[]},{"match":"","index":2,"groups":[]}] | JVM_NO_ERROR |
| `$replace('abracadabra', /.*?/, '$1')` |  | error D1004@9 | "abracadabra" | JVM_NO_ERROR |
| `$split('ab', /x*/)` |  | error D1004@7 | ["a","b",""] | JVM_NO_ERROR |
| `$split('42', /.*/)` |  | ["",""] | ["","",""] | VALUE |
| `$match('aaa', /.*/)` |  | {"match":"aaa","index":0,"groups":[]} | [{"match":"aaa","index":0,"groups":[]},{"match":"","index":3,"groups":[]}] | VALUE |

### RE-04

**Объект совпадения при `str ~> /re/` и regex как функция: `groups` содержит всё совпадение, другой порядок ключей; `$map(arr, /re/)` падает; функции-матчеры не поддерживаются**

Категория: regex; серьёзность: medium; решение: **исправить движок**; сложность: M; статус: воспроизводится 3/3.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `'abc' ~> /b/` |  | {"match":"b","start":1,"end":2,"groups":[],"next":<function:native>} | {"end":2,"match":"b","groups":["b"],"next":<function:native>,"start":1} | VALUE |
| `$map(['a','b'], /a/)` |  | {"match":"a","start":0,"end":1,"groups":[],"next":<function:native>} | crash java.lang.ClassCastException: java.lang.ClassCastException: class java.util.regex.Pattern cannot be cast to class com.dashjoin.jsonata | JVM_CRASH |
| `$match('aXbX', function($s, $o){ $o = 0 ? {'match':'X','start':1,'end':2,'groups':[], 'next': function(){undefined}} })` |  | undefined | crash java.lang.Exception: java.lang.Exception: Argument error calling function match | JVM_CRASH |

### RE-05

**`$match(str, re, limit)`: limit игнорируется**

Категория: regex; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 1/1.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$match('a1b2c3', /\d/, 2).match` |  | ["1","2"] | ["1","2","3"] | VALUE |

### RE-06

**`$replace`: `$$` в строке замены — исключение; функция замены, вернувшая не строку, — NPE/ClassCast вместо D3012; коды D3010/D3011 — текстом; непойманные группы — null вместо undefined**

Категория: regex; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 5/5.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$replace('abc', /b/, '$$')` |  | "a$c" | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Illegal group reference: group index is missing | JVM_CRASH |
| `$replace('abc', /b/, function($m){42})` |  | error D3012@9 | crash java.lang.NullPointerException: java.lang.NullPointerException: Cannot invoke "String.length()" because "replacement" is null | JVM_CRASH |
| `$replace('abc', '', 'x')` |  | error D3010@9 | error Second argument of replace function cannot be an empty string@0 | ERROR_CODE |
| `$replace('abc', 'b', 'x', -1)` |  | error D3011@9 | error Fourth argument of replace function must evaluate to a positive number@0 | ERROR_CODE |
| `$match('b', /(a)\|(b)/).groups` |  | [undefined,"b"] | [null,"b"] | UNDEF_NULL |

### DT-01

**`$toMillis(str)` без шаблона разбирает через java.time: принимает `20180201`, отвергает `2018-02`, `2018-02-30` даёт 28 февраля вместо 2 марта; ошибка — DateTimeParseException вместо D3110**

Категория: date; серьёзность: medium; решение: **исправить движок**; сложность: M; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$toMillis('2018-02')` |  | 1517443200000 | crash java.time.format.DateTimeParseException: java.time.format.DateTimeParseException: Text '2018-02' could not be parsed at index 7 | JVM_CRASH |
| `$toMillis('20180201')` |  | error D3110@10 | 636763467110400000 | JVM_NO_ERROR |
| `$toMillis('2018-02-30')` |  | 1519948800000 | 1519776000000 | NUMBER |
| `$toMillis('foo')` |  | error D3110@10 | crash java.time.format.DateTimeParseException: java.time.format.DateTimeParseException: Text 'foo' could not be parsed at index 0 | JVM_CRASH |

### DT-02

**`$toMillis(str, picture)`: фиксированная ширина полей (не перенесён 2dd49c6), `[f]` меньше 3 цифр падает, ошибки шаблона — исключения Java вместо D3132–D3136**

Категория: date; серьёзность: medium; решение: **исправить движок**; сложность: M; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$toMillis('1022024', '[D1][M01][Y0001]')` |  | 1706745600000 | crash java.time.DateTimeException: java.time.DateTimeException: Invalid value for MonthOfYear (valid values 1 - 12): 22 | JVM_CRASH |
| `$toMillis('2026-04-08T19:05:04.5', '[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f1]')` |  | 1775675104500 | crash java.lang.StringIndexOutOfBoundsException: java.lang.StringIndexOutOfBoundsException: Range [0, 3) out of bounds for length 1 | JVM_CRASH |
| `$toMillis('2018-05-22', '[Y]-[M]-[q]')` |  | error D3132@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: Unknown component specifier q in date/time picture string | JVM_CRASH |
| `$toMillis('2018-22', '[Y]-[D]')` |  | error D3136@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: The date/time picture string is missing specifiers required to parse the times | JVM_CRASH |

### DT-03

**`$fromMillis`: `[X]` (ISO-год недели) выводится как `[Y]`, `[w]`/`[x]` (неделя месяца) считаются иначе**

Категория: date; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 2/2.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$fromMillis($toMillis('2005-01-01'), '[X0001]-W[W01]-[F1]')` |  | "2004-W53-6" | "2005-W53-6" | VALUE |
| `$fromMillis(1359720000000, 'Week: [w] of [xNn]')` |  | "Week: 5 of January" | "Week: 0 of February" | VALUE |

### DT-04

**`$fromMillis`: ошибки шаблона (D3133–D3135) — исключения Java; часовой пояс не в формате ±HHMM (`Z`, `EST`, `+01:00`) — NumberFormatException**

Категория: date; серьёзность: low; решение: **исправить движок**; сложность: S; статус: воспроизводится 3/3.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$fromMillis(0, '[YN]-[M]-[D]')` |  | error D3133@12 | crash java.lang.RuntimeException: java.lang.RuntimeException: The 'name' modifier can only be applied to months and days in the date/time pi | JVM_CRASH |
| `$fromMillis(0, '[Y]', 'Z')` |  | "NaN" | crash java.lang.NumberFormatException: java.lang.NumberFormatException: For input string: "Z" | JVM_CRASH |
| `$fromMillis(0, '[H]', '+01:00')` |  | "0" | crash java.lang.NumberFormatException: java.lang.NumberFormatException: For input string: "+01:00" | JVM_CRASH |

### DT-05

**Отрицательный часовой пояс с минутами (`-0530`): JS сдвигает на час больше (-06:30), это ошибка JS; JVM считает верно**

Категория: date; серьёзность: low; решение: **согласованное исключение**; сложность: S; статус: воспроизводится 1/1.

- Примечание: Затрагивает только пояса вроде -03:30 (Ньюфаундленд), -09:30. Повторять ошибку JS не рекомендуется

| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$fromMillis(0, '[H01]:[m01] [Z]', '-0530')` |  | "17:30 -06:30" | "18:30 -05:30" | VALUE |

### ORD-01

**Порядок ключей вида целого числа (`"1"`, `"10"`): объекты JS ставят их первыми по возрастанию (и во входном JSON, и в создаваемых объектах), JVM сохраняет порядок вставки. Видно в `$keys`, `$each`, `$spread`, `$string`, `.*`, группировке `{}`**

Категория: order; серьёзность: medium; решение: **решение за вами (см. «Вопросы»)**; сложность: M; статус: воспроизводится 5/5.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `{'b':1,'2':2,'1':3}` |  | {"1":3,"2":2,"b":1} | {"b":1,"2":2,"1":3} | KEY_ORDER |
| `$keys({'b':1,'2':2,'1':3})` |  | ["1","2","b"] | ["b","2","1"] | VALUE |
| `items{k: n}` | {"items":[{"k":"b","n":1},{"k":"2","n":2},{"k":"1","n":3}]} | {"1":3,"2":2,"b":1} | {"b":1,"2":2,"1":3} | KEY_ORDER |
| `$string({'b':1,'1':2})` |  | "{\"1\":2,\"b\":1}" | "{\"b\":1,\"1\":2}" | VALUE |
| `{'b':1,'1':2}.*` |  | [2,1] | [1,2] | NUMBER |

### SORT-01

**`$sort` по умолчанию падает с ClassCastException на смеси Integer/Double (`[2, 0.5]`) и вместо D3070 на объектах/смеси типов**

Категория: sort; серьёзность: high; решение: **исправить движок**; сложность: S; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `[2, 0.5] ~> $sort` |  | [0.5,2] | crash java.lang.ClassCastException: java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.Double (java.lan | JVM_CRASH |
| `$sort(n)` | {"n":[3,1.5,2]} | [1.5,2,3] | crash java.lang.ClassCastException: java.lang.ClassCastException: class java.lang.Long cannot be cast to class java.lang.Double (java.lang.L | JVM_CRASH |
| `$sort([1,'a'])` |  | error D3070@6 | crash java.lang.ClassCastException: java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.String (java.lan | JVM_CRASH |
| `$sort([{'a':1},{'a':2}])` |  | error D3070@6 | crash java.lang.ClassCastException: java.lang.ClassCastException: class java.util.LinkedHashMap cannot be cast to class java.lang.Comparable | JVM_CRASH |

### SORT-02

**`$sort` с компаратором, возвращающим true на равных (`>=`, константа): другой порядок, чем у merge sort из JS**

Категория: sort; серьёзность: medium; решение: **исправить движок**; сложность: S; статус: воспроизводится 2/2.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$sort(items, function($l,$r){$l.k >= $r.k}).n` | {"items":[{"k":2,"n":"a"},{"k":1,"n":"b"},{"k":2,"n":"c"},{"k":1,"n":"d"},{"k":3,"n":"e"},{"k":1,"n":"f"}]} | ["f","d","b","c","a","e"] | ["b","d","f","a","c","e"] | VALUE |
| `$sort([3,1,2], function($l,$r){true})` |  | [2,1,3] | [3,1,2] | NUMBER |

### SORT-03

**Сортировка на несравнимых значениях: код (T2007 смесь типов / T2008 не число и не строка) зависит от того, какая пара сравнивается первой, то есть от алгоритма сортировки (см. SORT-02)**

Категория: sort; серьёзность: low; решение: **исправить движок**; сложность: S; статус: воспроизводится 1/1.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `items^(k)` | {"items":[{"k":1},{"k":"9"},{"k":null},{"k":"10"},{"k":2},{"n":"nok"}]} | error T2008@6 | error T2007@6 | ERROR_CODE |

### SEQ-01

**`$distinct` от массива с одним уникальным значением возвращает скаляр вместо массива**

Категория: sequence; серьёзность: low; решение: **исправить движок**; сложность: S; статус: воспроизводится 1/1.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$distinct([1,1])` |  | [1] | 1 | SINGLETON |

### ERR-01

**Нет позиции (0 или пусто) у ошибок времени выполнения T1003, T1006, T2001–T2004, T2009, T2010, D1002, D1009, T0412…**

Категория: error; серьёзность: low; решение: **исправить движок**; сложность: M; статус: воспроизводится 4/4.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `items{k: n}` | {"items":[{"k":1,"n":"a"}]} | error T1003@6 | error T1003@0 | ERROR_POSITION |
| `($x := 1; $x())` |  | error T1006@13 | error T1006@0 | ERROR_POSITION |
| `null + 1` |  | error T2001@6 | error T2001 | ERROR_POSITION |
| `{'x': 1, 'x': 2}` |  | error D1009@1 | error D1009@0 | ERROR_POSITION |

### ERR-02

**Другой код ошибки: `$number("10e500")` D1001 вместо D3030, `$string(1/0)` D1001 вместо D3001, частичное применение T1008 вместо T1007, `$type(age) = …` «Unexpected» вместо D3030**

Категория: error; серьёзность: low; решение: **исправить движок**; сложность: S; статус: воспроизводится 3/3.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `$number('10e500')` |  | error D3030@8 | error D1001@0 | ERROR_CODE |
| `$string(1/0)` |  | error D3001@8 | error D1001@0 | ERROR_CODE |
| `substring(?, 0, ?)` |  | error T1007@10 | error T1008@10 | ERROR_CODE |

### ERR-03

**Структура ошибки: у JException нет раздельных `token`/`value` (одно поле `current`), сообщения отличаются кавычками; «Expected ) before end» — token `)` вместо `(end)`**

Категория: error; серьёзность: low; решение: **решение за вами (см. «Вопросы»)**; сложность: S; статус: воспроизводится 2/2.


| пример | вход | JS 2.2.2 | JVM | категория |
|---|---|---|---|---|
| `foo(` |  | error S0203@4 | error S0203@4 | ERROR_TOKEN |
| `$uppercase(1)` |  | error T0410@11 | error T0410@11 | ERROR_TOKEN |
