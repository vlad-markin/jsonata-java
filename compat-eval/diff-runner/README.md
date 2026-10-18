# diff-runner

Сравнивает jsonata-js 2.2.2 (Node, npm-пакет из `compat-eval/package.json`) с JVM-движком на
одних и тех же случаях. Формат входа и выхода — JSONL, поэтому закрытый корпус можно прогнать локально.

## Запуск

```bash
cd compat-eval
npm ci                                  # jsonata@2.2.2
(cd jvm && mvn -q compile)              # JVM side + engine sources from ../../src/main/java
node diff-runner/js/run.js --cases my-cases.jsonl --out out/my-run [--timeout 20000]
# другой JVM-движок: класс, реализующий compat.engine.Engine
JVM_EXTRA_CP=/path/to/engine.jar node diff-runner/js/run.js --cases ... --out ... --engine my.pkg.MyEngine
```

Результаты в `--out`: `js.jsonl`, `jvm.jsonl` (сырые результаты), `diff.jsonl` (сравнение по
случаям), `summary.json`, `summary.md`. Каждый движок работает в отдельном процессе под
супервизором: если случай не отвечает за `--timeout` мс, процесс убивается, случаю ставится
`timeout`, и работа продолжается со следующего.

JVM-сторону можно запускать и без Node:
`java -cp jvm/target/classes compat.diff.RunCases cases.jsonl out.jsonl`.

## Формат случая (вход, одна строка JSON)

| поле | смысл |
|---|---|
| `id` | строка, уникальная |
| `expr` | выражение JSONata |
| `input` | входной документ; **отсутствие поля = undefined** (не null) |
| `bindings` | `{имя: значение}` (без `$`). Специальные значения: `{"$expr": "<jsonata>"}` — вычислить выражение на том же движке и привязать результат (например, лямбду); `{"$host": "<impl>"}` — функция хоста без сигнатуры; `{"$undefined": true}` |
| `functions` | `[{"name","impl","signature"}]` — регистрация функций хоста (`registerFunction`) с сигнатурой |
| `options` | опции компиляции jsonata-js 2.2: `timeout`, `stack`, `sequence` |
| `bounds` | `{"timelimit": ms, "depth": n}` — как в test-suite (JS: `timeboxExpression`, JVM: `Frame.setRuntimeBounds`) |
| `expected` | необязательно: ожидание из test-suite (`result`, `undefinedResult`, `code`, `token`, `position`) |
| `tags` | необязательно: метки для группировки статистики |

Функции хоста (`impl`) реализованы одинаково в `js/hostfns.js` и `jvm/.../HostFunctions.java`:
`echo`, `upper`, `concat`, `add`, `typeOf`, `argCount`, `argTypes`, `asyncEcho`/`asyncUpper`
(в JS возвращают Promise, в JVM блокируют поток), `apply`/`apply2`/`mapAll` (хост вызывает
переданную лямбду), `contextInput`, `throwError`, `makeObject`.

## Формат результата

`{"id","engine","status":"ok|error|crash|timeout|died","phase":"compile|evaluate|setup","result":<canon>,"error":{code,position,token,message,exception},"us":<микросекунды>}`

`crash` — исключение, которое не является ошибкой JSONata (в JVM, например, NPE или
ClassCastException; в JS — ошибка без `code`).

**Каноническое типизированное дерево** (`result`) снимает неоднозначности JSON:
`null` | `true` | `false` | `"строка"` (всё не-ASCII экранировано UTF-16 кодами) |
`["n", лексема, javaType?]` — число, лексема по правилам ECMAScript `Number#toString`, `-0` сохраняется;
JVM дописывает Java-тип (`L`, `I`, `D`, `BD`…) |
`["a",[…]]` — массив, `["s",[…]]` — массив с флагом последовательности (sequence) |
`["o",[[ключ, значение],…]]` — объект с сохранённым порядком ключей | `["u"]` — undefined |
`["f", вид]` — функция | `["x", описание]` — прочее.

## Нормализация и категории расхождений

Сравнение (`js/compare.js`) выдаёт набор видов расхождений; главная категория — самая серьёзная из них:

| категория | смысл |
|---|---|
| `MATCH` | эквивалентно |
| `JVM_TIMEOUT` / `JS_TIMEOUT` / `BOTH_TIMEOUT` | зависание (по супервизору) |
| `JVM_CRASH` | JVM бросил не-JSONata исключение (JS — значение или ошибку JSONata) |
| `JVM_ERROR` | JS вернул значение, JVM — ошибку JSONata |
| `JVM_NO_ERROR` | JS — ошибка, JVM — значение |
| `ERROR_CODE` | оба вернули ошибку, коды разные |
| `VALUE` | структурно разные значения |
| `NUMBER` | числа отличаются существенно (например, большие целые: JVM хранит точный `long`, JS — double) |
| `UNDEF_NULL` | undefined против null |
| `SINGLETON` | массив из одного элемента против скаляра |
| `NUMBER_PRECISION` | числа отличаются в пределах 1e-12 относительно |
| `NEG_ZERO` | `-0` против `0` |
| `KEY_ORDER` | одинаковые объекты, разный порядок ключей (сравнивается отдельно, может быть значимым) |
| `ERROR_POSITION`, `ERROR_TOKEN` | одинаковый код, разные позиция или токен |
| `CRASH_BOTH`, `JS_CRASH` | не-JSONata исключение в обоих движках или только в JS |

Справочные пометки (`info`) не считаются расхождением: `JAVA_DOUBLE_INTEGRAL` (JVM вернул `Double`
с целым значением, что важно Java-хосту при сериализации), `SEQ_FLAG`, `ERROR_MESSAGE`, `FUNCTION_KIND`.

Ключи объекта со значением undefined считаются отсутствующими. Входные числа на JVM: целое,
помещающееся в `long`, становится `Long`, остальные — `Double`, как в типичной настройке Jackson.
