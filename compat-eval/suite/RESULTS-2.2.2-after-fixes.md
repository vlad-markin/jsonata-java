# Фаза 1: test-suite jsonata-js v2.2.2 на JVM-движке (строгая проверка)

Сгенерировано `suite/report.js`. Проверка строгая: значение (порядок ключей не важен, как chai deep.equal), код ошибки, а также `token`/`position`, если они указаны в тесте. Overrides НЕ применяются; колонка «в overrides» показывает, сколько случаев группы покрыто записью `test/test-overrides.json`.

Итого: всего **1686**, прошло **1684**, упало **1** (+ 1 только по token/position), в overrides 0 (из них сейчас проходят 0).

| группа | всего | прошло | упало | упало (только token/position) | в overrides |
|---|---:|---:|---:|---:|---:|
| array-constructor | 26 | 26 | 0 | 0 | 0 |
| blocks | 7 | 7 | 0 | 0 | 0 |
| boolean-expresssions | 31 | 31 | 0 | 0 | 0 |
| closures | 2 | 2 | 0 | 0 | 0 |
| coalescing-operator | 15 | 15 | 0 | 0 | 0 |
| comments | 4 | 4 | 0 | 0 | 0 |
| comparison-operators | 48 | 48 | 0 | 0 | 0 |
| conditionals | 9 | 9 | 0 | 0 | 0 |
| context | 4 | 4 | 0 | 0 | 0 |
| default-operator | 19 | 19 | 0 | 0 | 0 |
| descendent-operator | 17 | 17 | 0 | 0 | 0 |
| encoding | 4 | 4 | 0 | 0 | 0 |
| errors | 27 | 26 | 0 | 1 | 0 |
| fields | 8 | 8 | 0 | 0 | 0 |
| flattening | 61 | 61 | 0 | 0 | 0 |
| function-abs | 4 | 4 | 0 | 0 | 0 |
| function-append | 6 | 6 | 0 | 0 | 0 |
| function-applications | 22 | 22 | 0 | 0 | 0 |
| function-assert | 8 | 8 | 0 | 0 | 0 |
| function-average | 13 | 13 | 0 | 0 | 0 |
| function-boolean | 24 | 24 | 0 | 0 | 0 |
| function-ceil | 4 | 4 | 0 | 0 | 0 |
| function-contains | 8 | 8 | 0 | 0 | 0 |
| function-count | 14 | 14 | 0 | 0 | 0 |
| function-decodeUrl | 3 | 3 | 0 | 0 | 0 |
| function-decodeUrlComponent | 3 | 3 | 0 | 0 | 0 |
| function-distinct | 8 | 8 | 0 | 0 | 0 |
| function-each | 4 | 4 | 0 | 0 | 0 |
| function-encodeUrl | 3 | 3 | 0 | 0 | 0 |
| function-encodeUrlComponent | 3 | 3 | 0 | 0 | 0 |
| function-error | 11 | 11 | 0 | 0 | 0 |
| function-eval | 12 | 12 | 0 | 0 | 0 |
| function-exists | 25 | 25 | 0 | 0 | 0 |
| function-floor | 4 | 4 | 0 | 0 | 0 |
| function-formatBase | 9 | 9 | 0 | 0 | 0 |
| function-formatInteger | 65 | 65 | 0 | 0 | 0 |
| function-formatNumber | 45 | 45 | 0 | 0 | 0 |
| function-fromMillis | 93 | 93 | 0 | 0 | 0 |
| function-join | 12 | 12 | 0 | 0 | 0 |
| function-keys | 7 | 7 | 0 | 0 | 0 |
| function-length | 17 | 17 | 0 | 0 | 0 |
| function-lookup | 4 | 4 | 0 | 0 | 0 |
| function-lowercase | 2 | 2 | 0 | 0 | 0 |
| function-max | 27 | 27 | 0 | 0 | 0 |
| function-merge | 5 | 5 | 0 | 0 | 0 |
| function-number | 34 | 34 | 0 | 0 | 0 |
| function-pad | 13 | 13 | 0 | 0 | 0 |
| function-parseInteger | 61 | 61 | 0 | 0 | 0 |
| function-power | 7 | 7 | 0 | 0 | 0 |
| function-replace | 12 | 12 | 0 | 0 | 0 |
| function-reverse | 4 | 4 | 0 | 0 | 0 |
| function-round | 18 | 18 | 0 | 0 | 0 |
| function-shuffle | 4 | 4 | 0 | 0 | 0 |
| function-sift | 5 | 5 | 0 | 0 | 0 |
| function-signatures | 41 | 41 | 0 | 0 | 0 |
| function-sort | 11 | 11 | 0 | 0 | 0 |
| function-split | 19 | 19 | 0 | 0 | 0 |
| function-spread | 4 | 4 | 0 | 0 | 0 |
| function-sqrt | 4 | 4 | 0 | 0 | 0 |
| function-string | 31 | 30 | 1 | 0 | 0 |
| function-substring | 19 | 19 | 0 | 0 | 0 |
| function-substringAfter | 5 | 5 | 0 | 0 | 0 |
| function-substringBefore | 5 | 5 | 0 | 0 | 0 |
| function-sum | 7 | 7 | 0 | 0 | 0 |
| function-tomillis | 60 | 60 | 0 | 0 | 0 |
| function-trim | 3 | 3 | 0 | 0 | 0 |
| function-typeOf | 13 | 13 | 0 | 0 | 0 |
| function-uppercase | 2 | 2 | 0 | 0 | 0 |
| function-zip | 6 | 6 | 0 | 0 | 0 |
| higher-order-functions | 3 | 3 | 0 | 0 | 0 |
| hof-filter | 4 | 4 | 0 | 0 | 0 |
| hof-map | 12 | 12 | 0 | 0 | 0 |
| hof-reduce | 11 | 11 | 0 | 0 | 0 |
| hof-single | 11 | 11 | 0 | 0 | 0 |
| hof-zip-map | 4 | 4 | 0 | 0 | 0 |
| inclusion-operator | 9 | 9 | 0 | 0 | 0 |
| joins | 45 | 45 | 0 | 0 | 0 |
| lambdas | 14 | 14 | 0 | 0 | 0 |
| literals | 24 | 24 | 0 | 0 | 0 |
| matchers | 2 | 2 | 0 | 0 | 0 |
| missing-paths | 6 | 6 | 0 | 0 | 0 |
| multiple-array-selectors | 3 | 3 | 0 | 0 | 0 |
| null | 7 | 7 | 0 | 0 | 0 |
| numeric-operators | 19 | 19 | 0 | 0 | 0 |
| object-constructor | 27 | 27 | 0 | 0 | 0 |
| parent-operator | 44 | 44 | 0 | 0 | 0 |
| parentheses | 8 | 8 | 0 | 0 | 0 |
| partial-application | 5 | 5 | 0 | 0 | 0 |
| performance | 2 | 2 | 0 | 0 | 0 |
| predicates | 4 | 4 | 0 | 0 | 0 |
| quoted-selectors | 8 | 8 | 0 | 0 | 0 |
| range-operator | 25 | 25 | 0 | 0 | 0 |
| regex | 39 | 39 | 0 | 0 | 0 |
| simple-array-selectors | 23 | 23 | 0 | 0 | 0 |
| sorting | 21 | 21 | 0 | 0 | 0 |
| string-concat | 12 | 12 | 0 | 0 | 0 |
| tail-recursion | 10 | 10 | 0 | 0 | 0 |
| token-conversion | 4 | 4 | 0 | 0 | 0 |
| transform | 104 | 104 | 0 | 0 | 0 |
| transforms | 15 | 15 | 0 | 0 | 0 |
| variables | 13 | 13 | 0 | 0 | 0 |
| wildcards | 13 | 13 | 0 | 0 | 0 |
| **итого** | **1686** | **1684** | **1** | **1** | **0** |

## Упавшие случаи

| id | override | причина | выражение | JS 2.2.2 | JVM |
|---|---|---|---|---|---|
| function-string/case019.json |  | error code D1001 != D3001 | `$string(1/0)` | error D3001@8 | error D1001@10 |

## Расхождения только по token/position ошибки

| id | причина | выражение |
|---|---|---|
| errors/case025.json | token 2 != min | `( $A := function(){$min(2, 3)}; $A() )` |

## Покрытие overrides

JsonataTest сопоставляет запись по подстроке (`name.indexOf(override)`), поэтому запись `..._1` покрывает и `..._11`, `..._12` и т.д.

| override | случаи, к которым применяется | сейчас проходят строго |
|---|---|---|
