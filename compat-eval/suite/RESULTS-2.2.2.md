# Фаза 1: test-suite jsonata-js v2.2.2 на JVM-движке (строгая проверка)

Сгенерировано `suite/report.js`. Проверка строгая: значение (порядок ключей не важен, как chai deep.equal), код ошибки, а также `token`/`position`, если они указаны в тесте. Overrides НЕ применяются; колонка «в overrides» показывает, сколько случаев группы покрыто записью `test/test-overrides.json`.

Итого: всего **1686**, прошло **1589**, упало **96** (+ 1 только по token/position), в overrides 33 (из них сейчас проходят 4).

| группа | всего | прошло | упало | упало (только token/position) | в overrides |
|---|---:|---:|---:|---:|---:|
| array-constructor | 26 | 26 | 0 | 0 | 0 |
| blocks | 7 | 7 | 0 | 0 | 0 |
| boolean-expresssions | 31 | 30 | 1 | 0 | 0 |
| closures | 2 | 2 | 0 | 0 | 0 |
| coalescing-operator | 15 | 15 | 0 | 0 | 0 |
| comments | 4 | 3 | 1 | 0 | 0 |
| comparison-operators | 48 | 47 | 1 | 0 | 0 |
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
| function-applications | 22 | 22 | 0 | 0 | 1 |
| function-assert | 8 | 7 | 1 | 0 | 0 |
| function-average | 13 | 13 | 0 | 0 | 0 |
| function-boolean | 24 | 24 | 0 | 0 | 0 |
| function-ceil | 4 | 4 | 0 | 0 | 0 |
| function-contains | 8 | 8 | 0 | 0 | 0 |
| function-count | 14 | 14 | 0 | 0 | 0 |
| function-decodeUrl | 3 | 2 | 1 | 0 | 0 |
| function-decodeUrlComponent | 3 | 2 | 1 | 0 | 0 |
| function-distinct | 8 | 8 | 0 | 0 | 0 |
| function-each | 4 | 4 | 0 | 0 | 0 |
| function-encodeUrl | 3 | 2 | 1 | 0 | 0 |
| function-encodeUrlComponent | 3 | 2 | 1 | 0 | 0 |
| function-error | 11 | 10 | 1 | 0 | 0 |
| function-eval | 12 | 12 | 0 | 0 | 0 |
| function-exists | 25 | 25 | 0 | 0 | 0 |
| function-floor | 4 | 4 | 0 | 0 | 0 |
| function-formatBase | 9 | 9 | 0 | 0 | 0 |
| function-formatInteger | 65 | 62 | 3 | 0 | 1 |
| function-formatNumber | 45 | 21 | 24 | 0 | 8 |
| function-fromMillis | 93 | 76 | 17 | 0 | 17 |
| function-join | 12 | 12 | 0 | 0 | 0 |
| function-keys | 7 | 7 | 0 | 0 | 0 |
| function-length | 17 | 16 | 1 | 0 | 0 |
| function-lookup | 4 | 4 | 0 | 0 | 0 |
| function-lowercase | 2 | 2 | 0 | 0 | 0 |
| function-max | 27 | 27 | 0 | 0 | 0 |
| function-merge | 5 | 5 | 0 | 0 | 0 |
| function-number | 34 | 28 | 6 | 0 | 0 |
| function-pad | 13 | 13 | 0 | 0 | 0 |
| function-parseInteger | 61 | 57 | 4 | 0 | 3 |
| function-power | 7 | 7 | 0 | 0 | 0 |
| function-replace | 12 | 9 | 3 | 0 | 0 |
| function-reverse | 4 | 4 | 0 | 0 | 0 |
| function-round | 18 | 18 | 0 | 0 | 0 |
| function-shuffle | 4 | 4 | 0 | 0 | 0 |
| function-sift | 5 | 5 | 0 | 0 | 0 |
| function-signatures | 41 | 39 | 2 | 0 | 0 |
| function-sort | 11 | 10 | 1 | 0 | 0 |
| function-split | 19 | 18 | 1 | 0 | 0 |
| function-spread | 4 | 4 | 0 | 0 | 0 |
| function-sqrt | 4 | 4 | 0 | 0 | 0 |
| function-string | 31 | 29 | 2 | 0 | 1 |
| function-substring | 19 | 19 | 0 | 0 | 0 |
| function-substringAfter | 5 | 5 | 0 | 0 | 0 |
| function-substringBefore | 5 | 5 | 0 | 0 | 0 |
| function-sum | 7 | 7 | 0 | 0 | 0 |
| function-tomillis | 60 | 51 | 9 | 0 | 0 |
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
| matchers | 2 | 0 | 2 | 0 | 1 |
| missing-paths | 6 | 6 | 0 | 0 | 0 |
| multiple-array-selectors | 3 | 3 | 0 | 0 | 0 |
| null | 7 | 7 | 0 | 0 | 0 |
| numeric-operators | 19 | 19 | 0 | 0 | 0 |
| object-constructor | 27 | 27 | 0 | 0 | 0 |
| parent-operator | 44 | 44 | 0 | 0 | 0 |
| parentheses | 8 | 8 | 0 | 0 | 0 |
| partial-application | 5 | 4 | 1 | 0 | 0 |
| performance | 2 | 2 | 0 | 0 | 0 |
| predicates | 4 | 4 | 0 | 0 | 0 |
| quoted-selectors | 8 | 8 | 0 | 0 | 0 |
| range-operator | 25 | 25 | 0 | 0 | 0 |
| regex | 39 | 36 | 3 | 0 | 1 |
| simple-array-selectors | 23 | 23 | 0 | 0 | 0 |
| sorting | 21 | 21 | 0 | 0 | 0 |
| string-concat | 12 | 12 | 0 | 0 | 0 |
| tail-recursion | 10 | 7 | 3 | 0 | 0 |
| token-conversion | 4 | 4 | 0 | 0 | 0 |
| transform | 104 | 99 | 5 | 0 | 0 |
| transforms | 15 | 15 | 0 | 0 | 0 |
| variables | 13 | 13 | 0 | 0 | 0 |
| wildcards | 13 | 13 | 0 | 0 | 0 |
| **итого** | **1686** | **1589** | **96** | **1** | **33** |

## Упавшие случаи

| id | override | причина | выражение | JS 2.2.2 | JVM |
|---|---|---|---|---|---|
| boolean-expresssions/case030.json |  | error code Unexpected != D3030 | `$type(age) = 'number' or $number(age) > 0` | error D3030@24 | error Unexpected@24 |
| comments/case002.json |  | expected error S0106, got crash | `$substring('Hello, world', 0 /* start at index 0 */, 5/*length*/) /* trailing comment - no closing tag should trigger an error ` | error S0106@66 | crash java.lang.StringIndexOutOfBoundsException: java.lang.StringIndexOutOfBoundsException: Index 127 out of bounds for length 127 |
| comparison-operators/deep-equals.json_5 |  | VALUE@$ | `data = [1, 2, 3, 4]` | true | false |
| function-assert/case004.json |  | expected error T0410, got crash | `$assert(null)` | error T0410@8 | crash java.lang.Exception: java.lang.Exception: Argument error calling function assertFn |
| function-decodeUrl/case002.json |  | expected error D3140, got crash | `$decodeUrl('%E0%A4%A')` | error D3140@11 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern |
| function-decodeUrlComponent/case002.json |  | expected error D3140, got crash | `$decodeUrlComponent('%E0%A4%A')` | error D3140@20 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern |
| function-encodeUrl/case002.json |  | error code Malformed URL != D3140 | `$encodeUrl('�')` | error D3140@11 | error Malformed URL@0 |
| function-encodeUrlComponent/case002.json |  | error code Malformed URL != D3140 | `$encodeUrlComponent('�')` | error D3140@20 | error Malformed URL@0 |
| function-error/case007.json |  | expected error T0410, got crash | `$error(null)` | error T0410@7 | crash java.lang.Exception: java.lang.Exception: Argument error calling function error |
| function-formatInteger/formatInteger.json_15 |  | expected error D3131, got crash | `$formatInteger(12340, '##0０')` | error D3131@15 | crash java.lang.RuntimeException: java.lang.RuntimeException: In a decimal digit pattern, all digits must be from the same decimal group |
| function-formatInteger/formatInteger.json_43 | да | VALUE@$ | `$formatInteger(1e+46, 'w')` | "ten billion trillion trillion trillion" | "nine million, two hundred and twenty-three thousand, three hundred and seventy-two trillion, thirty-six billion, eight hundred and fifty-four million |
| function-formatInteger/formatInteger.json_64 |  | expected error D3130, got crash | `$formatInteger(123456, 'α')` | error D3130@15 | crash java.lang.RuntimeException: java.lang.RuntimeException: Formatting or parsing an integer as a sequence starting with α is not supported by this  |
| function-formatNumber/case002.json | да | VALUE@$ | `$formatNumber(123412345678.9, "9,9,99.99")` | "123412345,6,78.90" | "12,34,12,34,56,78.90" |
| function-formatNumber/case003.json | да | unexpected crash java.lang.IllegalArgumentException: Malformed pattern "0,000.000,000" | `$formatNumber(1234.56789, "9,999.999,999")` | "1,234.567,890" | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Malformed pattern "0,000.000,000" |
| function-formatNumber/case011.json | да | VALUE@$ | `$formatNumber(1234.5678, "①①.①①①e①", {"zero-digit": "⑟"})` | "①②.③④⑥e②" | "①①①②③⑤.①①①e①" |
| function-formatNumber/case013.json | да | VALUE@$ | `$formatNumber(0.234, "#.00e0")` | "0.23e0" | ".234e0" |
| function-formatNumber/case014.json | да | VALUE@$ | `$formatNumber(0.123, "#.e9")` | "0.1e0" | "1.e-1" |
| function-formatNumber/case016.json | да | VALUE@$ | `$formatNumber(2392.14*(-36.58), "000,000.000###;###,###.000###")` | "87,504.4812" | "-087,504.4812" |
| function-formatNumber/case019.json |  | expected error D3080, got crash | `$formatNumber(20,"#;#;#")` | error D3080@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Unquoted special character ';' in pattern "#;#;#" |
| function-formatNumber/case020.json |  | expected error D3081, got crash | `$formatNumber(20,"#.0.0")` | error D3081@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Multiple decimal separators in pattern "#.0.0" |
| function-formatNumber/case021.json |  | expected error D3082, got crash | `$formatNumber(20,"#0%%")` | error D3082@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Too many percent/per mille characters in pattern "#0%%" |
| function-formatNumber/case022.json |  | expected error D3083, got crash | `$formatNumber(20,"#0‰‰")` | error D3083@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Too many percent/per mille characters in pattern "#0‰‰" |
| function-formatNumber/case023.json |  | expected error D3084, got crash | `$formatNumber(20,"#0%‰")` | error D3084@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Too many percent/per mille characters in pattern "#0%‰" |
| function-formatNumber/case024.json |  | expected error D3085, got crash | `$formatNumber(20,".e0")` | error D3085@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Malformed exponential pattern ".E0" |
| function-formatNumber/case025.json | да | expected error D3086, got ok | `$formatNumber(20,"0+.e0")` | error D3086@14 | "2.e1+" |
| function-formatNumber/case026.json |  | expected error D3087, got crash | `$formatNumber(20,"0,.e0")` | error D3087@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Malformed pattern "0,.E0" |
| function-formatNumber/case027.json |  | expected error D3088, got crash | `$formatNumber(20,"0,")` | error D3088@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Malformed pattern "0," |
| function-formatNumber/case028.json |  | expected error D3089, got crash | `$formatNumber(20,"0,,0")` | error D3089@14 | crash java.lang.RuntimeException: java.lang.RuntimeException: The sub-picture must not contain two adjacent instances of the 'grouping-separator' char |
| function-formatNumber/case029.json |  | expected error D3090, got crash | `$formatNumber(20,"0#.e0")` | error D3090@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Malformed pattern "0#.E0" |
| function-formatNumber/case030.json |  | expected error D3091, got crash | `$formatNumber(20,"#0.#0e0")` | error D3091@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Unexpected '0' in pattern "#0.#0E0" |
| function-formatNumber/case031.json |  | expected error D3092, got crash | `$formatNumber(20,"#0.0e0%")` | error D3092@14 | crash java.lang.RuntimeException: java.lang.RuntimeException: A sub-picture that contains a 'percent' or 'per-mille' character must not contain a char |
| function-formatNumber/case032.json |  | expected error D3093, got crash | `$formatNumber(20,"#0.0e0,0")` | error D3093@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Malformed pattern "#0.0E0,0" |
| function-formatNumber/issue786.json_0 |  | expected error D3086, got crash | `$formatNumber(42, '%%')` | error D3086@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Too many percent/per mille characters in pattern "%%" |
| function-formatNumber/issue786.json_1 |  | expected error D3086, got crash | `$formatNumber(42, '‰‰')` | error D3086@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Too many percent/per mille characters in pattern "‰‰" |
| function-formatNumber/issue786.json_2 |  | expected error D3086, got crash | `$formatNumber(42, '%‰')` | error D3086@14 | crash java.lang.IllegalArgumentException: java.lang.IllegalArgumentException: Too many percent/per mille characters in pattern "%‰" |
| function-formatNumber/issue786.json_3 | да | expected error D3086, got ok | `$formatNumber(42, '---')` | error D3086@14 | "---42" |
| function-fromMillis/formatDateTime.json_26 |  | expected error D3134, got crash | `$fromMillis(1230757500000, '[Y]-[M01]-[D01]T[H01]:[m]:[s].[f001][Z010101t]', '+0530')` | error D3134@12 | crash java.lang.RuntimeException: java.lang.RuntimeException: The timezone integer format specifier cannot have more than four digits |
| function-fromMillis/formatDateTime.json_61 | да | VALUE@$ | `$fromMillis(1359720000000, 'Week: [w] of [xNn]')` | "Week: 5 of January" | "Week: 0 of February" |
| function-fromMillis/formatDateTime.json_63 | да | VALUE@$ | `$fromMillis(1483272000000, 'Week: [w] of [xNn]')` | "Week: 5 of December" | "Week: 0 of January" |
| function-fromMillis/formatDateTime.json_64 | да | VALUE@$ | `$fromMillis(1533038400000, 'Week: [w] of [xNn]')` | "Week: 1 of August" | "Week: 5 of July" |
| function-fromMillis/formatDateTime.json_65 | да | VALUE@$ | `$fromMillis(1419940800000, 'Week: [w] of [xNn]')` | "Week: 1 of January" | "Week: 5 of December" |
| function-fromMillis/formatDateTime.json_69 |  | expected error D3133, got crash | `$fromMillis(1419940800000, '[YN]-[M]-[D]')` | error D3133@12 | crash java.lang.RuntimeException: java.lang.RuntimeException: The 'name' modifier can only be applied to months and days in the date/time picture stri |
| function-fromMillis/formatDateTime.json_70 |  | expected error D3135, got crash | `$fromMillis(1419940800000, '[YN]-[M')` | error D3135@12 | crash java.lang.RuntimeException: java.lang.RuntimeException: No matching closing bracket ']' in date/time picture string |
| function-fromMillis/isoWeekDate.json_0 | да | VALUE@$ | `($ts := $toMillis('2005-01-01', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2004-W53-6" | "2005-W53-6" |
| function-fromMillis/isoWeekDate.json_1 | да | VALUE@$ | `($ts := $toMillis('2005-01-02', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2004-W53-7" | "2005-W53-7" |
| function-fromMillis/isoWeekDate.json_3 | да | VALUE@$ | `($ts := $toMillis('2006-01-01', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2005-W52-7" | "2006-W52-7" |
| function-fromMillis/isoWeekDate.json_8 | да | VALUE@$ | `($ts := $toMillis('2007-12-31', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2008-W01-1" | "2007-W01-1" |
| function-fromMillis/isoWeekDate.json_11 | да | VALUE@$ | `($ts := $toMillis('2008-12-29', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2009-W01-1" | "2008-W01-1" |
| function-fromMillis/isoWeekDate.json_12 | да | VALUE@$ | `($ts := $toMillis('2008-12-30', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2009-W01-2" | "2008-W01-2" |
| function-fromMillis/isoWeekDate.json_13 | да | VALUE@$ | `($ts := $toMillis('2008-12-31', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2009-W01-3" | "2008-W01-3" |
| function-fromMillis/isoWeekDate.json_16 | да | VALUE@$ | `($ts := $toMillis('2010-01-01', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2009-W53-5" | "2010-W53-5" |
| function-fromMillis/isoWeekDate.json_17 | да | VALUE@$ | `($ts := $toMillis('2010-01-02', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2009-W53-6" | "2010-W53-6" |
| function-fromMillis/isoWeekDate.json_18 | да | VALUE@$ | `($ts := $toMillis('2010-01-03', '[Y]-[M]-[D]'); $fromMillis($ts, '[X0001]-W[W01]-[F1]') )` | "2009-W53-7" | "2010-W53-7" |
| function-length/case010.json |  | expected error T0410, got crash | `$length(null)` | error T0410@8 | crash java.lang.Exception: java.lang.Exception: Argument error calling function length |
| function-number/case008.json |  | error code D1001 != D3030 | `$number("10e500")` | error D3030@8 | error D1001@0 |
| function-number/case009.json |  | expected error D3030, got crash | `$number("Hello world")` | error D3030@8 | crash java.lang.NumberFormatException: java.lang.NumberFormatException: For input string: "Hello world" |
| function-number/case010.json |  | expected error D3030, got crash | `$number("1/2")` | error D3030@8 | crash java.lang.NumberFormatException: java.lang.NumberFormatException: For input string: "1/2" |
| function-number/case011.json |  | expected error D3030, got crash | `$number("1234 hello")` | error D3030@8 | crash java.lang.NumberFormatException: java.lang.NumberFormatException: For input string: "1234 hello" |
| function-number/case012.json |  | expected error D3030, got crash | `$number("")` | error D3030@8 | crash java.lang.NumberFormatException: java.lang.NumberFormatException: empty String |
| function-number/case018.json |  | expected error D3030, got crash | `$number("[1]")` | error D3030@8 | crash java.lang.NumberFormatException: java.lang.NumberFormatException: For input string: "[1]" |
| function-parseInteger/parseInteger.json_11 | да | VALUE@$ | `$parseInteger('١٢٣٤٠', '###١')` | 12340 | undefined |
| function-parseInteger/parseInteger.json_12 | да | VALUE@$ | `$parseInteger('１２３４０', '###０')` | 12340 | undefined |
| function-parseInteger/parseInteger.json_39 | да | NUMBER@$ 1e+46 vs 8023796054858137600 | `$parseInteger('ten billion trillion trillion trillion', 'w')` | 1e+46 | 8023796054858137600 |
| function-parseInteger/parseInteger.json_60 |  | expected error D3130, got crash | `$parseInteger('50', '#')` | error D3130@14 | crash java.text.ParseException: java.text.ParseException: Formatting or parsing an integer as a sequence starting with "#" is not supported by this im |
| function-replace/case006.json |  | expected error T0410, got crash | `$replace("hello", "l", "1", null)` | error T0410@9 | crash java.lang.Exception: java.lang.Exception: Argument error calling function replace |
| function-replace/case007.json |  | error code Fourth argument of replace function must evaluate to a positive number != D3011 | `$replace("hello", "l", "1", -2)` | error D3011@9 | error Fourth argument of replace function must evaluate to a positive number@0 |
| function-replace/case009.json |  | error code Second argument of replace function cannot be an empty string != D3010 | `$replace("hello", "", "bye")` | error D3010@9 | error Second argument of replace function cannot be an empty string@0 |
| function-signatures/case026.json |  | expected error S0401, got crash | `λ($arg)<n<n>>{$arg}(5)` | error S0401@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: Type parameters can only be applied to functions and arrays |
| function-signatures/case034.json |  | expected error S0402, got crash | `λ($arr)<(sa<n>)>>{$arr}([[1]])` | error S0402@9 | crash java.lang.RuntimeException: java.lang.RuntimeException: Choice groups containing parameterized types are not supported |
| function-sort/case007.json |  | expected error D3070, got crash | `$sort(Account.Order.Product)` | error D3070@6 | crash java.lang.ClassCastException: java.lang.ClassCastException: class java.util.LinkedHashMap cannot be cast to class java.lang.Comparable (java.uti |
| function-split/case012.json |  | expected error T0410, got crash | `$split("a, b, c, d", ", ", null)` | error T0410@7 | crash java.lang.Exception: java.lang.Exception: Argument error calling function split |
| function-string/case006.json | да | VALUE@$ | `$string(1e20)` | "100000000000000000000" | "1e+20" |
| function-string/case019.json |  | error code D1001 != D3001 | `$string(1/0)` | error D3001@8 | error D1001@0 |
| function-tomillis/case007.json |  | expected error D3110, got crash | `$toMillis("foo")` | error D3110@10 | crash java.time.format.DateTimeParseException: java.time.format.DateTimeParseException: Text 'foo' could not be parsed at index 0 |
| function-tomillis/case008.json |  | expected error D3110, got crash | `$toMillis("01-02-2018")` | error D3110@10 | crash java.time.format.DateTimeParseException: java.time.format.DateTimeParseException: Text '01-02-20:18' could not be parsed at index 0 |
| function-tomillis/case009.json |  | expected error D3110, got crash | `$toMillis("2018-02-03 11:15:33")` | error D3110@10 | crash java.time.format.DateTimeParseException: java.time.format.DateTimeParseException: Text '2018-02-03 11:15:33' could not be parsed at index 10 |
| function-tomillis/parseDateTime.json_35 |  | expected error D3132, got crash | `$toMillis('2018-05-22', '[Y]-[M]-[q]')` | error D3132@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: Unknown component specifier q in date/time picture string |
| function-tomillis/parseDateTime.json_36 |  | expected error D3133, got crash | `$toMillis('2018-05-22', '[YN]-[M]-[D]')` | error D3133@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: The 'name' modifier can only be applied to months and days in the date/time picture stri |
| function-tomillis/parseDateTime.json_37 |  | expected error D3136, got crash | `$toMillis('2018-22', '[Y]-[D]')` | error D3136@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: The date/time picture string is missing specifiers required to parse the timestamp |
| function-tomillis/parseDateTime.json_38 |  | expected error D3136, got crash | `$toMillis('5-22 23:59', '[M]-[D] [m]:[s]')` | error D3136@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: The date/time picture string is missing specifiers required to parse the timestamp |
| function-tomillis/parseDateTime.json_39 |  | expected error D3136, got crash | `$toMillis('2018-3-2-5', '[X]-[x]-[w]-[F1]')` | error D3136@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: The date/time picture string is missing specifiers required to parse the timestamp |
| function-tomillis/parseDateTime.json_40 |  | expected error D3136, got crash | `$toMillis('2018-32-5', '[X]-[W]-[F1]')` | error D3136@10 | crash java.lang.RuntimeException: java.lang.RuntimeException: The date/time picture string is missing specifiers required to parse the timestamp |
| matchers/case000.json | да | unexpected crash java.lang.Exception: Argument error calling function match | `(     $generateMatcher := function($ch) {       $match := function($str, $offset) {(         $before := $substringBefore($str, $ch);         $start :=` | [{"match":"a","index":0,"groups":[]},{"match":"a","index":3,"groups":[]},{"match":"a","index":5,"groups":[]},{"match":"a","index":7,"groups":[]},{"mat | crash java.lang.Exception: java.lang.Exception: Argument error calling function match |
| matchers/case001.json |  | expected error T1010, got crash | `$split('some text', $uppercase)` | error T1010@7 | crash java.lang.ClassCastException: java.lang.ClassCastException: class com.dashjoin.jsonata.Jsonata$JFunction cannot be cast to class java.util.regex |
| partial-application/case003.json |  | error code T1008 != T1007 | `substring(?, 0, ?)` | error T1007@10 | error T1008@10 |
| regex/case022.json | да | expected error D1004, got ok | `$replace("abracadabra", /.*?/, "$1")` | error D1004@9 | "abracadabra" |
| regex/case035.json |  | expected error D3012, got crash | `Account.Order.Product.$replace($."Product Name", /hat/i, function($match) { true })` | error D3012@31 | crash java.lang.NullPointerException: java.lang.NullPointerException: Cannot invoke "String.length()" because "replacement" is null |
| regex/case036.json |  | expected error D3012, got crash | `Account.Order.Product.$replace($."Product Name", /hat/i, function($match) { 42 })` | error D3012@31 | crash java.lang.NullPointerException: java.lang.NullPointerException: Cannot invoke "String.length()" because "replacement" is null |
| tail-recursion/case002.json |  | error code Stack overflow error: Check for non-terminating recursive function.  Consider rewriting as tail-recursive. Depth=303 max=302 != U1001 | `(  $factorial := function($n){$n = 0 ? 1 : $n * $factorial($n - 1)};  $factorial(100))             ` | error U1001@59 | error Stack overflow error: Check for non-terminating recursive function.  Consider rewriting as tail-recursive. Depth=303 max=302@59 |
| tail-recursion/case005.json |  | error code Stack overflow error: Check for non-terminating recursive function.  Consider rewriting as tail-recursive. Depth=301 max=300 != U1001 | `(  $inf := function($n){$n+$inf($n-1)};  $inf(5))` | error U1001@32 | error Stack overflow error: Check for non-terminating recursive function.  Consider rewriting as tail-recursive. Depth=301 max=300@32 |
| tail-recursion/case006.json |  | error code Expression evaluation timeout: Check for infinite loop != U1001 | `(  $inf := function(){$inf()};  $inf())` | error U1001@26 | error Expression evaluation timeout: Check for infinite loop@37 |
| transform/case064.json |  | expected error T0410, got crash | `$lowercase(Salary)` | error T0410@11 | crash java.lang.Exception: java.lang.Exception: Argument error calling function lowercase |
| transform/case070.json |  | expected error T0410, got crash | `$uppercase(Salary)` | error T0410@11 | crash java.lang.Exception: java.lang.Exception: Argument error calling function uppercase |
| transform/case077.json |  | expected error T0410, got crash | `$substringBefore(Salary,"xx")` | error T0410@17 | crash java.lang.Exception: java.lang.Exception: Argument error calling function substringBefore |
| transform/case085.json |  | expected error T0410, got crash | `$substringAfter(Salary,"xx")` | error T0410@16 | crash java.lang.Exception: java.lang.Exception: Argument error calling function substringAfter |
| transform/case095.json |  | expected error T0410, got crash | `$substring(Salary,2,4)` | error T0410@11 | crash java.lang.Exception: java.lang.Exception: Argument error calling function substring |

## Расхождения только по token/position ошибки

| id | причина | выражение |
|---|---|---|
| errors/case025.json | token 1 != min | `( $A := function(){$min(2, 3)}; $A() )` |

## Покрытие overrides

JsonataTest сопоставляет запись по подстроке (`name.indexOf(override)`), поэтому запись `..._1` покрывает и `..._11`, `..._12` и т.д.

| override | случаи, к которым применяется | сейчас проходят строго |
|---|---|---|
| function-formatInteger/formatInteger.json_43 | точно |  |
| function-parseInteger/parseInteger.json_11 | точно |  |
| function-parseInteger/parseInteger.json_12 | точно |  |
| function-parseInteger/parseInteger.json_39 | точно |  |
| function-formatNumber/case002.json | точно |  |
| function-formatNumber/case003.json | точно |  |
| function-formatNumber/case011.json | точно |  |
| function-formatNumber/case013.json | точно |  |
| function-formatNumber/case014.json | точно |  |
| function-formatNumber/case016.json | точно |  |
| function-formatNumber/case025.json | точно |  |
| function-formatNumber/issue786.json_3 | точно |  |
| function-fromMillis/isoWeekDate.json_0 | точно |  |
| function-fromMillis/isoWeekDate.json_1 | точно, isoWeekDate.json_10, isoWeekDate.json_11, isoWeekDate.json_12, isoWeekDate.json_13, isoWeekDate.json_14, isoWeekDate.json_15, isoWeekDate.json_16, isoWeekDate.json_17, isoWeekDate.json_18 | isoWeekDate.json_10, isoWeekDate.json_14, isoWeekDate.json_15 |
| function-fromMillis/isoWeekDate.json_3 | точно |  |
| function-fromMillis/isoWeekDate.json_8 | точно |  |
| function-fromMillis/isoWeekDate.json_11 | точно |  |
| function-fromMillis/isoWeekDate.json_12 | точно |  |
| function-fromMillis/isoWeekDate.json_13 | точно |  |
| function-fromMillis/isoWeekDate.json_16 | точно |  |
| function-fromMillis/isoWeekDate.json_17 | точно |  |
| function-fromMillis/isoWeekDate.json_18 | точно |  |
| function-fromMillis/formatDateTime.json_61 | точно |  |
| function-fromMillis/formatDateTime.json_63 | точно |  |
| function-fromMillis/formatDateTime.json_64 | точно |  |
| function-fromMillis/formatDateTime.json_65 | точно |  |
| function-string/case006.json | точно |  |
| function-applications/case008.json | точно | case008.json |
| matchers/case000.json | точно |  |
| regex/case022.json | точно |  |
