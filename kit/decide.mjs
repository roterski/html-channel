import * as squint_core from './squint-core.js';
var server = (() => {
const u_1 = (new URL(import.meta.url));
return u_1.origin;

})();
var page_id = (() => {
const or_1_2 = document.body.dataset.page;
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return location.pathname.split("/").pop().replace(/\.html?$/, "")};

})();
var state = squint_core.atom(({"online": null, "queue": [], "flushing": false}));
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
var post_BANG_ = async function (entry) {
try{
const r_1 = (await fetch(`${server??''}${"/_decisions"}`, ({"method": "POST", "body": JSON.stringify(entry)})));
return r_1.ok;
}
catch(__2){
return false;
}
;

};
var flush_BANG_ = async function () {
if (squint_core.truth_(squint_core.get(squint_core.deref(state), "flushing"))) {
return null} else {
squint_core.swap_BANG_(state, squint_core.assoc, "flushing", true);
while(true){
const temp__23314__auto___1 = squint_core.first(squint_core.get(squint_core.deref(state), "queue"));
if (squint_core.truth_(temp__23314__auto___1)) {
const entry_2 = temp__23314__auto___1;
if (squint_core.truth_((await post_BANG_(entry_2)))) {
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
return squint_core.swap_BANG_(state, squint_core.assoc, "flushing", false);
};

};
var record_BANG_ = function (id, value) {
squint_core.swap_BANG_(state, squint_core.update, "queue", squint_core.conj, ({"page": page_id, "id": id, "value": value, "at": (new Date()).toISOString()}));
mark_BANG_(id, "pending");
return flush_BANG_();

};
var el_value = function (el) {
if (("checkbox" === el.type)) {
return el.checked} else {
return el.value};

};
var restore_BANG_ = function (decisions) {
for (let G__1 of squint_core.iterable(Object.entries(decisions))) {
const vec___2_5 = G__1;
const id_6 = squint_core.nth(vec___2_5, 0, null);
const entry_7 = squint_core.nth(vec___2_5, 1, null);
const v_8 = squint_core.get(entry_7, "value");
for (let G__9 of squint_core.iterable(els_for(id_6))) {
const el_10 = G__9;
el_10.dataset.state = "saved";
if (("BUTTON" === el_10.tagName)) {
el_10.classList.toggle("chosen", squint_core._EQ_(v_8, el_10.dataset.choice))} else {
if (("checkbox" === el_10.type)) {
el_10.checked = squint_core.boolean$(v_8)} else {
if (("radio" === el_10.type)) {
el_10.checked = squint_core._EQ_(v_8, el_10.value)} else {
if ("else") {
el_10.value = v_8} else {
}}}}
}
}
return null;

};
document.addEventListener("click", (function (e) {
const temp__23314__auto___1 = e.target.closest("button[data-decision][data-choice]");
if (squint_core.truth_(temp__23314__auto___1)) {
const btn_2 = temp__23314__auto___1;
const id_3 = btn_2.dataset.decision;
for (let G__4 of squint_core.iterable(els_for(id_3))) {
const b_5 = G__4;
b_5.classList.remove("chosen")
};
btn_2.classList.add("chosen");
return record_BANG_(id_3, btn_2.dataset.choice);
};

}));
document.addEventListener("change", (function (e) {
const el_1 = e.target;
if (squint_core.truth_((() => {
const and_2_3 = el_1.dataset.decision;
if (squint_core.truth_(and_2_3)) {
return !("BUTTON" === el_1.tagName)} else {
return and_2_3};

})())) {
return record_BANG_(el_1.dataset.decision, el_value(el_1));
};

}));
var check_BANG_ = async function () {
const ok_1 = (await (async () => {
try{
return (await fetch(`${server??''}${"/_health"}`, ({"cache": "no-store"}))).ok;
}
catch(__2){
return false;
}

})());
set_online_BANG_(ok_1);
if (squint_core.truth_(((squint_core.truth_(ok_1)) ? (squint_core.seq(squint_core.get(squint_core.deref(state), "queue"))) : (ok_1)))) {
flush_BANG_()};
return ok_1;

};
var init_BANG_ = async function () {
if (squint_core.truth_((await check_BANG_()))) {
try{
const r_1 = (await fetch(`${server??''}${"/_decisions?page="}${encodeURIComponent(page_id)??''}`));
restore_BANG_((await r_1.json()))}
catch(e_2){
console.warn("decide: could not restore", e_2)}
};
return setInterval(check_BANG_, 3000);

};
init_BANG_();

export { restore_BANG_, check_BANG_, server, els_for, mark_BANG_, page_id, el_value, post_BANG_, set_online_BANG_, init_BANG_, record_BANG_, state, flush_BANG_ }
