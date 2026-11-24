import * as squint_core from '../squint-core.js';
import * as core from './core.mjs';
import * as decide_DOT_core from './core.mjs';
var links = squint_core.atom(null);
var shown = (new Map());
var display_problems = squint_core.atom(({}));
var settle_ms = 1500;
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
const values_13 = squint_core.vec(v_12.split("|"));
if (squint_core.truth_((((squint_core.count(values_13) > 1)) && (squint_core.some((function (_PERCENT_1) {
return squint_core._EQ_("", _PERCENT_1);

}), values_13))))) {
return ({"error": `${"the term \""}${word_5??''}${"\" in its \"when\" has an empty value; to match an unanswered question, write \""}${id_11??''}${"=\" as a term of its own"}`})} else {
let G__14 = squint_core.rest(ws_2);
let G__15 = squint_core.conj(terms_3, ({"id": id_11, "values": values_13}));
ws_2 = G__14;
terms_3 = G__15;
continue;
};
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
var link_name = function (key) {
return `${"the link \""}${key??''}${"\""}`;

};
var read_link = function (key, entry) {
const where_4 = link_name(key);
const vec___1_5 = (() => {
const or_6_7 = split_at_eq(key);
if (squint_core.truth_(or_6_7)) {
return or_6_7} else {
return [key, null]};

})();
const id_8 = squint_core.nth(vec___1_5, 0, null);
const value_9 = squint_core.nth(vec___1_5, 1, null);
if (((squint_core.not(plain_object_QMARK_(entry))) || (!squint_core.string_QMARK_(entry.when)))) {
return ({"problem": `${where_4??''}${" needs {\"when\": \"…\", \"reason\": \"…\"}"}`})} else {
const map___10_12 = parse_terms(entry.when);
const map___10_13 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___10_12))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___10_12))) ? (map___10_12) : (squint_core.seq_to_map_for_destructuring(map___10_12)))) : (map___10_12));
const terms_14 = squint_core.get(map___10_13, "terms");
const error_15 = squint_core.get(map___10_13, "error");
const reason_16 = ((squint_core.truth_(((squint_core.string_QMARK_(entry.reason)) && (squint_core.seq(entry.reason.trim()))))) ? (entry.reason.trim()) : (null));
if (squint_core.truth_(error_15)) {
return ({"problem": `${where_4??''}${": "}${error_15??''}`})} else {
const G__63_17 = ({"link": ({"key": key, "id": id_8, "value": value_9, "terms": terms_14, "reason": reason_16})});
if ((reason_16 == null)) {
return ({...G__63_17,["problem"]:`${where_4??''}${" has no \"reason\", so the kit writes one"}`})} else {
return G__63_17};
};
};

};
var control_QMARK_ = function (el) {
const or_1_2 = el.hasAttribute("data-decision");
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return el.matches("input, select, textarea, button, option")};

};
var resolve_place = function (p__64) {
const map___1_3 = p__64;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const key_5 = squint_core.get(map___1_4, "key");
const id_6 = squint_core.get(map___1_4, "id");
const value_7 = squint_core.get(map___1_4, "value");
const terms_8 = squint_core.get(map___1_4, "terms");
const where_9 = link_name(key_5);
const missing_10 = squint_core.some((function (_PERCENT_1) {
if (squint_core.truth_(squint_core.seq(controls(_PERCENT_1)))) {
return null} else {
return _PERCENT_1;
};

}), squint_core.cons(id_6, squint_core.map("id", terms_8)));
if (squint_core._EQ_(missing_10, id_6)) {
return ({"problem": `${where_9??''}${" names \""}${id_6??''}${"\", which no control on the page answers"}`})} else {
if (!(missing_10 == null)) {
return ({"problem": `${where_9??''}${": its \"when\" reads \""}${missing_10??''}${"\", which no control on the page answers"}`})} else {
if (!(value_7 == null)) {
const map___11_13 = option_target(id_6, value_7);
const map___11_14 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___11_13))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___11_13))) ? (map___11_13) : (squint_core.seq_to_map_for_destructuring(map___11_13)))) : (map___11_13));
const target_15 = squint_core.get(map___11_14, "target");
const error_16 = squint_core.get(map___11_14, "error");
if (squint_core.truth_(error_16)) {
return ({"problem": `${where_9??''}${": "}${error_16??''}`})} else {
if (("INPUT" === target_15.tagName)) {
return ({"target": target_15, "warning": `${where_9??''}${": the radio \""}${value_7??''}${"\" has no <label>, so only the radio greys; wrap it and its text in a <label>"}`})} else {
if ("else") {
return ({"target": target_15})} else {
return null}}};
} else {
if ("else") {
const cs_17 = Array.from(document.querySelectorAll(`${"[data-question=\""}${CSS.escape(id_6)??''}${"\"]"}`));
const c_18 = squint_core.first(cs_17);
const inside_19 = ((squint_core.truth_(c_18)) ? (squint_core.some((function (t) {
if (squint_core.truth_(squint_core.some((function (_PERCENT_1) {
return c_18.contains(_PERCENT_1);

}), controls(squint_core.get(t, "id"))))) {
return squint_core.get(t, "id");
};

}), terms_8)) : (null));
if ((c_18 == null)) {
return ({"problem": `${where_9??''}${": no element has data-question=\""}${id_6??''}${"\" to fold"}`})} else {
if (squint_core.truth_(control_QMARK_(c_18))) {
return ({"problem": `${where_9??''}${": data-question=\""}${id_6??''}${"\" is on a control; put it on an element around the question"}`})} else {
if (!(inside_19 == null)) {
return ({"problem": `${where_9??''}${": its data-question container holds the controls of \""}${inside_19??''}${"\", which its \"when\" reads, so folding would hide them; move them out"}`})} else {
if ((squint_core.count(cs_17) > 1)) {
return ({"container": c_18, "warning": `${where_9??''}${": "}${squint_core.count(cs_17)??''}${" elements have data-question=\""}${id_6??''}${"\"; only the first folds"}`})} else {
if ("else") {
return ({"container": c_18})} else {
return null}}}}};
} else {
return null}}}};

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
var cycle_members = function (stuck) {
const by_id_1 = (new Map(squint_core.map((function (l) {
return [squint_core.get(l, "id"), l];

}), stuck)));
const reads_2 = (function (l) {
return squint_core.keep((function (_PERCENT_1) {
return by_id_1.get(squint_core.get(_PERCENT_1, "id"));

}), squint_core.get(l, "terms"));

});
const reaches_itself_QMARK__3 = (function (start) {
let todo_4 = squint_core.vec(reads_2(start));
let seen_5 = (new Set());
while(true){
const temp__23255__auto___6 = squint_core.first(todo_4);
if (squint_core.truth_(temp__23255__auto___6)) {
const l_7 = temp__23255__auto___6;
if ((l_7 === start)) {
return true} else {
if (squint_core.truth_(seen_5.has(squint_core.get(l_7, "id")))) {
let G__8 = squint_core.vec(squint_core.rest(todo_4));
let G__9 = seen_5;
todo_4 = G__8;
seen_5 = G__9;
continue;
} else {
if ("else") {
seen_5.add(squint_core.get(l_7, "id"));
let G__10 = squint_core.vec(squint_core.concat(squint_core.rest(todo_4), reads_2(l_7)));
let G__11 = seen_5;
todo_4 = G__10;
seen_5 = G__11;
continue;
} else {
return null}}};
} else {
return false};
;break;
}
;

});
return squint_core.filterv(reaches_itself_QMARK__3, stuck);

};
var cycle_problem = function (members) {
if ((1 === squint_core.count(members))) {
return `${link_name(squint_core.get(squint_core.first(members), "key"))??''}${" forms a cycle, so it's dropped"}`} else {
return `${"the links "}${squint_core.into_array(squint_core.map((function (_PERCENT_1) {
return `${"\""}${squint_core.get(_PERCENT_1, "key")??''}${"\""}`;

}), members)).join(", ")??''}${" form a cycle, so they're dropped"}`};

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
const read_14 = squint_core.map((function (p__65) {
const vec___15_18 = p__65;
const k_19 = squint_core.nth(vec___15_18, 0, null);
const v_20 = squint_core.nth(vec___15_18, 1, null);
return read_link(k_19, v_20);

}), Object.entries(parsed_6));
const usable_21 = squint_core.vec(squint_core.keep("link", read_14));
const questions_22 = squint_core.vec(squint_core.filter((function (_PERCENT_1) {
return (squint_core.get(_PERCENT_1, "value") == null);

}), usable_21));
const vec___8_23 = dependency_order(questions_22);
const __24 = squint_core.nth(vec___8_23, 0, null);
const stuck_25 = squint_core.nth(vec___8_23, 1, null);
const members_26 = cycle_members(stuck_25);
const dropped_27 = (new Set(squint_core.map("key", members_26)));
const vec___11_28 = dependency_order(squint_core.vec(squint_core.remove((function (_PERCENT_1) {
return dropped_27.has(squint_core.get(_PERCENT_1, "key"));

}), questions_22)));
const ordered_29 = squint_core.nth(vec___11_28, 0, null);
const __30 = squint_core.nth(vec___11_28, 1, null);
return ({"links": ({"questions": ordered_29, "options": squint_core.vec(squint_core.filter((function (_PERCENT_1) {
return !(squint_core.get(_PERCENT_1, "value") == null);

}), usable_21))}), "problems": (() => {
const G__66_31 = squint_core.vec(squint_core.keep("problem", read_14));
if (squint_core.truth_(squint_core.seq(members_26))) {
return squint_core.conj(G__66_31, cycle_problem(members_26))} else {
return G__66_31};

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
if (!(squint_core.get(squint_core.get(squint_core.deref(core.answers), id), "value") == null)) {
return `${el.checked??''}`} else {
return ""}} else {
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
var checkbox_shows = function (id) {
return squint_core.some((function (_PERCENT_1) {
if (("checkbox" === _PERCENT_1.type)) {
return `${_PERCENT_1.checked??''}`;
};

}), controls(id));

};
var term_matches_QMARK_ = function (t, v) {
const or_1_2 = squint_core.get(t, "values").includes(v);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
if (squint_core._EQ_("", v)) {
const temp__23314__auto___3 = checkbox_shows(squint_core.get(t, "id"));
if (squint_core.truth_(temp__23314__auto___3)) {
const shown_4 = temp__23314__auto___3;
return squint_core.get(t, "values").includes(shown_4);
};
} else {
return false}};

};
var details_QMARK_ = function (el) {
return ("DETAILS" === el.tagName);

};
var first_heading = function (container) {
const or_1_2 = ((squint_core.truth_(details_QMARK_(container))) ? (container.querySelector(":scope > summary")) : (null));
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return container.querySelector("h1, h2, h3, h4, h5, h6")};

};
var head_child = function (container) {
const temp__23314__auto___1 = first_heading(container);
if (squint_core.truth_(temp__23314__auto___1)) {
const h_2 = temp__23314__auto___1;
let el_3 = h_2;
while(true){
if ((el_3.parentElement === container)) {
return el_3} else {
let G__4 = el_3.parentElement;
el_3 = G__4;
continue;
};
;break;
}
;
};

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
const G__67_3 = c_1;
const G__67_4 = (((G__67_3 == null)) ? (null) : (first_heading(G__67_3)));
if ((G__67_4 == null)) {
return null} else {
return text_of(G__67_4)};

})();
const h_5 = ((squint_core.truth_(squint_core.seq(heading_text_2))) ? (heading_text_2) : (id));
if (squint_core._EQ_("", v)) {
return `${"“"}${h_5??''}${"” is unanswered"}`} else {
return `${"“"}${h_5??''}${"” is “"}${answer_label(id, v)??''}${"”"}`};

};
var evaluate = function (p__68) {
const map___1_3 = p__68;
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
if (squint_core.truth_(term_matches_QMARK_(t, v_10))) {
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
if (squint_core.truth_(((squint_core.truth_(open_QMARK__1)) ? (details_QMARK_(container)) : (open_QMARK__1)))) {
container.open = true};
button.setAttribute("aria-expanded", `${open_QMARK__1??''}`);
return button.textContent = ((squint_core.truth_(open_QMARK__1)) ? ("Hide") : ("Show"));

};
var bar_of = function (container) {
return container.querySelector(":scope > .hc-moot-bar, :scope > summary > .hc-moot-bar");

};
var wrap_loose_text_BANG_ = function (container) {
for (let G__1 of squint_core.iterable(squint_core.filter((function (_PERCENT_1) {
if ((3 === _PERCENT_1.nodeType)) {
return squint_core.seq(_PERCENT_1.textContent.trim())} else {
return false};

}), Array.from(container.childNodes)))) {
const n_2 = G__1;
const span_3 = document.createElement("span");
n_2.replaceWith(span_3);
span_3.append(n_2)
}
return null;

};
var show_question_BANG_ = function (p__69, reason) {
const map___1_3 = p__69;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const container_5 = squint_core.get(map___1_4, "container");
const bar_6 = (() => {
const or_7_8 = bar_of(container_5);
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
const reason_el_9 = document.createElement("span");
const button_10 = document.createElement("button");
const bar_11 = document.createElement("div");
const head_12 = head_child(container_5);
wrap_loose_text_BANG_(container_5);
reason_el_9.className = "hc-moot-reason";
button_10.type = "button";
button_10.className = "hc-moot-toggle";
button_10.setAttribute("aria-expanded", "false");
button_10.textContent = "Show";
button_10.addEventListener("click", (function (e) {
e.preventDefault();
return toggle_BANG_(container_5, button_10);

}));
bar_11.className = "hc-moot-bar";
bar_11.append(reason_el_9, button_10);
if (squint_core.truth_(head_12)) {
head_12.classList.add("hc-moot-head")};
if (squint_core.truth_(((squint_core.truth_(head_12)) ? ((() => {
const and_13_14 = details_QMARK_(container_5);
if (squint_core.truth_(and_13_14)) {
return ("SUMMARY" === head_12.tagName)} else {
return and_13_14};

})()) : (head_12)))) {
head_12.append(bar_11)} else {
if (squint_core.truth_(head_12)) {
head_12.after(bar_11)} else {
if ("else") {
container_5.prepend(bar_11)} else {
}}};
container_5.classList.add("hc-moot");
return bar_11;
};

})();
return bar_6.querySelector(".hc-moot-reason").textContent = `${"Moot: "}${reason??''}`;

};
var unshow_question_BANG_ = function (p__70) {
const map___1_3 = p__70;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const container_5 = squint_core.get(map___1_4, "container");
const temp__23314__auto___6 = bar_of(container_5);
if (squint_core.truth_(temp__23314__auto___6)) {
const bar_7 = temp__23314__auto___6;
bar_7.remove();
container_5.classList.remove("hc-moot", "hc-moot-open");
const G__71_8 = head_child(container_5);
const G__71_9 = (((G__71_8 == null)) ? (null) : (G__71_8.classList));
if ((G__71_9 == null)) {
return null} else {
return G__71_9.remove("hc-moot-head")};
};

};
var option_reason_of = function (target) {
const n_1 = target.nextElementSibling;
if (squint_core.truth_(((squint_core.truth_(n_1)) ? (n_1.classList.contains("hc-moot-option-reason")) : (n_1)))) {
return n_1;
};

};
var show_option_BANG_ = function (p__72, reason) {
const map___1_3 = p__72;
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
var unshow_option_BANG_ = function (p__73) {
const map___1_3 = p__73;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const target_5 = squint_core.get(map___1_4, "target");
const temp__23314__auto___6 = option_reason_of(target_5);
if (squint_core.truth_(temp__23314__auto___6)) {
const r_7 = temp__23314__auto___6;
r_7.remove();
return target_5.classList.remove("hc-moot-option");
};

};
var show_BANG_ = function (l, reason) {
if (squint_core.truth_(squint_core.get(l, "container"))) {
return show_question_BANG_(l, reason)} else {
return show_option_BANG_(l, reason)};

};
var unshow_BANG_ = function (l) {
if (squint_core.truth_(squint_core.get(l, "container"))) {
return unshow_question_BANG_(l)} else {
return unshow_option_BANG_(l)};

};
var same_place_QMARK_ = function (a, b) {
if ((squint_core.get(a, "container") === squint_core.get(b, "container"))) {
return (squint_core.get(a, "target") === squint_core.get(b, "target"))} else {
return false};

};
var update_BANG_ = function () {
const map___1_3 = squint_core.deref(links);
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const questions_5 = squint_core.get(map___1_4, "questions");
const options_6 = squint_core.get(map___1_4, "options");
const resolved_7 = squint_core.mapv((function (_PERCENT_1) {
return squint_core.merge(_PERCENT_1, resolve_place(_PERCENT_1));

}), squint_core.concat(questions_5, options_6));
const placed_8 = squint_core.filterv((function (_PERCENT_1) {
return (squint_core.get(_PERCENT_1, "problem") == null);

}), resolved_7);
const now_9 = evaluate(({"questions": squint_core.filterv((function (_PERCENT_1) {
return (squint_core.get(_PERCENT_1, "value") == null);

}), placed_8), "options": squint_core.filterv((function (_PERCENT_1) {
return !(squint_core.get(_PERCENT_1, "value") == null);

}), placed_8)}));
squint_core.reset_BANG_(display_problems, squint_core.reduce((function (acc, l) {
const temp__23255__auto___10 = (() => {
const or_11_12 = squint_core.get(l, "problem");
if (squint_core.truth_(or_11_12)) {
return or_11_12} else {
return squint_core.get(l, "warning")};

})();
if (squint_core.truth_(temp__23255__auto___10)) {
const m_13 = temp__23255__auto___10;
return squint_core.assoc(acc, squint_core.get(l, "key"), m_13);
} else {
return acc};

}), ({}), resolved_7));
for (let G__14 of squint_core.iterable(resolved_7)) {
const l_15 = G__14;
const reason_16 = (((squint_core.get(l_15, "problem") == null)) ? (now_9[squint_core.get(l_15, "key")]) : (null));
const before_17 = shown.get(squint_core.get(l_15, "key"));
if (squint_core.truth_(((squint_core.truth_(before_17)) ? ((((reason_16 == null)) || (squint_core.not(same_place_QMARK_(before_17, l_15))))) : (before_17)))) {
unshow_BANG_(before_17);
shown.delete(squint_core.get(l_15, "key"))};
if (squint_core.truth_(reason_16)) {
show_BANG_(l_15, reason_16);
shown.set(squint_core.get(l_15, "key"), l_15)}
};
return squint_core.reset_BANG_(moot_now, now_9);

};
var answer_markup = "[data-decision], [data-question]";
var answer_markup_in_QMARK_ = function (nodes) {
return squint_core.some((function (n) {
if ((1 === n.nodeType)) {
const or_1_2 = n.matches(answer_markup);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return !(n.querySelector(answer_markup) == null)};
} else {
return false};

}), Array.from(nodes));

};
var on_mutations = function (records) {
if (squint_core.truth_(squint_core.some((function (_PERCENT_1) {
const or_1_2 = answer_markup_in_QMARK_(_PERCENT_1.addedNodes);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return answer_markup_in_QMARK_(_PERCENT_1.removedNodes)};

}), records))) {
return update_BANG_();
};

};
var report_display_problems_BANG_ = function (report_BANG_) {
for (let G__1 of squint_core.iterable(Object.entries(squint_core.deref(display_problems)))) {
const vec___2_5 = G__1;
const __6 = squint_core.nth(vec___2_5, 0, null);
const message_7 = squint_core.nth(vec___2_5, 1, null);
report_BANG_("moot", message_7, "hc-moot")
}
return null;

};
var moot_report = function () {
return squint_core.deref(moot_now);

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
core.report_on_done_BANG_("moot", moot_report);
core.on_answers_shown_BANG_(update_BANG_);
(new MutationObserver(on_mutations)).observe(document.body, ({"childList": true, "subtree": true}));
update_BANG_();
const report_later_11 = (function () {
return setTimeout((function () {
return report_display_problems_BANG_(report_BANG_);

}), settle_ms);

});
if (("complete" === document.readyState)) {
return report_later_11()} else {
return window.addEventListener("load", report_later_11, ({"once": true}))};
};
};

};

export { control_kind, split_at_eq, show_BANG_, display_problems, wrap_loose_text_BANG_, unshow_question_BANG_, moot_report, update_BANG_, cycle_problem, radio_label, written_reason, answer_markup_in_QMARK_, answer_markup, first_heading, link_name, option_target, unshow_option_BANG_, control_QMARK_, plain_object_QMARK_, show_option_BANG_, checkbox_shows, report_display_problems_BANG_, text_of, moot_now, shown, same_place_QMARK_, evaluate, parse_terms, start_BANG_, on_mutations, settle_ms, head_child, links, answer_of, option_reason_of, read_links, bar_of, dependency_order, answer_label, controls, term_matches_QMARK_, show_question_BANG_, toggle_BANG_, resolve_place, cycle_members, unshow_BANG_, choice_control_QMARK_, read_link, details_QMARK_ }
