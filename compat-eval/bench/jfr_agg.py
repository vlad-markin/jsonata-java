#!/usr/bin/env python3
"""Aggregate a JFR recording: top self/inclusive frames (jdk.ExecutionSample) and top allocated
types (jdk.ObjectAllocationSample, by weight). Usage: jfr_agg.py rec.jfr [topN] [filter-substring]"""
import json, subprocess, sys, collections

jfr, top = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 25
filt = sys.argv[3] if len(sys.argv) > 3 else None   # only samples whose stack contains this
def events(kind):
    out = subprocess.run(['jfr', 'print', '--json', '--stack-depth', '96', '--events', kind, jfr],
                         capture_output=True, text=True, check=True).stdout
    return json.loads(out)['recording']['events']
def fname(f):
    m = f['method']; t = m['type']['name'].split('.')[-1]
    return f"{t}.{m['name']}"
self_c, incl_c, n = collections.Counter(), collections.Counter(), 0
for e in events('jdk.ExecutionSample'):
    frames = (e['values'].get('stackTrace') or {}).get('frames') or []
    if not frames: continue
    names = [fname(f) for f in frames]
    if filt and not any(filt in x for x in names): continue
    n += 1
    self_c[names[0]] += 1
    for x in set(names): incl_c[x] += 1
print(f"## CPU samples: {n}\n\n### top self frames\n")
for k, v in self_c.most_common(top): print(f"{100*v/n:5.1f}%  {k}")
print("\n### top inclusive frames\n")
for k, v in incl_c.most_common(top): print(f"{100*v/n:5.1f}%  {k}")
alloc, site, tot = collections.Counter(), collections.Counter(), 0
for e in events('jdk.ObjectAllocationSample'):
    v = e['values']; w = v.get('weight', 0)
    frames = (v.get('stackTrace') or {}).get('frames') or []
    names = [fname(f) for f in frames]
    if filt and not any(filt in x for x in names): continue
    cls = (v.get('objectClass') or {}).get('name', '?')
    alloc[cls] += w; tot += w
    # first engine frame = allocation site
    s = next((x for x in names if not x.split('.')[0] in ('ArrayList', 'Arrays', 'AbstractCollection', 'HashMap', 'LinkedHashMap', 'Object', 'String', 'StringBuilder', 'AbstractStringBuilder', 'StringLatin1', 'Long', 'Double', 'Integer')), names[0] if names else '?')
    site[s] += w
print(f"\n### top allocated types (sampled weight, total {tot/2**20:.0f} MB)\n")
for k, v in alloc.most_common(top): print(f"{100*v/max(tot,1):5.1f}%  {k}")
print("\n### top allocation sites (first non-JDK-collection frame)\n")
for k, v in site.most_common(top): print(f"{100*v/max(tot,1):5.1f}%  {k}")
