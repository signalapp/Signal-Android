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

// Show scrollbar that fades after a second since last scroll
let timeout;
function showScrollBar() {
  editor.container.classList.add("show-scrollbar");
  clearTimeout(timeout);
  timeout = setTimeout(() => editor.container.classList.remove("show-scrollbar"), 1000);
}

editor.session.on("changeScrollTop", showScrollBar);
editor.session.on("changeScrollLeft", showScrollBar);
