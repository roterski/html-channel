import * as squint_core from '../squint-core.js';
var explorable_QMARK_ = function () {
return !(document.querySelector("meta[name=hc-kind][content=explorable]") == null);

};
var emit_BANG_ = function (target, type, detail) {
return target.dispatchEvent((new CustomEvent(type, ({"detail": detail}))));

};
var target_of = function (report_BANG_, el, attr, id) {
const or_3_4 = ((squint_core.truth_(id)) ? ((() => {
const or_1_2 = document.getElementById(id);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return document.querySelector(`${"[data-instrument=\""}${CSS.escape(id)??''}${"\"]"}`)};

})()) : (id));
if (squint_core.truth_(or_3_4)) {
return or_3_4} else {
report_BANG_("explorable", `${"<"}${el.tagName.toLowerCase()??''}${" "}${attr??''}${"=\""}${id??''}${"\">"}${" points at no element with that id or data-instrument"}`, attr);
return null;
};

};
var num_attr = function (el, attr, fallback) {
const v_1 = parseFloat(el.getAttribute(attr));
if (squint_core.truth_(isNaN(v_1))) {
return fallback} else {
return v_1};

};
var decimals = function (step) {
const s_1 = `${step??''}`;
const i_2 = s_1.indexOf(".");
if ((i_2 < 0)) {
return 0} else {
return (squint_core.count(s_1) - i_2 - 1)};

};
var pause_listeners = squint_core.atom([]);
var set_paused_BANG_ = function (paused_QMARK_) {
window.htmlChannel.paused = paused_QMARK_;
const temp__23314__auto___1 = document.getElementById("hc-pause");
if (squint_core.truth_(temp__23314__auto___1)) {
const b_2 = temp__23314__auto___1;
b_2.setAttribute("aria-pressed", `${paused_QMARK_??''}`);
b_2.textContent = ((squint_core.truth_(paused_QMARK_)) ? ("▶ Play animations") : ("⏸ Pause animations"))};
for (let G__3 of squint_core.iterable(squint_core.deref(pause_listeners))) {
const f_4 = G__3;
f_4(paused_QMARK_)
};
return emit_BANG_(document, "hc-pause", ({"paused": paused_QMARK_}));

};
var add_pause_button_BANG_ = function () {
const b_1 = document.createElement("button");
b_1.id = "hc-pause";
b_1.type = "button";
b_1.addEventListener("click", (function (_) {
return set_paused_BANG_(squint_core.not(window.htmlChannel.paused));

}));
return document.body.append(b_1);

};
var start_scrub_BANG_ = function (report_BANG_, el) {
const temp__23314__auto___1 = target_of(report_BANG_, el, "data-target", el.dataset.target);
if (squint_core.truth_(temp__23314__auto___1)) {
const target_2 = temp__23314__auto___1;
const name_3 = el.dataset.scrub;
const lo_4 = num_attr(el, "data-min", 0);
const hi_5 = num_attr(el, "data-max", 100);
const step_6 = num_attr(el, "data-step", 1);
const value_7 = squint_core.atom(num_attr(el, "data-value", parseFloat(el.textContent)));
const show_BANG__8 = (function () {
el.textContent = squint_core.deref(value_7).toFixed(decimals(step_6));
el.setAttribute("aria-valuenow", `${squint_core.deref(value_7)??''}`);
return emit_BANG_(target_2, "hc-param", ({"name": name_3, "value": squint_core.deref(value_7)}));

});
const set_value_BANG__9 = (function (v) {
const snapped_10 = Number((lo_4 + (step_6 * Math.round(((v - lo_4) / step_6)))).toFixed(decimals(step_6)));
const v_11 = Math.min(hi_5, Math.max(lo_4, snapped_10));
if (!squint_core._EQ_(v_11, squint_core.deref(value_7))) {
squint_core.reset_BANG_(value_7, v_11);
return show_BANG__8();
};

});
if (squint_core.truth_(isNaN(squint_core.deref(value_7)))) {
squint_core.reset_BANG_(value_7, lo_4)};
target_2.addEventListener("hc-state", (function (e) {
const v_12 = e.detail[name_3];
if (squint_core.number_QMARK_(v_12)) {
return set_value_BANG__9(v_12);
};

}));
el.tabIndex = 0;
for (let G__13 of squint_core.iterable([["role", "slider"], ["aria-valuemin", `${lo_4??''}`], ["aria-valuemax", `${hi_5??''}`]])) {
const vec___14_17 = G__13;
const k_18 = squint_core.nth(vec___14_17, 0, null);
const v_19 = squint_core.nth(vec___14_17, 1, null);
el.setAttribute(k_18, v_19)
};
if (squint_core.truth_(el.hasAttribute("aria-label"))) {
} else {
el.setAttribute("aria-label", name_3)};
el.addEventListener("keydown", (function (e) {
const temp__23314__auto___20 = (() => {
const k_21 = e.key;
if ((((k_21 === "ArrowRight")) || ((k_21 === "ArrowUp")))) {
return (squint_core.deref(value_7) + step_6)} else {
if ((((k_21 === "ArrowLeft")) || ((k_21 === "ArrowDown")))) {
return (squint_core.deref(value_7) - step_6)} else {
if ((k_21 === "Home")) {
return lo_4} else {
if ((k_21 === "End")) {
return hi_5} else {
return null}}}};

})();
if (squint_core.truth_(temp__23314__auto___20)) {
const v_22 = temp__23314__auto___20;
e.preventDefault();
return set_value_BANG__9(v_22);
};

}));
el.addEventListener("pointerdown", (function (e) {
e.preventDefault();
el.setPointerCapture(e.pointerId);
const x0_23 = e.clientX;
const v0_24 = squint_core.deref(value_7);
const move_25 = (function (m) {
return set_value_BANG__9((v0_24 + (step_6 * Math.round(((m.clientX - x0_23) / 4)))));

});
const up_26 = (function up (_) {
el.removeEventListener("pointermove", move_25);
el.removeEventListener("pointerup", up);
return el.removeEventListener("pointercancel", up);

});
el.addEventListener("pointermove", move_25);
el.addEventListener("pointerup", up_26);
return el.addEventListener("pointercancel", up_26);

}));
return show_BANG__8();
};

};
var start_state_link_BANG_ = function (report_BANG_, el) {
const temp__23314__auto___1 = target_of(report_BANG_, el, "data-set", el.dataset.set);
if (squint_core.truth_(temp__23314__auto___1)) {
const target_2 = temp__23314__auto___1;
const state_3 = (() => {
try{
return JSON.parse((() => {
const or_4_5 = el.dataset.state;
if (squint_core.truth_(or_4_5)) {
return or_4_5} else {
return "{}"};

})());
}
catch(e_6){
report_BANG_("explorable", `${"<a data-set=\""}${el.dataset.set??''}${"\">: data-state isn't JSON: "}${e_6.message??''}`, "data-state");
return null;
}

})();
if (squint_core.truth_(state_3)) {
if (squint_core.truth_(el.hasAttribute("href"))) {
} else {
el.setAttribute("href", "#")};
return el.addEventListener("click", (function (e) {
e.preventDefault();
return emit_BANG_(target_2, "hc-state", state_3);

}));
};
};

};
var el_BANG_ = function (tag, attrs, text) {
const e_1 = document.createElement(tag);
for (let G__2 of squint_core.iterable(Object.entries(attrs))) {
const vec___3_6 = G__2;
const k_7 = squint_core.nth(vec___3_6, 0, null);
const v_8 = squint_core.nth(vec___3_6, 1, null);
e_1.setAttribute(k_7, v_8)
};
if (squint_core.truth_(text)) {
e_1.textContent = text};
return e_1;

};
var start_timeline_BANG_ = function (report_BANG_, el) {
const temp__23314__auto___1 = target_of(report_BANG_, el, "data-timeline", el.dataset.timeline);
if (squint_core.truth_(temp__23314__auto___1)) {
const target_2 = temp__23314__auto___1;
const duration_3 = num_attr(el, "data-duration", 4);
const step_4 = num_attr(el, "data-step", (duration_3 / 100));
const loop_QMARK__5 = !("false" === el.dataset.loop);
const state_6 = squint_core.atom(({"t": 0, "playing": false, "resume": false, "speed": 1, "last": null, "frame": null}));
const play_7 = el_BANG_("button", ({"type": "button", "class": "hc-tl-play"}), null);
const back_8 = el_BANG_("button", ({"type": "button", "aria-label": "Step back"}), "⏮");
const fwd_9 = el_BANG_("button", ({"type": "button", "aria-label": "Step forward"}), "⏭");
const bar_10 = el_BANG_("input", ({"type": "range", "min": "0", "max": `${duration_3??''}`, "step": "any", "aria-label": "Time"}), null);
const speed_11 = el_BANG_("select", ({"aria-label": "Speed"}), null);
const readout_12 = el_BANG_("output", ({}), null);
const show_BANG__13 = (function () {
const map___14_16 = squint_core.deref(state_6);
const map___14_17 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___14_16))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___14_16))) ? (map___14_16) : (squint_core.seq_to_map_for_destructuring(map___14_16)))) : (map___14_16));
const t_18 = squint_core.get(map___14_17, "t");
const playing_19 = squint_core.get(map___14_17, "playing");
bar_10.value = `${t_18??''}`;
readout_12.textContent = `${t_18.toFixed(2)??''}${" s"}`;
play_7.textContent = ((squint_core.truth_(playing_19)) ? ("⏸") : ("▶"));
play_7.setAttribute("aria-label", ((squint_core.truth_(playing_19)) ? ("Pause") : ("Play")));
return emit_BANG_(target_2, "hc-time", ({"t": t_18, "playing": playing_19}));

});
const seek_BANG__20 = (function (t) {
squint_core.swap_BANG_(state_6, squint_core.assoc, "t", Math.min(duration_3, Math.max(0, t)));
return show_BANG__13();

});
const tick_21 = (function tick (now) {
const map___22_24 = squint_core.deref(state_6);
const map___22_25 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___22_24))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___22_24))) ? (map___22_24) : (squint_core.seq_to_map_for_destructuring(map___22_24)))) : (map___22_24));
const playing_26 = squint_core.get(map___22_25, "playing");
const last_27 = squint_core.get(map___22_25, "last");
const t_28 = squint_core.get(map___22_25, "t");
const speed_29 = squint_core.get(map___22_25, "speed");
if (squint_core.truth_(playing_26)) {
const t_SINGLEQUOTE__30 = (t_28 + (speed_29 * ((now - ((squint_core.truth_(last_27)) ? (last_27) : (now))) / 1000)));
const t_SINGLEQUOTE__31 = (((t_SINGLEQUOTE__30 < duration_3)) ? (t_SINGLEQUOTE__30) : (((loop_QMARK__5) ? (squint_core.mod(t_SINGLEQUOTE__30, duration_3)) : ((("else") ? (duration_3) : (null))))));
squint_core.swap_BANG_(state_6, squint_core.assoc, "t", t_SINGLEQUOTE__31, "last", now, "playing", ((loop_QMARK__5) ? (loop_QMARK__5) : ((t_SINGLEQUOTE__31 < duration_3))), "frame", null);
show_BANG__13();
if (squint_core.truth_(squint_core.get(squint_core.deref(state_6), "playing"))) {
return squint_core.swap_BANG_(state_6, squint_core.assoc, "frame", requestAnimationFrame(tick));
};
};

});
const set_playing_BANG__32 = (function (p) {
if (!squint_core._EQ_(p, squint_core.get(squint_core.deref(state_6), "playing"))) {
const G__21_33 = squint_core.get(squint_core.deref(state_6), "frame");
if ((G__21_33 == null)) {
} else {
cancelAnimationFrame(G__21_33)};
if (squint_core.truth_(((squint_core.truth_(p)) ? ((squint_core.get(squint_core.deref(state_6), "t") >= duration_3)) : (p)))) {
squint_core.swap_BANG_(state_6, squint_core.assoc, "t", 0)};
squint_core.swap_BANG_(state_6, squint_core.assoc, "playing", p, "last", null, "frame", ((squint_core.truth_(p)) ? (requestAnimationFrame(tick_21)) : (null)));
return show_BANG__13();
};

});
const by_reader_BANG__34 = (function (f) {
return function (e) {
squint_core.swap_BANG_(state_6, squint_core.assoc, "resume", false);
return f(e);

};

});
for (let G__35 of squint_core.iterable([0.25, 0.5, 1, 2])) {
const s_36 = G__35;
const o_37 = el_BANG_("option", ({"value": `${s_36??''}`}), `${s_36??''}${"×"}`);
o_37.selected = (s_36 === 1);
speed_11.append(o_37)
};
el.classList.add("hc-timeline");
el.append(play_7, back_8, fwd_9, bar_10, readout_12, speed_11);
play_7.addEventListener("click", by_reader_BANG__34((function (_) {
return set_playing_BANG__32(squint_core.not(squint_core.get(squint_core.deref(state_6), "playing")));

})));
back_8.addEventListener("click", by_reader_BANG__34((function (_) {
set_playing_BANG__32(false);
return seek_BANG__20((squint_core.get(squint_core.deref(state_6), "t") - step_4));

})));
fwd_9.addEventListener("click", by_reader_BANG__34((function (_) {
set_playing_BANG__32(false);
return seek_BANG__20((squint_core.get(squint_core.deref(state_6), "t") + step_4));

})));
bar_10.addEventListener("input", by_reader_BANG__34((function (_) {
set_playing_BANG__32(false);
return seek_BANG__20(parseFloat(bar_10.value));

})));
speed_11.addEventListener("change", (function (_) {
return squint_core.swap_BANG_(state_6, squint_core.assoc, "speed", parseFloat(speed_11.value));

}));
squint_core.swap_BANG_(pause_listeners, squint_core.conj, (function (paused_QMARK_) {
if (squint_core.truth_(paused_QMARK_)) {
squint_core.swap_BANG_(state_6, squint_core.assoc, "resume", squint_core.get(squint_core.deref(state_6), "playing"));
return set_playing_BANG__32(false);
} else {
if (squint_core.truth_(squint_core.get(squint_core.deref(state_6), "resume"))) {
squint_core.swap_BANG_(state_6, squint_core.assoc, "resume", false);
return set_playing_BANG__32(true);
}};

}));
show_BANG__13();
if (squint_core.truth_((() => {
const or_38_39 = window.htmlChannel.paused;
if (squint_core.truth_(or_38_39)) {
return or_38_39} else {
return ("false" === el.dataset.autoplay)};

})())) {
return squint_core.swap_BANG_(state_6, squint_core.assoc, "resume", !("false" === el.dataset.autoplay))} else {
return set_playing_BANG__32(true)};
};

};
var start_BANG_ = function (report_BANG_) {
const timelines_1 = Array.from(document.querySelectorAll("[data-timeline]"));
window.htmlChannel.paused = matchMedia("(prefers-reduced-motion: reduce)").matches;
window.htmlChannel.setPaused = set_paused_BANG_;
if (squint_core.truth_((() => {
const or_2_3 = explorable_QMARK_();
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return squint_core.seq(timelines_1)};

})())) {
add_pause_button_BANG_();
set_paused_BANG_(window.htmlChannel.paused)};
for (let G__4 of squint_core.iterable(document.querySelectorAll("[data-scrub]"))) {
const el_5 = G__4;
start_scrub_BANG_(report_BANG_, el_5)
};
for (let G__6 of squint_core.iterable(document.querySelectorAll("a[data-set]"))) {
const el_7 = G__6;
start_state_link_BANG_(report_BANG_, el_7)
};
for (let G__8 of squint_core.iterable(timelines_1)) {
const el_9 = G__8;
start_timeline_BANG_(report_BANG_, el_9)
}
return null;

};

export { start_state_link_BANG_, add_pause_button_BANG_, start_scrub_BANG_, emit_BANG_, explorable_QMARK_, num_attr, start_BANG_, start_timeline_BANG_, set_paused_BANG_, target_of, el_BANG_, decimals, pause_listeners }
