# Фаза 5б. Управление вычислением: отмена, бюджеты, потоки, хост-функции

Пробы: `bench/jvm/src/main/java/compat/bench/ControlProbes.java`, запуск `bench/run-probes.sh`.
Полный вывод с таблицей: [results/control-probes.txt](results/control-probes.txt). Ссылки вида
**[C2]** указывают на строки этой таблицы. Накладные расходы: [performance.md](performance.md#накладные-расходы-средств-контроля-jvm-10k-чередование-modescsv).
Окружение: JDK 21.0.12, 4 vCPU.

## Итог

| возможность | есть? | как работает в форке | главные проблемы |
|---|---|---|---|
| внешняя отмена | **нет** | `Thread.interrupt()` ни на что не влияет [C1]. Остаётся бросать исключение из entry-колбэка [C2] | исключение хоста подменяется в `and`/`or` (`JException "Unexpected"`), `$sort` (`RuntimeException`-обёртка), `$replace` с функцией (проглатывается, затем NPE), `$eval` (D3121) [C3b–C3i] |
| дедлайн | частично | `Options.timeout` → D1012 [T1]. `Frame.setRuntimeBounds` → Timebox | проверка только на входе в узел, блокирующую хост-функцию не прерывает [T3]; D1012 внутри `$sort` и `$eval` приходит обёрнутым или заменённым [T4]; часы Timebox идут с момента `setRuntimeBounds()`, а не `evaluate()` [B1]; код ошибки Timebox — текст сообщения, не U1001 [B2] |
| глубина рекурсии | частично | `Options.stack` (D1011), Timebox `maxDepth` | Timebox теряет счётчик глубины после исключения при повторном использовании `Frame` [B4] и неверен при общем `Frame` между потоками [B5] |
| бюджет памяти | **нет** | только `Options.sequence` (D2015) для диапазонов, путей и `$map` [M2] | `$append` лимит не учитывает (JS 2.2.2 учитывает) [M2]; размер строк, объектов и результата не ограничен [M1] |
| колбэки entry/exit | есть | на каждый узел AST (79–119 тыс. вызовов на запрос по 10k [K1]), no-op почти бесплатен | хранятся как обычные переменные `__evaluate_entry/exit`: перетирают друг друга с `setRuntimeBounds` [B3]; выражение само может отключить Timebox [B6] или уронить вычисление `ClassCastException` [B6b] |
| конкурентное использование одного выражения | только с `Frame` | с `Frame` на каждый вызов: корректно [S3] | без `Frame` — неверные результаты (12% из 20 тыс. на 8 потоках) и утечка `:=` между вызовами [S1, S3] |
| виртуальные потоки | да | 10 тыс. одновременных вычислений корректны [V1]; сам движок carrier-потоки не пиннит [V2] | `ThreadLocal`-клон и новый `Parser` на каждый новый поток: +9 мкс на первый `evaluate` [V3]; `$now()/$millis()` «замерзают» на переиспользуемых потоках [S4] |
| блокирующие хост-функции | да | синхронные; на виртуальных потоках блокировка дешёвая [V2] | нет асинхронной модели (в JS 2.x хост-функция может вернуть Promise); тайм-аут не прерывает [T3] |
| структурированные ошибки | частично | `JException` (`getError`, `getLocation`, `getCurrent`) | Timebox кладёт текст в поле кода [B2]; хостовые и Java-исключения (NPE, ClassCastException, RuntimeException-обёртки) выходят наружу как есть [T2, C3, B6b] |

## Подробности с доказательствами

### Внешняя отмена

* **[C1]** `Thread.interrupt()` через 100 мс после старта: вычисление идёт до конца (1,9 с, база
  1,5 с), флаг прерывания остаётся выставленным, исключения нет. Движок нигде не проверяет
  `Thread.interrupted()`.
* **[C2]** Рабочий способ: `Frame.setEvaluateEntryCallback` проверяет флаг и бросает своё
  `RuntimeException`. Задержка ~150 мкс, наружу приходит исходное исключение. Так же доходят до
  вызывающего исключения из тела пути, из `$map`, `$filter`, группировки и из самой хост-функции [C3a, C3e, C3f, C3h, C3j].
* **Где исключение хоста теряется или подменяется:**
  * **[C3b]** правая часть `and`/`or`: `evaluateBinary` превращает любое не-`JException` в
    `JException("Unexpected")`, тип и причина теряются (`Jsonata.java`, ветка `and/or`);
  * **[C3c, C3i]** компаратор `$sort`: `Functions.sort` оборачивает любой `Throwable` в
    `RuntimeException` (то же для D1012 [T4]);
  * **[C3d]** функция-замена в `$replace`: `safeReplaceAllFn` ловит `Throwable`, печатает stack trace
    в stderr, подставляет `null`, и вызывающий получает `NullPointerException`;
  * **[C3g]** тело `$eval`: любое исключение превращается в `D3121`. Тайм-аут внутри `$eval` тоже [T4].
* Вывод: отмену через колбэк можно строить, но вызывающий код должен распознавать своё исключение
  и через цепочку `getCause()`, и по `JException("Unexpected")`, и по NPE после `$replace`. Надёжно
  это сделать нельзя: после `$replace` вычисление продолжается, пока не дойдёт до следующего узла.
  Обходной путь — дополнительно проверять флаг отмены после возврата из `evaluate`.

### Бюджет времени

* **`Options.timeout` [T1]** срабатывает точно (300 мс → 303 мс, D1012). Время отсчитывается от
  начала `evaluate`. Проверка `Frame.guardrails()` выполняется при каждом входе в узел.
* **[T2] NPE:** если задан `timeout` или `stack`, но не `sequence`, любой диапазон `[a..b]` падает с
  `NullPointerException` в `evaluateRangeExpression`: `size > environment.base.options.sequence`
  распаковывает `null`, Jsonata.java ~1259. `createSequence` проверяет `null` корректно, а здесь
  проверки нет. Исправление: `options.sequence != null && size > options.sequence`.
* **[T3]** Дедлайн не вытесняет: хост-функция спит 1,5 с при тайм-ауте 200 мс, и D1012 приходит
  через 1,5 с, при входе в следующий узел.
* **`Frame.setRuntimeBounds` (Timebox):**
  * **[B1]** время начинается в момент вызова `setRuntimeBounds()` (поле `time` инициализируется в
    конструкторе Timebox), а не в начале `evaluate`. Если `Frame` подготовлен заранее или
    переиспользуется, «тайм-аут» срабатывает на `1 + 1`.
  * **[B2]** код ошибки: `new JException("Expression evaluation timeout…", -1)`, поэтому
    `getError()` возвращает весь текст сообщения. В JS это `{code: "U1001"}`. Три теста
    `tail-recursion` в строгом прогоне фазы 1 падают по той же причине.
  * **[B3]** `setRuntimeBounds` и пользовательские entry/exit-колбэки пишут в одни и те же
    переменные (`__evaluate_entry`/`__evaluate_exit`). Выигрывает последний: колбэк после bounds
    отключает тайм-аут, bounds после колбэка отключают колбэк (0 вызовов). Мониторинг и лимит
    совместить нельзя.
  * **[B4]** глубина считается `depth++` во входном и `depth--` в выходном колбэке. При исключении
    выходной колбэк не вызывается, и глубина «утекает». После 30 неудачных вычислений с тем же
    `Frame` тривиальное выражение падает «Stack overflow … Depth=69 max=50».
  * **[B5]** общий bounds-`Frame` для нескольких потоков: счётчик `depth` (обычный `int`) общий, и
    3955 из 4000 вычислений ложно падают по глубине.
  * **[B6]** колбэки — обычные строковые переменные, и выражение может их затенить:
    `($__evaluate_entry := $undefinedVar; …)` отключает Timebox до выхода из блока. Цикл с лимитом
    100 мс отработал 4,9 с без проверок. `Options.timeout` так не обойти. **[B6b]**
    `$__evaluate_entry := 1` роняет вычисление с `ClassCastException`. В JS ключи — `Symbol.for('jsonata.__evaluate_entry')`,
    из выражения они недоступны (`jsonata.js:3513`).
* Стоимость: `setRuntimeBounds` ×1,1–1,9, `Options` ×1,2–1,55 на наших запросах. Основная цена —
  `System.currentTimeMillis()` (~25 нс) и распаковка `Integer` на каждом узле. Полезную работу
  на узел составляет 50–100 нс.

### Бюджет памяти

* Есть только `Options.sequence` [M2]: диапазон > лимита, пути (`[1..40].([1..40])`) и результат
  `$map` дают D2015. `$append([1..600],[1..600])` при лимите 1000 возвращает 1200 (JS 2.2.2
  бросает D2015: не перенесён коммит 91532d4, см. фазу 1). Строки и объекты не ограничены.
* **[M1]** при всех заданных `Options` выражение удвоением строит строку длиной 33,5 млн символов
  (~64 МБ) за 137 мс. Пятью шагами больше — OOM всего JVM, а не ошибка вычисления.
  `OutOfMemoryError` в общем JVM бьёт по всем запросам сервиса.
* Нет ни учёта аллокаций, ни лимита размера результата, ни лимита числа шагов.

### Потокобезопасность и повторное использование выражения

* **[S1]** без `Frame` все вызовы `evaluate` работают в одном `environment` скомпилированного
  выражения. `$y := ($exists($y) ? $y + 1 : 1)` дважды даёт `1, 2` (JS: `1, 1`). Утёкшее `$y=2`
  видно и вызовам с `Frame`, потому что `Frame` наследуется от общего окружения: они дают `3, 3`.
  Это не перенесённый upstream-коммит df63a7c (фаза 1).
* **[S3]** одно выражение, 8 потоков, 20 тыс. вызовов с разным входом
  (`id & ':' & $$.id & ':' & $count(items)`):
  * **без `Frame`: 2423 неверных результата.** `exec_env.bind("$", input)` пишет корень документа в
    общий `LinkedHashMap`, и `$$` видит чужой вход. Возможна и порча самой `LinkedHashMap`;
  * **с `Frame` на каждый вызов: 0 ошибок.**
* Правило для хоста: **всегда `expr.evaluate(input, expr.createFrame())`**, хост-функции
  регистрировать через `frame.bind(...)` (или до первого конкурентного использования через
  `registerFunction`), один `Frame` не делить между потоками.
* **[S4]** `$now()`/`$millis()` читают `Jsonata.current.get().timestamp`, то есть поле клона
  выражения для текущего потока. `evaluate` обновляет `timestamp` у исходного объекта, а не у
  клона. На любом потоке, кроме того, где выражение компилировалось, время «замерзает»: два вызова
  с интервалом 300 мс вернули одно и то же значение, и другое выражение на том же потоке тоже.
  В пуле потоков `$now()` возвращает время первого использования потока. Это ошибка корректности.
* Кроме того, `Jsonata.current` хранит **один** экземпляр на поток (последний скомпилированный
  или первый использованный), а не один на выражение. Конструктор `Jsonata` делает
  `current.set(this)`, и `$eval` компилирует новое выражение, подменяя `current` посреди вычисления.

### Виртуальные потоки (JDK 21)

* **[V1]** 10 000 одновременных вычислений (каждое с `Frame` и своим `$x`) на
  `newVirtualThreadPerTaskExecutor`: все результаты верны, 166 мс.
* **[V2]** хост-функция с `Thread.sleep(10)`, 3 вызова на вычисление, 10 000 виртуальных потоков:
  559 мс (последовательно было бы 300 с), 0 событий `jdk.VirtualThreadPinned` (JFR, порог 0).
  Значит, `synchronized` в движке (`getPerThreadInstance`, `getParser`) на пути блокировки не
  стоит. Контроль: та же хост-функция, но со `sleep` внутри собственного `synchronized` хоста, даёт
  29 761 событие пиннинга и 9,6 с, так что детектор работает. Стек пиннинга проходит через
  `Jsonata.applyInner → Functions.map`, то есть хост-код вызывается без мониторов движка.
* **[V3]** цена `ThreadLocal`-модели: первый `evaluate` на новом потоке создаёт клон `Jsonata` и
  новый `Parser` (через `static synchronized getParser()` и `ThreadLocal<Parser>`). Это +9 мкс
  на вычисление при модели «виртуальный поток на запрос», против 0,6 мкс на само вычисление
  `a + 1`. Для тяжёлых запросов несущественно, для миллионов мелких — заметно. Объекты живут, пока
  жив поток; на пуле платформенных потоков `current` удерживает последний клон (и его окружение)
  бессрочно.
* Пиннинг возможен только из хост-кода (`synchronized` + блокировка). На JDK 21 это проблема
  хоста, начиная с JDK 24 (JEP 491) — нет.

### Блокирующие хост-функции

* Регистрируются синхронно (`registerFunction`/`Frame.bind` с `Fn0..Fn7`, `JFunction`). Все
  вызовы последовательны: `$map` не распараллеливает. Асинхронной модели нет. В jsonata-js 2.x
  `evaluate` асинхронный, и хост-функция может вернуть Promise; в Java её аналог —
  блокирующий вызов на виртуальном потоке.
* Хост-функция не получает ни контекста отмены, ни дедлайна. Прервать её может только хост
  (`Thread.interrupt()` разбудит `sleep`/IO в самой функции, но движок флаг не проверит [C1]).
* Исключение из хост-функции доходит неизменным, кроме `$sort`, `and/or`, `$replace` и `$eval` [C3i].

## Чего не хватает

1. Токена кооперативной отмены, который проверяется движком (а не колбэком в пространстве имён переменных).
2. Абсолютного дедлайна, привязанного к началу вычисления, который передаётся и в хост-функции.
3. Бюджетов: число шагов (узлов), глубина, длина последовательности, длина строки, размер
   результата и/или учёт аллокаций, размер объекта.
4. Единой модели ошибок: стабильные коды (`U1001`/`D1011`/`D1012`/…, новый код для отмены), без
   обёрток и подмен по пути, без NPE/ClassCastException наружу.
5. Изоляции вычислений по умолчанию: свежее окружение на каждый вызов, как в JS после df63a7c.
6. Асинхронной или явно блокирующей модели хост-функций с контекстом вычисления.
7. Наблюдаемости без конфликта с лимитами: отдельные слоты для колбэков мониторинга и для лимитов.

## Предлагаемый контракт (не зависит от движка)

Контракт описывает, что хост ожидает от любого JVM-движка JSONata. Реализовать его можно и в этом
форке, и в другом движке.

```java
/** Immutable per-evaluation settings; a fresh EvaluationContext per evaluate() call. */
public interface EvaluationContext {
    CancellationToken cancellation();        // cooperative; checked by the engine
    Instant deadline();                      // absolute; null = none
    Budgets budgets();                       // limits below
    Map<String, Object> bindings();          // variables + host functions for this call only
    EvaluationListener listener();           // optional observability, never used for limits
}

public interface CancellationToken {
    boolean isCancelled();
    static CancellationToken of(java.util.concurrent.Future<?> f) { return f::isCancelled; }
    static CancellationToken ofThreadInterrupt() { return () -> Thread.currentThread().isInterrupted(); }
}

public record Budgets(
    long maxSteps,          // AST node evaluations (deterministic replacement of wall-clock in tests)
    int  maxDepth,          // evaluation stack depth
    int  maxSequence,       // any sequence/array produced (ranges, paths, $append, $map, ...)
    int  maxStringLength,   // any string produced
    int  maxObjectKeys,     // any object produced
    long maxResultBytes     // estimated size of the final result (or allocation estimate)
) {}

/** Engine-neutral result: value or structured error, never a raw Java exception. */
public sealed interface EvaluationResult {
    record Ok(Object value) implements EvaluationResult {}
    record Failed(EvaluationError error) implements EvaluationResult {}
}
public record EvaluationError(
    Kind kind,              // EXPRESSION_ERROR, TYPE_ERROR, CANCELLED, DEADLINE, BUDGET, HOST_ERROR, INTERNAL
    String code,            // JSONata code: T2001, D1012, U1001, D2015, ... ; "X0001" for CANCELLED etc.
    String message,
    int position, String token,
    String budget,          // which budget was exceeded (for BUDGET)
    Throwable hostCause     // original host exception for HOST_ERROR / CANCELLED, unwrapped
) {}

/** Host functions: synchronous, may block (run evaluations on virtual threads), get the context. */
@FunctionalInterface
public interface HostFunction {
    Object call(HostCall call) throws Exception;
}
public interface HostCall {
    List<Object> args();
    Object focus();                         // context item
    EvaluationContext context();            // deadline + cancellation to pass to IO (e.g. HTTP timeout)
}
```

**Семантика:**
* Движок проверяет `cancellation`, `deadline` и `maxSteps` на входе в узел. Дешёвый вариант:
  счётчик шагов на каждом узле, а часы и токен раз в 1024 шага. Тогда стоимость ~1 нс на узел
  против 25–100 нс сейчас.
* Перед вызовом хост-функции и после него движок проверяет отмену и дедлайн. Хост-функции
  передают `context().deadline()` в свои IO-тайм-ауты.
* Хост-функции блокирующие. Модель исполнения — «одно вычисление = один виртуальный поток».
  Движок гарантирует отсутствие `synchronized` и мониторов на пути вызова хост-функции (проверяется
  пробой V2 в CI). Асинхронный вариант (`CompletableFuture<Object>`) оборачивается хостом в
  блокирующий `join()` на виртуальном потоке; отдельная async-модель в движке не нужна.
* `Thread.interrupt()` потока вычисления эквивалентен отмене (`CancellationToken.ofThreadInterrupt()` по умолчанию).
* Исключения хоста и отмена не оборачиваются и не подменяются ни в одной встроенной функции
  (`$sort`, `$replace`, `$eval`, `and/or`, …). Наружу выходит `EvaluationError` с `hostCause`.
* Скомпилированное выражение неизменяемо и потокобезопасно. Каждое вычисление получает свежее
  окружение. `$now()` берётся из контекста вычисления, а не из `ThreadLocal`.
* Слушатель (`EvaluationListener.onEnter/onExit`) только наблюдает. Лимиты им не реализуются, и
  конфликт [B3] невозможен.

### Как реализовать в этом форке (оценка)

| шаг | изменение | объём |
|---|---|---|
| 1 | исправить NPE в диапазоне (`options.sequence != null`), U1001 в Timebox, время Timebox от начала `evaluate` | десятки строк |
| 2 | поля `cancellation`, `deadline`, `steps` в базовом `Frame`; одна проверка в `_evaluate` (счётчик + часы раз в N шагов), `Thread.interrupted()` | ~50 строк, без смены архитектуры |
| 3 | перестать подменять исключения: `evaluateBinary` (`and/or`), `Functions.sort` (пробрасывать RuntimeException как есть), `safeReplaceAllFn` (не глотать), `functionEval` (пробрасывать служебные ошибки) | ~30 строк |
| 4 | колбэки хранить в полях `Frame`, а не в именованных переменных: снимает B3 и B6 и даёт ускорение patch B | ~30 строк |
| 5 | `evaluate()` без `Frame` создаёт свежее окружение (перенос df63a7c); `timestamp` — поле `Frame`, а не клона в `ThreadLocal` | ~30 строк; чинит S1/S3/S4 |
| 6 | бюджеты строк, объектов и `$append` (D2015) в местах создания значений | ~100 строк, точечно |
| 7 | обёртка `EvaluationResult`/`EvaluationError` поверх `JException` в адаптере (compat.engine) | вне движка |
