# Proposal: how to configure the aggregator

Add remote BlackLab Server instances to the aggregator configuration. The aggregator will then be able to query these remote instances and combine their results with local corpora. In `blacklab-server.yaml`:

```yaml
remote:
  servers:
  - name: corpora
    url: http://corpora.ivdnt.org/blacklab-server/
  - name: autosearch
    url: http://autosearch.ivdnt.org/blacklab-server/
```

Remote corpora will be represented by `corpusName@serverName`, e.g. `BaB@corpora` or `jan.niestadt:stuff@autosearch`. BlackLab will then be able to query these remote corpora and combine their results with local corpora.

Aggregated corpora are managed using the same BLS endpoints as for managing local (private) corpora,with specific parameter indicating that we're creating an aggregated corpus. There may be "global" aggregated corpora (managed using BlackLab admin mode) and private aggregated corpora (managed by users themselves, with names like `jan.niestadt@ivdnt.org:my-aggregated-corpus`).

