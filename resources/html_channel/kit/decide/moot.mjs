import * as squint_core from '../squint-core.js';
import * as core from './core.mjs';
import * as decide_DOT_core from './core.mjs';
var links = squint_core.atom(null);
var moot_now = squint_core.atom(({}));
var split_at_eq = function (s) {
const i_1 = s.indexOf("=");
if ((i_1 >= 0)) {
return [squint_core.subs(s, 0, i_1), squint_core.subs(s, (i_1 + 1))];
};

};
var controls = function (id) {
return Array.from(core.els_for(id));

};
var plain_object_QMARK_ = function (x) {
return ("[object Object]" === Object.prototype.toString.call(x));

};
var choice_control_QMARK_ = function (el) {
if (("BUTTON" === el.tagName)) {
return true} else {
return ("radio" === el.type)};

};
var control_kind = function (el) {
if (("SELECT" === el.tagName)) {
return "select"} else {
if (("checkbox" === el.type)) {
return "checkbox"} else {
if ("else") {
return "text field"} else {
return null}}};

};
var parse_terms = function (w) {
const words_1 = w.trim().split(/\s+/).filter((function (_PERCENT_1) {
return (_PERCENT_1.length > 0);

}));
if ((words_1.length === 0)) {
return ({"error": "its \"when\" is empty"})} else {
let ws_2 = squint_core.seq(words_1);
let terms_3 = [];
while(true){
const temp__23255__auto___4 = squint_core.first(ws_2);
if (squint_core.truth_(temp__23255__auto___4)) {
const word_5 = temp__23255__auto___4;
const temp__23255__auto___6 = split_at_eq(word_5);
if (squint_core.truth_(temp__23255__auto___6)) {
const vec___7_10 = temp__23255__auto___6;
const id_11 = squint_core.nth(vec___7_10, 0, null);
const v_12 = squint_core.nth(vec___7_10, 1, null);
if (squint_core.truth_(squint_core.seq(controls(id_11)))) {
let G__13 = squint_core.rest(ws_2);
let G__14 = squint_core.conj(terms_3, ({"id": id_11, "values": squint_core.vec(v_12.split("|"))}));
ws_2 = G__13;
terms_3 = G__14;
continue;
} else {
return ({"error": `${"its \"when\" reads \""}${id_11??''}${"\", which no control on the page answers"}`})};
} else {
return ({"error": `${"the term \""}${word_5??''}${"\" in its \"when\" has no ="}`})};
} else {
return ({"terms": terms_3})};
;break;
}
};

};
var radio_label = function (input) {
const or_1_2 = input.closest("label");
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
const or_3_4 = ((squint_core.truth_(squint_core.seq(input.id))) ? (document.querySelector(`${"label[for=\""}${CSS.escape(input.id)??''}${"\"]"}`)) : (null));
if (squint_core.truth_(or_3_4)) {
return or_3_4} else {
return input};
};

};
var option_target = function (id, value) {
const els_1 = controls(id);
const match_2 = squint_core.some((function (el) {
if (((((("BUTTON" === el.tagName)) && (squint_core._EQ_(value, el.dataset.choice)))) || (((("radio" === el.type)) && (squint_core._EQ_(value, el.value)))))) {
return el;
};

}), els_1);
if (squint_core.truth_(match_2)) {
return ({"target": ((("radio" === match_2.type)) ? (radio_label(match_2)) : (match_2))})} else {
if (squint_core.truth_(squint_core.some(choice_control_QMARK_, els_1))) {
return ({"error": `${"question \""}${id??''}${"\" has no button or radio with the value \""}${value??''}${"\""}`})} else {
if ("else") {
return ({"error": `${"\""}${id??''}${"\" is a "}${control_kind(squint_core.first(els_1))??''}${": only button and radio options can be moot"}`})} else {
return null}}};

};
var read_link = function (key, entry) {
const where_4 = `${"the link \""}${key??''}${"\""}`;
const vec___1_5 = (() => {
const or_6_7 = split_at_eq(key);
if (squint_core.truth_(or_6_7)) {
return or_6_7} else {
return [key, null]};

})();
const id_8 = squint_core.nth(vec___1_5, 0, null);
const value_9 = squint_core.nth(vec___1_5, 1, null);
if (((squint_core.not(plain_object_QMARK_(entry))) || (!squint_core.string_QMARK_(entry.when)))) {
return ({"problem": `${where_4}${" needs {\"when\": \"…\", \"reason\": \"…\"}"}`})} else {
if (squint_core.not(squint_core.seq(controls(id_8)))) {
return ({"problem": `${where_4}${" names \""}${id_8??''}${"\", which no control on the page answers"}`})} else {
if ("else") {
const map___10_12 = parse_terms(entry.when);
const map___10_13 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___10_12))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___10_12))) ? (map___10_12) : (squint_core.seq_to_map_for_destructuring(map___10_12)))) : (map___10_12));
const terms_14 = squint_core.get(map___10_13, "terms");
const error_15 = squint_core.get(map___10_13, "error");
const place_16 = ((squint_core.truth_(error_15)) ? (({"error": error_15})) : (((!(value_9 == null)) ? (option_target(id_8, value_9)) : ((("else") ? ((() => {
const temp__23255__auto___17 = document.querySelector(`${"[data-question=\""}${CSS.escape(id_8)??''}${"\"]"}`);
if (squint_core.truth_(temp__23255__auto___17)) {
const c_18 = temp__23255__auto___17;
return ({"container": c_18});
} else {
return ({"error": `${"no element has data-question=\""}${id_8??''}${"\" to fold"}`})};

})()) : (null))))));
const reason_19 = ((squint_core.truth_(((squint_core.string_QMARK_(entry.reason)) && (squint_core.seq(entry.reason.trim()))))) ? (entry.reason.trim()) : (null));
if (squint_core.truth_(squint_core.get(place_16, "error"))) {
return ({"problem": `${where_4}${": "}${squint_core.get(place_16, "error")??''}`})} else {
const G__52_20 = ({"link": squint_core.merge(({"key": key, "id": id_8, "value": value_9, "terms": terms_14, "reason": reason_19}), place_16)});
if ((reason_19 == null)) {
return ({...G__52_20,["problem"]:`${where_4}${" has no \"reason\", so the kit writes one"}`})} else {
return G__52_20};
};
} else {
return null}}};

};
var dependency_order = function (questions) {
const linked_1 = (new Set(squint_core.map("id", questions)));
let done_2 = (new Set());
let ordered_3 = [];
let left_4 = questions;
while(true){
const ready_5 = squint_core.vec(squint_core.filter((function (l) {
return squint_core.every_QMARK_((function (t) {
if (squint_core.not(linked_1.has(squint_core.get(t, "id")))) {
return true} else {
return done_2.has(squint_core.get(t, "id"))};

}), squint_core.get(l, "terms"));

}), left_4));
if (squint_core.empty_QMARK_(ready_5)) {
return [ordered_3, left_4]} else {
for (let G__6 of squint_core.iterable(ready_5)) {
const l_7 = G__6;
done_2.add(squint_core.get(l_7, "id"))
};
let G__8 = done_2;
let G__9 = squint_core.vec(squint_core.concat(ordered_3, ready_5));
let G__10 = squint_core.vec(squint_core.remove((function (l) {
return done_2.has(squint_core.get(l, "id"));

}), left_4));
done_2 = G__8;
ordered_3 = G__9;
left_4 = G__10;
continue;
};
;break;
}
;

};
var read_links = function (text) {
const vec___1_4 = (() => {
try{
return [JSON.parse(text), null];
}
catch(e_5){
return [null, e_5.message];
}

})();
const parsed_6 = squint_core.nth(vec___1_4, 0, null);
const parse_error_7 = squint_core.nth(vec___1_4, 1, null);
if (squint_core.not(plain_object_QMARK_(parsed_6))) {
return ({"problems": [`${"the link list isn't a JSON object"}${((squint_core.truth_(parse_error_7)) ? (`${": "}${parse_error_7??''}`) : (null))??''}`]})} else {
const read_11 = squint_core.map((function (p__53) {
const vec___12_15 = p__53;
const k_16 = squint_core.nth(vec___12_15, 0, null);
const v_17 = squint_core.nth(vec___12_15, 1, null);
return read_link(k_16, v_17);

}), Object.entries(parsed_6));
const usable_18 = squint_core.vec(squint_core.keep("link", read_11));
const vec___8_19 = dependency_order(squint_core.vec(squint_core.filter((function (_PERCENT_1) {
return (squint_core.get(_PERCENT_1, "value") == null);

}), usable_18)));
const ordered_20 = squint_core.nth(vec___8_19, 0, null);
const stuck_21 = squint_core.nth(vec___8_19, 1, null);
return ({"links": ({"questions": ordered_20, "options": squint_core.vec(squint_core.filter((function (_PERCENT_1) {
return !(squint_core.get(_PERCENT_1, "value") == null);

}), usable_18))}), "problems": (() => {
const G__54_22 = squint_core.vec(squint_core.keep("problem", read_11));
if (squint_core.truth_(squint_core.seq(stuck_21))) {
return squint_core.conj(G__54_22, `${"the links "}${squint_core.into_array(squint_core.map((function (_PERCENT_1) {
return `${"\""}${squint_core.get(_PERCENT_1, "key")??''}${"\""}`;

}), stuck_21)).join(", ")??''}${" form or depend on a cycle, so they're dropped"}`)} else {
return G__54_22};

})()});
};

};
var answer_of = function (id) {
const or_1_2 = squint_core.some((function (el) {
if (("BUTTON" === el.tagName)) {
if (squint_core.truth_(el.classList.contains("chosen"))) {
return el.dataset.choice;
}} else {
if (("radio" === el.type)) {
if (squint_core.truth_(el.checked)) {
return el.value;
}} else {
if (("checkbox" === el.type)) {
return `${el.checked??''}`} else {
if ("else") {
if ((el.value.length > 0)) {
return el.value;
}} else {
return null}}}};

}), controls(id));
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return ""};

};
var first_heading = function (container) {
return squint_core.some((function (_PERCENT_1) {
if (squint_core.truth_(/^H[1-6]$/.test(_PERCENT_1.tagName))) {
return _PERCENT_1;
};

}), Array.from(container.children));

};
var text_of = function (el) {
return el.textContent.replace(/\s+/, " ").trim();

};
var answer_label = function (id, v) {
const or_2_3 = squint_core.some((function (el) {
if (((("BUTTON" === el.tagName)) && (squint_core._EQ_(v, el.dataset.choice)))) {
return text_of(el)} else {
if (((("radio" === el.type)) && (squint_core._EQ_(v, el.value)))) {
const l_1 = radio_label(el);
if (!squint_core._EQ_(l_1, el)) {
return text_of(l_1);
};
} else {
if (("SELECT" === el.tagName)) {
return squint_core.some((function (_PERCENT_1) {
if (squint_core._EQ_(v, _PERCENT_1.value)) {
return text_of(_PERCENT_1);
};

}), Array.from(el.options))} else {
return null}}};

}), controls(id));
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return v};

};
var written_reason = function (id, v) {
const c_1 = document.querySelector(`${"[data-question=\""}${CSS.escape(id)??''}${"\"]"}`);
const heading_text_2 = (() => {
const G__55_3 = c_1;
const G__55_4 = (((G__55_3 == null)) ? (null) : (first_heading(G__55_3)));
if ((G__55_4 == null)) {
return null} else {
return text_of(G__55_4)};

})();
const h_5 = ((squint_core.truth_(squint_core.seq(heading_text_2))) ? (heading_text_2) : (id));
if (squint_core._EQ_("", v)) {
return `${"“"}${h_5??''}${"” is unanswered"}`} else {
return `${"“"}${h_5??''}${"” is “"}${answer_label(id, v)??''}${"”"}`};

};
var evaluate = function (p__56) {
const map___1_3 = p__56;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const questions_5 = squint_core.get(map___1_4, "questions");
const options_6 = squint_core.get(map___1_4, "options");
const moot_ids_7 = (new Set());
const value_of_8 = (function (id) {
if (squint_core.truth_(moot_ids_7.has(id))) {
return ""} else {
return answer_of(id)};

});
const holding_term_9 = (function (link) {
return squint_core.some((function (t) {
const v_10 = value_of_8(squint_core.get(t, "id"));
if (squint_core.truth_(squint_core.get(t, "values").includes(v_10))) {
return [squint_core.get(t, "id"), v_10];
};

}), squint_core.get(link, "terms"));

});
return squint_core.reduce((function (acc, link) {
const temp__23255__auto___11 = holding_term_9(link);
if (squint_core.truth_(temp__23255__auto___11)) {
const vec___12_15 = temp__23255__auto___11;
const id_16 = squint_core.nth(vec___12_15, 0, null);
const v_17 = squint_core.nth(vec___12_15, 1, null);
if ((squint_core.get(link, "value") == null)) {
moot_ids_7.add(squint_core.get(link, "id"))};
return squint_core.assoc(acc, squint_core.get(link, "key"), (() => {
const or_18_19 = squint_core.get(link, "reason");
if (squint_core.truth_(or_18_19)) {
return or_18_19} else {
return written_reason(id_16, v_17)};

})());
} else {
return acc};

}), ({}), squint_core.concat(questions_5, options_6));

};
var toggle_BANG_ = function (container, button) {
const open_QMARK__1 = container.classList.toggle("hc-moot-open");
button.setAttribute("aria-expanded", `${open_QMARK__1??''}`);
return button.textContent = ((squint_core.truth_(open_QMARK__1)) ? ("Hide") : ("Show"));

};
var bar_of = function (container) {
return container.querySelector(":scope > .hc-moot-bar");

};
var show_question_BANG_ = function (p__57, reason) {
const map___1_3 = p__57;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const container_5 = squint_core.get(map___1_4, "container");
const bar_6 = (() => {
const or_7_8 = bar_of(container_5);
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
const reason_el_9 = document.createElement("span");
const button_10 = document.createElement("button");
const bar_11 = document.createElement("div");
const heading_12 = first_heading(container_5);
reason_el_9.className = "hc-moot-reason";
button_10.type = "button";
button_10.className = "hc-moot-toggle";
button_10.setAttribute("aria-expanded", "false");
button_10.textContent = "Show";
button_10.addEventListener("click", (function (_) {
return toggle_BANG_(container_5, button_10);

}));
bar_11.className = "hc-moot-bar";
bar_11.append(reason_el_9, button_10);
if (squint_core.truth_(heading_12)) {
heading_12.classList.add("hc-moot-head");
heading_12.after(bar_11)} else {
container_5.prepend(bar_11)};
container_5.classList.add("hc-moot");
return bar_11;
};

})();
return bar_6.querySelector(".hc-moot-reason").textContent = `${"Moot: "}${reason??''}`;

};
var unshow_question_BANG_ = function (p__58) {
const map___1_3 = p__58;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const container_5 = squint_core.get(map___1_4, "container");
const temp__23314__auto___6 = bar_of(container_5);
if (squint_core.truth_(temp__23314__auto___6)) {
const bar_7 = temp__23314__auto___6;
bar_7.remove();
container_5.classList.remove("hc-moot", "hc-moot-open");
const G__59_8 = first_heading(container_5);
const G__59_9 = (((G__59_8 == null)) ? (null) : (G__59_8.classList));
if ((G__59_9 == null)) {
return null} else {
return G__59_9.remove("hc-moot-head")};
};

};
var option_reason_of = function (target) {
const n_1 = target.nextElementSibling;
if (squint_core.truth_(((squint_core.truth_(n_1)) ? (n_1.classList.contains("hc-moot-option-reason")) : (n_1)))) {
return n_1;
};

};
var show_option_BANG_ = function (p__60, reason) {
const map___1_3 = p__60;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const target_5 = squint_core.get(map___1_4, "target");
const r_6 = (() => {
const or_7_8 = option_reason_of(target_5);
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
const r_9 = document.createElement("span");
r_9.className = "hc-moot-option-reason";
target_5.classList.add("hc-moot-option");
target_5.after(r_9);
return r_9;
};

})();
return r_6.textContent = `${"Moot: "}${reason??''}`;

};
var unshow_option_BANG_ = function (p__61) {
const map___1_3 = p__61;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const target_5 = squint_core.get(map___1_4, "target");
const temp__23314__auto___6 = option_reason_of(target_5);
if (squint_core.truth_(temp__23314__auto___6)) {
const r_7 = temp__23314__auto___6;
r_7.remove();
return target_5.classList.remove("hc-moot-option");
};

};
var update_BANG_ = function () {
const map___1_3 = squint_core.deref(links);
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const ls_5 = map___1_4;
const questions_6 = squint_core.get(map___1_4, "questions");
const options_7 = squint_core.get(map___1_4, "options");
const now_8 = evaluate(ls_5);
for (let G__9 of squint_core.iterable(questions_6)) {
const l_10 = G__9;
const temp__23255__auto___11 = now_8[squint_core.get(l_10, "key")];
if (squint_core.truth_(temp__23255__auto___11)) {
const r_12 = temp__23255__auto___11;
show_question_BANG_(l_10, r_12)} else {
unshow_question_BANG_(l_10)}
};
for (let G__13 of squint_core.iterable(options_7)) {
const l_14 = G__13;
const temp__23255__auto___15 = now_8[squint_core.get(l_14, "key")];
if (squint_core.truth_(temp__23255__auto___15)) {
const r_16 = temp__23255__auto___15;
show_option_BANG_(l_14, r_16)} else {
unshow_option_BANG_(l_14)}
};
return squint_core.reset_BANG_(moot_now, now_8);

};
var on_done = function (e) {
if (squint_core.truth_(e.target.closest("[data-decision=status][data-choice=done]"))) {
return window.htmlChannel.record("moot", squint_core.deref(moot_now));
};

};
var start_BANG_ = function (report_BANG_) {
const lists_1 = document.querySelectorAll("script#hc-moot");
const n_2 = lists_1.length;
if ((n_2 > 0)) {
if ((n_2 > 1)) {
report_BANG_("moot", `${"a page has one link list, <script id=\"hc-moot\">; found "}${n_2??''}${", so only the first is read"}`, "hc-moot")};
const map___3_5 = read_links(lists_1[0].textContent);
const map___3_6 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___3_5))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___3_5))) ? (map___3_5) : (squint_core.seq_to_map_for_destructuring(map___3_5)))) : (map___3_5));
const ls_7 = squint_core.get(map___3_6, "links");
const problems_8 = squint_core.get(map___3_6, "problems");
for (let G__9 of squint_core.iterable(problems_8)) {
const p_10 = G__9;
report_BANG_("moot", p_10, "hc-moot")
};
if (squint_core.truth_(ls_7)) {
squint_core.reset_BANG_(links, ls_7);
document.addEventListener("click", on_done, true);
core.on_answers_shown_BANG_(update_BANG_);
return update_BANG_();
};
};

};

export { control_kind, split_at_eq, unshow_question_BANG_, update_BANG_, radio_label, written_reason, first_heading, option_target, unshow_option_BANG_, plain_object_QMARK_, show_option_BANG_, text_of, moot_now, evaluate, parse_terms, start_BANG_, links, answer_of, option_reason_of, read_links, bar_of, dependency_order, answer_label, controls, show_question_BANG_, toggle_BANG_, choice_control_QMARK_, on_done, read_link }
