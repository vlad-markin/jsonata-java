| query | N | JS median | JS p95 | JVM median | JVM p95 | JS/JVM | JS heapΔ MB | JVM alloc MB/eval | JVM peak heap MB | equal |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| path_filter | 10k | 27.5 | 49.7 | 8.7 | 9.7 | 3.15× | 2.5 | 5.9 | 37.4 | EQUAL |
| path_tags | 10k | 24.5 | 37.9 | 9.1 | 12.0 | 2.70× | 3.2 | 6.7 | 38.0 | EQUAL |
| group_agg | 10k | 58.9 | 80.4 | 15.1 | 19.6 | 3.91× | 13.5 | 6.3 | 33.4 | EQUAL |
| group_nested | 10k | 21.9 | 26.8 | 9.4 | 11.0 | 2.33× | 6.9 | 7.8 | 37.6 | EQUAL |
| join_scan | 10k | 432 | 443 | 84.8 | 93.1 | 5.10× | 3.4 | 131 | 157 | EQUAL |
| join_index | 10k | 84.0 | 127 | 26.5 | 28.2 | 3.17× | 26.4 | 37.6 | 65.6 | EQUAL |
| sort_fn | 10k | 1272 | 1584 | 143 | 156 | 8.89× | 200 | 296 | 326 | EQUAL |
| orderby | 10k | 679 | 751 | 60.6 | 81.1 | 11.20× | 195 | 105 | 170 | EQUAL |
| map_filter_reduce | 10k | 53.4 | 56.4 | 12.4 | 14.2 | 4.33× | 1.7 | 17.3 | 45.5 | EQUAL |
| strings | 10k | 169 | 186 | 42.9 | 45.2 | 3.93× | 7.6 | 88.7 | 130 | EQUAL |
| regex | 10k | 62.4 | 69.9 | 22.3 | 23.9 | 2.80× | 11.0 | 40.2 | 70.7 | EQUAL |
| recursion_tree | 10k | 1632 | 1746 | 361 | 415 | 4.52× | 19.4 | 501 | 531 | EQUAL |
| tail_loop | 10k | 41.0 | 49.5 | 8.5 | 9.4 | 4.85× | 17.7 | 6.9 | 34.5 | EQUAL |
| fib | 10k | 24.8 | 30.2 | 6.3 | 7.0 | 3.92× | 3.8 | 3.4 | 30.5 | EQUAL |
| path_filter | 50k | 126 | 142 | 84.1 | 107 | 1.50× | 11.2 | 29.3 | 178 | EQUAL |
| path_tags | 50k | 118 | 170 | 102 | 143 | 1.15× | 11.4 | 32.9 | 185 | EQUAL |
| group_agg | 50k | 1131 | 1608 | 190 | 250 | 5.96× | 12.5 | 31.4 | 170 | EQUAL |
| group_nested | 50k | 117 | 235 | 86.2 | 146 | 1.36× | 1.0 | 39.2 | 183 | EQUAL |
| join_scan | 50k | 2277 | 2349 | 578 | 624 | 3.94× | 9.5 | 656 | 795 | EQUAL |
| join_index | 50k | 661 | 882 | 185 | 192 | 3.56× | 67.1 | 189 | 338 | EQUAL |
| sort_fn | 50k | — | — | 1125 | 2930 | — | — | 1750 | 1894 | JS not run |
| orderby | 50k | — | — | 1034 | 1466 | — | — | 641 | 810 | JS not run |
| map_filter_reduce | 50k | 276 | 349 | 118 | 260 | 2.34× | 17.3 | 86.3 | 230 | EQUAL |
| strings | 50k | 1012 | 1193 | 276 | 487 | 3.67× | 20.2 | 444 | 619 | EQUAL |
| regex | 50k | 348 | 442 | 149 | 175 | 2.33× | 7.8 | 202 | 355 | EQUAL |
| recursion_tree | 50k | 7637 | 9015 | 3054 | 5445 | 2.50× | 47.5 | 2509 | 2659 | EQUAL |
| tail_loop | 50k | 214 | 285 | 52.1 | 60.8 | 4.10× | 46.7 | 34.7 | 178 | EQUAL |
| path_filter | 200k | 493 | 546 | 371 | 376 | 1.33× | 13.1 | 118 | 697 | EQUAL |
| path_tags | 200k | 472 | 566 | 379 | 391 | 1.24× | 18.3 | 133 | 707 | EQUAL |
| group_nested | 200k | 522 | 614 | 337 | 797 | 1.55× | 9.7 | 156 | 724 | EQUAL |
| join_scan | 200k | 9621 | 10923 | 10983 | 12406 | 0.88× | 22.2 | 2623 | 3184 | EQUAL |
| join_index | 200k | 2023 | 2026 | 1027 | 3340 | 1.97× | 295 | 757 | 1322 | EQUAL |
| sort_fn | 200k | — | — | 23588 | 28222 | — | — | 7930 | 4241 | JS not run |
| orderby | 200k | — | — | 8442 | 17894 | — | — | 2970 | 3557 | JS not run |
| map_filter_reduce | 200k | 982 | 1011 | 398 | 1311 | 2.47× | 19.0 | 345 | 901 | EQUAL |
| strings | 200k | 3908 | 4424 | 1051 | 1165 | 3.72× | 79.1 | 1770 | 2425 | EQUAL |
| regex | 200k | 1914 | 2003 | 1337 | 2024 | 1.43× | 26.7 | 808 | 1394 | EQUAL |
| recursion_tree | 200k | — | — | 33452 | 42673 | — | — | 10052 | 4271 | JS not run |
| tail_loop | 200k | 964 | 1046 | 533 | 650 | 1.81× | 138 | 141 | 713 | EQUAL |

Times in ms. JS/JVM > 1 means the JVM engine is faster. `*` = V8 ran GC during the measured evaluation, so heapΔ underestimates allocation.

### Compile (parse) time, median of 200, ms

| query | JS | JVM |
|---|---:|---:|
| path_filter | 0.079 | 0.290 |
| path_tags | 0.072 | 0.079 |
| group_agg | 0.059 | 0.026 |
| group_nested | 0.044 | 0.010 |
| join_scan | 0.084 | 0.056 |
| join_index | 0.054 | 0.474 |
| sort_fn | 0.084 | 0.049 |
| orderby | 0.040 | 0.185 |
| map_filter_reduce | 0.081 | 0.036 |
| strings | 0.055 | 0.021 |
| regex | 0.073 | 0.084 |
| recursion_tree | 0.066 | 0.030 |
| tail_loop | 0.065 | 0.258 |
| fib | 0.057 | 0.017 |

### JVM overhead of evaluation-control features (N=10k, interleaved rounds), median ms (median per-round ratio vs plain)

| query | plain | frame | novalidate | callbacks | bounds | guardrails |
|---|---:|---:|---:|---:|---:|---:|
| path_filter | 8.4 | 8.3 (×0.98) | 5.9 (×0.70) | 8.4 (×1.01) | 14.0 (×1.67) | 11.5 (×1.38) |
| group_nested | 9.4 | 9.3 (×1.00) | 6.3 (×0.67) | 9.6 (×1.01) | 10.1 (×1.05) | 13.1 (×1.35) |
| join_scan | 124 | 93.3 (×0.84) | 108 (×0.90) | 94.9 (×0.75) | 222 (×1.70) | 189 (×1.66) |
| map_filter_reduce | 11.1 | 11.3 (×1.00) | 9.0 (×0.80) | 11.8 (×1.03) | 19.6 (×1.73) | 15.8 (×1.39) |
| strings | 44.4 | 43.5 (×0.98) | 41.4 (×0.93) | 43.7 (×0.98) | 58.4 (×1.31) | 57.4 (×1.29) |
| recursion_tree | 249 | 223 (×0.93) | 216 (×0.98) | 239 (×1.01) | 459 (×1.93) | 351 (×1.55) |
| tail_loop | 7.8 | 7.8 (×1.01) | 5.4 (×0.69) | 7.9 (×1.00) | 15.4 (×1.97) | 11.6 (×1.48) |
