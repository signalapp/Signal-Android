let editor;
let timeout;
let session;

let logLines = ""; // Original logLines
let input = ""; // Search query input
let selectedLevels = []; // Log levels that are selected in checkboxes
let markers = []; // IDs of highlighted search markers
let matchRanges = []; // Ranges of all search matches
let matchCount = 0; // Total number of matches
let isFiltered = false;
let isCaseSensitive = false;

// Create custom text mode to color different levels of debug log lines
const TextMode = ace.require("ace/mode/text").Mode;
const TextHighlightRules = ace.require("ace/mode/text_highlight_rules").TextHighlightRules;
const Range = ace.require("ace/range").Range;

function main() {
  // Create Ace Editor using the custom mode
  editor = ace.edit("container", {
    mode: new CustomMode(),
    theme: "ace/theme/textmate",
    wrap: false, // Allow for horizontal scrolling
    readOnly: true,
    showGutter: false,
    highlightActiveLine: false,
    highlightSelectedWord: false, // Prevent Ace Editor from automatically highlighting all instances of a selected word (really laggy!)
    showPrintMargin: false,
  });

  editor.session.on("changeScrollTop", showScrollBar);
  editor.session.on("changeScrollLeft", showScrollBar);

  // Generate highlight markers for all search matches
  session = editor.getSession();
}

function CustomHighlightRules() {
  this.$rules = {
    start: [
      { token: "verbose", regex: "^.*\\sV\\s.*$" },
      { token: "debug", regex: "^.*\\sD\\s.*$" },
      { token: "info", regex: "^.*\\sI\\s.*$" },
      { token: "warning", regex: "^.*\\sW\\s.*$" },
      { token: "error", regex: "^.*\\sE\\s.*$" },
      { token: "none", regex: ".*" },
    ],
  };
}
CustomHighlightRules.prototype = new TextHighlightRules();

function CustomMode() {
  TextMode.call(this);
  this.HighlightRules = CustomHighlightRules;
}
CustomMode.prototype = Object.create(TextMode.prototype);
CustomMode.prototype.constructor = CustomMode;

// Show scrollbar that fades after a second since last scroll
function showScrollBar() {
  editor.container.classList.add("show-scrollbar");
  clearTimeout(timeout);
  timeout = setTimeout(() => editor.container.classList.remove("show-scrollbar"), 1000);
}

// Clear all search markers and match info
function clearMarkers() {
  markers.forEach((id) => session.removeMarker(id));
  markers = [];
  matchRanges = [];
  matchCount = 0;
}

// Highlight all instances of the search term
function highlightAllMatches(term) {
  clearMarkers();
  if (!term) {
    return;
  }

  const searchTerm = isCaseSensitive ? term : term.toLowerCase();
  session
    .getDocument()
    .getAllLines()
    .forEach((line, row) => {
      let start = 0;
      const caseLine = isCaseSensitive ? line : line.toLowerCase();
      while (true) {
        const index = caseLine.indexOf(searchTerm, start);
        if (index === -1) {
          break;
        }
        const range = new Range(row, index, row, index + term.length);
        markers.push(session.addMarker(range, "searchMatches", "text", false));
        matchRanges.push(range);
        start = index + term.length;
      }
    });
  matchCount = markers.length;
}

// Return index of current match
function getCurrentMatchIndex() {
  const current = editor.getSelection().getRange();
  return matchRanges.findIndex(
    (r) =>
      r.start.row === current.start.row &&
      r.start.column === current.start.column &&
      r.end.row === current.end.row &&
      r.end.column === current.end.column,
  );
}

function getSearchPosition() {
  if (input == "") {
    return "";
  }
  return matchCount == 0 ? "No match" : `${getCurrentMatchIndex() + 1} of ${matchCount}`;
}

function onSearchUp() {
  editor.find(input, {
    backwards: true,
    wrap: true,
    skipCurrent: true,
    caseSensitive: isCaseSensitive,
  });
}

function onSearchDown() {
  editor.find(input, {
    backwards: false,
    wrap: true,
    skipCurrent: true,
    caseSensitive: isCaseSensitive,
  });
}

function onSearchClose() {
  editor.setValue(logLines, -1);
  editor.getSelection().clearSelection();
  input = "";
  clearMarkers();
}

function onToggleCaseSensitive() {
  isCaseSensitive = !isCaseSensitive;
  (isFiltered) ? onFilter() : highlightAllMatches(input);
}

function onSearchInput(value) {
  input = value;
  highlightAllMatches(input);
  editor.find(input, {
    backwards: false,
    wrap: true,
    skipCurrent: false,
    caseSensitive: isCaseSensitive,
  });
}

function onSearch() {
  highlightAllMatches(input);
}

function onFilter() {
  isFiltered = true;
  editor.getSelection().clearSelection();
  clearMarkers();
  applyFilter();
}

function onFilterClose() {
  if (isFiltered) {
    isFiltered = false;
    if (selectedLevels.length === 0) {
      editor.setValue(logLines, -1);
    } else {
      const filtered = logLines
        .split("\n")
        .filter((line) => {
          return selectedLevels.some((level) => line.includes(level));
        })
        .join("\n");

      editor.setValue(filtered, -1);
    }
    highlightAllMatches(input);
  }
}

function onFilterLevel(sLevels) {
  selectedLevels = sLevels;

  if (isFiltered) {
    applyFilter();
  } else {
    if (selectedLevels.length === 0) {
      editor.setValue(logLines, -1);
      editor.scrollToRow(0);
    } else {
      const filtered = logLines
        .split("\n")
        .filter((line) => {
          return selectedLevels.some((level) => line.includes(level));
        })
        .join("\n");

      editor.setValue(filtered, -1);
    }
    onSearch();
  }
}

function applyFilter() {
  const filtered = logLines
    .split("\n")
    .filter((line) => {
      const newLine = isCaseSensitive ? line : line.toLowerCase();
      const lineMatch = newLine.includes(isCaseSensitive ? input : input.toLowerCase());
      const levelMatch = selectedLevels.length === 0 || selectedLevels.some((level) => line.includes(level));
      return lineMatch && levelMatch;
    })
    .join("\n");

  editor.setValue(filtered, -1);
}

function appendLines(lines) {
  editor.session.insert({ row: editor.session.getLength(), column: 0}, lines);
  logLines += lines;
}

function readLines(offset, limit) {
  const lines = logLines.split("\n")
  if (offset >= lines.length) {
    return "<<END OF INPUT>>";
  }

  return lines.slice(offset, offset + limit).join("\n")
}

main();