# Proposal: how to configure aggregated corpus

Define a remote corpus:

```yaml
type: remote
server: http://svotmc10.ivdnt.org/blacklab-server/
corpora:
- name: BaB
- name: gysseling
  localName: Gysseling   # (optional)
```

Define aggregated corpus:

```yaml
type: aggregated
displayName: Corpzilla, Destroyer of Worlds
description: Beware, He cometh!
corpora:
- name: BaB
- name: Gysseling
- name: Couranten
```

Combination, define aggregated corpus that includes remote corpora:

```yaml
type: aggregated
displayName: Corpzilla, Destroyer of Worlds
description: Beware, He cometh!

# Couranten staat lokaal en wordt opgenomen in aggregated corpus
corpora:
- name: Couranten

# BaB en Gysseling worden aangemaakt als remote corpora en ook opgenomen in aggregated corpus
remotes:
- server: http://svotmc10.ivdnt.org/blacklab-server/
  corpora:
  - name: BaB
  - name: gysseling
    localName: Gysseling
```
