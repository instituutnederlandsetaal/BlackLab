# Document metadata

Get the metadata for a document.

**URL** : `/blacklab-server/<corpus-name>/docs/<pid>`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

### Content examples

```json
{
  "docPid": "bab0604",
  "docInfo": {
    "afz_loc_regio_norm": [
      "Noord-Europa"
    ],
    "afz_klasse": [
      "unknown"
    ],
    "afz_naam_norm": [
      "Machiel Jochems"
    ],
    "author": [
      "Machiel Jochems"
    ],
    "afz_loc_plaats_norm": [
      "Frederiksstad"
    ],
    "witnessYear_from": [
      "1666"
    ],
    "pid": [
      "bab0604"
    ],
    "adr_loc_land_norm": [
      "Nederland"
    ],
    "adr_naam_norm": [
      "Jannetje Alberts"
    ],
    "title": [
      "To Jannetje Alberts, 9 augustus 1666"
    ],
    "afz_loc_land_norm": [
      "Duitsland"
    ],
    "afz_geslacht": [
      "male"
    ],
    "afz_rel_tot_adr": [
      "friend (m)"
    ],
    "datum_jaar": [
      "1666"
    ],
    "fromInputFile": [
      "\/2.8TDN\/05-01-2009_001-002.exported.xml"
    ],
    "adr_loc_regio_norm": [
      "Noord-Holland"
    ],
    "autograaf": [
      "uncertain"
    ],
    "type_brief": [
      "private"
    ],
    "adr_loc_plaats_norm": [
      "Amsterdam"
    ],
    "signatuur": [
      "HCA 30-643"
    ],
    "lengthInTokens": 355,
    "mayView": true
  },
  "metadataFieldGroups": [
    {
      "name": "Letter",
      "fields": [
        "datum_jaar",
        "type_brief",
        "autograaf",
        "signatuur"
      ]
    },
    {
      "name": "Sender",
      "fields": [
        "afz_naam_norm",
        "afz_geslacht",
        "afz_klasse",
        "afz_geb_lftcat",
        "regiocode",
        "afz_rel_tot_adr"
      ]
    },
    {
      "name": "Addressee",
      "fields": [
        "adr_naam_norm",
        "adr_loc_plaats_norm",
        "adr_loc_land_norm",
        "adr_loc_regio_norm",
        "adr_loc_schip_norm"
      ]
    },
    {
      "name": "Sent from",
      "fields": [
        "afz_loc_plaats_norm",
        "afz_loc_land_norm",
        "afz_loc_regio_norm",
        "afz_loc_schip_norm"
      ]
    }
  ],
  "docFields": {
    "pidField": "pid",
    "titleField": "title",
    "authorField": "author",
    "dateField": "witnessYear_from"
  },
  "metadataFieldDisplayNames": {
    "adr_loc_land_norm": "Country",
    "adr_loc_plaats_norm": "Place",
    "adr_loc_regio_norm": "Region",
    "adr_loc_schip_norm": "Ship",
    "adr_naam_norm": "Name",
    "afz_geb_lftcat": "Age",
    "afz_geslacht": "Gender",
    "afz_klasse": "Class",
    "afz_loc_land_norm": "Country",
    "afz_loc_plaats_norm": "Place",
    "afz_loc_regio_norm": "Region",
    "afz_loc_schip_norm": "Ship",
    "afz_naam_norm": "Name",
    "afz_rel_tot_adr": "Relationship to addressee",
    "author": "Author",
    "autograaf": "Autograph",
    "datum_jaar": "Year",
    "fromInputFile": "From input file",
    "pid": "Pid",
    "regiocode": "Region of residence",
    "signatuur": "Signature",
    "title": "Title",
    "type_brief": "Text type",
    "witnessYear_from": "Witness year from"
  }
}
```


### Notes

- `docInfo` returns the metadata field values in arrays because some fields can have multiple values. Most usually have only one, though.
- Probably don't rely on the `docFields`, `metadataFieldDisplayNames` and `metadataFieldGroups` fields as they may be removed in a future API update. Instead, get this information once from the corpus information page, `/blacklab-server/<corpus-name>`.
