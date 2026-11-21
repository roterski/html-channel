import * as squint_core from '../squint-core.js';
import * as code_links from './code_links.mjs';
import * as decide_DOT_code_links from './code_links.mjs';
import * as core from './core.mjs';
import * as decide_DOT_core from './core.mjs';
import * as highlight from './highlight.mjs';
import * as decide_DOT_highlight from './highlight.mjs';
import * as versions from './../viz/versions.mjs';
import * as viz_DOT_versions from './../viz/versions.mjs';
var layout_key = "html-channel:diff-layout";
var layouts = ["auto", "split", "unified"];
var context_lines = 3;
var min_similarity = 0.25;
var max_marked_line = 1000;
var hunk_map = squint_core.atom(({}));
var jsdiff_promise = squint_core.atom(null);
var jsdiff_BANG_ = function () {
const or_1_2 = squint_core.deref(jsdiff_promise);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return squint_core.reset_BANG_(jsdiff_promise, import(versions.jsdiff))};

};
var el_BANG_ = /* @__PURE__ */ (() => {
const impl26_1 = (function (tag, attrs, children) {
const e_2 = document.createElement(tag);
for (let G__3 of squint_core.iterable(Object.entries(((squint_core.truth_(attrs)) ? (attrs) : (({})))))) {
const vec___4_7 = G__3;
const k_8 = squint_core.nth(vec___4_7, 0, null);
const v_9 = squint_core.nth(vec___4_7, 1, null);
e_2.setAttribute(k_8, v_9)
};
for (let G__10 of squint_core.iterable(children)) {
const c_11 = G__10;
if (!(c_11 == null)) {
e_2.append(c_11)}
};
return e_2;

});
const f22 = (function (arg23, arg24, ...rest25) {
const self__23546__auto___12 = this;
return impl26_1.call(self__23546__auto___12, arg23, arg24, (((rest25.length === 0)) ? (null) : (rest25)));

});
(f22["squint$lang$variadic"] = impl26_1);
return f22;

})();
var fnv1a = function (s) {
let i_1 = 0;
let h_2 = 2166136261;
while(true){
if ((i_1 < s.length)) {
let G__3 = (i_1 + 1);
let G__4 = Math.imul((h_2 ^ s.charCodeAt(i_1)), 16777619);
i_1 = G__3;
h_2 = G__4;
continue;
} else {
return (h_2 >>> 0).toString(16).padStart(8, "0")};
;break;
}
;

};
var ensure_newline = function (s) {
if (squint_core.truth_(((squint_core._EQ_("", s)) || (s.endsWith("\n"))))) {
return s} else {
return `${s??''}${"\n"}`};

};
var patch_path = function (name) {
if (squint_core.truth_((() => {
const and_1_2 = squint_core.seq(name);
if (squint_core.truth_(and_1_2)) {
return !("/dev/null" === name)} else {
return and_1_2};

})())) {
return name.replace(/^[ab]\//, "");
};

};
var file_name = function (path) {
return squint_core.last(((squint_core.truth_(path)) ? (path) : ("")).split("/"));

};
var numeric_sort = function (xs) {
return squint_core.into_array(xs).sort((function (a, b) {
return (a - b);

}));

};
var hunk_of = function (h) {
return ({"old-start": h.oldStart, "old-lines": h.oldLines, "new-start": h.newStart, "new-lines": h.newLines, "lines": squint_core.filterv((function (_PERCENT_1) {
return squint_core.not(_PERCENT_1.startsWith("\\"));

}), h.lines)});

};
var patch_model = function (jsdiff, pre) {
return ({"files": squint_core.filterv((function (_PERCENT_1) {
return squint_core.seq(squint_core.get(_PERCENT_1, "hunks"));

}), squint_core.mapv((function (f) {
return ({"path": (() => {
const or_1_2 = patch_path(f.newFileName);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
const or_3_4 = patch_path(f.oldFileName);
if (squint_core.truth_(or_3_4)) {
return or_3_4} else {
return ""};
};

})(), "deleted?": (patch_path(f.newFileName) == null), "hunks": squint_core.mapv(hunk_of, f.hunks)});

}), jsdiff.parsePatch(pre.textContent))), "repo": squint_core.not_empty(pre.getAttribute("data-repo")), "old-offset": 0, "new-offset": 0, "lang-scope": [pre]});

};
var texts_model = function (jsdiff, fig) {
const old_pre_1 = fig.querySelector("pre[data-diff-old]");
const new_pre_2 = fig.querySelector("pre[data-diff-new]");
if (squint_core.not(((squint_core.truth_(old_pre_1)) ? (new_pre_2) : (old_pre_1)))) {
return ({"error": "needs a <pre data-diff-old> and a <pre data-diff-new> inside it"})} else {
const old_src_3 = code_links.parse_src(old_pre_1.getAttribute("data-src"));
const new_src_4 = code_links.parse_src(new_pre_2.getAttribute("data-src"));
const src_5 = ((squint_core.truth_(new_src_4)) ? (new_src_4) : (old_src_3));
const path_6 = (() => {
const or_7_8 = squint_core.get(src_5, "path");
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
return ""};

})();
const old_text_9 = ensure_newline(old_pre_1.textContent);
const new_text_10 = ensure_newline(new_pre_2.textContent);
const p_11 = jsdiff.structuredPatch(path_6, path_6, old_text_9, new_text_10, "", "", ({"context": context_lines}));
return ({"files": squint_core.filterv((function (_PERCENT_1) {
return squint_core.seq(squint_core.get(_PERCENT_1, "hunks"));

}), [({"path": path_6, "hunks": squint_core.mapv(hunk_of, p_11.hunks)})]), "texts": ({"old": old_text_9, "new": new_text_10}), "repo": squint_core.get(src_5, "repo"), "old-offset": ((() => {
const or_12_13 = squint_core.get(old_src_3, "start");
if (squint_core.truth_(or_12_13)) {
return or_12_13} else {
const or_14_15 = squint_core.get(new_src_4, "start");
if (squint_core.truth_(or_14_15)) {
return or_14_15} else {
return 1};
};

})() - 1), "new-offset": ((() => {
const or_16_17 = squint_core.get(new_src_4, "start");
if (squint_core.truth_(or_16_17)) {
return or_16_17} else {
const or_18_19 = squint_core.get(old_src_3, "start");
if (squint_core.truth_(or_18_19)) {
return or_18_19} else {
return 1};
};

})() - 1), "lang-scope": [fig, old_pre_1, new_pre_2]});
};

};
var rows = function (hunk) {
let ls_1 = squint_core.get(hunk, "lines");
let o_2 = squint_core.get(hunk, "old-start");
let n_3 = squint_core.get(hunk, "new-start");
let out_4 = [];
while(true){
const temp__23255__auto___5 = squint_core.first(ls_1);
if (squint_core.truth_(temp__23255__auto___5)) {
const l_6 = temp__23255__auto___5;
const text_7 = squint_core.subs(l_6, 1);
const G__27_8 = squint_core.subs(l_6, 0, 1);
switch (G__27_8) {case "-":
let G__10 = squint_core.rest(ls_1);
let G__11 = (o_2 + 1);
let G__12 = n_3;
let G__13 = squint_core.conj(out_4, ({"kind": "del", "old": o_2, "text": text_7}));
ls_1 = G__10;
o_2 = G__11;
n_3 = G__12;
out_4 = G__13;
continue;

break;
case "+":
let G__14 = squint_core.rest(ls_1);
let G__15 = o_2;
let G__16 = (n_3 + 1);
let G__17 = squint_core.conj(out_4, ({"kind": "add", "new": n_3, "text": text_7}));
ls_1 = G__14;
o_2 = G__15;
n_3 = G__16;
out_4 = G__17;
continue;

break;
default:
let G__18 = squint_core.rest(ls_1);
let G__19 = (o_2 + 1);
let G__20 = (n_3 + 1);
let G__21 = squint_core.conj(out_4, ({"kind": "ctx", "old": o_2, "new": n_3, "text": text_7}));
ls_1 = G__18;
o_2 = G__19;
n_3 = G__20;
out_4 = G__21;
continue;
};
} else {
return out_4};
;break;
}
;

};
var blocks = function (rows) {
const out_1 = [];
const cur_2 = squint_core.atom(null);
const flush_BANG__3 = (function () {
if (squint_core.truth_(squint_core.deref(cur_2))) {
out_1.push(squint_core.deref(cur_2));
return squint_core.reset_BANG_(cur_2, null);
};

});
for (let G__4 of squint_core.iterable(rows)) {
const r_5 = G__4;
const G__28_6 = squint_core.get(r_5, "kind");
switch (G__28_6) {case "ctx":
flush_BANG__3();
out_1.push(({"ctx": r_5}));

break;
case "del":
if (squint_core.truth_((() => {
const and_8_9 = squint_core.deref(cur_2);
if (squint_core.truth_(and_8_9)) {
return squint_core.seq(squint_core.get(squint_core.deref(cur_2), "adds"))} else {
return and_8_9};

})())) {
flush_BANG__3()};
if (squint_core.truth_(squint_core.deref(cur_2))) {
} else {
squint_core.reset_BANG_(cur_2, ({"dels": [], "adds": []}))};
squint_core.get(squint_core.deref(cur_2), "dels").push(r_5);

break;
default:
if (squint_core.truth_(squint_core.deref(cur_2))) {
} else {
squint_core.reset_BANG_(cur_2, ({"dels": [], "adds": []}))};
squint_core.get(squint_core.deref(cur_2), "adds").push(r_5)}
};
flush_BANG__3();
return out_1;

};
var mark_words_BANG_ = function (jsdiff, rows) {
for (let G__1 of squint_core.iterable(blocks(rows))) {
const b_2 = G__1;
if (squint_core.truth_(squint_core.get(b_2, "dels"))) {
for (let G__3 of squint_core.iterable(squint_core.map(squint_core.vector, squint_core.get(b_2, "dels"), squint_core.get(b_2, "adds")))) {
const vec___4_7 = G__3;
const d_8 = squint_core.nth(vec___4_7, 0, null);
const a_9 = squint_core.nth(vec___4_7, 1, null);
if ((((squint_core.get(d_8, "text").length <= max_marked_line)) && ((squint_core.get(a_9, "text").length <= max_marked_line)))) {
const parts_10 = jsdiff.diffWordsWithSpace(squint_core.get(d_8, "text"), squint_core.get(a_9, "text"));
const common_11 = squint_core.reduce(squint_core._PLUS_, 0, squint_core.map((function (_PERCENT_1) {
if (squint_core.truth_((() => {
const or_12_13 = _PERCENT_1.added;
if (squint_core.truth_(or_12_13)) {
return or_12_13} else {
return _PERCENT_1.removed};

})())) {
return 0} else {
return _PERCENT_1.value.length};

}), parts_10));
const longest_14 = squint_core.max(1, squint_core.get(d_8, "text").length, squint_core.get(a_9, "text").length);
if (((common_11 / longest_14) >= min_similarity)) {
const dm_15 = [];
const am_16 = [];
const o_17 = squint_core.atom(0);
const n_18 = squint_core.atom(0);
for (let G__19 of squint_core.iterable(parts_10)) {
const p_20 = G__19;
const len_21 = p_20.value.length;
if (squint_core.truth_(p_20.removed)) {
dm_15.push([squint_core.deref(o_17), (squint_core.deref(o_17) + len_21)]);
squint_core.swap_BANG_(o_17, squint_core._PLUS_, len_21)} else {
if (squint_core.truth_(p_20.added)) {
am_16.push([squint_core.deref(n_18), (squint_core.deref(n_18) + len_21)]);
squint_core.swap_BANG_(n_18, squint_core._PLUS_, len_21)} else {
if ("else") {
squint_core.swap_BANG_(o_17, squint_core._PLUS_, len_21);
squint_core.swap_BANG_(n_18, squint_core._PLUS_, len_21)} else {
}}}
};
(d_8["marks"] = dm_15);
(a_9["marks"] = am_16)}}
}}
}
return null;

};
var language = async function (model, path) {
const req_1 = (await (async () => {
const or_2_3 = highlight.declared(squint_core.get(model, "lang-scope"));
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return highlight.request_for_file(file_name(path))};

})());
if (squint_core.truth_((((req_1 == null)) || (squint_core.get(req_1, "plain"))))) {
return highlight.language_of(req_1, null)} else {
try{
return highlight.language_of(req_1, squint_core.get((await highlight.shiki_BANG_()), "names"));
}
catch(e_4){
return ({"problem": `${"Shiki didn't load ("}${e_4.message??''}${")"}`});
}
};

};
var tokens_or_nil = async function (lang, code, problem) {
if (squint_core.truth_(((squint_core.truth_(lang)) ? (squint_core.not(squint_core.deref(problem))) : (lang)))) {
try{
return (await highlight.tokenize_BANG_(lang, code));
}
catch(e_1){
squint_core.reset_BANG_(problem, `${lang??''}${" didn't load ("}${e_1.message??''}${")"}`);
return null;
}
;
};

};
var tokens_of_lines = async function (lang, texts, problem) {
if (squint_core.truth_(squint_core.seq(texts))) {
return (await tokens_or_nil(lang, squint_core.into_array(texts).join("\n"), problem));
};

};
var color_patch_rows_BANG_ = async function (lang, rows, problem) {
const olds_1 = squint_core.filterv("old", rows);
const news_2 = squint_core.filterv("new", rows);
const ot_3 = (await tokens_of_lines(lang, squint_core.mapv("text", olds_1), problem));
const nt_4 = (await tokens_of_lines(lang, squint_core.mapv("text", news_2), problem));
if (squint_core.truth_(ot_3)) {
for (let G__5 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, olds_1))) {
const vec___6_9 = G__5;
const i_10 = squint_core.nth(vec___6_9, 0, null);
const r_11 = squint_core.nth(vec___6_9, 1, null);
(r_11["oldTokens"] = ot_3[i_10])
}};
if (squint_core.truth_(nt_4)) {
for (let G__12 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, news_2))) {
const vec___13_16 = G__12;
const i_17 = squint_core.nth(vec___13_16, 0, null);
const r_18 = squint_core.nth(vec___13_16, 1, null);
(r_18["newTokens"] = nt_4[i_17])
}};
for (let G__19 of squint_core.iterable(rows)) {
const r_20 = G__19;
(r_20["tokens"] = ((("del" === squint_core.get(r_20, "kind"))) ? (squint_core.get(r_20, "oldTokens")) : (squint_core.get(r_20, "newTokens"))))
}
return null;

};
var color_text_rows_BANG_ = function (ot, nt, rows) {
for (let G__1 of squint_core.iterable(rows)) {
const r_2 = G__1;
(r_2["tokens"] = ((("del" === squint_core.get(r_2, "kind"))) ? ((() => {
const G__29_3 = ot;
if ((G__29_3 == null)) {
return null} else {
return G__29_3[(squint_core.get(r_2, "old") - 1)]};

})()) : ((() => {
const G__30_4 = nt;
if ((G__30_4 == null)) {
return null} else {
return G__30_4[(squint_core.get(r_2, "new") - 1)]};

})())))
}
return null;

};
var line_nodes = function (text, tokens, marks) {
const frag_1 = document.createDocumentFragment();
const pos_2 = squint_core.atom(0);
for (let G__3 of squint_core.iterable(((squint_core.truth_(tokens)) ? (tokens) : ([({"content": text})])))) {
const t_4 = G__3;
const start_5 = squint_core.deref(pos_2);
const end_6 = (start_5 + t_4.content.length);
const cuts_7 = numeric_sort(squint_core.distinct(squint_core.concat([start_5, end_6], squint_core.filter((function (_PERCENT_1) {
if ((_PERCENT_1 > start_5)) {
return (_PERCENT_1 < end_6)} else {
return false};

}), squint_core.mapcat(squint_core.identity, marks)))));
for (let G__8 of squint_core.iterable(squint_core.map(squint_core.vector, cuts_7, squint_core.rest(cuts_7)))) {
const vec___9_12 = G__8;
const a_13 = squint_core.nth(vec___9_12, 0, null);
const b_14 = squint_core.nth(vec___9_12, 1, null);
const s_15 = highlight.token_span(t_4, squint_core.subs(t_4.content, (a_13 - start_5), (b_14 - start_5)));
if (squint_core.truth_(squint_core.some((function (p__31) {
const vec___16_19 = p__31;
const ms_20 = squint_core.nth(vec___16_19, 0, null);
const me_21 = squint_core.nth(vec___16_19, 1, null);
if ((ms_20 <= a_13)) {
return (b_14 <= me_21)} else {
return false};

}), marks))) {
s_15.classList.add("hc-diff-word")};
frag_1.append(s_15)
};
squint_core.reset_BANG_(pos_2, end_6)
};
return frag_1;

};
var signs = ({"del": "−", "add": "+", "ctx": " "});
var spoken = ({"del": "removed", "add": "added"});
var sign_cell = function (kind) {
return el_BANG_("td", ({"class": "hc-diff-sign"}), el_BANG_("span", ({"aria-hidden": "true"}), squint_core.get(signs, kind)), (() => {
const temp__23314__auto___1 = squint_core.get(spoken, kind);
if (squint_core.truth_(temp__23314__auto___1)) {
const w_2 = temp__23314__auto___1;
return el_BANG_("span", ({"class": "hc-sr"}), w_2);
};

})());

};
var side_cells = function (row, number) {
if (squint_core.truth_(row)) {
return [el_BANG_("td", ({"class": "hc-diff-no"}), `${number??''}`), sign_cell(squint_core.get(row, "kind")), el_BANG_("td", ({"class": "hc-diff-code"}), line_nodes(squint_core.get(row, "text"), squint_core.get(row, "tokens"), squint_core.get(row, "marks")))]} else {
return [el_BANG_("td", ({"class": "hc-diff-no hc-diff-empty"})), el_BANG_("td", ({"class": "hc-diff-sign hc-diff-empty"})), el_BANG_("td", ({"class": "hc-diff-code hc-diff-empty"}))]};

};
var unified_table = function (rows, num) {
return el_BANG_("table", ({"class": "hc-diff-table"}), (() => {
const tb_1 = el_BANG_("tbody", ({}));
for (let G__2 of squint_core.iterable(rows)) {
const r_3 = G__2;
tb_1.append(el_BANG_("tr", ({"class": `hc-diff-${squint_core.get(r_3, "kind")??''}`}), el_BANG_("td", ({"class": "hc-diff-no"}), ((squint_core.truth_(squint_core.get(r_3, "old"))) ? (`${num("old", r_3)??''}`) : (""))), el_BANG_("td", ({"class": "hc-diff-no"}), ((squint_core.truth_(squint_core.get(r_3, "new"))) ? (`${num("new", r_3)??''}`) : (""))), sign_cell(squint_core.get(r_3, "kind")), el_BANG_("td", ({"class": "hc-diff-code"}), line_nodes(squint_core.get(r_3, "text"), squint_core.get(r_3, "tokens"), squint_core.get(r_3, "marks")))))
};
return tb_1;

})());

};
var split_row = function (left, right, num) {
const tr_1 = el_BANG_("tr", ({}));
const cell_kind_2 = (function (r) {
if (squint_core.truth_(r)) {
return `hc-diff-${squint_core.get(r, "kind")??''}`} else {
return "hc-diff-none"};

});
for (let G__3 of squint_core.iterable(side_cells(left, ((squint_core.truth_(left)) ? (num("old", left)) : (null))))) {
const c_4 = G__3;
c_4.classList.add(cell_kind_2(left));
tr_1.append(c_4)
};
for (let G__5 of squint_core.iterable(side_cells(right, ((squint_core.truth_(right)) ? (num("new", right)) : (null))))) {
const c_6 = G__5;
c_6.classList.add(cell_kind_2(right));
tr_1.append(c_6)
};
return tr_1;

};
var split_table = function (rows, num) {
return el_BANG_("table", ({"class": "hc-diff-table"}), (() => {
const tb_1 = el_BANG_("tbody", ({}));
for (let G__2 of squint_core.iterable(blocks(rows))) {
const b_3 = G__2;
const temp__23255__auto___4 = squint_core.get(b_3, "ctx");
if (squint_core.truth_(temp__23255__auto___4)) {
const r_5 = temp__23255__auto___4;
tb_1.append(split_row(r_5, r_5, num))} else {
for (let G__6 of squint_core.iterable(squint_core.range(squint_core.max(squint_core.count(squint_core.get(b_3, "dels")), squint_core.count(squint_core.get(b_3, "adds")))))) {
const i_7 = G__6;
tb_1.append(split_row(squint_core.get(squint_core.get(b_3, "dels"), i_7), squint_core.get(squint_core.get(b_3, "adds"), i_7), num))
}}
};
return tb_1;

})());

};
var body = function (rows, num) {
const split_1 = el_BANG_("div", ({"class": "hc-diff-split"}));
(split_1["hcBuild"] = (function () {
return split_table(rows, num);

}));
return el_BANG_("div", ({"class": "hc-diff-body"}), el_BANG_("div", ({"class": "hc-diff-unified"}), unified_table(rows, num)), split_1);

};
var build_shown_splits_BANG_ = function (root) {
for (let G__1 of squint_core.iterable(Array.from(root.querySelectorAll(".hc-diff-split")))) {
const d_2 = G__1;
const build_3 = d_2["hcBuild"];
if (squint_core.truth_(((squint_core.truth_(build_3)) ? (!("none" === getComputedStyle(d_2).display)) : (build_3)))) {
(d_2["hcBuild"] = null);
d_2.append(build_3())}
}
return null;

};
var range_label = function (start, count) {
return `${(((count === 0)) ? ((start - 1)) : (start))??''}${","}${count??''}`;

};
var hunk_head = function (model, p__32, hunk) {
const map___1_3 = p__32;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const path_5 = squint_core.get(map___1_4, "path");
const deleted_QMARK__6 = squint_core.get(map___1_4, "deleted?");
const new_start_7 = (squint_core.get(hunk, "new-start") + squint_core.get(model, "new-offset"));
const nl_8 = squint_core.get(hunk, "new-lines");
const head_9 = `${"@@ -"}${range_label((squint_core.get(hunk, "old-start") + squint_core.get(model, "old-offset")), squint_core.get(hunk, "old-lines"))??''}${" +"}${range_label(new_start_7, nl_8)??''}${" @@"}`;
return el_BANG_("div", ({"class": "hc-diff-hunk-head"}), el_BANG_("span", ({}), head_9), ((squint_core.truth_((() => {
const and_10_11 = squint_core.get(model, "repo");
if (squint_core.truth_(and_10_11)) {
const and_12_13 = squint_core.seq(path_5);
if (squint_core.truth_(and_12_13)) {
return squint_core.not(deleted_QMARK__6)} else {
return and_12_13};
} else {
return and_10_11};

})())) ? (el_BANG_("code", ({"data-src": `${squint_core.get(model, "repo")??''}${":"}${path_5??''}${"#L"}${new_start_7}${(((nl_8 > 1)) ? (`-L${(new_start_7 + nl_8 + -1)}`) : (""))??''}`}), `${path_5??''}${":"}${new_start_7}`)) : (null)));

};
var file_head = function (model, p__33) {
const map___1_3 = p__33;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const path_5 = squint_core.get(map___1_4, "path");
const deleted_QMARK__6 = squint_core.get(map___1_4, "deleted?");
return el_BANG_("div", ({"class": "hc-diff-file-head"}), ((squint_core.not(squint_core.seq(path_5))) ? (el_BANG_("code", ({"data-lang": "text"}), "(no file name)")) : (((squint_core.truth_(deleted_QMARK__6)) ? (el_BANG_("code", ({"data-lang": "text"}), `${path_5??''}${" (deleted)"}`)) : (((squint_core.truth_(squint_core.get(model, "repo"))) ? (el_BANG_("code", ({"data-src": `${squint_core.get(model, "repo")??''}${":"}${path_5??''}`}), path_5)) : ((("else") ? (el_BANG_("code", ({"data-lang": "text"}), path_5)) : (null)))))))));

};
var choice_label = function (choice) {
return `${squint_core.subs(choice, 0, 1).toUpperCase()??''}${squint_core.subs(choice, 1)}`;

};
var choice_controls = function (id, choices) {
const d_1 = el_BANG_("div", ({"class": "hc-hunk-choice"}));
for (let G__2 of squint_core.iterable(choices)) {
const c_3 = G__2;
d_1.append(el_BANG_("button", ({"type": "button", "data-decision": id, "data-choice": c_3}), choice_label(c_3)))
};
d_1.append(el_BANG_("button", ({"type": "button", "data-clear": id, "class": "hc-hunk-clear"}), "Clear"));
d_1.append(el_BANG_("textarea", ({"rows": "2", "data-decision": `${id??''}-note`, "placeholder": "Another answer, or a comment"})));
return d_1;

};
var gap = function (model, from, to, delta, ot) {
if ((from <= to)) {
const n_1 = ((to - from) + 1);
const label_2 = (function (verb) {
return `${verb??''}${" "}${n_1}${" unchanged line"}${(((1 === n_1)) ? ("") : ("s"))??''}`;

});
const old_lines_3 = squint_core.get(squint_core.get(model, "texts"), "old").split("\n");
const g_4 = el_BANG_("div", ({"class": "hc-diff-gap"}));
const btn_5 = el_BANG_("button", ({"type": "button", "class": "hc-diff-expand", "aria-expanded": "false"}), label_2("Show"));
const lines_6 = squint_core.atom(null);
btn_5.addEventListener("click", (function (_) {
if (squint_core.truth_(squint_core.deref(lines_6))) {
} else {
const rs_7 = squint_core.mapv((function (o) {
return ({"kind": "ctx", "old": o, "new": (o + delta), "text": old_lines_3[(o - 1)], "tokens": (() => {
const G__34_8 = ot;
if ((G__34_8 == null)) {
return null} else {
return G__34_8[(o - 1)]};

})()});

}), squint_core.range(from, (to + 1)));
const b_9 = body(rs_7, (function (side, r) {
return (squint_core.get(r, side) + squint_core.get(model, (((side === "old")) ? ("old-offset") : ("new-offset"))));

}));
b_9.hidden = true;
g_4.after(b_9);
squint_core.reset_BANG_(lines_6, b_9)};
const show_QMARK__10 = squint_core.deref(lines_6).hidden;
squint_core.deref(lines_6).hidden = squint_core.not(show_QMARK__10);
btn_5.setAttribute("aria-expanded", `${show_QMARK__10??''}`);
btn_5.textContent = label_2(((squint_core.truth_(show_QMARK__10)) ? ("Hide") : ("Show")));
if (squint_core.truth_(show_QMARK__10)) {
return build_shown_splits_BANG_(g_4.closest(".hc-diff"));
};

}));
g_4.append(btn_5);
return g_4;
};

};
var hunk_keys = function (path, hunks) {
const hash_of_1 = (function (lines) {
return fnv1a(`${path??''}${" "}${squint_core.into_array(lines).join("\n")??''}`);

});
const changed_2 = squint_core.mapv((function (h) {
return hash_of_1(squint_core.filter((function (_PERCENT_1) {
return squint_core.not(_PERCENT_1.startsWith(" "));

}), squint_core.get(h, "lines")));

}), hunks);
const counts_3 = squint_core.reduce((function (m, k) {
return squint_core.assoc(m, k, (squint_core.get(m, k, 0) + 1));

}), ({}), changed_2);
const hashes_4 = squint_core.mapv((function (i) {
if ((squint_core.get(counts_3, squint_core.get(changed_2, i)) > 1)) {
return hash_of_1(squint_core.get(squint_core.get(hunks, i), "lines"))} else {
return squint_core.get(changed_2, i)};

}), squint_core.range(squint_core.count(hunks)));
const seen_5 = squint_core.atom(({}));
return squint_core.mapv((function (k) {
const n_6 = squint_core.get(squint_core.swap_BANG_(seen_5, squint_core.update, k, (function (_PERCENT_1) {
return (((squint_core.truth_(_PERCENT_1)) ? (_PERCENT_1) : (0)) + 1);

})), k);
if ((n_6 > 1)) {
return `${k??''}-${n_6??''}`} else {
return k};

}), hashes_4);

};
var file_section = async function (jsdiff, model, file, id, choices) {
const path_1 = squint_core.get(file, "path");
const lang_r_2 = (await language(model, path_1));
const lang_3 = squint_core.get(lang_r_2, "lang");
const problem_4 = squint_core.atom(squint_core.get(lang_r_2, "problem"));
const texts_5 = squint_core.get(model, "texts");
const ot_6 = ((squint_core.truth_(texts_5)) ? ((await tokens_or_nil(lang_3, squint_core.get(texts_5, "old"), problem_4))) : (null));
const nt_7 = ((squint_core.truth_(texts_5)) ? ((await tokens_or_nil(lang_3, squint_core.get(texts_5, "new"), problem_4))) : (null));
const num_8 = (function (side, r) {
return (squint_core.get(r, side) + squint_core.get(model, (((side === "old")) ? ("old-offset") : ("new-offset"))));

});
const section_9 = el_BANG_("div", ({"class": "hc-diff-file"}), file_head(model, file));
const keyed_10 = ({});
const hunks_11 = squint_core.get(file, "hunks");
const ks_12 = hunk_keys(path_1, hunks_11);
const old_count_13 = ((squint_core.truth_(texts_5)) ? ((squint_core.get(texts_5, "old").split("\n").length - 1)) : (null));
for (let G__14 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, hunks_11))) {
const vec___15_18 = G__14;
const i_19 = squint_core.nth(vec___15_18, 0, null);
const h_20 = squint_core.nth(vec___15_18, 1, null);
const rs_21 = rows(h_20);
mark_words_BANG_(jsdiff, rs_21);
if (squint_core.truth_(texts_5)) {
color_text_rows_BANG_(ot_6, nt_7, rs_21)} else {
(await color_patch_rows_BANG_(lang_3, rs_21, problem_4))};
if (squint_core.truth_(texts_5)) {
const prev_22 = squint_core.get(hunks_11, (i_19 - 1));
const from_23 = ((squint_core.truth_(prev_22)) ? ((squint_core.get(prev_22, "old-start") + squint_core.get(prev_22, "old-lines"))) : (1));
const G__35_24 = gap(model, from_23, (squint_core.get(h_20, "old-start") - 1), (squint_core.get(h_20, "new-start") - squint_core.get(h_20, "old-start")), ot_6);
if ((G__35_24 == null)) {
} else {
section_9.append(G__35_24)}};
section_9.append(hunk_head(model, file, h_20), body(rs_21, num_8));
if (squint_core.truth_(id)) {
const k_25 = squint_core.get(ks_12, i_19);
(keyed_10[k_25] = ({"file": path_1, "old": range_label((squint_core.get(h_20, "old-start") + squint_core.get(model, "old-offset")), squint_core.get(h_20, "old-lines")), "new": range_label((squint_core.get(h_20, "new-start") + squint_core.get(model, "new-offset")), squint_core.get(h_20, "new-lines"))}));
section_9.append(choice_controls(`${id??''}${"."}${k_25??''}`, choices))}
};
if (squint_core.truth_(((squint_core.truth_(texts_5)) ? (squint_core.seq(hunks_11)) : (texts_5)))) {
const l_26 = squint_core.last(hunks_11);
const G__36_27 = gap(model, (squint_core.get(l_26, "old-start") + squint_core.get(l_26, "old-lines")), old_count_13, ((squint_core.get(l_26, "new-start") + squint_core.get(l_26, "new-lines")) - (squint_core.get(l_26, "old-start") + squint_core.get(l_26, "old-lines"))), ot_6);
if ((G__36_27 == null)) {
} else {
section_9.append(G__36_27)}};
return ({"node": section_9, "hunks": keyed_10, "problem": squint_core.deref(problem_4)});

};
var stored_layout = function () {
try{
const v_1 = localStorage.getItem(layout_key);
if (squint_core.truth_(layouts.includes(v_1))) {
return v_1} else {
return "auto"};
}
catch(__2){
return "auto";
}
;

};
var set_layout_BANG_ = function (v) {
try{
localStorage.setItem(layout_key, v)}
catch(__1){
}
;
for (let G__2 of squint_core.iterable(Array.from(document.querySelectorAll(".hc-diff")))) {
const d_3 = G__2;
d_3.dataset.layout = v;
for (let G__4 of squint_core.iterable(Array.from(d_3.querySelectorAll(".hc-diff-layout button")))) {
const b_5 = G__4;
b_5.setAttribute("aria-pressed", `${squint_core._EQ_(v, b_5.dataset.layout)}`)
}
};
return build_shown_splits_BANG_(document);

};
var layout_bar = function () {
const bar_1 = el_BANG_("div", ({"class": "hc-diff-layout", "role": "group", "aria-label": "Diff layout"}));
const current_2 = stored_layout();
for (let G__3 of squint_core.iterable(layouts)) {
const v_4 = G__3;
const b_5 = el_BANG_("button", ({"type": "button", "data-layout": v_4, "aria-pressed": `${squint_core._EQ_(v_4, current_2)}`}), choice_label(v_4));
b_5.addEventListener("click", (function (_) {
return set_layout_BANG_(v_4);

}));
bar_1.append(b_5)
};
return bar_1;

};
var fail_BANG_ = function (report_BANG_, src, label, message) {
src.after(el_BANG_("div", ({"class": "hc-viz-error"}), `${label??''}${": "}${message??''}`));
return report_BANG_("diff", `${label??''}${": "}${message??''}`, label);

};
var diff_label = function (src, i) {
const temp__23255__auto___1 = squint_core.not_empty((() => {
const or_2_3 = src.getAttribute("data-diff");
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return ""};

})().trim());
if (squint_core.truth_(temp__23255__auto___1)) {
const id_4 = temp__23255__auto___1;
return `${"diff \""}${id_4??''}${"\""}`;
} else {
return `${"diff #"}${i??''}`};

};
var choices_of = function (src) {
const G__37_1 = src.getAttribute("data-hunk-choices");
const G__37_2 = (((G__37_1 == null)) ? (null) : (squint_core.re_seq(/\S+/, G__37_1)));
const G__37_3 = (((G__37_2 == null)) ? (null) : (squint_core.vec(G__37_2)));
if ((G__37_3 == null)) {
return null} else {
return squint_core.not_empty(G__37_3)};

};
var render_diff_BANG_ = async function (jsdiff, src, i, id_counts, report_BANG_) {
const id_1 = squint_core.not_empty((await (async () => {
const or_2_3 = src.getAttribute("data-diff");
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return ""};

})()).trim());
const label_4 = diff_label(src, i);
const tags_5 = highlight.unescaped_tags(src);
const model_6 = ((squint_core.truth_(squint_core.seq(tags_5))) ? (({"error": `${"the browser read "}${squint_core.into_array(squint_core.map((function (_PERCENT_1) {
return `${"<"}${_PERCENT_1??''}${">"}`;

}), tags_5)).join(", ")??''}${" in it as HTML tags, so its text is cut; write < as &lt; and & as &amp;"}`})) : (((("figure" === src.localName)) ? (texts_model(jsdiff, src)) : ((("else") ? ((await (async () => {
try{
return patch_model(jsdiff, src);
}
catch(e_7){
return ({"error": `${"the patch doesn't parse: "}${e_7.message??''}${". Check that each @@ header's line counts match the lines under it"}`});
}

})())) : (null))))));
const choices_8 = choices_of(src);
if (squint_core.truth_(squint_core.get(model_6, "error"))) {
return fail_BANG_(report_BANG_, src, label_4, squint_core.get(model_6, "error"))} else {
if (squint_core.empty_QMARK_(squint_core.get(model_6, "files"))) {
return fail_BANG_(report_BANG_, src, label_4, ((squint_core.truth_(squint_core.get(model_6, "texts"))) ? ("the two texts are the same") : ("no hunks found; is it a unified diff?")))} else {
if ("else") {
const choice_id_9 = ((squint_core.truth_(choices_8)) ? ((((id_1 == null)) ? ((await (async () => {
report_BANG_("diff", `${label_4??''}${": data-hunk-choices needs a data-diff ID; rendered read-only"}`, label_4);
return null
})())) : ((((squint_core.get(id_counts, id_1) > 1)) ? ((await (async () => {
report_BANG_("diff", `${label_4??''}${": its ID is used by another diff; rendered read-only"}`, label_4);
return null
})())) : ((("else") ? (id_1) : (null))))))) : (null));
const container_10 = el_BANG_("div", ({"class": "hc-diff", "data-layout": stored_layout()}), layout_bar());
const diff_keys_11 = ({});
for (let G__12 of squint_core.iterable(squint_core.get(model_6, "files"))) {
const f_13 = G__12;
const map___14_16 = (await file_section(jsdiff, model_6, f_13, choice_id_9, choices_8));
const map___14_17 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___14_16))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___14_16))) ? (map___14_16) : (squint_core.seq_to_map_for_destructuring(map___14_16)))) : (map___14_16));
const node_18 = squint_core.get(map___14_17, "node");
const hunks_19 = squint_core.get(map___14_17, "hunks");
const problem_20 = squint_core.get(map___14_17, "problem");
container_10.append(node_18);
for (let G__21 of squint_core.iterable(Object.entries(hunks_19))) {
const vec___22_25 = G__21;
const k_26 = squint_core.nth(vec___22_25, 0, null);
const v_27 = squint_core.nth(vec___22_25, 1, null);
(diff_keys_11[k_26] = v_27)
};
if (squint_core.truth_(problem_20)) {
report_BANG_("highlight", `${label_4??''}${": "}${problem_20??''}${", so it stays plain. Set data-lang on the diff."}`, label_4)}
};
src.after(container_10);
src.hidden = true;
build_shown_splits_BANG_(container_10);
(new ResizeObserver((function (_) {
return build_shown_splits_BANG_(container_10);

}))).observe(container_10);
if (squint_core.truth_(choice_id_9)) {
squint_core.swap_BANG_(hunk_map, squint_core.assoc, choice_id_9, diff_keys_11)};
return core.restore_within_BANG_(container_10);
} else {
return null}}};

};
var on_done = function (e) {
if (squint_core.truth_((() => {
const and_1_2 = squint_core.seq(Object.keys(squint_core.deref(hunk_map)));
if (squint_core.truth_(and_1_2)) {
return e.target.closest("[data-decision=status][data-choice=done]")} else {
return and_1_2};

})())) {
return window.htmlChannel.record("hunks", squint_core.deref(hunk_map));
};

};
var start_BANG_ = async function (report_BANG_) {
const srcs_1 = Array.from(document.querySelectorAll("pre[data-diff], figure[data-diff]"));
if (squint_core.truth_(squint_core.seq(srcs_1))) {
document.addEventListener("click", on_done, true);
const id_counts_2 = squint_core.reduce((function (m, s) {
const v_3 = (() => {
const or_4_5 = s.getAttribute("data-diff");
if (squint_core.truth_(or_4_5)) {
return or_4_5} else {
return ""};

})().trim();
if (squint_core.truth_(squint_core.seq(v_3))) {
return squint_core.assoc(m, v_3, (squint_core.get(m, v_3, 0) + 1))} else {
return m};

}), ({}), srcs_1);
try{
const jsdiff_6 = (await jsdiff_BANG_());
for (let G__7 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, srcs_1))) {
const vec___8_11 = G__7;
const i_12 = squint_core.nth(vec___8_11, 0, null);
const src_13 = squint_core.nth(vec___8_11, 1, null);
try{
(await render_diff_BANG_(jsdiff_6, src_13, i_12, id_counts_2, report_BANG_))}
catch(e_14){
fail_BANG_(report_BANG_, src_13, diff_label(src_13, i_12), `${"didn't render: "}${e_14.message??''}`)}

}
return null;
}
catch(e_15){
for (let G__16 of squint_core.iterable(srcs_1)) {
const src_17 = G__16;
src_17.after(el_BANG_("div", ({"class": "hc-viz-error"}), "jsdiff didn't load, so this diff shows as source"))
};
return report_BANG_("diff", `${"jsdiff didn't load, so "}${squint_core.count(srcs_1)??''}${" diff(s) show as source: "}${e_15.message??''}`, "jsdiff");
}
;
};

};

export { signs, range_label, diff_label, gap, choice_label, hunk_map, body, file_section, split_row, unified_table, max_marked_line, jsdiff_promise, tokens_or_nil, build_shown_splits_BANG_, tokens_of_lines, side_cells, numeric_sort, sign_cell, layouts, blocks, jsdiff_BANG_, start_BANG_, color_text_rows_BANG_, layout_bar, context_lines, min_similarity, choices_of, mark_words_BANG_, split_table, texts_model, render_diff_BANG_, stored_layout, el_BANG_, hunk_of, layout_key, ensure_newline, file_head, fnv1a, rows, choice_controls, line_nodes, hunk_head, language, patch_model, fail_BANG_, patch_path, hunk_keys, on_done, set_layout_BANG_, spoken, color_patch_rows_BANG_, file_name }
