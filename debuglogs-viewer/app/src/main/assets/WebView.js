// Create custom text mode to color different levels of debug log lines
const TextMode = ace.require("ace/mode/text").Mode;
const TextHighlightRules = ace.require("ace/mode/text_highlight_rules").TextHighlightRules;

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

// Create Ace Editor using the custom mode
let editor = ace.edit("container", {
  mode: new CustomMode(),
  theme: "ace/theme/textmate",
  wrap: false, // Allow for horizontal scrolling
  readOnly: true,
  showGutter: false,
  highlightActiveLine: false,
  highlightSelectedWord: false, // Prevent Ace Editor from automatically highlighting all instances of a selected word (really laggy!)
  showPrintMargin: false,
});

// Get search bar functionalities
const input = document.getElementById("searchInput");
const prevButton = document.getElementById("prevButton");
const nextButton = document.getElementById("nextButton");
const cancelButton = document.getElementById("cancelButton");
const caseSensitiveButton = document.getElementById("caseSensitiveButton");

// Generate highlight markers for all search matches
const Range = ace.require("ace/range").Range;
const session = editor.getSession();

let markers = []; // IDs of highlighted search markers
let matchRanges = []; // Ranges of all search matches
let matchCount = 0; // Total number of matches
let caseSensitive = false;

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
    updateMatchPosition();
    return;
  }

  const searchTerm = caseSensitive ? term : term.toLowerCase();
  session
    .getDocument()
    .getAllLines()
    .forEach((line, row) => {
      let start = 0;
      const caseLine = caseSensitive ? line : line.toLowerCase();
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
  updateMatchPosition();
}

input.addEventListener("input", () => highlightAllMatches(input.value));

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

// Update the display for current match
function updateMatchPosition() {
  document.getElementById("matchCount").textContent = matchCount == 0 ? "No match" : `${getCurrentMatchIndex() + 1} / ${matchCount}`;
}

// Event listeners
prevButton.onclick = () => {
  editor.find(input.value, {
    backwards: true,
    wrap: true,
    skipCurrent: true,
    caseSensitive: caseSensitive,
  });
  updateMatchPosition();
};

nextButton.onclick = () => {
  editor.find(input.value, {
    backwards: false,
    wrap: true,
    skipCurrent: true,
    caseSensitive: caseSensitive,
  });
  updateMatchPosition();
};

cancelButton.onclick = () => {
  editor.getSelection().clearSelection();
  input.value = "";
  clearMarkers();
  updateMatchPosition();
  document.getElementById("searchBar").style.display = "none";
};

caseSensitiveButton.onclick = () => {
  caseSensitive = !caseSensitive;
  highlightAllMatches(input.value);
  caseSensitiveButton.classList.toggle("active", caseSensitive);
};

// Filter by log levels
let logLines = "";
function filterLogs() {
  const selectedLevels = Array.from(document.querySelectorAll('input[type="checkbox"]:checked')).map((cb) => cb.value);

  if (selectedLevels.length === 0) {
    // If no level is selected, show all
    editor.setValue(logLines, -1);
    return;
  }

  const filtered = logLines
    .split("\n")
    .filter((line) => {
      return selectedLevels.some((level) => line.includes(level));
    })
    .join("\n");

  editor.setValue(filtered, -1);
}

document.querySelectorAll('input[type="checkbox"]').forEach((cb) => {
  cb.addEventListener("change", filterLogs);
});