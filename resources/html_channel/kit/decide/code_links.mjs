import * as squint_core from '../squint-core.js';
var shared_key = "html-channel:code-links:shared";
var page_key = function () {
return `${"html-channel:code-links:"}${location.pathname??''}`;

};
var formats = [["vscode", "VS Code"], ["github", "GitHub"]];
var name_re = /^[a-z0-9._-]+$/;
var web_re = /^https:\/\/github\.com\/[^/\s]+\/[^/\s]+\/blob\/[^/\s]+\/?$/;
var src_re = /^([a-z0-9._-]+):([^#\s](?:[^#]*[^#\s])?)(?:#L(\d+)(?:-L?(\d+))?)?$/;
var absolute_re = /^(\/|[A-Za-z]:[\\/])/;
var xlink_ns = "http://www.w3.org/1999/xlink";
var svg_ns = "http://www.w3.org/2000/svg";
var state = squint_core.atom(({"repos": ({}), "report!": null, "bar": null}));
var present = function (s) {
if (squint_core.string_QMARK_(s)) {
const t_1 = s.trim();
if ((squint_core.count(t_1) > 0)) {
return t_1;
};
};

};
var without_trailing_slash = function (p) {
return p.replace(/\/+$/, "");

};
var absolute_path = function (p) {
const temp__23314__auto___1 = present(p);
if (squint_core.truth_(temp__23314__auto___1)) {
const p_2 = temp__23314__auto___1;
if (squint_core.truth_(absolute_re.test(p_2))) {
return p_2;
};
};

};
var valid_web = function (u) {
const temp__23314__auto___1 = present(u);
if (squint_core.truth_(temp__23314__auto___1)) {
const u_2 = temp__23314__auto___1;
if (squint_core.truth_(web_re.test(u_2))) {
if (squint_core.truth_(u_2.endsWith("/"))) {
return u_2} else {
return `${u_2??''}${"/"}`};
};
};

};
var remote_of = function (web) {
const G__1_1 = web;
if ((G__1_1 == null)) {
return null} else {
return G__1_1.replace(/\/blob\/[^/]+\/?$/, "")};

};
var meta_fields = function (content) {
return squint_core.reduce((function (m, part) {
const i_1 = part.indexOf("=");
if ((i_1 > 0)) {
return squint_core.assoc(m, squint_core.subs(part, 0, i_1).trim(), squint_core.subs(part, (i_1 + 1)).trim())} else {
return m};

}), ({}), ((squint_core.truth_(content)) ? (content) : ("")).split(";"));

};
var repo_problem = function (f) {
const n_1 = squint_core.get(f, "name");
const root_2 = squint_core.get(f, "root");
const web_3 = squint_core.get(f, "web");
if (squint_core.not(((squint_core.string_QMARK_(n_1)) && (name_re.test(n_1))))) {
return `${"name "}${JSON.stringify(n_1)??''}${" must match [a-z0-9._-]+"}`} else {
if (squint_core.not(absolute_path(root_2))) {
return `${"repo "}${n_1??''}${": root must be an absolute path"}`} else {
if (squint_core.truth_((() => {
const and_4_5 = present(web_3);
if (squint_core.truth_(and_4_5)) {
return squint_core.not(valid_web(web_3))} else {
return and_4_5};

})())) {
return `${"repo "}${n_1??''}${": web must look like https://github.com/OWNER/REPO/blob/SHA/"}`} else {
return null}}};

};
var read_repos = function (report_BANG_) {
return squint_core.reduce((function (repos, el) {
const content_1 = el.getAttribute("content");
const f_2 = meta_fields(content_1);
const problem_3 = (() => {
const or_4_5 = repo_problem(f_2);
if (squint_core.truth_(or_4_5)) {
return or_4_5} else {
if (squint_core.truth_(Object.hasOwn(repos, squint_core.get(f_2, "name")))) {
return `${"repo "}${squint_core.get(f_2, "name")??''}${" is declared twice"}`;
}};

})();
if (squint_core.truth_(problem_3)) {
report_BANG_("code-link", `${"<meta name=\"hc-repo\">: "}${problem_3??''}`, content_1);
return repos;
} else {
const web_6 = valid_web(squint_core.get(f_2, "web"));
return squint_core.assoc(repos, squint_core.get(f_2, "name"), ({"name": squint_core.get(f_2, "name"), "root": without_trailing_slash(squint_core.get(f_2, "root")), "web": web_6, "remote": remote_of(web_6)}));
};

}), ({}), Array.from(document.querySelectorAll("meta[name=hc-repo]")));

};
var decoded = function (s) {
try{
return decodeURIComponent(s);
}
catch(__1){
return null;
}
;

};
var parse_src = function (s) {
const temp__23314__auto___1 = src_re.exec(((squint_core.truth_(s)) ? (s) : ("")));
if (squint_core.truth_(temp__23314__auto___1)) {
const m_2 = temp__23314__auto___1;
const path_3 = decoded(m_2[2]);
if (squint_core.truth_(((squint_core.truth_(path_3)) ? (squint_core.not(path_3.startsWith("/"))) : (path_3)))) {
return ({"repo": m_2[1], "path": path_3, "start": (() => {
const G__2_4 = m_2[3];
if ((G__2_4 == null)) {
return null} else {
return parseInt(G__2_4, 10)};

})(), "end": (() => {
const G__3_5 = m_2[4];
if ((G__3_5 == null)) {
return null} else {
return parseInt(G__3_5, 10)};

})()});
};
};

};
var declared_QMARK_ = function (repo_name) {
return Object.hasOwn(squint_core.get(squint_core.deref(state), "repos"), repo_name);

};
var declared_ref = function (s) {
const temp__23314__auto___1 = parse_src(s);
if (squint_core.truth_(temp__23314__auto___1)) {
const ref_2 = temp__23314__auto___1;
if (squint_core.truth_(declared_QMARK_(squint_core.get(ref_2, "repo")))) {
return ref_2;
};
};

};
var read_json = function (k) {
try{
const G__4_1 = localStorage.getItem(k);
if ((G__4_1 == null)) {
return null} else {
return JSON.parse(G__4_1)};
}
catch(__2){
return null;
}
;

};
var write_json_BANG_ = function (k, v) {
try{
return localStorage.setItem(k, JSON.stringify(v));
}
catch(__1){
return null;
}
;

};
var remove_key_BANG_ = function (k) {
try{
return localStorage.removeItem(k);
}
catch(__1){
return null;
}
;

};
var page_settings = function () {
const or_1_2 = read_json(page_key());
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return ({})};

};
var shared_defaults = function () {
const or_1_2 = read_json(shared_key);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return ({})};

};
var identifiers = function (repo) {
return ({"name": squint_core.get(repo, "name"), "root": squint_core.get(repo, "root"), "remote": squint_core.get(repo, "remote")});

};
var same_repo_QMARK_ = function (record, repo) {
const ids_1 = identifiers(repo);
const both_2 = squint_core.filter((function (_PERCENT_1) {
if (!(squint_core.get(record, _PERCENT_1) == null)) {
return !(squint_core.get(ids_1, _PERCENT_1) == null)} else {
return false};

}), ["name", "root", "remote"]);
const and_3_4 = squint_core.seq(both_2);
if (squint_core.truth_(and_3_4)) {
return squint_core.every_QMARK_((function (_PERCENT_1) {
return squint_core._EQ_(squint_core.get(record, _PERCENT_1), squint_core.get(ids_1, _PERCENT_1));

}), both_2)} else {
return and_3_4};

};
var shared_record = function (shared, repo) {
return squint_core.last(squint_core.filter((function (_PERCENT_1) {
return same_repo_QMARK_(_PERCENT_1, repo);

}), (() => {
const or_1_2 = squint_core.get(shared, "repos");
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return []};

})()));

};
var per_repo = function (f) {
return squint_core.reduce((function (m, repo) {
return squint_core.assoc(m, squint_core.get(repo, "name"), f(repo));

}), ({}), Object.values(squint_core.get(squint_core.deref(state), "repos")));

};
var valid_format = function (f) {
if (squint_core.truth_(squint_core.some((function (_PERCENT_1) {
return squint_core._EQ_(f, squint_core.first(_PERCENT_1));

}), formats))) {
return f;
};

};
var settings = function () {
const page_1 = page_settings();
const shared_2 = shared_defaults();
return ({"format": (() => {
const or_3_4 = valid_format(squint_core.get(page_1, "format"));
if (squint_core.truth_(or_3_4)) {
return or_3_4} else {
const or_5_6 = valid_format(squint_core.get(shared_2, "format"));
if (squint_core.truth_(or_5_6)) {
return or_5_6} else {
return "vscode"};
};

})(), "roots": per_repo((function (repo) {
return without_trailing_slash((() => {
const or_7_8 = absolute_path(squint_core.get(squint_core.get(page_1, "roots"), squint_core.get(repo, "name")));
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
const or_9_10 = absolute_path(squint_core.get(shared_record(shared_2, repo), "savedRoot"));
if (squint_core.truth_(or_9_10)) {
return or_9_10} else {
return squint_core.get(repo, "root")};
};

})());

})), "webs": per_repo((function (repo) {
const or_11_12 = valid_web(squint_core.get(squint_core.get(page_1, "webs"), squint_core.get(repo, "name")));
if (squint_core.truth_(or_11_12)) {
return or_11_12} else {
const or_13_14 = valid_web(squint_core.get(shared_record(shared_2, repo), "savedWeb"));
if (squint_core.truth_(or_13_14)) {
return or_13_14} else {
return squint_core.get(repo, "web")};
};

}))});

};
var update_page_settings_BANG_ = function (path, value) {
const page_1 = page_settings();
const v_2 = present(value);
return write_json_BANG_(page_key(), (((1 === squint_core.count(path))) ? (((squint_core.truth_(v_2)) ? (squint_core.assoc(page_1, squint_core.first(path), v_2)) : (squint_core.dissoc(page_1, squint_core.first(path))))) : ((() => {
const vec___3_6 = path;
const group_7 = squint_core.nth(vec___3_6, 0, null);
const k_8 = squint_core.nth(vec___3_6, 1, null);
const m_9 = (() => {
const or_10_11 = squint_core.get(page_1, group_7);
if (squint_core.truth_(or_10_11)) {
return or_10_11} else {
return ({})};

})();
return squint_core.assoc(page_1, group_7, ((squint_core.truth_(v_2)) ? (squint_core.assoc(m_9, k_8, v_2)) : (squint_core.dissoc(m_9, k_8))));

})())));

};
var overridden_QMARK_ = function (s, repo) {
return !squint_core._EQ_(squint_core.get(squint_core.get(s, "roots"), squint_core.get(repo, "name")), squint_core.get(repo, "root"));

};
var sources = function () {
const s_1 = settings();
return ({"format": squint_core.get(s_1, "format"), "repos": per_repo((function (repo) {
return ({"root": squint_core.get(squint_core.get(s_1, "roots"), squint_core.get(repo, "name")), "default": squint_core.get(repo, "root"), "overridden": overridden_QMARK_(s_1, repo)});

}))});

};
var save_shared_defaults_BANG_ = function () {
const s_1 = settings();
const page_2 = page_settings();
const shared_3 = shared_defaults();
const records_4 = squint_core.reduce((function (records, repo) {
const prior_5 = shared_record(shared_3, repo);
const web_6 = (() => {
const or_7_8 = valid_web(squint_core.get(squint_core.get(page_2, "webs"), squint_core.get(repo, "name")));
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
return squint_core.get(prior_5, "savedWeb")};

})();
return squint_core.conj(squint_core.vec(squint_core.remove((function (_PERCENT_1) {
return same_repo_QMARK_(_PERCENT_1, repo);

}), records)), (() => {
const G__5_9 = ({"name": squint_core.get(repo, "name"), "root": squint_core.get(repo, "root"), "savedRoot": squint_core.get(squint_core.get(s_1, "roots"), squint_core.get(repo, "name"))});
const G__5_10 = ((squint_core.truth_(squint_core.get(repo, "remote"))) ? (({...G__5_9,["remote"]:squint_core.get(repo, "remote")})) : (G__5_9));
if (squint_core.truth_(web_6)) {
return squint_core.assoc(G__5_10, "savedWeb", web_6)} else {
return G__5_10};

})());

}), squint_core.vec((() => {
const or_11_12 = squint_core.get(shared_3, "repos");
if (squint_core.truth_(or_11_12)) {
return or_11_12} else {
return []};

})()), Object.values(squint_core.get(squint_core.deref(state), "repos")));
return write_json_BANG_(shared_key, ({"format": squint_core.get(s_1, "format"), "repos": records_4}));

};
var encode_path = function (p) {
return p.split("/").map((function (seg) {
if (squint_core.truth_(/^[A-Za-z]:$/.test(seg))) {
return seg} else {
return encodeURIComponent(seg)};

})).join("/");

};
var vscode_url = function (root, p__6) {
const map___1_3 = p__6;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const path_5 = squint_core.get(map___1_4, "path");
const start_6 = squint_core.get(map___1_4, "start");
const abs_7 = `${root??''}${"/"}${path_5??''}`;
return `${"vscode://file"}${((squint_core.truth_(abs_7.startsWith("/"))) ? (null) : ("/"))??''}${encode_path(abs_7)??''}${((squint_core.truth_(start_6)) ? (`${":"}${start_6??''}${":1"}`) : (null))??''}`;

};
var github_url = function (web, p__7) {
const map___1_3 = p__7;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const path_5 = squint_core.get(map___1_4, "path");
const start_6 = squint_core.get(map___1_4, "start");
const end_7 = squint_core.get(map___1_4, "end");
return `${web??''}${encode_path(path_5)??''}${((squint_core.truth_(start_6)) ? (`${"?plain=1#L"}${start_6??''}${((squint_core.truth_(((squint_core.truth_(end_7)) ? (!squint_core._EQ_(end_7, start_6)) : (end_7)))) ? (`-L${end_7??''}`) : (null))??''}`) : (null))??''}`;

};
var href = function (s, ref) {
const web_1 = squint_core.get(squint_core.get(s, "webs"), squint_core.get(ref, "repo"));
if (squint_core.truth_(((("github" === squint_core.get(s, "format"))) && (web_1)))) {
return github_url(web_1, ref)} else {
return vscode_url(squint_core.get(squint_core.get(s, "roots"), squint_core.get(ref, "repo")), ref)};

};
var copy_text = function (s, p__8) {
const map___1_3 = p__8;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const repo_5 = squint_core.get(map___1_4, "repo");
const path_6 = squint_core.get(map___1_4, "path");
const start_7 = squint_core.get(map___1_4, "start");
return `${squint_core.get(squint_core.get(s, "roots"), repo_5)??''}${"/"}${path_6??''}${((squint_core.truth_(start_7)) ? (`${":"}${start_7??''}`) : (null))??''}`;

};
var label = function (p__9) {
const map___1_3 = p__9;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const repo_5 = squint_core.get(map___1_4, "repo");
const path_6 = squint_core.get(map___1_4, "path");
const start_7 = squint_core.get(map___1_4, "start");
const end_8 = squint_core.get(map___1_4, "end");
return `${repo_5??''}${"/"}${path_6??''}${((squint_core.truth_(start_7)) ? (`${":"}${start_7??''}${((squint_core.truth_(((squint_core.truth_(end_8)) ? (!squint_core._EQ_(end_8, start_7)) : (end_8)))) ? (`-${end_8??''}`) : (null))??''}`) : (null))??''}`;

};
var refresh_hrefs_BANG_ = function () {
const s_1 = settings();
for (let G__2 of squint_core.iterable(Array.from(document.querySelectorAll("a[data-src][data-src-linked]")))) {
const a_3 = G__2;
const temp__23314__auto___4 = declared_ref(a_3.getAttribute("data-src"));
if (squint_core.truth_(temp__23314__auto___4)) {
const ref_5 = temp__23314__auto___4;
const h_6 = href(s_1, ref_5);
a_3.setAttribute("href", h_6);
if (squint_core._EQ_(svg_ns, a_3.namespaceURI)) {
a_3.setAttributeNS(xlink_ns, "xlink:href", h_6)}}
}
return null;

};
var el_BANG_ = /* @__PURE__ */ (() => {
const impl14_1 = (function (tag, attrs, children) {
const e_2 = document.createElement(tag);
for (let G__3 of squint_core.iterable(Object.entries(attrs))) {
const vec___4_7 = G__3;
const k_8 = squint_core.nth(vec___4_7, 0, null);
const v_9 = squint_core.nth(vec___4_7, 1, null);
e_2.setAttribute(k_8, v_9)
};
for (let G__10 of squint_core.iterable(children)) {
const c_11 = G__10;
e_2.append(c_11)
};
return e_2;

});
const f10 = (function (arg11, arg12, ...rest13) {
const self__23546__auto___12 = this;
return impl14_1.call(self__23546__auto___12, arg11, arg12, (((rest13.length === 0)) ? (null) : (rest13)));

});
(f10["squint$lang$variadic"] = impl14_1);
return f10;

})();
var format_name = function (f) {
return squint_core.second(squint_core.first(squint_core.filter((function (_PERCENT_1) {
return squint_core._EQ_(f, squint_core.first(_PERCENT_1));

}), formats)));

};
var refresh_bar_BANG_ = function () {
const temp__23314__auto___1 = squint_core.get(squint_core.deref(state), "bar");
if (squint_core.truth_(temp__23314__auto___1)) {
const bar_2 = temp__23314__auto___1;
const s_3 = settings();
const page_4 = page_settings();
const n_5 = squint_core.count(Object.keys(squint_core.get(squint_core.deref(state), "repos")));
bar_2.querySelector("summary").textContent = `${"Code links: "}${format_name(squint_core.get(s_3, "format"))??''}${" · "}${n_5??''}${(((1 === n_5)) ? (" repo") : (" repos"))??''}`;
bar_2.querySelector("select").value = squint_core.get(s_3, "format");
for (let G__6 of squint_core.iterable(Array.from(bar_2.querySelectorAll("fieldset[data-repo]")))) {
const row_7 = G__6;
const nm_8 = row_7.dataset.repo;
const root_in_9 = row_7.querySelector("input[name=root]");
const web_in_10 = row_7.querySelector("input[name=web]");
const typed_11 = present(squint_core.get(squint_core.get(page_4, "webs"), nm_8));
const web_12 = squint_core.get(squint_core.get(s_3, "webs"), nm_8);
if (squint_core._EQ_(root_in_9, document.activeElement)) {
} else {
root_in_9.value = squint_core.get(squint_core.get(s_3, "roots"), nm_8)};
if (squint_core._EQ_(web_in_10, document.activeElement)) {
} else {
web_in_10.value = ((squint_core.truth_(typed_11)) ? (typed_11) : (((squint_core.truth_(web_12)) ? (web_12) : (""))))};
row_7.querySelector(".hc-cl-unavailable").hidden = !(web_12 == null);
row_7.querySelector(".hc-cl-hint").hidden = (((typed_11 == null)) || (!(valid_web(typed_11) == null)))
}
return null;
};

};
var settings_changed_BANG_ = function () {
refresh_bar_BANG_();
return refresh_hrefs_BANG_();

};
var flash_BANG_ = function (btn, text) {
const was_1 = btn.textContent;
btn.textContent = text;
return setTimeout((function () {
return btn.textContent = was_1;

}), 1500);

};
var repo_row = function (repo) {
const nm_1 = squint_core.get(repo, "name");
const root_in_2 = el_BANG_("input", ({"type": "text", "name": "root", "spellcheck": "false", "placeholder": squint_core.get(repo, "root"), "aria-label": `${nm_1??''}${": checkout root"}`}));
const web_in_3 = el_BANG_("input", ({"type": "text", "name": "web", "spellcheck": "false", "placeholder": "https://github.com/OWNER/REPO/blob/SHA/", "aria-label": `${nm_1??''}${": GitHub URL"}`}));
const root_hint_4 = el_BANG_("span", ({"class": "hc-cl-root-hint", "hidden": ""}));
root_in_2.addEventListener("change", (function (e) {
const v_5 = present(e.target.value);
if (squint_core.truth_(((squint_core.truth_(v_5)) ? (squint_core.not(absolute_path(v_5))) : (v_5)))) {
root_hint_4.textContent = `${JSON.stringify(v_5)??''}${" isn't an absolute path, so it's ignored"}`;
root_hint_4.hidden = false} else {
root_hint_4.hidden = true;
update_page_settings_BANG_(["roots", nm_1], v_5)};
return settings_changed_BANG_();

}));
web_in_3.addEventListener("change", (function (e) {
update_page_settings_BANG_(["webs", nm_1], e.target.value);
return settings_changed_BANG_();

}));
return el_BANG_("fieldset", ({"data-repo": nm_1}), el_BANG_("legend", ({}), nm_1), el_BANG_("label", ({}), "Checkout root", root_in_2), root_hint_4, el_BANG_("label", ({}), "GitHub URL", web_in_3), el_BANG_("span", ({"class": "hc-cl-unavailable"}), "GitHub unavailable for this repo: enter its URL to link there"), el_BANG_("span", ({"class": "hc-cl-hint"}), "Not a GitHub blob URL, so it's ignored"));

};
var bar_BANG_ = function () {
const select_1 = el_BANG_("select", ({"aria-label": "Open code in"}));
const save_2 = el_BANG_("button", ({"type": "button"}), "Save as default");
const reset_3 = el_BANG_("button", ({"type": "button"}), "Reset");
const repos_4 = Object.values(squint_core.get(squint_core.deref(state), "repos"));
const bar_5 = el_BANG_("details", ({"id": "decide-code-links"}), el_BANG_("summary", ({}), "Code links"), el_BANG_("label", ({}), "Open code in ", select_1), squint_core.apply(el_BANG_, "div", ({"class": "hc-cl-repos"}), squint_core.map(repo_row, repos_4)), el_BANG_("div", ({"class": "hc-cl-actions"}), save_2, reset_3));
for (let G__6 of squint_core.iterable(formats)) {
const vec___7_10 = G__6;
const v_11 = squint_core.nth(vec___7_10, 0, null);
const text_12 = squint_core.nth(vec___7_10, 1, null);
select_1.append(el_BANG_("option", ({"value": v_11}), text_12))
};
select_1.addEventListener("change", (function (e) {
update_page_settings_BANG_(["format"], e.target.value);
return settings_changed_BANG_();

}));
save_2.addEventListener("click", (function (_) {
save_shared_defaults_BANG_();
return flash_BANG_(save_2, "Saved");

}));
reset_3.addEventListener("click", (function (_) {
remove_key_BANG_(page_key());
return settings_changed_BANG_();

}));
return bar_5;

};
var ensure_bar_BANG_ = function () {
if (squint_core.truth_(squint_core.get(squint_core.deref(state), "bar"))) {
return null} else {
const bar_1 = bar_BANG_();
const s_2 = settings();
squint_core.swap_BANG_(state, squint_core.assoc, "bar", bar_1);
if (squint_core.truth_(squint_core.some((function (_PERCENT_1) {
return overridden_QMARK_(s_2, _PERCENT_1);

}), Object.values(squint_core.get(squint_core.deref(state), "repos"))))) {
bar_1.open = true};
document.body.prepend(bar_1);
return refresh_bar_BANG_();
};

};
var copy_button = function (src) {
return el_BANG_("button", ({"type": "button", "class": "hc-src-copy", "data-src-copy": src, "aria-label": "Copy path"}), "Copy");

};
var link_el = function (src) {
return el_BANG_("a", ({"class": "hc-src-link", "data-src": src, "data-src-linked": ""}));

};
var last_decoration_id = squint_core.atom(0);
var block_of = function (el) {
const G__15_1 = el.localName;
switch (G__15_1) {case "pre":
return el;

break;
case "code":
const p_3 = el.parentElement;
if (squint_core.truth_(((squint_core.truth_(p_3)) ? (("pre" === p_3.localName)) : (p_3)))) {
return p_3;
};

break;
};

};
var decorate_BANG_ = function (el, src, ref) {
const id_1 = `${squint_core.swap_BANG_(last_decoration_id, squint_core.inc)??''}`;
const own_2 = (function (n) {
n.setAttribute("data-src-of", id_1);
return n;

});
const block_3 = block_of(el);
const page_link_4 = (() => {
const G__16_5 = el.parentElement;
if ((G__16_5 == null)) {
return null} else {
return G__16_5.closest("a")};

})();
el.setAttribute("data-src-id", id_1);
if (("a" === el.localName)) {
return null} else {
if (squint_core.truth_(block_3)) {
const a_6 = link_el(src);
a_6.textContent = label(ref);
return block_3.before(own_2(el_BANG_("div", ({"class": "hc-src"}), a_6, copy_button(src))));
} else {
if (squint_core.truth_(page_link_4)) {
return page_link_4.after(own_2(copy_button(src)))} else {
if ("else") {
const a_7 = own_2(link_el(src));
el.before(a_7);
a_7.append(el);
return a_7.after(own_2(copy_button(src)));
} else {
return null}}}};

};
var undecorate_BANG_ = function (el) {
const temp__23314__auto___1 = el.getAttribute("data-src-id");
if (squint_core.truth_(temp__23314__auto___1)) {
const id_2 = temp__23314__auto___1;
const sel_3 = `${"[data-src-of=\""}${id_2??''}${"\"]"}`;
const wrapper_4 = el.parentElement;
if (squint_core.truth_(((squint_core.truth_(wrapper_4)) ? (wrapper_4.matches(sel_3)) : (wrapper_4)))) {
wrapper_4.before(el)};
for (let G__5 of squint_core.iterable(Array.from(document.querySelectorAll(sel_3)))) {
const n_6 = G__5;
n_6.remove()
};
return el.removeAttribute("data-src-id");
};

};
var link_BANG_ = function (el) {
el.setAttribute("data-src-linked", "");
const src_1 = el.getAttribute("data-src");
const ref_2 = parse_src(src_1);
const report_BANG__3 = squint_core.get(squint_core.deref(state), "report!");
if ((ref_2 == null)) {
report_BANG__3("code-link", `${"data-src "}${JSON.stringify(src_1)??''}${" is not NAME:PATH[#Lstart[-Lend]]"}`, src_1);
return false;
} else {
if (squint_core.not(declared_QMARK_(squint_core.get(ref_2, "repo")))) {
report_BANG__3("code-link", `${"data-src "}${JSON.stringify(src_1)??''}${" names repo "}${squint_core.get(ref_2, "repo")??''}${", which no <meta name=\"hc-repo\"> declares"}`, src_1);
return false;
} else {
if ("else") {
ensure_bar_BANG_();
decorate_BANG_(el, src_1, ref_2);
return true;
} else {
return null}}};

};
var relink_BANG_ = function (el) {
undecorate_BANG_(el);
if (("a" === el.localName)) {
el.removeAttribute("href");
el.removeAttributeNS(xlink_ns, "href")};
if (squint_core.truth_(el.hasAttribute("data-src"))) {
return link_BANG_(el)} else {
el.removeAttribute("data-src-linked");
return false;
};

};
var link_within_BANG_ = function (root) {
const sel_1 = "[data-src]:not([data-src-linked])";
const els_2 = (() => {
const G__17_3 = Array.from(root.querySelectorAll(sel_1));
if (squint_core.truth_(root.matches(sel_1))) {
return G__17_3.concat([root])} else {
return G__17_3};

})();
return (squint_core.count(squint_core.filter(squint_core.identity, squint_core.mapv(link_BANG_, els_2))) > 0);

};
var on_mutations = function (records) {
if (squint_core.truth_(squint_core.some(squint_core.identity, squint_core.mapv((function (r) {
if (("attributes" === r.type)) {
const t_1 = r.target;
if (squint_core.truth_(t_1.hasAttribute("data-src-linked"))) {
return relink_BANG_(t_1)} else {
if (squint_core.truth_(t_1.hasAttribute("data-src"))) {
return link_BANG_(t_1)} else {
if ("else") {
return false} else {
return null}}};
} else {
return squint_core.some(squint_core.identity, squint_core.mapv((function (_PERCENT_1) {
if ((1 === _PERCENT_1.nodeType)) {
return link_within_BANG_(_PERCENT_1)} else {
return false};

}), Array.from(r.addedNodes)))};

}), records)))) {
return refresh_hrefs_BANG_();
};

};
var select_text_BANG_ = function (btn, text) {
const next_1 = btn.nextElementSibling;
const span_2 = ((squint_core.truth_(((squint_core.truth_(next_1)) ? (next_1.classList.contains("hc-src-path")) : (next_1)))) ? (next_1) : ((() => {
const s_3 = el_BANG_("span", ({"class": "hc-src-path"}));
btn.after(s_3);
return s_3;

})()));
const range_4 = document.createRange();
const sel_5 = window.getSelection();
span_2.textContent = text;
range_4.selectNodeContents(span_2);
sel_5.removeAllRanges();
return sel_5.addRange(range_4);

};
var copy_BANG_ = async function (btn) {
const text_1 = copy_text(settings(), parse_src(btn.dataset.srcCopy));
try{
(await navigator.clipboard.writeText(text_1));
return flash_BANG_(btn, "Copied");
}
catch(__2){
return select_text_BANG_(btn, text_1);
}
;

};
var on_click = function (e) {
const temp__23314__auto___1 = e.target.closest("button.hc-src-copy");
if (squint_core.truth_(temp__23314__auto___1)) {
const btn_2 = temp__23314__auto___1;
return copy_BANG_(btn_2);
};

};
var on_done = function (e) {
if (squint_core.truth_((() => {
const and_1_2 = squint_core.get(squint_core.deref(state), "bar");
if (squint_core.truth_(and_1_2)) {
return e.target.closest("[data-decision=status][data-choice=done]")} else {
return and_1_2};

})())) {
return window.htmlChannel.record("sources", sources());
};

};
var start_BANG_ = function (report_BANG_) {
squint_core.reset_BANG_(state, ({"repos": read_repos(report_BANG_), "report!": report_BANG_, "bar": null}));
if (squint_core.truth_(link_within_BANG_(document.body))) {
refresh_hrefs_BANG_()};
(new MutationObserver(on_mutations)).observe(document.body, ({"childList": true, "subtree": true, "attributes": true, "attributeFilter": ["data-src"]}));
document.addEventListener("click", on_done, true);
return document.addEventListener("click", on_click);

};

export { sources, absolute_path, meta_fields, decoded, shared_record, declared_QMARK_, shared_key, copy_BANG_, github_url, relink_BANG_, svg_ns, refresh_bar_BANG_, save_shared_defaults_BANG_, settings, flash_BANG_, identifiers, repo_row, label, encode_path, decorate_BANG_, valid_web, last_decoration_id, repo_problem, block_of, copy_button, on_click, same_repo_QMARK_, href, link_BANG_, per_repo, valid_format, ensure_bar_BANG_, overridden_QMARK_, start_BANG_, vscode_url, read_repos, on_mutations, link_el, undecorate_BANG_, name_re, src_re, absolute_re, write_json_BANG_, copy_text, link_within_BANG_, remote_of, formats, update_page_settings_BANG_, state, parse_src, settings_changed_BANG_, format_name, web_re, present, without_trailing_slash, el_BANG_, declared_ref, refresh_hrefs_BANG_, page_settings, page_key, bar_BANG_, read_json, on_done, remove_key_BANG_, xlink_ns, select_text_BANG_, shared_defaults }
