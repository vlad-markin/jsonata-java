# Проба API хоста (JVM-движок)

Сгенерировано `compat.api.ApiProbe`. Колонка JS описывает поведение jsonata-js 2.2.2 при эквивалентном использовании API.

| id | проверка | JVM (наблюдение) | jsonata-js 2.2.2 | итог |
|---|---|---|---|---|
| API-1 | одно выражение, 3× `evaluate(input)` без Frame: `$y := ($exists($y) ? $y+1 : 1)` (присваивание на верхнем уровне) | 1, 2, 3 | 1, 1, 1 | **расходится** |
| API-1b | то же, но с `evaluate(input, createFrame())` | 1, 1 | 1, 1 | совпадает |
| API-2 | 8 потоков × 400 `evaluate` одного выражения `[1..200].($$.id) ~> $distinct()` без Frame | неверных 191, исключений 0 из 400 | каждый вызов изолирован (в JS исправлено в 2.2.2, df63a7c) | **расходится** |
| API-2b | 8 потоков × 400 `evaluate` одного выражения `[1..200].($$.id) ~> $distinct()` с Frame | неверных 0, исключений 0 из 400 | каждый вызов изолирован (в JS исправлено в 2.2.2, df63a7c) | совпадает |
| API-3 | Java-типы аргументов функции хоста для `null, nothing, 1, 1.5, Long 7, [..], a.b, {..}, function, /re/, Long 2^53+1` | Jsonata.NULL_VALUE, null, Integer, Double, Long, ImmutableCollections$List12, Utils$JList, LinkedHashMap, Parser$Symbol, regex.Pattern, Long | JS: null, undefined, number×3, Array, Array(sequence), Object, lambda-объект, RegExp, number(округлён) | **расходится** |
| API-4 | хост возвращает Java `null` / `Jsonata.NULL_VALUE`; выражение `$h() = null` | false / true | в JS `null` → JSON null, `undefined` → undefined; true | **расходится** |
| API-5 | хост вызывает переданную лямбду через `Functions.funcApply` в потоке вычисления | 42 | 42 (`fn.apply(this,[x])`) | совпадает |
| API-5b | хост вызывает лямбду из **другого потока** (executor) во время вычисления | EXC RuntimeException: java.util.concurrent.ExecutionException: java.lang.NullPointerException: Cannot read field "environment" because the return value of "java.lang.ThreadLocal.get()" is null | 42 (в JS однопоточно; Promise) | **расходится** |
| API-5c | лямбда, возвращённая как результат, вызывается хостом **после** `evaluate` (тот же поток) | 42 (тип результата: Parser$Symbol) | 42 (`result.apply(null,[21])`) | совпадает |
| API-5d | та же лямбда вызывается после `evaluate` из другого потока | EXC NullPointerException: Cannot read field "environment" because the return value of "java.lang.ThreadLocal.get()" is null | 42 | **расходится** |
| API-6 | исключение хоста: unchecked / checked | EXC IllegalStateException: bad / EXC RuntimeException: java.io.IOException: io | исключение пробрасывается как есть (JS добавляет position к объекту ошибки) | совпадает |
| API-7 | один Frame с `setRuntimeBounds(300ms, 1000)` используется для двух `evaluate` с паузой 400 мс | 1000 / EXC JException: JSonataException Expression evaluation timeout: Check for infinite loop | в JS timebox отсчитывается от начала вычисления (тестовый харнесс), ошибка U1001 | **расходится** |
| API-8 | `setRuntimeBounds` + свой entry-колбэк на одном Frame | 1 (колбэк замещает Timebox: лимиты отключены) | в JS колбэки тоже одна переменная окружения | совпадает |
| API-9 | `registerFunction` на скомпилированном выражении глобален для всех последующих вызовов | A, B | A, B (так же) | совпадает |
| API-10 | Java-лямбда `Fn1` без сигнатуры получает аргумент `null` | Jsonata.NULL_VALUE | null | **расходится** |
| API-11 | Java-типы чисел в результате `[1, 1.0, 2.5, 1/2*2, $sum([1,2]), 1e10, 9007199254740993, a.b]` | Integer:1, Integer:1, Double:2.5, Integer:1, Integer:3, Long:10000000000, Long:9007199254740992, Integer:1; класс списка Utils$JList | JS: только number (double); 9007199254740993 → 9007199254740992 | **расходится** |
