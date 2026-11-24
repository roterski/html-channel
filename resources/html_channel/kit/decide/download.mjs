import * as squint_core from '../squint-core.js';
import * as core from './core.mjs';
import * as decide_DOT_core from './core.mjs';
import * as edn from './edn.mjs';
import * as decide_DOT_edn from './edn.mjs';
var name_key = "html-channel:respondent";
var unsaved = squint_core.atom(false);
var done_button = function () {
return document.querySelector("[data-decision=status][data-choice=done]");

};
var page_state = function () {
if (("share" === squint_core.get(squint_core.deref(core.transport), "mode"))) {
return "share"} else {
if (squint_core.false_QMARK_(squint_core.get(squint_core.deref(core.state), "online"))) {
return "disconnected"} else {
if ("else") {
return "connected"} else {
return null}}};

};
var typed_name = function () {
const G__44_1 = document.querySelector(".hc-download-name");
const G__44_2 = (((G__44_1 == null)) ? (null) : (G__44_1.value));
const G__44_3 = (((G__44_2 == null)) ? (null) : (G__44_2.trim()));
if ((G__44_3 == null)) {
return null} else {
return squint_core.not_empty(G__44_3)};

};
var respondent = function () {
if (("connected" === page_state())) {
return edn.kw("author")} else {
return typed_name()};

};
var file_text = function () {
const t_1 = squint_core.deref(core.transport);
const reports_2 = core.done_reports();
const decisions_3 = squint_core.reduce((function (m, p__45) {
const vec___4_7 = p__45;
const id_8 = squint_core.nth(vec___4_7, 0, null);
const entry_9 = squint_core.nth(vec___4_7, 1, null);
if (squint_core.truth_(core.report_only_ids.has(id_8))) {
return m} else {
return squint_core.assoc(m, id_8, entry_9)};

}), ({}), Object.entries(squint_core.deref(core.answers)));
return `${";; html-channel answers. Read with: html-channel decisions --file <this file>\n"}${edn.write_fields([["html-channel/answers", 1], ["session", squint_core.get(t_1, "session")], ["page", squint_core.get(t_1, "page")], ["rev", squint_core.get(squint_core.deref(core.state), "rev")], ["respondent", respondent()], ["state", page_state()], ["exported-at", (new Date()).toISOString()], ["done", true], ["decisions", decisions_3], ["sources", squint_core.get(reports_2, "sources")], ["hunks", squint_core.get(reports_2, "hunks")], ["moot", squint_core.get(reports_2, "moot")]])??''}${"\n"}`;

};
var slug = function (s) {
return s.toLowerCase().replace((new RegExp("[^a-z0-9]+", "g")), "-").replace((new RegExp("^-+|-+$", "g")), "");

};
var file_name = function () {
const rev_1 = squint_core.get(squint_core.deref(core.state), "rev");
const who_2 = ((!("connected" === page_state())) ? ((() => {
const G__46_3 = typed_name();
const G__46_4 = (((G__46_3 == null)) ? (null) : (slug(G__46_3)));
if ((G__46_4 == null)) {
return null} else {
return squint_core.not_empty(G__46_4)};

})()) : (null));
return `${squint_core.get(squint_core.deref(core.transport), "page")??''}${((!(rev_1 == null)) ? (`${"@"}${rev_1??''}`) : (null))??''}${((squint_core.truth_(who_2)) ? (`-${who_2??''}`) : (null))??''}${".edn"}`;

};
var download_BANG_ = function () {
const url_1 = URL.createObjectURL((new Blob([file_text()], ({"type": "application/octet-stream"}))));
const a_2 = document.createElement("a");
a_2.href = url_1;
a_2.download = file_name();
document.body.append(a_2);
a_2.click();
a_2.remove();
setTimeout((function () {
return URL.revokeObjectURL(url_1);

}), 60000);
return squint_core.reset_BANG_(unsaved, false);

};
var controls = function () {
const box_1 = document.createElement("span");
const input_2 = document.createElement("input");
const btn_3 = document.createElement("button");
box_1.className = "hc-download";
input_2.className = "hc-download-name";
input_2.placeholder = "Your name (optional)";
input_2.setAttribute("aria-label", "Your name, optional, saved in the downloaded file");
input_2.value = (() => {
const or_5_6 = (() => {
try{
return localStorage.getItem(name_key);
}
catch(__4){
return null;
}

})();
if (squint_core.truth_(or_5_6)) {
return or_5_6} else {
return ""};

})();
input_2.addEventListener("change", (function (_) {
try{
return localStorage.setItem(name_key, input_2.value.trim());
}
catch(__7){
return null;
}
;

}));
btn_3.className = "hc-download-button";
btn_3.type = "button";
btn_3.textContent = "Download answers";
btn_3.addEventListener("click", (function (_) {
return download_BANG_();

}));
box_1.append(input_2, btn_3);
return box_1;

};
var add_banner_hint_BANG_ = function () {
const temp__23314__auto___1 = document.getElementById("decide-offline");
if (squint_core.truth_(temp__23314__auto___1)) {
const b_2 = temp__23314__auto___1;
const s_3 = document.createElement("span");
s_3.className = "hc-download-hint";
s_3.textContent = " To send them another way, Download answers saves them to a file.";
return b_2.append(s_3);
};

};
var start_BANG_ = function () {
const temp__23314__auto___1 = done_button();
if (squint_core.truth_(temp__23314__auto___1)) {
const done_2 = temp__23314__auto___1;
done_2.after(controls());
add_banner_hint_BANG_();
core.on_record_BANG_((function (_, __3) {
return squint_core.reset_BANG_(unsaved, true);

}));
return window.addEventListener("beforeunload", (function (e) {
if (squint_core.truth_((() => {
const and_4_5 = squint_core.deref(unsaved);
if (squint_core.truth_(and_4_5)) {
return !("connected" === page_state())} else {
return and_4_5};

})())) {
e.preventDefault();
return e.returnValue = "";
};

}));
};

};

export { respondent, typed_name, name_key, download_BANG_, add_banner_hint_BANG_, done_button, slug, file_text, start_BANG_, controls, unsaved, page_state, file_name }
