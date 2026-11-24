import * as squint_core from '../squint-core.js';
var transport = squint_core.atom(null);
var state = squint_core.atom(({"online": null, "queue": [], "flushing": null, "rev": null, "carried": []}));
var answers = squint_core.atom(({}));
var els_for = function (id) {
return document.querySelectorAll(`${"[data-decision=\""}${CSS.escape(id)??''}${"\"]"}`);

};
var mark_BANG_ = function (id, s) {
for (let G__1 of squint_core.iterable(els_for(id))) {
const el_2 = G__1;
el_2.dataset.state = s
}
return null;

};
var set_online_BANG_ = function (online_QMARK_) {
if (!squint_core._EQ_(online_QMARK_, squint_core.get(squint_core.deref(state), "online"))) {
squint_core.swap_BANG_(state, squint_core.assoc, "online", online_QMARK_);
const temp__23314__auto___1 = document.getElementById("decide-offline");
if (squint_core.truth_(temp__23314__auto___1)) {
const b_2 = temp__23314__auto___1;
b_2.hidden = online_QMARK_};
return document.documentElement.classList.toggle("decide-is-offline", squint_core.not(online_QMARK_));
};

};
var tab_id = `${Date.now()??''}-${`${Math.random()??''}`.slice(2, 10)??''}`;
var orphan_after_ms = 10000;
var queue_key_prefix = function () {
return `${"html-channel:queue:"}${location.pathname??''}${"#"}`;

};
var queue_key = function () {
return `${queue_key_prefix()??''}${tab_id??''}`;

};
var mirror_queue_BANG_ = function () {
try{
if (squint_core.truth_(squint_core.seq(squint_core.get(squint_core.deref(state), "queue")))) {
return localStorage.setItem(queue_key(), JSON.stringify(({"beat": Date.now(), "queue": squint_core.get(squint_core.deref(state), "queue")})))} else {
return localStorage.removeItem(queue_key())};
}
catch(__1){
return null;
}
;

};
var orphaned_entries_BANG_ = function () {
try{
const prefix_1 = queue_key_prefix();
const own_2 = queue_key();
const now_3 = Date.now();
return squint_core.vec(squint_core.sort_by("at", squint_core.mapcat((function (k) {
const m_4 = (() => {
try{
return JSON.parse(localStorage.getItem(k));
}
catch(__5){
return null;
}

})();
if (squint_core.truth_(((squint_core.truth_(m_4)) ? (((now_3 - squint_core.get(m_4, "beat")) < orphan_after_ms)) : (m_4)))) {
return []} else {
localStorage.removeItem(k);
const or_6_7 = squint_core.get(m_4, "queue");
if (squint_core.truth_(or_6_7)) {
return or_6_7} else {
return []};
};

}), squint_core.filter((function (_PERCENT_1) {
const and_8_9 = _PERCENT_1.startsWith(prefix_1);
if (squint_core.truth_(and_8_9)) {
return !squint_core._EQ_(_PERCENT_1, own_2)} else {
return and_8_9};

}), Object.keys(localStorage)))));
}
catch(__10){
return [];
}
;

};
var report_error_BANG_ = function (kind, message, source) {
const temp__23255__auto___1 = (() => {
const G__19_2 = window;
const G__19_3 = (((G__19_2 == null)) ? (null) : (G__19_2.htmlChannel));
if ((G__19_3 == null)) {
return null} else {
return G__19_3.reportError};

})();
if (squint_core.truth_(temp__23255__auto___1)) {
const f_4 = temp__23255__auto___1;
return f_4(kind, message, source);
} else {
return console.error("html-channel:", kind, message, source)};

};
var send_queue_BANG_ = async function () {
while(true){
const temp__23314__auto___1 = squint_core.first(squint_core.get(squint_core.deref(state), "queue"));
if (squint_core.truth_(temp__23314__auto___1)) {
const entry_2 = temp__23314__auto___1;
const map___3_5 = (await squint_core.get(squint_core.deref(transport), "send!")(entry_2));
const map___3_6 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___3_5))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___3_5))) ? (map___3_5) : (squint_core.seq_to_map_for_destructuring(map___3_5)))) : (map___3_5));
const outcome_7 = squint_core.get(map___3_6, "outcome");
const reason_8 = squint_core.get(map___3_6, "reason");
const id_9 = squint_core.get(entry_2, "id");
const settled_BANG__10 = (function (s) {
squint_core.swap_BANG_(state, squint_core.update, "queue", (function (_PERCENT_1) {
return squint_core.vec(squint_core.rest(_PERCENT_1));

}));
mirror_queue_BANG_();
if (squint_core.truth_(squint_core.some((function (_PERCENT_1) {
return squint_core._EQ_(squint_core.get(_PERCENT_1, "id"), id_9);

}), squint_core.get(squint_core.deref(state), "queue")))) {
return null} else {
return mark_BANG_(id_9, s);
};

});
const G__20_11 = outcome_7;
switch (G__20_11) {case "saved":
settled_BANG__10("saved");
continue;

break;
case "rejected":
settled_BANG__10("rejected");
report_error_BANG_("js", `${"answer "}${JSON.stringify(id_9)??''}${" rejected by "}${((("share" === squint_core.get(squint_core.deref(transport), "mode"))) ? ("this browser") : ("the server"))??''}${": "}${reason_8??''}`, null);
continue;

break;
default:
set_online_BANG_(false)}};break;
}
;
return squint_core.empty_QMARK_(squint_core.get(squint_core.deref(state), "queue"));

};
var flush_BANG_ = function () {
const or_1_2 = squint_core.get(squint_core.deref(state), "flushing");
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
const p_3 = send_queue_BANG_().finally((function () {
return squint_core.swap_BANG_(state, squint_core.assoc, "flushing", null);

}));
squint_core.swap_BANG_(state, squint_core.assoc, "flushing", p_3);
return p_3;
};

};
var remove_clear_button_BANG_ = function () {
const G__21_1 = document.getElementById("decide-clear-carried");
if ((G__21_1 == null)) {
return null} else {
return G__21_1.remove()};

};
var uncarry_BANG_ = function (id) {
squint_core.swap_BANG_(state, squint_core.update, "carried", (function (ids) {
return squint_core.vec(squint_core.remove((function (_PERCENT_1) {
return squint_core._EQ_(_PERCENT_1, id);

}), ids));

}));
if (squint_core.empty_QMARK_(squint_core.get(squint_core.deref(state), "carried"))) {
return remove_clear_button_BANG_();
};

};
var answer_listeners = squint_core.atom([]);
var on_answers_shown_BANG_ = function (f) {
return squint_core.swap_BANG_(answer_listeners, squint_core.conj, f);

};
var answers_shown_BANG_ = function () {
for (let G__1 of squint_core.iterable(squint_core.deref(answer_listeners))) {
const f_2 = G__1;
f_2()
}
return null;

};
var record_listeners = squint_core.atom([]);
var on_record_BANG_ = function (f) {
return squint_core.swap_BANG_(record_listeners, squint_core.conj, f);

};
var record_BANG_ = function (id, value) {
uncarry_BANG_(id);
const entry_1 = ({"page": squint_core.get(squint_core.deref(transport), "page"), "id": id, "value": value, "rev": squint_core.get(squint_core.deref(state), "rev"), "at": (new Date()).toISOString()});
squint_core.swap_BANG_(state, squint_core.update, "queue", squint_core.conj, entry_1);
if (("deeper" === id)) {
} else {
squint_core.swap_BANG_(answers, squint_core.assoc, id, ({"value": value, "rev": entry_1["rev"], "at": entry_1["at"]}))};
mirror_queue_BANG_();
mark_BANG_(id, "pending");
for (let G__2 of squint_core.iterable(squint_core.deref(record_listeners))) {
const f_3 = G__2;
f_3(id, value)
};
answers_shown_BANG_();
return flush_BANG_();

};
var done_report_reads = squint_core.atom([]);
var report_on_done_BANG_ = function (id, read) {
return squint_core.swap_BANG_(done_report_reads, squint_core.conj, [id, read]);

};
var done_reports = function () {
return squint_core.reduce((function (m, p__22) {
const vec___1_4 = p__22;
const id_5 = squint_core.nth(vec___1_4, 0, null);
const read_6 = squint_core.nth(vec___1_4, 1, null);
const v_7 = read_6();
if ((v_7 == null)) {
return m} else {
return squint_core.assoc(m, id_5, v_7)};

}), ({}), squint_core.deref(done_report_reads));

};
var record_done_reports_BANG_ = function () {
for (let G__1 of squint_core.iterable(Object.entries(done_reports()))) {
const vec___2_5 = G__1;
const id_6 = squint_core.nth(vec___2_5, 0, null);
const v_7 = squint_core.nth(vec___2_5, 1, null);
record_BANG_(id_6, v_7)
}
return null;

};
var el_value = function (el) {
if (("checkbox" === el.type)) {
return el.checked} else {
return el.value};

};
var show_value_BANG_ = function (el, v) {
if (("BUTTON" === el.tagName)) {
return el.classList.toggle("chosen", squint_core._EQ_(v, el.dataset.choice))} else {
if (("checkbox" === el.type)) {
return el.checked = squint_core.boolean$(v)} else {
if (("radio" === el.type)) {
return el.checked = squint_core._EQ_(v, el.value)} else {
if ("else") {
return el.value = (((v == null)) ? ("") : (v))} else {
return null}}}};

};
var clear_carried_BANG_ = function () {
const ids_1 = squint_core.get(squint_core.deref(state), "carried");
for (let G__2 of squint_core.iterable(ids_1)) {
const id_3 = G__2;
for (let G__4 of squint_core.iterable(els_for(id_3))) {
const el_5 = G__4;
show_value_BANG_(el_5, null)
};
record_BANG_(id_3, null)
}
return null;

};
var insert_clear_button_BANG_ = function () {
if (squint_core.truth_(document.getElementById("decide-clear-carried"))) {
return null} else {
const b_1 = document.createElement("button");
b_1.id = "decide-clear-carried";
b_1.type = "button";
b_1.textContent = "Clear carried answers";
b_1.addEventListener("click", (function (_) {
return clear_carried_BANG_();

}));
return document.body.prepend(b_1);
};

};
var show_deeper_BANG_ = function (targets) {
for (let G__1 of squint_core.iterable(document.querySelectorAll("button[data-deeper]"))) {
const b_2 = G__1;
b_2.classList.toggle("chosen", ((squint_core.truth_(targets)) ? (targets) : ([])).includes(b_2.dataset.deeper))
}
return null;

};
var report_only_ids = (new Set (["sources", "hunks", "moot"]));
var show_entry_BANG_ = function (els, entry, rev) {
const v_1 = squint_core.get(entry, "value");
const carried_QMARK__2 = (((squint_core.get(entry, "rev") < rev)) && (((!(v_1 == null)) && ((els.length > 0)))));
for (let G__3 of squint_core.iterable(els)) {
const el_4 = G__3;
show_value_BANG_(el_4, v_1);
el_4.dataset.state = ((carried_QMARK__2) ? ("carried") : ("saved"))
};
return carried_QMARK__2;

};
var restore_BANG_ = function (decisions, rev) {
squint_core.swap_BANG_(state, squint_core.assoc, "loaded", ({"decisions": decisions, "rev": rev}));
for (let G__1 of squint_core.iterable(Object.entries(decisions))) {
const vec___2_5 = G__1;
const id_6 = squint_core.nth(vec___2_5, 0, null);
const entry_7 = squint_core.nth(vec___2_5, 1, null);
squint_core.swap_BANG_(answers, squint_core.assoc, id_6, squint_core.select_keys(entry_7, ["value", "rev", "at"]))
};
const carried_8 = squint_core.atom([]);
for (let G__9 of squint_core.iterable(Object.entries(decisions))) {
const vec___10_13 = G__9;
const id_14 = squint_core.nth(vec___10_13, 0, null);
const entry_15 = squint_core.nth(vec___10_13, 1, null);
if (!squint_core.contains_QMARK_(report_only_ids, id_14)) {
if (squint_core.truth_(show_entry_BANG_(els_for(id_14), entry_15, rev))) {
squint_core.swap_BANG_(carried_8, squint_core.conj, id_14)}}
};
squint_core.swap_BANG_(state, squint_core.assoc, "carried", squint_core.deref(carried_8));
if (squint_core.truth_(squint_core.seq(squint_core.deref(carried_8)))) {
insert_clear_button_BANG_()} else {
remove_clear_button_BANG_()};
return answers_shown_BANG_();

};
var restore_within_BANG_ = function (root) {
const temp__23314__auto___1 = squint_core.get(squint_core.deref(state), "loaded");
if (squint_core.truth_(temp__23314__auto___1)) {
const map___2_4 = temp__23314__auto___1;
const map___2_5 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___2_4))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___2_4))) ? (map___2_4) : (squint_core.seq_to_map_for_destructuring(map___2_4)))) : (map___2_4));
const decisions_6 = squint_core.get(map___2_5, "decisions");
const rev_7 = squint_core.get(map___2_5, "rev");
const ids_8 = squint_core.distinct(squint_core.map((function (_PERCENT_1) {
return _PERCENT_1.dataset.decision;

}), Array.from(root.querySelectorAll("[data-decision]"))));
for (let G__9 of squint_core.iterable(ids_8)) {
const id_10 = G__9;
const entry_11 = decisions_6[id_10];
if (squint_core.truth_(((squint_core.truth_(entry_11)) ? (!squint_core.contains_QMARK_(report_only_ids, id_10)) : (entry_11)))) {
const els_12 = root.querySelectorAll(`${"[data-decision=\""}${CSS.escape(id_10)??''}${"\"]"}`);
if (squint_core.truth_((() => {
const and_13_14 = show_entry_BANG_(els_12, entry_11, rev_7);
if (squint_core.truth_(and_13_14)) {
return squint_core.not(squint_core.get(squint_core.deref(state), "carried").includes(id_10))} else {
return and_13_14};

})())) {
squint_core.swap_BANG_(state, squint_core.update, "carried", squint_core.conj, id_10);
insert_clear_button_BANG_()}}
};
return answers_shown_BANG_();
};

};
var clear_BANG_ = function (id) {
for (let G__1 of squint_core.iterable(els_for(id))) {
const el_2 = G__1;
show_value_BANG_(el_2, null)
};
return record_BANG_(id, null);

};
var on_click = function (e) {
const t_1 = e.target;
const temp__23255__auto___2 = t_1.closest("button[data-decision][data-choice]");
if (squint_core.truth_(temp__23255__auto___2)) {
const btn_3 = temp__23255__auto___2;
const id_4 = btn_3.dataset.decision;
if (squint_core.truth_((() => {
const and_5_6 = btn_3.classList.contains("chosen");
if (squint_core.truth_(and_5_6)) {
return !("status" === id_4)} else {
return and_5_6};

})())) {
return clear_BANG_(id_4)} else {
for (let G__7 of squint_core.iterable(els_for(id_4))) {
const b_8 = G__7;
b_8.classList.remove("chosen")
};
btn_3.classList.add("chosen");
if (((("status" === id_4)) && (("done" === btn_3.dataset.choice)))) {
record_done_reports_BANG_()};
return record_BANG_(id_4, btn_3.dataset.choice);
};
} else {
const temp__23255__auto___9 = t_1.closest("button[data-clear]");
if (squint_core.truth_(temp__23255__auto___9)) {
const btn_10 = temp__23255__auto___9;
return clear_BANG_(btn_10.dataset.clear);
} else {
const temp__23314__auto___11 = t_1.closest("button[data-deeper]");
if (squint_core.truth_(temp__23314__auto___11)) {
const btn_12 = temp__23314__auto___11;
btn_12.classList.add("chosen");
return record_BANG_("deeper", btn_12.dataset.deeper);
};
};
};

};
var on_change = function (e) {
const el_1 = e.target;
if (squint_core.truth_((() => {
const and_2_3 = el_1.dataset.decision;
if (squint_core.truth_(and_2_3)) {
return !("BUTTON" === el_1.tagName)} else {
return and_2_3};

})())) {
return record_BANG_(el_1.dataset.decision, el_value(el_1));
};

};
var claim_orphaned_queues_BANG_ = function () {
const claimed_1 = squint_core.filter((function (entry) {
const shown_2 = squint_core.get(squint_core.get(squint_core.deref(answers), squint_core.get(entry, "id")), "at");
return squint_core.not(((squint_core.truth_(shown_2)) ? ((shown_2 > squint_core.get(entry, "at"))) : (shown_2)));

}), orphaned_entries_BANG_());
if (squint_core.truth_(squint_core.seq(claimed_1))) {
for (let G__3 of squint_core.iterable(claimed_1)) {
const entry_4 = G__3;
const id_5 = squint_core.get(entry_4, "id");
for (let G__6 of squint_core.iterable(els_for(id_5))) {
const el_7 = G__6;
show_value_BANG_(el_7, squint_core.get(entry_4, "value"))
};
squint_core.swap_BANG_(state, squint_core.update, "queue", squint_core.conj, entry_4);
if (("deeper" === id_5)) {
} else {
squint_core.swap_BANG_(answers, squint_core.assoc, id_5, squint_core.select_keys(entry_4, ["value", "rev", "at"]))};
mark_BANG_(id_5, "pending")
};
mirror_queue_BANG_();
return answers_shown_BANG_();
};

};
var claim_lock_hold_ms = 250;
var claim_orphaned_queues_once_BANG_ = async function () {
const temp__23255__auto___1 = (await (async () => {
const G__23_2 = navigator;
if ((G__23_2 == null)) {
return null} else {
return G__23_2.locks};

})());
if (squint_core.truth_(temp__23255__auto___1)) {
const locks_3 = temp__23255__auto___1;
return (await locks_3.request(`${"html-channel:claim:"}${location.pathname??''}`, ({"ifAvailable": true}), (function (lock) {
if (squint_core.truth_(lock)) {
claim_orphaned_queues_BANG_();
return (new Promise((function (resolve) {
return setTimeout(resolve, claim_lock_hold_ms);

})));
};

})));
} else {
return claim_orphaned_queues_BANG_()};

};
var load_BANG_ = async function () {
const temp__23314__auto___1 = (await squint_core.get(squint_core.deref(transport), "load")());
if (squint_core.truth_(temp__23314__auto___1)) {
const eff_2 = temp__23314__auto___1;
squint_core.swap_BANG_(state, squint_core.assoc, "rev", squint_core.get(eff_2, "rev"));
restore_BANG_(squint_core.get(eff_2, "decisions"), squint_core.get(eff_2, "rev"));
return show_deeper_BANG_(squint_core.get(eff_2, "deeper"));
};

};
var check_BANG_ = async function () {
if (!(squint_core.get(squint_core.deref(state), "rev") == null)) {
(await claim_orphaned_queues_once_BANG_())};
if (squint_core.truth_(squint_core.seq(squint_core.get(squint_core.deref(state), "queue")))) {
mirror_queue_BANG_()};
const rev_1 = (await squint_core.get(squint_core.deref(transport), "rev")());
set_online_BANG_(!(rev_1 == null));
if (!(rev_1 == null)) {
const drained_2 = ((squint_core.truth_(squint_core.seq(squint_core.get(squint_core.deref(state), "queue")))) ? ((await flush_BANG_())) : (true));
const loaded_3 = squint_core.get(squint_core.deref(state), "rev");
if ((loaded_3 == null)) {
return (await load_BANG_())} else {
if (squint_core.truth_(((squint_core.truth_(drained_2)) ? ((rev_1 > loaded_3)) : (drained_2)))) {
return location.reload()} else {
return null}};
};

};
var record_from_page_BANG_ = function (id, value) {
for (let G__1 of squint_core.iterable(els_for(id))) {
const el_2 = G__1;
show_value_BANG_(el_2, value)
};
return record_BANG_(id, value);

};
var save_early_answers_BANG_ = function () {
const temp__23314__auto___1 = window.htmlChannel.early;
if (squint_core.truth_(temp__23314__auto___1)) {
const early_2 = temp__23314__auto___1;
window.htmlChannel.early = null;
for (let G__3 of squint_core.iterable(early_2)) {
const a_4 = G__3;
const temp__23255__auto___5 = a_4.el;
if (squint_core.truth_(temp__23255__auto___5)) {
const el_6 = temp__23255__auto___5;
record_BANG_(el_6.dataset.decision, el_value(el_6))} else {
record_from_page_BANG_(a_4.id, a_4.value)}
}
return null;
};

};
var start_BANG_ = function (t) {
squint_core.reset_BANG_(transport, t);
document.documentElement.classList.toggle("decide-share", ("share" === squint_core.get(t, "mode")));
window.htmlChannel = (() => {
const or_1_2 = window.htmlChannel;
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return ({})};

})();
window.htmlChannel.record = record_from_page_BANG_;
save_early_answers_BANG_();
document.addEventListener("click", on_click);
document.addEventListener("change", on_change);
check_BANG_();
return setInterval(check_BANG_, 3000);

};

export { restore_BANG_, orphaned_entries_BANG_, restore_within_BANG_, transport, check_BANG_, done_reports, tab_id, show_value_BANG_, els_for, claim_lock_hold_ms, answers_shown_BANG_, uncarry_BANG_, mark_BANG_, clear_BANG_, save_early_answers_BANG_, report_on_done_BANG_, on_click, record_done_reports_BANG_, el_value, insert_clear_button_BANG_, record_listeners, on_record_BANG_, report_error_BANG_, answers, orphan_after_ms, start_BANG_, queue_key_prefix, report_only_ids, done_report_reads, set_online_BANG_, claim_orphaned_queues_BANG_, record_BANG_, state, on_change, remove_clear_button_BANG_, queue_key, flush_BANG_, clear_carried_BANG_, mirror_queue_BANG_, show_entry_BANG_, answer_listeners, on_answers_shown_BANG_, record_from_page_BANG_, load_BANG_, claim_orphaned_queues_once_BANG_, show_deeper_BANG_, send_queue_BANG_ }
