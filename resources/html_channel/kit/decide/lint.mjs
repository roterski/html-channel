import * as squint_core from '../squint-core.js';
var min_sections = 6;
var max_sections = 12;
var heading_QMARK_ = function (re) {
return squint_core.some((function (_PERCENT_1) {
return squint_core.re_find(re, _PERCENT_1.textContent.trim());

}), Array.from(document.querySelectorAll("h2, h3")));

};
var describe = function (el) {
return `${"<"}${el.tagName.toLowerCase()??''}${((squint_core.truth_(squint_core.seq(el.id))) ? (`${" id=\""}${el.id??''}${"\""}`) : (""))??''}${(() => {
const temp__23255__auto___1 = squint_core.not_empty(el.getAttribute("name"));
if (squint_core.truth_(temp__23255__auto___1)) {
const n_2 = temp__23255__auto___1;
return `${" name=\""}${n_2??''}${"\""}`;
} else {
return ""};

})()??''}${(() => {
const temp__23255__auto___3 = squint_core.not_empty(el.getAttribute("class"));
if (squint_core.truth_(temp__23255__auto___3)) {
const c_4 = temp__23255__auto___3;
return `${" class=\""}${c_4??''}${"\""}`;
} else {
return ""};

})()??''}${">"}`;

};
var labeled_QMARK_ = function (el) {
const or_3_4 = (() => {
const G__6_1 = el.getAttribute("aria-label");
const G__6_2 = (((G__6_1 == null)) ? (null) : (G__6_1.trim()));
if ((G__6_2 == null)) {
return null} else {
return squint_core.seq(G__6_2)};

})();
if (squint_core.truth_(or_3_4)) {
return or_3_4} else {
const or_5_6 = el.hasAttribute("aria-labelledby");
if (squint_core.truth_(or_5_6)) {
return or_5_6} else {
const or_9_10 = (() => {
const G__7_7 = el.getAttribute("title");
const G__7_8 = (((G__7_7 == null)) ? (null) : (G__7_7.trim()));
if ((G__7_8 == null)) {
return null} else {
return squint_core.seq(G__7_8)};

})();
if (squint_core.truth_(or_9_10)) {
return or_9_10} else {
if (((() => {
const or_12_13 = (() => {
const G__8_11 = el.labels;
if ((G__8_11 == null)) {
return null} else {
return G__8_11.length};

})();
if (squint_core.truth_(or_12_13)) {
return or_12_13} else {
return 0};

})() > 0)) {
return true} else {
if (("BUTTON" === el.tagName)) {
return squint_core.seq(el.textContent.trim())} else {
return false}}};
};
};

};
var findings = function () {
const concepts_1 = Array.from(document.querySelectorAll("section[data-concept]"));
const n_2 = squint_core.count(concepts_1);
const controls_3 = Array.from(document.querySelectorAll("[data-instrument] :is(input:not([type=hidden]), select, textarea, button, [data-scrub])"));
return squint_core.concat((((((n_2 < min_sections)) || ((n_2 > max_sections)))) ? ([["sections", `${n_2??''}${" <section data-concept> on the page; an explorable has "}${min_sections??''}${"–"}${max_sections??''}`]]) : (null)), squint_core.lazy((function* () {
for (let G__4 of squint_core.iterable(concepts_1)) {
const s_5 = G__4;
if (squint_core.not(s_5.querySelector("[data-instrument]"))) {
yield ["no-instrument", `${"concept section \""}${s_5.dataset.concept??''}${"\" has no [data-instrument]"}`];}
}
return null;

})), ((squint_core.truth_(heading_QMARK_(/^Limits\b/))) ? (null) : ([["no-limits", "no h2/h3 heading starting with \"Limits\": say where the model stops being true"]])), ((squint_core.truth_(heading_QMARK_(/^Sources\b/))) ? (null) : ([["no-sources", "no h2/h3 heading starting with \"Sources\": list where the facts and constants come from"]])), squint_core.lazy((function* () {
for (let G__6 of squint_core.iterable(controls_3)) {
const c_7 = G__6;
if (squint_core.not(labeled_QMARK_(c_7))) {
yield ["unlabeled-control", `${describe(c_7)??''}${" in instrument \""}${c_7.closest("[data-instrument]").dataset.instrument??''}${"\" has no accessible name (a <label>, aria-label or title)"}`];}
}
return null;

})));

};
var start_BANG_ = function (report_BANG_) {
const run_1 = (function () {
return setTimeout((function () {
for (let G__2 of squint_core.iterable(findings())) {
const vec___3_6 = G__2;
const rule_7 = squint_core.nth(vec___3_6, 0, null);
const message_8 = squint_core.nth(vec___3_6, 1, null);
report_BANG_("explorable", message_8, rule_7)
}
return null;

}), 500);

});
if (("complete" === document.readyState)) {
return run_1()} else {
return window.addEventListener("load", run_1)};

};

export { min_sections, max_sections, heading_QMARK_, describe, labeled_QMARK_, findings, start_BANG_ }
