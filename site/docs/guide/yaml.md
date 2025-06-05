---
excludeFromSidebar: true # see vitepress sidebar config
description: Contains examples that we link to sometimes, but aren't in the main doc navs
---


# Working with YAML

While YAML ([yaml.org](https://yaml.org/)) is a human-friendly format compared to XML and JSON, and is the recommended format for configuration files in BlackLab, there are a few things you should be aware of.

## When to quote values

Most values in YAML don't need to be quoted, e.g.:

```yaml
a: xyz
b: 123
c: 1 + 2 = 3
```

However, some characters have special significance in YAML. These are:

    :{}[],&*#?|-<>=!%@\)

If a value contains such a character, it is generally safest put quotes around it, e.g.:

```yaml
d: "x:y:z"
e: "[test]"
f: "@attribute"
```

## Lists vs. objects

Sometimes a value needs to be a list, even if that list only has one element. For example, to do string processing on a value (see <a href="#processing-values">Processing values</a>), you have to specify a *list* of processing steps (each of which is an object), even if there's only one step (note the dash):

```yaml
process:
- action: replace
  find: apple
  replace: pear
```

Here, "process" is a list containing one element: an object with three keys (action, find and replace). This is correct.

Howerver, it is easy to accidentally type this:

```yaml
process:
  action: replace
  find: apple
  replace: pear
```

Here, "process" is not a list of processing step objects, but a single object.

## Validating, syntax highlighting
You can validate YAML files online, for example using [YAMLlint](http://www.yamllint.com/). This can help check for mistakes and diagnose any resulting problems. 

Many text editors can also help you edit YAML files by highlighting characters with special meaning, so you can clearly see e.g. when a value should be quoted. Two examples are <a href='https://www.sublimetext.com/'>Sublime Text</a> and <a href='https://notepad-plus-plus.org/download/v7.4.2.html'>Notepad++</a>. If support for YAML highlighting isn't built-in to your favourite editor, it is often easy to install as a plugin. We recommend using an editor with syntax highlighting to edit your YAML files.

## More information
To learn more about YAML, see the [official site](http://yaml.org/) or this [introduction](http://docs.ansible.com/ansible/latest/YAMLSyntax.html).

