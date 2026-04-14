# JSON schemas

<!-- @include: ../../../_from_v5.md -->

Return a JSON schema for a (file) format related to BlackLab.


- **URL** (list schemas) : `/blacklab-server/schema/`
- **URL** (specific schema): `/blacklab-server/schema/NAME`

**Method** : `GET`

Available schemas:
- `input-format`: JSON schema for `.blf.yaml` files that configure an input format for data to be indexed by BlackLab.
- `bcql`: (NOT YET AVAILABLE) JSON schema for the BCQL JSON structure returned by BlackLab, parsed from the BCQL query.


## Success Response

**HTTP response code**: `200 OK`

The requested schema will be returned.
