---
excludeFromSidebar: true # see vitepress sidebar config
description: Contains examples that we link to sometimes, but aren't in the main doc navs
---

# Example XPaths

[When using `processor: saxon`](miscellaneous.md#xpath-support-level), 
you have extensive possibilities using XPath in BlackLab input format configurations. Some noteworthy examples are shown below.

To learn more about modern XPath, [Altova's XPath 3 training](https://www.altova.com/training/xpath3) is a good resource, and there are many others.

## Capture punctuation between words

To capture text content between `<w/>` tags:

```yaml
punctPath: .//text()[not(ancestor::w)]
```

This captures any text node that is not a descendant of a `<w/>` tag.

Another possible approach:

```yaml
punctPath: .//text()[.!='' and preceding-sibling::tei:w]|.//tei:pc |.//tei:lb
```

This captures non-empty text nodes after a `<w/>` tag plus (the text contents of) `pc` or `lb` tags.

## Isolate a part of speech feature

Your data may have part of speech information that includes detailed features. Let's say this information is stored in an attribute with values like `UPosTag=PRON|Case=Nom|Person=3|PronType=Prs`. You can isolate the value of the `Case` feature like this:

```yaml
valuePath: replace(./@msd, '.*Case=([A-Za-z0-9]+).*', '$1')
```

## Use default if value is missing

If some of your words have a lemma attribute, and you want to index the value `_UNKNOWN_` if it's missing (perhaps to be able to locate these data problems easily), you can do that as follows:

```yaml
valuePath: ./(string(@lemma), '_UNKNOWN_')[1]
```

## Using either an attribute, or a standoff annotation

Again, let's say some of your words have `lemma` attributes. But some have the lemma in a separate `tei:join` element instead. You might use XPath to look up the appropriate value like this:

```yaml
valuePath: >-
  let $xid := @xml:id
  return if (@lemma) then @lemma 
  else if ($xid) then
    following-sibling::tei:join[@lemma][matches(@target,'#'||$xid||'( |$)')]/@lemma 
  else ()
```

Note how we can easily split XPath expressions over multiple lines using `>-` in YAML. (see [YAML multiline strings](https://yaml-multiline.info/)).

## For loops

You can even use `for` loops if necessary, e.g.:

```xquery
for $w in //tei:w[@xml:id]
return let $xid := $w/@xml:id
    return 
    if ($w/@lemma) then
        $w/@lemma else
            if ($xid) then
                let $join := $w/following-sibling::tei:join[@lemma][matches(@target,concat('#',$xid,'( |$)'))]
                return
                $join/@lemma else
                ()
```

Thanks to [@eduarddrenth](https://github.com/eduarddrenth) for the initial Saxon version and some of the examples.
