| query | N | JS median | JS p95 | JVM median | JVM p95 | JS/JVM | JS heapΔ MB | JVM alloc MB/eval | JVM peak heap MB | equal |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| path_filter | 10k | 41.9 | 82.0 | 7.4 | 9.1 | 5.69× | 2.8 | 7.0 | 41.2 | EQUAL |
| path_tags | 10k | 22.1 | 40.6 | 9.1 | 11.8 | 2.42× | 3.3 | 8.3 | 39.8 | EQUAL |
| group_agg | 10k | 57.7 | 88.3 | 137 | 223 | 0.42× | 13.6 | 544 | 575 | EQUAL |
| group_nested | 10k | 20.9 | 29.6 | 8.5 | 18.8 | 2.45× | 6.9 | 10.3 | 39.4 | EQUAL |
| join_scan | 10k | 537 | 671 | 83.2 | 110 | 6.45× | 3.3 | 146 | 173 | EQUAL |
| join_index | 10k | 141 | 195 | 24.9 | 36.3 | 5.67× | 25.5 | 56.9 | 87.3 | EQUAL |
| sort_fn | 10k | 1562 | 2158 | 501 | 757 | 3.12× | 202 | 627 | 655 | EQUAL |
| orderby | 10k | 826 | 1286 | 48.3 | 73.8 | 17.09× | 203 | 102 | 153 | EQUAL |
| map_filter_reduce | 10k | 50.7 | 64.4 | 11.8 | 14.2 | 4.28× | 1.6 | 19.1 | 47.3 | EQUAL |
| strings | 10k | 179 | 230 | 58.5 | 127 | 3.06× | 7.6 | 168 | 206 | EQUAL |
| regex | 10k | 91.3 | 156 | 25.8 | 31.5 | 3.53× | 11.3 | 62.8 | 94.3 | EQUAL |
| recursion_tree | 10k | 1591 | 2759 | 378 | 441 | 4.21× | 20.7 | 856 | 890 | EQUAL |
| tail_loop | 10k | 50.5 | 79.8 | 8.8 | 17.6 | 5.74× | 17.5 | 9.4 | 38.1 | EQUAL |
| fib | 10k | 25.9 | 55.4 | 6.6 | 11.1 | 3.96× | 3.8 | 4.7 | 34.1 | EQUAL |
| path_filter | 50k | 136 | 253 | 52.5 | 61.7 | 2.60× | 11.2 | 36.5 | 189 | EQUAL |
| path_tags | 50k | 129 | 213 | 52.3 | 101 | 2.47× | 11.8 | 39.5 | 188 | EQUAL |
| group_agg | 50k | 1065 | 1221 | 1491 | 4662 | 0.71× | 11.0 | 13402 | 1982 | EQUAL |
| group_nested | 50k | 121 | 142 | 261 | 498 | 0.47× | 3.1 | 51.3 | 194 | EQUAL |
| join_scan | 50k | 2170 | 2505 | 510 | 631 | 4.25× | 9.4 | 729 | 871 | EQUAL |
| join_index | 50k | 570 | 630 | 407 | 553 | 1.40× | 66.8 | 286 | 434 | EQUAL |
| sort_fn | 50k | — | — | 2074 | 5946 | — | — | 3470 | 1984 | JS not run |
| orderby | 50k | — | — | 626 | 953 | — | — | 576 | 732 | JS not run |
| map_filter_reduce | 50k | 254 | 347 | 77.8 | 83.0 | 3.27× | 17.5 | 95.3 | 237 | EQUAL |
| strings | 50k | 950 | 1023 | 363 | 916 | 2.62× | 20.2 | 840 | 1008 | EQUAL |
| regex | 50k | 356 | 447 | 284 | 357 | 1.25× | 7.1 | 314 | 467 | EQUAL |
| recursion_tree | 50k | 9088 | 9810 | 1916 | 18179 | 4.74× | 95.1 | 4283 | 1992 | EQUAL |
| tail_loop | 50k | 195 | 220 | 58.2 | 60.7 | 3.34× | 46.4 | 46.9 | 191 | EQUAL |
| path_filter | 200k | 682 | 987 | 418 | 437 | 1.63× | 14.0 | 140 | 718 | EQUAL |
| path_tags | 200k | 595 | 780 | 352 | 844 | 1.69× | 18.7 | 159 | 725 | EQUAL |
| group_nested | 200k | 632 | 729 | 1083 | 3309 | 0.58× | 10.1 | 206 | 760 | EQUAL |
| join_scan | 200k | 10283 | 11290 | 4611 | 13927 | 2.23× | 22.2 | 2916 | 2392 | EQUAL |
| join_index | 200k | 2005 | 3320 | 1220 | 4170 | 1.64× | 291 | 1147 | 1725 | EQUAL |
| sort_fn | 200k | — | — | 27484 | 29384 | — | — | 14281 | 2392 | JS not run |
| orderby | 200k | — | — | 7742 | 8283 | — | — | 2384 | 2391 | JS not run |
| map_filter_reduce | 200k | 988 | 1114 | 969 | 1138 | 1.02× | 19.4 | 381 | 927 | EQUAL |
| strings | 200k | 5142 | 5177 | 6651 | 10010 | 0.77× | 80.3 | 3194 | 2625 | EQUAL |
| regex | 200k | 1346 | 1587 | 823 | 3883 | 1.64× | 26.3 | 1257 | 1836 | EQUAL |
| recursion_tree | 200k | — | — | 20357 | 27781 | — | — | 17160 | 2421 | JS not run |
| tail_loop | 200k | 1000 | 1027 | 538 | 681 | 1.86× | 136 | 190 | 753 | EQUAL |

Times in ms. JS/JVM > 1 means the JVM engine is faster. `*` = V8 ran GC during the measured evaluation, so heapΔ underestimates allocation.

### Compile (parse) time, median of 200, ms

| query | JS | JVM |
|---|---:|---:|
| path_filter | 0.090 | 0.102 |
| path_tags | 0.070 | 0.093 |
| group_agg | 0.048 | 0.041 |
| group_nested | 0.044 | 0.444 |
| join_scan | 0.085 | 0.095 |
| join_index | 0.057 | 0.016 |
| sort_fn | 0.092 | 0.049 |
| orderby | 0.049 | 0.102 |
| map_filter_reduce | 0.079 | 0.035 |
| strings | 0.054 | 0.014 |
| regex | 0.074 | 0.128 |
| recursion_tree | 0.082 | 0.021 |
| tail_loop | 0.067 | 0.042 |
| fib | 0.058 | 0.027 |

### JVM overhead of evaluation-control features (N=10k, interleaved rounds), median ms (median per-round ratio vs plain)

| query | plain | frame | novalidate | callbacks | bounds | guardrails |
|---|---:|---:|---:|---:|---:|---:|
| path_filter | 8.1 | 8.6 (×1.06) | 5.3 (×0.66) | 8.3 (×1.03) | 14.0 (×1.72) | 10.6 (×1.30) |
| group_nested | 8.5 | 9.2 (×1.08) | 6.2 (×0.72) | 9.2 (×1.09) | 9.4 (×1.10) | 10.3 (×1.23) |
| join_scan | 108 | 97.6 (×0.92) | 99.5 (×0.94) | 107 (×1.00) | 195 (×1.93) | 163 (×1.55) |
| map_filter_reduce | 12.6 | 12.9 (×1.02) | 9.7 (×0.75) | 12.6 (×1.00) | 20.0 (×1.58) | 17.4 (×1.39) |
| strings | 54.8 | 58.6 (×1.07) | 52.9 (×0.97) | 61.0 (×1.11) | 72.9 (×1.33) | 66.6 (×1.21) |
| recursion_tree | 321 | 332 (×1.03) | 330 (×0.98) | 364 (×1.05) | 514 (×1.57) | 442 (×1.35) |
| tail_loop | 8.8 | 8.9 (×1.01) | 6.3 (×0.70) | 9.7 (×1.10) | 16.3 (×1.85) | 13.1 (×1.48) |
