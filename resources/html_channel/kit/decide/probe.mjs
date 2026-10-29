import * as squint_core from '../squint-core.js';
var frames = function (n) {
return (new Promise((function (resolve) {
return setTimeout(resolve, (50 * n));

})));

};
var fingerprint = function (el) {
return `${el.outerHTML??''}${squint_core.apply(squint_core.str, squint_core.lazy((function* () {
for (let G__1 of squint_core.iterable(Array.from(el.querySelectorAll("canvas")))) {
const c_2 = G__1;
yield (yield* (function* () {
try{
return c_2.toDataURL();
}
catch(__3){
return "tainted";
}

})());
}
return null;

})))??''}`;

};
var skip = "[data-decision], [data-clear], [data-deeper], #hc-pause";
var controls_of = function (el, id) {
const q_1 = CSS.escape(id);
const inside_2 = el.querySelectorAll("input:not([type=hidden]), select, textarea, button, a[data-set], [data-scrub]");
const outside_3 = document.querySelectorAll(`${"[data-target=\""}${q_1??''}${"\"], a[data-set=\""}${q_1??''}${"\"], [data-timeline=\""}${q_1??''}${"\"] input[type=range]"}`);
return squint_core.vec(squint_core.distinct(squint_core.remove((function (_PERCENT_1) {
return _PERCENT_1.matches(skip);

}), squint_core.concat(Array.from(inside_2), Array.from(outside_3)))));

};
var fire_BANG_ = function (el, type) {
return el.dispatchEvent((new Event(type, ({"bubbles": true}))));

};
var key_BANG_ = function (el, k) {
return el.dispatchEvent((new KeyboardEvent("keydown", ({"key": k, "bubbles": true}))));

};
var drive_BANG_ = function (c) {
const tag_1 = c.tagName;
const type_2 = c.type;
if (squint_core.truth_(c.hasAttribute("data-scrub"))) {
return key_BANG_(c, ((squint_core._EQ_(c.getAttribute("aria-valuenow"), c.getAttribute("aria-valuemax"))) ? ("Home") : ("End")))} else {
if ((((tag_1 === "INPUT")) && ((((type_2 === "range")) || ((type_2 === "number")))))) {
const hi_3 = (() => {
const or_4_5 = squint_core.not_empty(c.max);
if (squint_core.truth_(or_4_5)) {
return or_4_5} else {
return "100"};

})();
const lo_6 = (() => {
const or_7_8 = squint_core.not_empty(c.min);
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
return "0"};

})();
c.value = ((squint_core._EQ_(c.value, hi_3)) ? (lo_6) : (hi_3));
fire_BANG_(c, "input");
return fire_BANG_(c, "change");
} else {
if ((((tag_1 === "INPUT")) && ((((type_2 === "checkbox")) || ((type_2 === "radio")))))) {
return c.click()} else {
if ((tag_1 === "SELECT")) {
c.selectedIndex = squint_core.mod((c.selectedIndex + 1), squint_core.max(1, c.options.length));
fire_BANG_(c, "input");
return fire_BANG_(c, "change");
} else {
if ((((tag_1 === "INPUT")) || ((tag_1 === "TEXTAREA")))) {
c.value = `${c.value??''}7`;
fire_BANG_(c, "input");
return fire_BANG_(c, "change");
} else {
if ("else") {
return c.click()} else {
return null}}}}}};

};
var drag_BANG_ = function (el) {
const r_1 = el.getBoundingClientRect();
const x_2 = (r_1.left + (r_1.width / 2));
const y_3 = (r_1.top + (r_1.height / 2));
const t_4 = (() => {
const or_5_6 = document.elementFromPoint(x_2, y_3);
if (squint_core.truth_(or_5_6)) {
return or_5_6} else {
return el};

})();
const at_7 = (function (type, dx) {
const init_8 = ({"bubbles": true, "clientX": (x_2 + dx), "clientY": y_3, "pointerId": 1, "buttons": 1, "isPrimary": true});
return t_4.dispatchEvent(((squint_core.truth_(type.startsWith("pointer"))) ? ((new PointerEvent(type, init_8))) : ((new MouseEvent(type, init_8)))));

});
at_7("pointerdown", 0);
at_7("mousedown", 0);
at_7("pointermove", 30);
at_7("mousemove", 30);
at_7("pointerup", 30);
at_7("mouseup", 30);
return at_7("click", 30);

};
var probe_one = async function (el) {
const id_1 = el.dataset.instrument;
const cs_2 = controls_of(el, id_1);
el.scrollIntoView(({"block": "center"}));
(await frames(2));
const before_3 = fingerprint(el);
if (squint_core.truth_(squint_core.seq(cs_2))) {
const G__7_11 = cs_2;
const vec___8_12 = G__7_11;
const seq___9_13 = squint_core.seq(vec___8_12);
const first___10_14 = squint_core.first(seq___9_13);
const seq___9_15 = squint_core.next(seq___9_13);
const c_16 = first___10_14;
const more_17 = seq___9_15;
let G__7_18 = G__7_11;
while(true){
const vec___19_22 = G__7_18;
const seq___20_23 = squint_core.seq(vec___19_22);
const first___21_24 = squint_core.first(seq___20_23);
const seq___20_25 = squint_core.next(seq___20_23);
const c_26 = first___21_24;
const more_27 = seq___20_25;
if (squint_core.not(c_26)) {
return ["inert", `${"instrument \""}${id_1??''}${"\" didn't change when its "}${squint_core.count(cs_2)??''}${" control(s) were driven"}${((squint_core.truth_(el.querySelector("canvas"))) ? (" (a WebGL canvas needs preserveDrawingBuffer: true to be read)") : (null))??''}`]} else {
drive_BANG_(c_26);
(await frames(2));
if (squint_core._EQ_(before_3, fingerprint(el))) {
let G__28 = more_27;
G__7_18 = G__28;
continue;
};
};
;break;
}
;
} else {
drag_BANG_(el);
(await frames(2));
if (squint_core._EQ_(before_3, fingerprint(el))) {
return ["unprobed", `${"instrument \""}${id_1??''}${"\" has no controls, and a synthetic drag across it changed nothing; "}${"check by hand that it responds"}`];
};
};

};
var probe_BANG_ = async function (report_BANG_) {
const hc_1 = window.htmlChannel;
const was_paused_2 = hc_1.paused;
if (squint_core.truth_(hc_1.setPaused)) {
hc_1.setPaused(true)};
for (let G__3 of squint_core.iterable(Array.from(document.querySelectorAll("[data-instrument]")))) {
const el_4 = G__3;
const temp__23314__auto___5 = (await probe_one(el_4));
if (squint_core.truth_(temp__23314__auto___5)) {
const vec___6_9 = temp__23314__auto___5;
const kind_10 = squint_core.nth(vec___6_9, 0, null);
const message_11 = squint_core.nth(vec___6_9, 1, null);
report_BANG_(kind_10, message_11, el_4.dataset.instrument)}
};
scrollTo(0, 0);
if (squint_core.truth_(hc_1.setPaused)) {
return hc_1.setPaused(was_paused_2);
};

};
var start_BANG_ = function (report_BANG_) {
const run_1 = (function () {
return setTimeout((function () {
return probe_BANG_(report_BANG_);

}), 500);

});
if (("complete" === document.readyState)) {
return run_1()} else {
return window.addEventListener("load", run_1)};

};

export { skip, key_BANG_, fingerprint, frames, drag_BANG_, fire_BANG_, start_BANG_, drive_BANG_, probe_one, probe_BANG_, controls_of }
