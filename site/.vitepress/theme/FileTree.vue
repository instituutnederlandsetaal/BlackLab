<script lang="ts">
// --- Imports ---
import { defineComponent, h, VNode, VNodeArrayChildren, VNodeChild, VNodeNormalizedChildren } from "vue";
import * as markdownit from 'markdown-it';

// --- Types ---
type Line = {
  name: string;
  comment: string;
  indentation: number;
  parents: Line[];
  children: Line[];
  lineNumber: number;
  isHighlighted: boolean;
}

// --- Constants ---
const DECORATORS = {
  empty: '   ',
  unclosed: '|  ',
  child: '├─ ',
  lastChild: '└─ ',
};
const LINE_REGEX = /^(\s*)([^\s]+)(?:\s+(.*))?$/;

// --- Utility Functions ---
/**
 * Extracts plain text from Vue VNodes (slot content).
 */
function extractTextFromVNodes(vnodes: VNodeArrayChildren[number] | VNodeNormalizedChildren): string {
  if (vnodes == null) return '';
  if (typeof vnodes === 'string' || typeof vnodes === 'number' || typeof vnodes === 'boolean') return vnodes.toString();
  if (Array.isArray(vnodes)) return vnodes.map(extractTextFromVNodes).join('');
  if ('type' in vnodes) return extractTextFromVNodes((vnodes as VNode).children);
  return '';
}

/**
 * Parses indented text into a tree of Line objects.
 */
function parseLines(text: string, tabWidth: number, highlightedLines: [number, number][]): Line[] {
  const tabAsSpaces = ' '.repeat(tabWidth);
  const lines: Line[] = text.trim().split("\n")
    .map(line => line.match(LINE_REGEX))
    .filter((match): match is RegExpMatchArray => !!match)
    .map<Line>((match, lineNumber) => {
      const indentation = match[1].replace(/\t/g, tabAsSpaces).length;
      const name = match[2];
      const comment = match[3]?.trim() || "";
      return {
        name,
        indentation,
        comment,
        parents: [],
        children: [],
        lineNumber,
        isHighlighted: highlightedLines.some(([a, b]) => lineNumber >= a && lineNumber <= b),
      } satisfies Line;
    });

  // Build parent/child relationships
  const stack: Line[] = [];
  for (const line of lines) {
    while (stack.length && stack[stack.length - 1].indentation >= line.indentation) stack.pop();
    if (stack.length) {
      const parent = stack[stack.length - 1];
      parent.children.push(line);
      line.parents = [...parent.parents, parent];
    }
    stack.push(line);
  }
  return lines;
}

function parseHighlightedLineNumbers(hl?: string): [number, number][] {
  return hl?.split(',')
    .map(h => h.trim())
    .filter(h => h.match(/^\d+(-\d+)?$/))
    .map(h => {
      const [start, end] = h.split('-').map(Number);
      return [start - 1, (end && end > start) ? end - 1 : start - 1] as [number, number];
    }) ?? [];
}

/**
 * Renders the tree connector and filename for a line.
 */
function renderConnectorAndName(line: Line): VNodeChild {
  const parent = line.parents[line.parents.length - 1];
  const isDirectory = line.children.length > 0;
  // Build the left-side connector string
  const prologue = line.parents.map(p => {
    const closesAt = p.children[p.children.length - 1].lineNumber;
    if (closesAt === line.lineNumber) return DECORATORS.lastChild;
    if (p === parent) return DECORATORS.child;
    if (closesAt > line.lineNumber) return DECORATORS.unclosed;
    return DECORATORS.empty;
  }).join('');
  const name = line.name + (isDirectory && !line.name.endsWith('/') ? '/' : '');
  return h('div', { class: `name-and-connector ${line.isHighlighted ? 'hl' : ''}` }, [
    prologue && h('pre', prologue),
    h('span', name),
  ]);
}

/**
 * Renders the comment for a line, using markdown-it for formatting.
 */
function renderComment(line: Line): VNodeChild {
  let comment = '\u00A0'; // non-breaking space
  if (line.comment) {
    // @ts-expect-error markdown-it is not a nice looking module
    const mdParser = markdownit.default();
    comment = mdParser.renderInline('<--- ' + line.comment);
  }
  return h('div', {
    class: `comment ${line.isHighlighted ? 'hl' : ''}`,
    innerHTML: comment.replace(/^<p>|<\/p>$/, ''),
  });
}

// --- Main Component ---
const FileTree = defineComponent({
  name: "FileTree",
  props: { 
    /** Tab length as a number of spaces */
    tabSize: { type: Number, default: 2 }, 
    /** Highlighted lines as a comma-separated list containing numbers or two numbers with a dash, indicating a range, e.g. 1,2,3-5. Lines start at 1. */
    hl: String 
  },
  render() {
    // Parse highlight ranges from prop
    const highlightedLineNumbers = parseHighlightedLineNumbers(this.hl);
    // Get text from default slot
    const textContent = extractTextFromVNodes(this.$slots.default?.() || []);
    const lines = parseLines(textContent, this.tabSize, highlightedLineNumbers);
    return h('div', { class: 'file-tree' }, [
      h('div', {class: 'structure'}, lines.map(renderConnectorAndName)),
      h('div', {class: 'comments'}, lines.map(renderComment)),
    ]);
  },
});

export default FileTree;
</script>

<style scoped>
.file-tree {
  display: inline-flex;
  flex-wrap: nowrap;
  white-space: nowrap;
  > * > * { height: 1.8em; line-height: 1.8em; }
}

pre { margin: 0; padding: 0; display: inline-block; white-space: pre;}
.comment {
  padding-left: 1em;
  width: 100%;
}

.hl {
  background-color: var(--vp-code-line-highlight-color);
}

</style>