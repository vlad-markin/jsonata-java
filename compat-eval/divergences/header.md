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
