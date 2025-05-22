# BlackLab Reverse Proxy Server

A reverse proxy between the client and a BlackLab backend (either BLS or Solr). 

One goal of this is API compatibility, to offer one API regardless of whether you're using (an older version of) BLS or Solr+BlackLab.

Another goal is security: it is generally not recommended to open the Solr port directly to the internet. This provides a layer of security, enabling only the BlackLab functionality and shielding any other Solr functionality (and with it, potential security issues).

## TODO

This is a work in progress. Still to do:

- anything related to creating and adding private user corpora
- `/cache-info`
- ensure proxying to BlackLab Server works as well

## API versions (v3 / v4)

The Solr component and this proxy introduce version 4.0 of the BlackLab webservice API, with a few minor improvements. Pass `api=3` to get the old behaviour.

See [API versions](https://blacklab.ivdnt.org/server/rest-api/api-versions.html) for details.


## Configuration

Create a configuration file named `proxy.yaml` in one of these locations: `$HOME/.blacklab/`, `/etc/blacklab/`, with 
the following structure (for proxying to Solr+BlackLab):

```yaml
proxyTarget:
  url: http://localhost:8983/solr
  protocol: solr
  defaultCorpusName: test      # needed for "server-wide" BLS operations (Solr request always needs a core)
```

To proxy to a BlackLab Server instance instead:

```yaml
proxyTarget:
  url: http://localhost:8080/blacklab-server
  protocol: bls
```
