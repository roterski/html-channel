import * as squint_core from '../squint-core.js';
import * as diagram from './statechart_diagram.mjs';
import * as decide_DOT_statechart_diagram from './statechart_diagram.mjs';
import * as sm from './statechart_machine.mjs';
import * as decide_DOT_statechart_machine from './statechart_machine.mjs';
import * as load from './../viz/load.mjs';
import * as viz_DOT_load from './../viz/load.mjs';
import * as mermaid from './../viz/mermaid.mjs';
import * as viz_DOT_mermaid from './../viz/mermaid.mjs';
import * as versions from './../viz/versions.mjs';
import * as viz_DOT_versions from './../viz/versions.mjs';
var kind = "statechart";
var present_QMARK_ = function () {
return !(document.querySelector("[data-statechart]") == null);

};
var el_BANG_ = /* @__PURE__ */ (() => {
const impl33_1 = (function (tag, attrs, children) {
const e_2 = document.createElement(tag);
for (let G__3 of squint_core.iterable(Object.entries(attrs))) {
const vec___4_7 = G__3;
const k_8 = squint_core.nth(vec___4_7, 0, null);
const v_9 = squint_core.nth(vec___4_7, 1, null);
if (!(v_9 == null)) {
e_2.setAttribute(k_8, v_9)}
};
for (let G__10 of squint_core.iterable(children)) {
const c_11 = G__10;
if (!(c_11 == null)) {
e_2.append(c_11)}
};
return e_2;

});
const f29 = (function (arg30, arg31, ...rest32) {
const self__23546__auto___12 = this;
return impl33_1.call(self__23546__auto___12, arg30, arg31, (((rest32.length === 0)) ? (null) : (rest32)));

});
(f29["squint$lang$variadic"] = impl33_1);
return f29;

})();
var chart_name = function (fig, i) {
const or_1_2 = squint_core.not_empty(fig.dataset.instrument);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
const or_3_4 = squint_core.not_empty(fig.id);
if (squint_core.truth_(or_3_4)) {
return or_3_4} else {
return `${"#"}${i??''}`};
};

};
var hex = function (c) {
const temp__23255__auto___1 = /rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(c);
if (squint_core.truth_(temp__23255__auto___1)) {
const m_2 = temp__23255__auto___1;
return `${"#"}${squint_core.vec(squint_core.lazy((function* () {
for (let G__3 of squint_core.iterable([1, 2, 3])) {
const i_4 = G__3;
yield Number(m_2[i_4]).toString(16).padStart(2, "0");
}
return null;

}))).join("")??''}`;
} else {
return c};

};
var token = function (el, name) {
const probe_1 = el_BANG_("span", ({"style": `${"color: var("}${name??''}${")"}`}));
el.append(probe_1);
const c_2 = getComputedStyle(probe_1).color;
probe_1.remove();
return hex(c_2);

};
var mermaid_theme = function (el) {
const t_1 = (function (_PERCENT_1) {
return token(el, _PERCENT_1);

});
const bg_2 = t_1("--hc-bg");
const fg_3 = t_1("--hc-fg");
const card_4 = t_1("--hc-card");
const muted_5 = t_1("--hc-muted");
return ({"primaryBorderColor": muted_5, "compositeTitleBackground": card_4, "altBackground": bg_2, "compositeBorder": muted_5, "stateBkg": card_4, "compositeBackground": bg_2, "background": bg_2, "transitionColor": fg_3, "secondaryColor": bg_2, "specialStateColor": fg_3, "clusterBorder": muted_5, "mainBkg": card_4, "titleColor": fg_3, "stateLabelColor": fg_3, "textColor": fg_3, "primaryColor": card_4, "tertiaryColor": bg_2, "lineColor": fg_3, "labelBackgroundColor": bg_2, "innerEndBackground": fg_3, "edgeLabelBackground": bg_2, "primaryTextColor": fg_3, "nodeBorder": muted_5, "clusterBkg": bg_2, "transitionLabelColor": fg_3});

};
var smcat_module = squint_core.atom(null);
var smcat_BANG_ = async function () {
const or_1_2 = squint_core.deref(smcat_module);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
const m_3 = (await import(versions.state_machine_cat));
squint_core.reset_BANG_(smcat_module, m_3);
return m_3;
};

};
var xstate_BANG_ = async function () {
(await load.scripts_BANG_(versions.xstate));
return window.XState;

};
var renderer_of = function (fig, m) {
const forced_1 = fig.dataset.renderer;
if (((("mermaid" === forced_1)) || (("smcat" === forced_1)))) {
return forced_1} else {
if (squint_core.truth_(sm.has_history_QMARK_(m))) {
return "smcat"} else {
if ("else") {
return "mermaid"} else {
return null}}};

};
var state_label = function (m, uid) {
return sm.path_of(uid).join(".");

};
var code_entries = function (p__34) {
const map___1_3 = p__34;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const m_5 = map___1_4;
const idx_6 = squint_core.get(map___1_4, "idx");
return squint_core.vec(squint_core.concat(squint_core.lazy((function* () {
for (let G__7 of squint_core.iterable(Object.values(idx_6))) {
const n_8 = G__7;
if (squint_core.truth_(((!squint_core._EQ_("", squint_core.get(n_8, "uid"))) && (squint_core.seq(squint_core.get(n_8, "code")))))) {
yield ({"uid": squint_core.get(n_8, "uid"), "label": state_label(m_5, squint_core.get(n_8, "uid")), "refs": squint_core.get(n_8, "code")});}
}
return null;

})), squint_core.lazy((function* () {
for (let G__9 of squint_core.iterable(Object.values(idx_6))) {
const n_10 = G__9;
for (let G__11 of squint_core.iterable(squint_core.get(n_10, "transitions"))) {
const t_12 = G__11;
if (squint_core.truth_(squint_core.seq(squint_core.get(t_12, "code")))) {
yield ({"uid": null, "label": `${(yield* (function* () {
const or_13_14 = squint_core.get(t_12, "event");
if (squint_core.truth_(or_13_14)) {
return or_13_14} else {
return "always"};

})())??''}${": "}${((squint_core._EQ_("", squint_core.get(n_10, "uid"))) ? ("any state") : (state_label(m_5, squint_core.get(n_10, "uid"))))??''}${" → "}${squint_core.vec(squint_core.map((function (_PERCENT_1) {
return state_label(m_5, _PERCENT_1);

}), squint_core.get(t_12, "targets"))).join(", ")??''}`, "refs": squint_core.get(t_12, "code")});}
}
}
return null;

}))));

};
var build_ui_BANG_ = function (fig, m, definition) {
const inferred_1 = (() => {
const G__35_2 = definition;
const G__35_3 = (((G__35_2 == null)) ? (null) : (G__35_2["meta"]));
const G__35_4 = (((G__35_3 == null)) ? (null) : (G__35_3["inferred"]));
if ((G__35_4 == null)) {
return null} else {
return G__35_4["basis"]};

})();
const chart_5 = el_BANG_("div", ({"class": "hc-sc-chart"}));
const events_6 = el_BANG_("div", ({"class": "hc-sc-events", "role": "group", "aria-label": "Send an event"}));
const ctx_keys_7 = Object.keys(squint_core.get(m, "context"));
const context_8 = (((squint_core.count(ctx_keys_7) > 0)) ? (el_BANG_("fieldset", ({"class": "hc-sc-context"}), el_BANG_("legend", ({}), "Context"))) : (null));
const reset_9 = el_BANG_("button", ({"type": "button", "class": "hc-sc-reset"}), "Reset");
const log_10 = el_BANG_("ol", ({"class": "hc-sc-log-entries"}));
const log_box_11 = el_BANG_("div", ({"class": "hc-sc-log"}), el_BANG_("div", ({"class": "hc-sc-log-head"}), el_BANG_("span", ({}), "Event log"), reset_9), log_10);
const refs_12 = code_entries(m);
const code_13 = ((squint_core.truth_(squint_core.seq(refs_12))) ? (el_BANG_("div", ({"class": "hc-sc-code"}), el_BANG_("p", ({}), "Where in the code"), squint_core.apply(el_BANG_, "ul", ({}), squint_core.lazy((function* () {
for (let G__14 of squint_core.iterable(refs_12)) {
const map___15_17 = G__14;
const map___15_18 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___15_17))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___15_17))) ? (map___15_17) : (squint_core.seq_to_map_for_destructuring(map___15_17)))) : (map___15_17));
const uid_19 = squint_core.get(map___15_18, "uid");
const label_20 = squint_core.get(map___15_18, "label");
const refs_21 = squint_core.get(map___15_18, "refs");
yield squint_core.apply(el_BANG_, "li", ({"data-uid": uid_19}), el_BANG_("span", ({}), label_20), squint_core.lazy((function* () {
for (let G__22 of squint_core.iterable(refs_21)) {
const r_23 = G__22;
yield el_BANG_("code", ({"data-src": r_23}), r_23);
}
return null;

})));
}
return null;

}))))) : (null));
if (squint_core.truth_(inferred_1)) {
fig.append(el_BANG_("p", ({"class": "hc-sc-inferred"}), `${"Inferred from code: "}${inferred_1??''}`))};
fig.append(chart_5, events_6);
if (squint_core.truth_(context_8)) {
fig.append(context_8)};
fig.append(log_box_11);
if (squint_core.truth_(code_13)) {
fig.append(code_13)};
return ({"chart": chart_5, "events": events_6, "context": context_8, "reset": reset_9, "log": log_10, "code": code_13});

};
var context_inputs_BANG_ = function (fieldset, context, enums, on_change) {
for (let G__1 of squint_core.iterable(Object.entries(context))) {
const vec___2_5 = G__1;
const k_6 = squint_core.nth(vec___2_5, 0, null);
const v_7 = squint_core.nth(vec___2_5, 1, null);
const opts_8 = ((squint_core.truth_(enums)) ? (enums[k_6]) : (null));
const input_9 = ((squint_core.truth_(Array.isArray(opts_8))) ? (squint_core.apply(el_BANG_, "select", ({"name": k_6}), squint_core.lazy((function* () {
for (let G__10 of squint_core.iterable(opts_8)) {
const o_11 = G__10;
yield el_BANG_("option", ({"value": `${o_11??''}`}), `${o_11??''}`);
}
return null;

})))) : (((squint_core.boolean_QMARK_(v_7)) ? (el_BANG_("input", ({"type": "checkbox", "name": k_6}))) : (((squint_core.number_QMARK_(v_7)) ? (el_BANG_("input", ({"type": "number", "step": "any", "name": k_6}))) : ((("else") ? (el_BANG_("input", ({"type": "text", "name": k_6}))) : (null))))))));
const value_of_12 = (function () {
if (("checkbox" === input_9.type)) {
return input_9.checked} else {
if (squint_core.number_QMARK_(v_7)) {
return parseFloat(input_9.value)} else {
if (squint_core.truth_(Array.isArray(opts_8))) {
const or_13_14 = squint_core.some((function (_PERCENT_1) {
if ((`${_PERCENT_1??''}` === input_9.value)) {
return _PERCENT_1;
};

}), opts_8);
if (squint_core.truth_(or_13_14)) {
return or_13_14} else {
return input_9.value};
} else {
if ("else") {
return input_9.value} else {
return null}}}};

});
input_9.addEventListener("change", (function (_) {
const x_15 = value_of_12();
if (squint_core.truth_(((squint_core.number_QMARK_(x_15)) && (isNaN(x_15))))) {
return null} else {
return on_change(k_6, x_15);
};

}));
fieldset.append(el_BANG_("label", ({}), el_BANG_("span", ({}), k_6), input_9))
}
return null;

};
var show_context_BANG_ = function (fieldset, context) {
if (squint_core.truth_(fieldset)) {
for (let G__1 of squint_core.iterable(Object.entries(context))) {
const vec___2_5 = G__1;
const k_6 = squint_core.nth(vec___2_5, 0, null);
const v_7 = squint_core.nth(vec___2_5, 1, null);
const temp__23314__auto___8 = fieldset.querySelector(`${"[name=\""}${CSS.escape(k_6)??''}${"\"]"}`);
if (squint_core.truth_(temp__23314__auto___8)) {
const input_9 = temp__23314__auto___8;
if (squint_core._EQ_(input_9, document.activeElement)) {
} else {
if (("checkbox" === input_9.type)) {
input_9.checked = squint_core.boolean$(v_7)} else {
input_9.value = `${v_7??''}`}}}
}
return null;
};

};
var paused_QMARK_ = function () {
return squint_core.boolean$((() => {
const G__36_1 = window;
const G__36_2 = (((G__36_1 == null)) ? (null) : (G__36_1.htmlChannel));
if ((G__36_2 == null)) {
return null} else {
return G__36_2.paused};

})());

};
var clear_timers_BANG_ = function (st) {
for (let G__1 of squint_core.iterable(squint_core.get(squint_core.deref(st), "timers"))) {
const t_2 = G__1;
const G__37_3 = squint_core.get(t_2, "handle");
if ((G__37_3 == null)) {
} else {
clearTimeout(G__37_3)}
};
return squint_core.swap_BANG_(st, squint_core.assoc, "timers", []);

};
var arm_BANG_ = function (st, t, send_BANG_) {
return squint_core.assoc(t, "started", performance.now(), "handle", setTimeout((function () {
return send_BANG_(({"type": squint_core.get(t, "event")}));

}), squint_core.get(t, "remaining")));

};
var start_timers_BANG_ = function (st, delays, send_BANG_) {
clear_timers_BANG_(st);
return squint_core.swap_BANG_(st, squint_core.assoc, "timers", squint_core.vec(squint_core.lazy((function* () {
for (let G__1 of squint_core.iterable(delays)) {
const map___2_4 = G__1;
const map___2_5 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___2_4))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___2_4))) ? (map___2_4) : (squint_core.seq_to_map_for_destructuring(map___2_4)))) : (map___2_4));
const event_6 = squint_core.get(map___2_5, "event");
const delay_7 = squint_core.get(map___2_5, "delay");
yield (yield* (function* () {
const t_8 = ({"event": event_6, "remaining": delay_7});
if (squint_core.truth_(paused_QMARK_())) {
return t_8} else {
return arm_BANG_(st, t_8, send_BANG_)};

})());
}
return null;

}))));

};
var pause_timers_BANG_ = function (st, paused, send_BANG_) {
return squint_core.swap_BANG_(st, squint_core.update, "timers", (function (ts) {
return squint_core.vec(squint_core.lazy((function* () {
for (let G__1 of squint_core.iterable(ts)) {
const t_2 = G__1;
yield ((squint_core.truth_(paused)) ? ((yield* (function* () {
const temp__23255__auto___3 = squint_core.get(t_2, "handle");
if (squint_core.truth_(temp__23255__auto___3)) {
const h_4 = temp__23255__auto___3;
clearTimeout(h_4);
return squint_core.assoc(t_2, "handle", null, "remaining", Math.max(0, (squint_core.get(t_2, "remaining") - (performance.now() - squint_core.get(t_2, "started")))));
} else {
return t_2};

})())) : (((squint_core.truth_(squint_core.get(t_2, "handle"))) ? (t_2) : (arm_BANG_(st, t_2, send_BANG_)))));
}
return null;

})));

}));

};
var tag_states_BANG_ = function (chart, m) {
for (let G__1 of squint_core.iterable(Array.from(chart.querySelectorAll("g[id*='-state-']")))) {
const g_2 = G__1;
const temp__23314__auto___3 = /-state-(s\d+)-\d+$/.exec(g_2.id);
if (squint_core.truth_(temp__23314__auto___3)) {
const mt_4 = temp__23314__auto___3;
const temp__23314__auto___5 = sm.uid_of_diagram_id(m, mt_4[1]);
if (squint_core.truth_(temp__23314__auto___5)) {
const u_6 = temp__23314__auto___5;
g_2.setAttribute("data-uid", u_6)}}
};
for (let G__7 of squint_core.iterable(Array.from(chart.querySelectorAll("g.node, g.cluster")))) {
const g_8 = G__7;
const temp__23314__auto___9 = (() => {
const G__38_10 = g_8.querySelector(":scope > title");
if ((G__38_10 == null)) {
return null} else {
return G__38_10.textContent};

})();
if (squint_core.truth_(temp__23314__auto___9)) {
const t_11 = temp__23314__auto___9;
const temp__23314__auto___12 = sm.uid_of_diagram_id(m, t_11.replace(/^cluster_/, ""));
if (squint_core.truth_(temp__23314__auto___12)) {
const u_13 = temp__23314__auto___12;
g_8.setAttribute("data-uid", u_13)}}
}
return null;

};
var mark_event_labels_BANG_ = function (chart, events) {
const mark_BANG__1 = (function (el, text) {
const ev_2 = squint_core.first(text.trim().split(" [")).trim();
if (squint_core.truth_(events.includes(ev_2))) {
return el.setAttribute("data-hc-event", ev_2);
};

});
for (let G__3 of squint_core.iterable(Array.from(chart.querySelectorAll(".edgeLabel")))) {
const l_4 = G__3;
mark_BANG__1(l_4, l_4.textContent)
};
for (let G__5 of squint_core.iterable(Array.from(chart.querySelectorAll("g.edge")))) {
const g_6 = G__5;
mark_BANG__1(g_6, squint_core.vec(squint_core.map((function (_PERCENT_1) {
return _PERCENT_1.textContent;

}), Array.from(g_6.querySelectorAll("text")))).join(" "))
}
return null;

};
var draw_BANG_ = async function (fig, st) {
const map___1_3 = squint_core.deref(st);
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const m_5 = squint_core.get(map___1_4, "m");
const active_6 = squint_core.get(map___1_4, "active");
const renderer_7 = squint_core.get(map___1_4, "renderer");
const ui_8 = squint_core.get(map___1_4, "ui");
const n_draw_9 = squint_core.get(map___1_4, "n-draw");
const i_10 = squint_core.get(map___1_4, "i");
const n_11 = (((squint_core.truth_(n_draw_9)) ? (n_draw_9) : (0)) + 1);
const chart_12 = squint_core.get(ui_8, "chart");
squint_core.swap_BANG_(st, squint_core.assoc, "n-draw", n_11);
try{
const svg_13 = ((("smcat" === renderer_7)) ? ((await (await smcat_BANG_()).render(diagram.smcat(m_5, active_6), ({"outputType": "svg"})))) : ((await (async () => {
const mm_14 = (await mermaid.ready_BANG_());
const src_15 = diagram.mermaid(m_5, active_6, mermaid_theme(fig), token(fig, "--hc-accent"));
return (await mm_14.render(`hc-sc-${i_10??''}-${n_11}`, src_15)).svg;

})())));
if ((n_11 === squint_core.get(squint_core.deref(st), "n-draw"))) {
chart_12.className = `${"hc-sc-chart hc-sc-"}${renderer_7??''}`;
chart_12.innerHTML = svg_13;
tag_states_BANG_(chart_12, m_5);
mark_event_labels_BANG_(chart_12, sm.events_of(m_5, squint_core.get(squint_core.deref(st), "active")));
if (("smcat" === renderer_7)) {
return chart_12.style.setProperty("--hc-sc-accent", token(fig, "--hc-accent"));
};
};
}
catch(e_16){
if ((n_11 === squint_core.get(squint_core.deref(st), "n-draw"))) {
chart_12.textContent = `${"The chart couldn't be drawn: "}${(await (async () => {
const or_17_18 = e_16.message;
if (squint_core.truth_(or_17_18)) {
return or_17_18} else {
return e_16};

})())??''}`;
return console.error("html-channel statechart:", e_16);
};
}
;

};
var show_BANG_ = function (fig, st, event, send_BANG_) {
const map___1_3 = squint_core.deref(st);
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const m_5 = squint_core.get(map___1_4, "m");
const actor_6 = squint_core.get(map___1_4, "actor");
const ui_7 = squint_core.get(map___1_4, "ui");
const log_8 = squint_core.get(map___1_4, "log");
const cursor_9 = squint_core.get(map___1_4, "cursor");
const snap_10 = actor_6.getSnapshot();
const active_11 = sm.active_uids(m_5, snap_10.value);
const hl_12 = sm.highlighted(m_5, active_11);
squint_core.swap_BANG_(st, squint_core.assoc, "active", active_11);
fig.setAttribute("data-active", JSON.stringify(hl_12));
draw_BANG_(fig, st);
const box_13 = squint_core.get(ui_7, "events");
const evs_14 = sm.events_of(m_5, active_11);
const old_15 = squint_core.into(({}), squint_core.lazy((function* () {
for (let G__16 of squint_core.iterable(Array.from(box_13.children))) {
const b_17 = G__16;
yield [b_17.dataset.event, b_17];
}
return null;

})));
for (let G__18 of squint_core.iterable(Object.entries(old_15))) {
const vec___19_22 = G__18;
const e_23 = squint_core.nth(vec___19_22, 0, null);
const b_24 = squint_core.nth(vec___19_22, 1, null);
if (squint_core.truth_(evs_14.includes(e_23))) {
} else {
b_24.remove()}
};
const bs_25 = squint_core.vec(squint_core.lazy((function* () {
for (let G__26 of squint_core.iterable(evs_14)) {
const e_27 = G__26;
yield (yield* (function* () {
const b_28 = (yield* (function* () {
const or_29_30 = squint_core.get(old_15, e_27);
if (squint_core.truth_(or_29_30)) {
return or_29_30} else {
const G__39_31 = el_BANG_("button", ({"type": "button", "data-event": e_27}), e_27);
G__39_31.addEventListener("click", (function (_) {
return send_BANG_(({"type": e_27}));

}));
return G__39_31;
};

})());
b_28.disabled = squint_core.not(snap_10.can(({"type": e_27})));
return b_28;

})());
}
return null;

})));
const order_32 = (function () {
return squint_core.vec(squint_core.map((function (_PERCENT_1) {
return _PERCENT_1.dataset.event;

}), Array.from(box_13.children))).join("\n");

});
if (!squint_core._EQ_(order_32(), evs_14.join("\n"))) {
for (let G__33 of squint_core.iterable(bs_25)) {
const b_34 = G__33;
box_13.append(b_34)
}};
show_context_BANG_(squint_core.get(ui_7, "context"), snap_10.context);
if ((((log_8 === squint_core.get(squint_core.deref(st), "shown-log"))) && (squint_core._EQ_(cursor_9, squint_core.get(squint_core.deref(st), "shown-cursor"))))) {
} else {
squint_core.swap_BANG_(st, squint_core.assoc, "shown-log", log_8, "shown-cursor", cursor_9);
const ol_35 = squint_core.get(ui_7, "log");
const entry_36 = (function (idx, text, edit_QMARK_) {
const b_37 = el_BANG_("button", ({"type": "button"}), text);
const place_38 = ((squint_core._EQ_(idx, cursor_9)) ? ("hc-sc-current") : ((((idx > cursor_9)) ? ("hc-sc-later") : (null))));
const li_39 = el_BANG_("li", ({"class": ((squint_core.truth_(((squint_core.truth_(place_38)) ? (edit_QMARK_) : (place_38)))) ? (`${place_38??''}${" hc-sc-edit"}`) : (((squint_core.truth_(edit_QMARK_)) ? ("hc-sc-edit") : ((("else") ? (place_38) : (null))))))}), b_37);
if (squint_core._EQ_(idx, cursor_9)) {
b_37.setAttribute("aria-current", "step")};
b_37.addEventListener("click", (function (_) {
return squint_core.get(squint_core.deref(st), "go!")(idx);

}));
return li_39;

});
ol_35.textContent = "";
ol_35.append(entry_36(-1, `${"start → "}${squint_core.vec(squint_core.map((function (_PERCENT_1) {
return state_label(m_5, _PERCENT_1);

}), squint_core.get(squint_core.deref(st), "start-hl"))).join(", ")??''}`, false));
for (let G__40 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, log_8))) {
const vec___41_46 = G__40;
const idx_47 = squint_core.nth(vec___41_46, 0, null);
const map___44_48 = squint_core.nth(vec___41_46, 1, null);
const map___44_49 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___44_48))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___44_48))) ? (map___44_48) : (squint_core.seq_to_map_for_destructuring(map___44_48)))) : (map___44_48));
const kind_50 = squint_core.get(map___44_49, "kind");
const label_51 = squint_core.get(map___44_49, "label");
const hl_52 = squint_core.get(map___44_49, "hl");
const no_effect_53 = squint_core.get(map___44_49, "no-effect");
ol_35.append(entry_36(idx_47, `${label_51??''}${((squint_core.truth_(no_effect_53)) ? (" (no effect)") : (null))??''}${" → "}${squint_core.vec(squint_core.map((function (_PERCENT_1) {
return state_label(m_5, _PERCENT_1);

}), hl_52)).join(", ")??''}`, ("edit" === kind_50)))
}};
start_timers_BANG_(st, sm.delays_of(m_5, active_11), send_BANG_);
return fig.dispatchEvent((new CustomEvent("hc-statechart", ({"detail": ({"value": snap_10.value, "context": snap_10.context, "event": event})}))));

};
var run_BANG_ = async function (fig, i, m, definition, renderer, pending) {
const ui_1 = build_ui_BANG_(fig, m, definition);
const st_2 = squint_core.atom(({"m": m, "ui": ui_1, "renderer": renderer, "i": i, "log": [], "cursor": -1, "timers": []}));
const xs_3 = (await xstate_BANG_());
const machine_4 = xs_3.createMachine(sm.engine_config(m, xs_3.assign));
const fresh_5 = (function (snapshot) {
const G__40_6 = xs_3.createActor(machine_4, ((squint_core.truth_(snapshot)) ? (({"snapshot": snapshot})) : (null)));
G__40_6.start();
return G__40_6;

});
const actor_7 = fresh_5(null);
const start_snap_8 = actor_7.getPersistedSnapshot();
const hl_of_9 = (function (a) {
return sm.highlighted(m, sm.active_uids(m, a.getSnapshot().value));

});
squint_core.swap_BANG_(st_2, squint_core.assoc, "actor", actor_7, "start-snap", start_snap_8, "start-hl", hl_of_9(actor_7));
const replace_actor_BANG__10 = (function (snapshot) {
squint_core.get(squint_core.deref(st_2), "actor").stop();
return squint_core.swap_BANG_(st_2, squint_core.assoc, "actor", fresh_5(snapshot));

});
const start_over_BANG__11 = (function () {
replace_actor_BANG__10(start_snap_8);
const temp__23314__auto___20 = squint_core.get(squint_core.deref(st_2), "prose-context");
if (squint_core.truth_(temp__23314__auto___20)) {
const pc_21 = temp__23314__auto___20;
squint_core.get(squint_core.deref(st_2), "actor").send(({"type": "hc.context", "patch": pc_21}))};
return squint_core.swap_BANG_(st_2, squint_core.assoc, "start-hl", hl_of_9(squint_core.get(squint_core.deref(st_2), "actor")));

});
const restore_BANG__12 = (function (idx) {
if ((idx < 0)) {
return start_over_BANG__11()} else {
return replace_actor_BANG__10(squint_core.get(squint_core.nth(squint_core.get(squint_core.deref(st_2), "log"), idx), "snap"))};

});
const apply_entry_BANG__13 = (function (entry) {
const actor_22 = squint_core.get(squint_core.deref(st_2), "actor");
const event_QMARK__23 = ("event" === squint_core.get(entry, "kind"));
const no_effect_24 = ((event_QMARK__23) ? (squint_core.not(actor_22.getSnapshot().can(squint_core.get(entry, "ev")))) : (event_QMARK__23));
actor_22.send(((event_QMARK__23) ? (squint_core.get(entry, "ev")) : (({"type": "hc.context", "patch": squint_core.get(entry, "patch")}))));
return squint_core.assoc(entry, "snap", actor_22.getPersistedSnapshot(), "hl", hl_of_9(actor_22), "no-effect", no_effect_24);

});
const append_BANG__14 = (function (entry) {
const map___25_27 = squint_core.deref(st_2);
const map___25_28 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___25_27))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___25_27))) ? (map___25_27) : (squint_core.seq_to_map_for_destructuring(map___25_27)))) : (map___25_27));
const log_29 = squint_core.get(map___25_28, "log");
const cursor_30 = squint_core.get(map___25_28, "cursor");
return squint_core.swap_BANG_(st_2, squint_core.assoc, "log", squint_core.conj(squint_core.vec(squint_core.take((cursor_30 + 1), log_29)), apply_entry_BANG__13(entry)), "cursor", (cursor_30 + 1));

});
const send_BANG__15 = (function (ev) {
append_BANG__14(({"kind": "event", "label": squint_core.get(ev, "type"), "ev": ev}));
return show_BANG_(fig, st_2, squint_core.get(ev, "type"), send_BANG__15);

});
const edit_BANG__16 = (function (k, v) {
const map___31_33 = squint_core.deref(st_2);
const map___31_34 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___31_33))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___31_33))) ? (map___31_33) : (squint_core.seq_to_map_for_destructuring(map___31_33)))) : (map___31_33));
const log_35 = squint_core.get(map___31_34, "log");
const cursor_36 = squint_core.get(map___31_34, "cursor");
const actor_37 = squint_core.get(map___31_34, "actor");
const current_38 = (((cursor_36 >= 0)) ? (squint_core.nth(log_35, cursor_36)) : (null));
const entry_39 = ({"kind": "edit", "label": `${k??''}${" = "}${JSON.stringify(v)??''}`, "key": k, "patch": (() => {
const G__41_40 = ({});
(G__41_40[k] = v);
return G__41_40;

})()});
if ((v === actor_37.getSnapshot().context[k])) {
return null} else {
if (squint_core.truth_(((squint_core.truth_(current_38)) ? (((("edit" === squint_core.get(current_38, "kind"))) && (squint_core._EQ_(k, squint_core.get(current_38, "key"))))) : (current_38)))) {
restore_BANG__12((cursor_36 - 1));
squint_core.swap_BANG_(st_2, squint_core.assoc, "log", squint_core.conj(squint_core.vec(squint_core.take(cursor_36, log_35)), apply_entry_BANG__13(entry_39)))} else {
append_BANG__14(entry_39)};
return show_BANG_(fig, st_2, null, send_BANG__15);
};

});
const replay_BANG__17 = (function () {
start_over_BANG__11();
squint_core.swap_BANG_(st_2, squint_core.assoc, "log", squint_core.mapv(apply_entry_BANG__13, squint_core.get(squint_core.deref(st_2), "log")));
restore_BANG__12(squint_core.get(squint_core.deref(st_2), "cursor"));
return show_BANG_(fig, st_2, null, send_BANG__15);

});
const go_BANG__18 = (function (idx) {
restore_BANG__12(idx);
squint_core.swap_BANG_(st_2, squint_core.assoc, "cursor", idx);
return show_BANG_(fig, st_2, null, send_BANG__15);

});
const restart_BANG__19 = (function () {
start_over_BANG__11();
squint_core.swap_BANG_(st_2, squint_core.assoc, "log", [], "cursor", -1);
return show_BANG_(fig, st_2, null, send_BANG__15);

});
squint_core.swap_BANG_(st_2, squint_core.assoc, "go!", go_BANG__18);
const temp__23314__auto___41 = squint_core.get(ui_1, "context");
if (squint_core.truth_(temp__23314__auto___41)) {
const fs_42 = temp__23314__auto___41;
context_inputs_BANG_(fs_42, squint_core.get(m, "context"), (await (async () => {
const G__42_43 = definition;
const G__42_44 = (((G__42_43 == null)) ? (null) : (G__42_43["meta"]));
if ((G__42_44 == null)) {
return null} else {
return G__42_44["enum"]};

})()), edit_BANG__16)};
squint_core.get(ui_1, "reset").addEventListener("click", (function (_) {
return restart_BANG__19();

}));
document.addEventListener("hc-pause", (function (e) {
return pause_timers_BANG_(st_2, e.detail.paused, send_BANG__15);

}));
matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (function (_) {
return draw_BANG_(fig, st_2);

}));
squint_core.get(ui_1, "chart").addEventListener("click", (function (e) {
const temp__23255__auto___45 = e.target.closest("[data-hc-event]");
if (squint_core.truth_(temp__23255__auto___45)) {
const label_46 = temp__23255__auto___45;
const temp__23314__auto___47 = squint_core.get(ui_1, "events").querySelector(`${"button[data-event=\""}${CSS.escape(label_46.getAttribute("data-hc-event"))??''}${"\"]"}`);
if (squint_core.truth_(temp__23314__auto___47)) {
const b_48 = temp__23314__auto___47;
if (squint_core.truth_(b_48.disabled)) {
return null} else {
return b_48.click();
};
};
} else {
const temp__23314__auto___49 = squint_core.get(ui_1, "code");
if (squint_core.truth_(temp__23314__auto___49)) {
const list_50 = temp__23314__auto___49;
const temp__23314__auto___51 = e.target.closest("[data-uid]");
if (squint_core.truth_(temp__23314__auto___51)) {
const g_52 = temp__23314__auto___51;
const uid_53 = g_52.getAttribute("data-uid");
const temp__23314__auto___54 = list_50.querySelector(`${"li[data-uid=\""}${CSS.escape(uid_53)??''}${"\"]"}`);
if (squint_core.truth_(temp__23314__auto___54)) {
const li_55 = temp__23314__auto___54;
li_55.scrollIntoView(({"block": "nearest"}));
li_55.classList.add("hc-sc-flash");
return setTimeout((function () {
return li_55.classList.remove("hc-sc-flash");

}), 1500);
};
};
};
};

}));
show_BANG_(fig, st_2, null, send_BANG__15);
const handle_56 = (function (type, detail) {
const G__43_57 = type;
switch (G__43_57) {case "hc-param":
if (squint_core.truth_(Object.hasOwn(squint_core.get(m, "context"), detail.name))) {
const patch_59 = (() => {
const G__44_60 = ({});
(G__44_60[detail.name] = detail.value);
return G__44_60;

})();
squint_core.swap_BANG_(st_2, squint_core.assoc, "prose-context", Object.assign(({}), squint_core.get(squint_core.deref(st_2), "prose-context"), patch_59));
return replay_BANG__17();
};

break;
case "hc-state":
if (squint_core.truth_(Array.isArray(detail.events))) {
restart_BANG__19();
for (let G__61 of squint_core.iterable(detail.events)) {
const ev_62 = G__61;
send_BANG__15(((squint_core.string_QMARK_(ev_62)) ? (({"type": ev_62})) : (ev_62)))
}
return null;
};

break;
};

});
for (let G__63 of squint_core.iterable(squint_core.deref(pending))) {
const vec___64_67 = G__63;
const type_68 = squint_core.nth(vec___64_67, 0, null);
const detail_69 = squint_core.nth(vec___64_67, 1, null);
handle_56(type_68, detail_69)
};
squint_core.reset_BANG_(pending, null);
squint_core.swap_BANG_(st_2, squint_core.assoc, "prose", handle_56);
for (let G__70 of squint_core.iterable(["hc-param", "hc-state"])) {
const type_71 = G__70;
fig.addEventListener(type_71, (function (e) {
return handle_56(type_71, e.detail);

}))
};
return st_2;

};
var fail_chart_BANG_ = function (fig, message, source_text) {
return fig.append(el_BANG_("div", ({"class": "hc-viz-error"}), message), el_BANG_("pre", ({"class": "hc-sc-source"}), source_text));

};
var start_one_BANG_ = function (report_BANG_, fig, i) {
const nm_1 = chart_name(fig, i);
const say_BANG__2 = (function (rule, msg) {
return report_BANG_(kind, `${"statechart \""}${nm_1??''}${"\": "}${msg??''}`, rule);

});
const machine_id_3 = fig.dataset.machine;
const script_4 = ((squint_core.truth_(machine_id_3)) ? (document.getElementById(machine_id_3)) : (fig.querySelector(":scope > script[type=\"application/json\"]")));
const pending_5 = squint_core.atom([]);
for (let G__6 of squint_core.iterable(["hc-param", "hc-state"])) {
const type_7 = G__6;
fig.addEventListener(type_7, (function (e) {
const temp__23314__auto___8 = squint_core.deref(pending_5);
if (squint_core.truth_(temp__23314__auto___8)) {
const p_9 = temp__23314__auto___8;
return squint_core.swap_BANG_(pending_5, squint_core.conj, [type_7, e.detail]);
};

}))
};
if (squint_core.truth_(fig.hasAttribute("data-instrument"))) {
} else {
say_BANG__2("statechart-instrument", "<figure data-statechart> needs data-instrument, so the check's probe drives it")};
if (squint_core.truth_(((squint_core.truth_(machine_id_3)) ? (squint_core.not(((squint_core.truth_(script_4)) ? (("application/json" === script_4.type)) : (script_4)))) : (machine_id_3)))) {
return say_BANG__2("statechart-machine", `${"data-machine=\""}${machine_id_3??''}${"\" names no <script type=\"application/json\" id=\""}${machine_id_3??''}${"\">"}`)} else {
if ((script_4 == null)) {
say_BANG__2("statechart-json", "no <script type=\"application/json\"> definition inside the figure");
return fail_chart_BANG_(fig, "This statechart has no definition.", "");
} else {
if ("else") {
const text_10 = script_4.textContent;
const definition_11 = (() => {
try{
return JSON.parse(text_10);
}
catch(e_12){
say_BANG__2("statechart-json", `${"the definition isn't JSON: "}${e_12.message??''}`);
return null;
}

})();
const collapse_13 = squint_core.vec(squint_core.remove(squint_core.empty_QMARK_, squint_core.map((function (_PERCENT_1) {
return _PERCENT_1.trim();

}), (() => {
const or_14_15 = fig.dataset.collapse;
if (squint_core.truth_(or_14_15)) {
return or_14_15} else {
return ""};

})().split(","))));
const m_16 = ((squint_core.truth_(definition_11)) ? (sm.model(definition_11, collapse_13)) : (null));
if (squint_core.truth_(m_16)) {
for (let G__17 of squint_core.iterable(squint_core.get(m_16, "findings"))) {
const vec___18_21 = G__17;
const rule_22 = squint_core.nth(vec___18_21, 0, null);
const msg_23 = squint_core.nth(vec___18_21, 1, null);
say_BANG__2(rule_22, msg_23)
}};
if (squint_core.truth_((((m_16 == null)) || (squint_core.get(m_16, "fatal"))))) {
return fail_chart_BANG_(fig, ((squint_core.truth_(m_16)) ? ("This statechart's definition has errors; see the reported problems.") : ("This statechart's definition isn't valid JSON.")), text_10)} else {
const renderer_24 = renderer_of(fig, m_16);
for (let G__25 of squint_core.iterable(sm.unreachable(m_16))) {
const u_26 = G__25;
say_BANG__2("statechart-unreachable", `${"state \""}${state_label(m_16, u_26)??''}${"\" can't be reached from the start"}`)
};
if (squint_core.truth_(((("mermaid" === renderer_24)) && (sm.has_history_QMARK_(m_16))))) {
say_BANG__2("statechart-history", "data-renderer=\"mermaid\" draws history states only as a plain H state")};
return run_BANG_(fig, i, m_16, definition_11, renderer_24, pending_5).catch((function (e) {
return fail_chart_BANG_(fig, `${"The statechart couldn't start: "}${(() => {
const or_27_28 = e.message;
if (squint_core.truth_(or_27_28)) {
return or_27_28} else {
return e};

})()??''}`, text_10);

}));
};
} else {
return null}}};

};
var start_BANG_ = function (report_BANG_) {
for (let G__1 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, Array.from(document.querySelectorAll("[data-statechart]"))))) {
const vec___2_5 = G__1;
const i_6 = squint_core.nth(vec___2_5, 0, null);
const fig_7 = squint_core.nth(vec___2_5, 1, null);
start_one_BANG_(report_BANG_, fig_7, i_6)
}
return null;

};

export { code_entries, mermaid_theme, build_ui_BANG_, show_BANG_, context_inputs_BANG_, xstate_BANG_, token, start_timers_BANG_, draw_BANG_, start_one_BANG_, hex, tag_states_BANG_, present_QMARK_, run_BANG_, pause_timers_BANG_, show_context_BANG_, clear_timers_BANG_, start_BANG_, paused_QMARK_, mark_event_labels_BANG_, state_label, chart_name, kind, arm_BANG_, renderer_of, el_BANG_, smcat_module, fail_chart_BANG_, smcat_BANG_ }
