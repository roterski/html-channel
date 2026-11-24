import * as squint_core from '../squint-core.js';
import * as versions from './../viz/versions.mjs';
import * as viz_DOT_versions from './../viz/versions.mjs';
var plain_names = (new Set (["text", "plaintext", "plain", "txt"]));
var extension_languages = ({"cljc": "clojure", "bashrc": "shellscript", "hpp": "cpp", "cljs": "clojure", "gemfile": "ruby", "mli": "ocaml", "pl": "perl", "gemspec": "ruby", "ml": "ocaml", "htm": "html", "zshrc": "shellscript", "exs": "elixir", "pm": "perl", "ex": "elixir", "hh": "cpp", "jsonl": "json", "cxx": "cpp", "bb": "clojure", "rakefile": "ruby", "h": "c", "edn": "clojure", "rake": "ruby", "cc": "cpp"});
var skipped = `${"[data-viz], [data-diff], .hc-diff, figure[data-statechart], .hc-src, .hc-viz, .hc-viz-error,"}${" #decide-code-links, #decide-tiers, #decide-offline"}`;
var language_names = function (infos) {
const names_1 = ({});
for (let G__2 of squint_core.iterable(infos)) {
const info_3 = G__2;
(names_1[info_3.id] = info_3.id);
for (let G__4 of squint_core.iterable((() => {
const or_5_6 = info_3.aliases;
if (squint_core.truth_(or_5_6)) {
return or_5_6} else {
return []};

})())) {
const a_7 = G__4;
(names_1[a_7.toLowerCase()] = info_3.id)
}
};
for (let G__8 of squint_core.iterable(Object.entries(extension_languages))) {
const vec___9_12 = G__8;
const n_13 = squint_core.nth(vec___9_12, 0, null);
const target_14 = squint_core.nth(vec___9_12, 1, null);
if (squint_core.truth_(((squint_core.not(names_1[n_13])) && (names_1[target_14])))) {
(names_1[n_13] = names_1[target_14])}
};
return names_1;

};
var load_shiki = async function () {
const shiki_1 = (await import(versions.shiki));
const engine_2 = (await import(versions.shiki_js_engine));
const theme_3 = shiki_1.createCssVariablesTheme(({"name": "hc", "variablePrefix": "--shiki-"}));
const hl_4 = (await shiki_1.createHighlighter(({"themes": [theme_3], "langs": [], "engine": engine_2.createJavaScriptRegexEngine()})));
return ({"highlighter": hl_4, "names": language_names(shiki_1.bundledLanguagesInfo)});

};
var shiki_promise = squint_core.atom(null);
var shiki_BANG_ = function () {
const or_1_2 = squint_core.deref(shiki_promise);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return squint_core.reset_BANG_(shiki_promise, load_shiki())};

};
var tokenize_BANG_ = async function (lang, code) {
const map___1_3 = (await shiki_BANG_());
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const highlighter_5 = squint_core.get(map___1_4, "highlighter");
(await highlighter_5.loadLanguage(lang));
return highlighter_5.codeToTokensBase(code, ({"lang": lang, "theme": "hc"}));

};
var file_name = function (src) {
if (squint_core.truth_(squint_core.seq(src))) {
return squint_core.last(src.replace(/^[^:]*:/, "").replace(/#.*$/, "").split("/"));
};

};
var class_language = function (el) {
const G__50_1 = el.classList;
const G__50_2 = (((G__50_1 == null)) ? (null) : (Array.from(G__50_1)));
if ((G__50_2 == null)) {
return null} else {
return squint_core.some((function (_PERCENT_1) {
return squint_core.second(squint_core.re_find(/^(?:language|lang)-(.+)$/, _PERCENT_1));

}), G__50_2)};

};
var declared = function (els) {
const temp__23314__auto___1 = (() => {
const or_2_3 = squint_core.some((function (_PERCENT_1) {
return squint_core.not_empty(_PERCENT_1.getAttribute("data-lang"));

}), els);
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return squint_core.some(class_language, els)};

})();
if (squint_core.truth_(temp__23314__auto___1)) {
const n_4 = temp__23314__auto___1;
if (squint_core.contains_QMARK_(plain_names, n_4.toLowerCase())) {
return ({"plain": true})} else {
return ({"name": n_4})};
};

};
var request_for_file = function (name) {
if (squint_core.truth_(squint_core.seq(name))) {
return ({"file": name});
};

};
var language_of = function (req, names) {
if ((req == null)) {
return ({"problem": "no language"})} else {
if (squint_core.truth_(squint_core.get(req, "plain"))) {
return ({"plain": true})} else {
if (squint_core.truth_(squint_core.get(req, "name"))) {
const temp__23255__auto___1 = squint_core.get(names, squint_core.get(req, "name").toLowerCase());
if (squint_core.truth_(temp__23255__auto___1)) {
const id_2 = temp__23255__auto___1;
return ({"lang": id_2});
} else {
return ({"problem": `${"unknown language \""}${squint_core.get(req, "name")??''}${"\""}`})};
} else {
if ("else") {
const f_3 = squint_core.get(req, "file").toLowerCase();
const ext_4 = squint_core.second(squint_core.re_find(/\.([^.]+)$/, f_3));
const temp__23255__auto___5 = (() => {
const or_6_7 = squint_core.get(names, f_3);
if (squint_core.truth_(or_6_7)) {
return or_6_7} else {
if (squint_core.truth_(ext_4)) {
return squint_core.get(names, ext_4)} else {
return ext_4}};

})();
if (squint_core.truth_(temp__23255__auto___5)) {
const id_8 = temp__23255__auto___5;
return ({"lang": id_8});
} else {
if (squint_core.contains_QMARK_(plain_names, ext_4)) {
return ({"plain": true})} else {
return ({"problem": `${"no language known for files like \""}${squint_core.get(req, "file")??''}${"\""}`})}};
} else {
return null}}}};

};
var sole_code_child = function (pre) {
const kids_1 = pre.children;
if ((((1 === kids_1.length)) && (("code" === kids_1[0].localName)))) {
return kids_1[0];
};

};
var text_only_QMARK_ = function (el) {
return (el.childElementCount === 0);

};
var unescaped_tags = function (el) {
return squint_core.vec(squint_core.distinct(squint_core.map((function (_PERCENT_1) {
return _PERCENT_1.localName;

}), squint_core.filter((function (_PERCENT_1) {
const c__23641__auto___1 = HTMLUnknownElement;
const x__23642__auto___2 = _PERCENT_1;
const ret__23643__auto___3 = (x__23642__auto___2 instanceof c__23641__auto___1);
return ret__23643__auto___3;

}), Array.from(el.querySelectorAll("*"))))));

};
var unescaped_cause = "an unescaped < that the browser read as a tag, which cut the text; write < as &lt; and & as &amp;";
var hosts = function () {
const blocks_1 = squint_core.lazy((function* () {
for (let G__2 of squint_core.iterable(Array.from(document.querySelectorAll("pre")))) {
const pre_3 = G__2;
if (squint_core.not(pre_3.closest(skipped))) {
const code_4 = sole_code_child(pre_3);
const host_5 = ((squint_core.truth_(code_4)) ? (code_4) : (pre_3));
const tags_6 = unescaped_tags(pre_3);
if (squint_core.truth_(((squint_core.not(host_5.hasAttribute("data-hl"))) && ((yield* (function* () {
const or_7_8 = text_only_QMARK_(host_5);
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
return squint_core.seq(tags_6)};

})()))))) {
yield ({"host": host_5, "scope": ((squint_core.truth_(code_4)) ? ([code_4, pre_3]) : ([pre_3])), "block?": true, "unescaped": tags_6});}}
}
return null;

}));
const inline_9 = squint_core.lazy((function* () {
for (let G__10 of squint_core.iterable(Array.from(document.querySelectorAll("code[data-lang], code[data-src], code[class*=lang]")))) {
const code_11 = G__10;
if (squint_core.truth_(((squint_core.not(code_11.closest("pre"))) && (((squint_core.not(code_11.closest(skipped))) && (((squint_core.not(code_11.hasAttribute("data-hl"))) && ((yield* (function* () {
const or_12_13 = declared([code_11]);
if (squint_core.truth_(or_12_13)) {
return or_12_13} else {
return code_11.hasAttribute("data-src")};

})()))))))))) {
const tags_14 = unescaped_tags(code_11);
if (squint_core.truth_((yield* (function* () {
const or_15_16 = text_only_QMARK_(code_11);
if (squint_core.truth_(or_15_16)) {
return or_15_16} else {
return squint_core.seq(tags_14)};

})()))) {
yield ({"host": code_11, "scope": [code_11], "block?": false, "unescaped": tags_14});}}
}
return null;

}));
return squint_core.vec(squint_core.concat(blocks_1, inline_9));

};
var request = function (p__51) {
const map___1_3 = p__51;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const scope_5 = squint_core.get(map___1_4, "scope");
const or_6_7 = declared(scope_5);
if (squint_core.truth_(or_6_7)) {
return or_6_7} else {
return request_for_file(squint_core.some((function (_PERCENT_1) {
return file_name(_PERCENT_1.getAttribute("data-src"));

}), scope_5))};

};
var describe = function (el) {
const src_1 = el.getAttribute("data-src");
const text_2 = el.textContent.trim();
return `${"<"}${el.localName??''}${((squint_core.truth_(src_1)) ? (`${" data-src=\""}${src_1??''}${"\""}`) : (""))??''}${">"}${(((text_2.length > 30)) ? (`${squint_core.subs(text_2, 0, 30)}${"…"}`) : (text_2))??''}`;

};
var token_span = /* @__PURE__ */ (() => {
let f52_1 = null;
const impl55_2 = (function (t) {
return token_span(t, t.content);

});
const impl56_3 = (function (t, text) {
const s_4 = document.createElement("span");
const fs_5 = (() => {
const or_6_7 = t.fontStyle;
if (squint_core.truth_(or_6_7)) {
return or_6_7} else {
return 0};

})();
s_4.textContent = text;
if (squint_core.truth_(t.color)) {
s_4.style.color = t.color};
if (((fs_5 & 1) > 0)) {
s_4.style.fontStyle = "italic"};
if (((fs_5 & 2) > 0)) {
s_4.style.fontWeight = "bold"};
return s_4;

});
f52_1 = (function (...args53) {
const self57_8 = this;
const G__58_9 = args53.length;
switch (G__58_9) {case 1:
return impl55_2.call(self57_8, args53[0]);

break;
case 2:
return impl56_3.call(self57_8, args53[0], args53[1]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args53.length??''}`))};

});
return f52_1;

})();
var highlight_BANG_ = async function (host, lang) {
const lines_1 = (await tokenize_BANG_(lang, host.textContent));
const frag_2 = document.createDocumentFragment();
for (let G__3 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, lines_1))) {
const vec___4_7 = G__3;
const i_8 = squint_core.nth(vec___4_7, 0, null);
const line_9 = squint_core.nth(vec___4_7, 1, null);
if ((i_8 > 0)) {
frag_2.append("\n")};
for (let G__10 of squint_core.iterable(line_9)) {
const t_11 = G__10;
frag_2.append(token_span(t_11))
}
};
host.replaceChildren(frag_2);
return host.setAttribute("data-hl", lang);

};
var report_groups_BANG_ = function (report_BANG_, problems) {
for (let G__1 of squint_core.iterable(squint_core.group_by("cause", problems))) {
const vec___2_5 = G__1;
const cause_6 = squint_core.nth(vec___2_5, 0, null);
const hs_7 = squint_core.nth(vec___2_5, 1, null);
report_BANG_("highlight", `${squint_core.count(hs_7)??''}${" code element(s): "}${cause_6??''}${", so they stay plain"}${((squint_core._EQ_(cause_6, unescaped_cause)) ? ("") : (". Set data-lang (data-lang=\"text\" for plain text)"))??''}${": "}${squint_core.mapv((function (_PERCENT_1) {
return `${describe(squint_core.get(_PERCENT_1, "host"))??''}${((squint_core.truth_(squint_core.seq(squint_core.get(_PERCENT_1, "unescaped")))) ? (`${" (read as "}${squint_core.into_array(squint_core.map((function (t) {
return `${"<"}${t??''}${">"}`;

}), squint_core.get(_PERCENT_1, "unescaped"))).join(", ")??''}${")"}`) : (""))??''}`;

}), squint_core.take(3, hs_7)).join(", ")??''}${(((squint_core.count(hs_7) > 3)) ? (", …") : (""))??''}`, cause_6)
}
return null;

};
var start_BANG_ = async function (report_BANG_) {
const all_1 = hosts();
const unescaped_2 = squint_core.filterv((function (_PERCENT_1) {
return squint_core.seq(squint_core.get(_PERCENT_1, "unescaped"));

}), all_1);
const hs_3 = squint_core.mapv((function (_PERCENT_1) {
return squint_core.assoc(_PERCENT_1, "req", request(_PERCENT_1));

}), squint_core.filterv((function (_PERCENT_1) {
return squint_core.empty_QMARK_(squint_core.get(_PERCENT_1, "unescaped"));

}), all_1));
const wanted_4 = squint_core.filterv((function (_PERCENT_1) {
const and_5_6 = squint_core.get(_PERCENT_1, "req");
if (squint_core.truth_(and_5_6)) {
return squint_core.not(squint_core.get(squint_core.get(_PERCENT_1, "req"), "plain"))} else {
return and_5_6};

}), hs_3);
const unnamed_7 = squint_core.filterv((function (_PERCENT_1) {
const and_8_9 = squint_core.get(_PERCENT_1, "block?");
if (squint_core.truth_(and_8_9)) {
return (squint_core.get(_PERCENT_1, "req") == null)} else {
return and_8_9};

}), hs_3);
report_groups_BANG_(report_BANG_, squint_core.mapv((function (_PERCENT_1) {
return squint_core.assoc(_PERCENT_1, "cause", unescaped_cause);

}), unescaped_2));
report_groups_BANG_(report_BANG_, squint_core.mapv((function (_PERCENT_1) {
return squint_core.assoc(_PERCENT_1, "cause", "no language");

}), unnamed_7));
if (squint_core.truth_(squint_core.seq(wanted_4))) {
try{
const map___10_12 = (await shiki_BANG_());
const map___10_13 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___10_12))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___10_12))) ? (map___10_12) : (squint_core.seq_to_map_for_destructuring(map___10_12)))) : (map___10_12));
const names_14 = squint_core.get(map___10_13, "names");
const problems_15 = squint_core.atom([]);
for (let G__16 of squint_core.iterable(wanted_4)) {
const h_17 = G__16;
const r_18 = language_of(squint_core.get(h_17, "req"), names_14);
const temp__23255__auto___19 = squint_core.get(r_18, "lang");
if (squint_core.truth_(temp__23255__auto___19)) {
const lang_20 = temp__23255__auto___19;
try{
(await highlight_BANG_(squint_core.get(h_17, "host"), lang_20))}
catch(e_21){
squint_core.swap_BANG_(problems_15, squint_core.conj, squint_core.assoc(h_17, "cause", `${"failed in "}${lang_20??''}${": "}${e_21.message??''}`))}
} else {
if (squint_core.truth_((await (async () => {
const or_22_23 = squint_core.get(h_17, "block?");
if (squint_core.truth_(or_22_23)) {
return or_22_23} else {
return squint_core.not(squint_core.get(squint_core.get(h_17, "req"), "file"))};

})()))) {
squint_core.swap_BANG_(problems_15, squint_core.conj, squint_core.assoc(h_17, "cause", squint_core.get(r_18, "problem")))}}
};
return report_groups_BANG_(report_BANG_, squint_core.deref(problems_15));
}
catch(e_24){
return report_BANG_("highlight", `${"Shiki didn't load, so "}${squint_core.count(wanted_4)??''}${" code element(s) stay plain: "}${e_24.message??''}`, "shiki");
}
;
};

};

export { unescaped_tags, request_for_file, sole_code_child, hosts, unescaped_cause, language_names, shiki_BANG_, class_language, report_groups_BANG_, language_of, plain_names, shiki_promise, extension_languages, token_span, request, declared, start_BANG_, describe, tokenize_BANG_, text_only_QMARK_, highlight_BANG_, load_shiki, skipped, file_name }
