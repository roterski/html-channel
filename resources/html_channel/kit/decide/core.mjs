import * as squint_core from '../squint-core.js';
var transport = squint_core.atom(null);
var state = squint_core.atom(({"online": null, "queue": [], "flushing": false, "rev": null, "carried": []}));
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
var flush_BANG_ = async function () {
if (squint_core.truth_(squint_core.get(squint_core.deref(state), "flushing"))) {
return false} else {
squint_core.swap_BANG_(state, squint_core.assoc, "flushing", true);
while(true){
const temp__23314__auto___1 = squint_core.first(squint_core.get(squint_core.deref(state), "queue"));
if (squint_core.truth_(temp__23314__auto___1)) {
const entry_2 = temp__23314__auto___1;
if (squint_core.truth_((await squint_core.get(squint_core.deref(transport), "send!")(entry_2)))) {
squint_core.swap_BANG_(state, squint_core.update, "queue", (function (_PERCENT_1) {
return squint_core.vec(squint_core.rest(_PERCENT_1));

}));
if (squint_core.truth_(squint_core.some((function (_PERCENT_1) {
return squint_core._EQ_(squint_core.get(_PERCENT_1, "id"), squint_core.get(entry_2, "id"));

}), squint_core.get(squint_core.deref(state), "queue")))) {
} else {
mark_BANG_(squint_core.get(entry_2, "id"), "saved")};
continue;
} else {
set_online_BANG_(false)}};break;
}
;
squint_core.swap_BANG_(state, squint_core.assoc, "flushing", false);
return squint_core.empty_QMARK_(squint_core.get(squint_core.deref(state), "queue"));
};

};
var remove_clear_button_BANG_ = function () {
const G__6_1 = document.getElementById("decide-clear-carried");
if ((G__6_1 == null)) {
return null} else {
return G__6_1.remove()};

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
var record_BANG_ = function (id, value) {
uncarry_BANG_(id);
squint_core.swap_BANG_(state, squint_core.update, "queue", squint_core.conj, ({"page": squint_core.get(squint_core.deref(transport), "page"), "id": id, "value": value, "rev": squint_core.get(squint_core.deref(state), "rev"), "at": (new Date()).toISOString()}));
mark_BANG_(id, "pending");
return flush_BANG_();

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
var restore_BANG_ = function (decisions, rev) {
const carried_1 = squint_core.atom([]);
for (let G__2 of squint_core.iterable(Object.entries(decisions))) {
const vec___3_6 = G__2;
const id_7 = squint_core.nth(vec___3_6, 0, null);
const entry_8 = squint_core.nth(vec___3_6, 1, null);
const els_9 = els_for(id_7);
const v_10 = squint_core.get(entry_8, "value");
const carried_QMARK__11 = (((squint_core.get(entry_8, "rev") < rev)) && (((!(v_10 == null)) && ((els_9.length > 0)))));
if (carried_QMARK__11) {
squint_core.swap_BANG_(carried_1, squint_core.conj, id_7)};
for (let G__12 of squint_core.iterable(els_9)) {
const el_13 = G__12;
show_value_BANG_(el_13, v_10);
el_13.dataset.state = ((carried_QMARK__11) ? ("carried") : ("saved"))
}
};
squint_core.swap_BANG_(state, squint_core.assoc, "carried", squint_core.deref(carried_1));
if (squint_core.truth_(squint_core.seq(squint_core.deref(carried_1)))) {
return insert_clear_button_BANG_()} else {
return remove_clear_button_BANG_()};

};
var on_click = function (e) {
const temp__23255__auto___1 = e.target.closest("button[data-decision][data-choice]");
if (squint_core.truth_(temp__23255__auto___1)) {
const btn_2 = temp__23255__auto___1;
const id_3 = btn_2.dataset.decision;
for (let G__4 of squint_core.iterable(els_for(id_3))) {
const b_5 = G__4;
b_5.classList.remove("chosen")
};
btn_2.classList.add("chosen");
return record_BANG_(id_3, btn_2.dataset.choice);
} else {
const temp__23314__auto___6 = e.target.closest("button[data-deeper]");
if (squint_core.truth_(temp__23314__auto___6)) {
const btn_7 = temp__23314__auto___6;
btn_7.classList.add("chosen");
return record_BANG_("deeper", btn_7.dataset.deeper);
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
var report_error_BANG_ = function (kind, message, source) {
const temp__23255__auto___1 = (() => {
const G__7_2 = window;
const G__7_3 = (((G__7_2 == null)) ? (null) : (G__7_2.htmlChannel));
if ((G__7_3 == null)) {
return null} else {
return G__7_3.reportError};

})();
if (squint_core.truth_(temp__23255__auto___1)) {
const f_4 = temp__23255__auto___1;
return f_4(kind, message, source);
} else {
return console.error("html-channel:", kind, message, source)};

};
var record_from_page_BANG_ = function (id, value) {
for (let G__1 of squint_core.iterable(els_for(id))) {
const el_2 = G__1;
show_value_BANG_(el_2, value)
};
return record_BANG_(id, value);

};
var start_BANG_ = function (t) {
squint_core.reset_BANG_(transport, t);
window.htmlChannel = (() => {
const or_1_2 = window.htmlChannel;
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return ({})};

})();
window.htmlChannel.record = record_from_page_BANG_;
document.addEventListener("click", on_click);
document.addEventListener("change", on_change);
check_BANG_();
return setInterval(check_BANG_, 3000);

};

export { restore_BANG_, transport, check_BANG_, show_value_BANG_, els_for, uncarry_BANG_, mark_BANG_, on_click, el_value, insert_clear_button_BANG_, report_error_BANG_, start_BANG_, set_online_BANG_, record_BANG_, state, on_change, remove_clear_button_BANG_, flush_BANG_, clear_carried_BANG_, record_from_page_BANG_, load_BANG_, show_deeper_BANG_ }
