# List spans and relations

Return an overview of spans ("inline tags") and relations classes and types in the corpus, and their approximate frequencies.

**NOTE:** as a technical detail, spans are a special relation type with the builtin relation class `__tag`, but by default, we report them separately for convenience.

**NOTE2:** the reported frequencies don't take deleted documents into account. Therefore, if you've deleted documents, the frequencies may not be accurate.

**URL** : `/blacklab-server/<corpus-name>/relations`

**Method** : `GET`

| Parameter       | Description                                                                                                                                     |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `field`         | Annotated field to get relations for. If omitted, the main annotated field is used.                                                             |
| `classes`       | Comma-separated list of relations classes to return. If omitted, return all relations classes.                                                  |
| `onlyspans`     | If `true`, only returns spans. Equivalent to `classes=__tag`. Default: `false`                                                                  |
| `separatespans` | If `true` (the default), return spans differently than other relations. Set to `false` to return them as a regular relation with class `__tag`. |
| `limitvalues`   | Maximum number of values to return for attributes. Default: `200`                                                                                |


## Success Response

**Code** : `200 OK`

### Content examples

Result for `/relations`:

```json
{
    "spans": {
        "p": {
            "attributes": {
                "fixed": {
                    "valueListComplete": true,
                    "values": {
                        "true": 1,
                        "false": 2
                    }
                }
            },
            "count": 3
        }
    },
    "relations": {
        "dep": {
            "nsubj": {
                "count": 1
            },
            "nobj": {
                "count": 2
            },
            "nmod": {
                "count": 3
            }
        },
        "fam": {
            "mother": {
                "count": 1
            },
            "father": {
                "count": 2
            },
            "sibling": {
                "attributes": {
                    "gender": {
                        "valueListComplete": true,
                        "values": {
                            "male": 1,
                            "female": 2,
                            "other": 1
                        }
                    }
                },
                "count": 4
            }
        }
    }
}
```

Result for `/relations?onlyspans=true`:

```json
{
    "spans": {
        "p": {
            "attributes": {
                "fixed": {
                    "valueListComplete": true,
                    "values": {
                        "true": 1,
                        "false": 2
                    }
                }
            },
            "count": 3
        }
    }
}
```

Result for `/relations?classes=fam`:

```json
{
    "relations": {
        "fam": {
            "mother": {
                "count": 1
            },
            "father": {
                "count": 2
            },
            "sibling": {
                "attributes": {
                    "gender": {
                        "valueListComplete": true,
                        "values": {
                            "male": 1,
                            "female": 2,
                            "other": 1
                        }
                    }
                },
                "count": 4
            }
        }
    }
}
```

Result for `/relations?separatespans=false`:

```json
{
    "relations": {
        "__tag": {
            "p": {
                "attributes": {
                    "fixed": {
                        "valueListComplete": true,
                        "values": {
                            "true": 1,
                            "false": 2
                        }
                    }
                },
                "count": 3
            }
        },
        "dep": {
            "nsubj": {
                "count": 1
            },
            "nobj": {
                "count": 2
            },
            "nmod": {
                "count": 3
            }
        },
        "fam": {
            "mother": {
                "count": 1
            },
            "father": {
                "count": 2
            },
            "sibling": {
                "attributes": {
                    "gender": {
                        "valueListComplete": true,
                        "values": {
                            "male": 1,
                            "female": 2,
                            "other": 1
                        }
                    }
                },
                "count": 4
            }
        }
    }
}
```
