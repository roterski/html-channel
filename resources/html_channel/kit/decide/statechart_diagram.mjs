import * as squint_core from '../squint-core.js';
import * as sm from './statechart_machine.mjs';
import * as decide_DOT_statechart_machine from './statechart_machine.mjs';
var active_class = "hc-sc-active";
var ancestors_of = function (idx, uid) {
let u_1 = squint_core.get(idx[uid], "parent");
let acc_2 = [];
while(true){
if ((u_1 == null)) {
return acc_2} else {
let G__3 = squint_core.get(idx[u_1], "parent");
let G__4 = squint_core.conj(acc_2, u_1);
u_1 = G__3;
acc_2 = G__4;
continue;
};
;break;
}
;

};
var container = function (idx, a, b) {
const as_1 = squint_core.into((new Set ([])), squint_core.conj(ancestors_of(idx, a), a));
const or_2_3 = squint_core.some((function (_PERCENT_1) {
if (squint_core.truth_(as_1.has(_PERCENT_1))) {
return _PERCENT_1;
};

}), squint_core.conj(ancestors_of(idx, b), b));
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return ""};

};
var block_of = function (idx, src, dst) {
const c_1 = container(idx, src, dst);
if (((squint_core._EQ_(c_1, src)) || (squint_core._EQ_(c_1, dst)))) {
const or_2_3 = squint_core.get(idx[c_1], "parent");
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return ""};
} else {
return c_1};

};
var label_of = function (t) {
const g_1 = ((squint_core.truth_(squint_core.get(t, "guard"))) ? (`${"["}${sm.guard_text(squint_core.get(t, "guard"))??''}${"]"}`) : (null));
return squint_core.vec(squint_core.remove(squint_core.nil_QMARK_, [(() => {
const G__45_2 = squint_core.get(t, "kind");
switch (G__45_2) {case "always":
if (squint_core.truth_(g_1)) {
return null} else {
return "always";
};

break;
default:
return squint_core.get(t, "event")};

})(), g_1])).join(" ");

};
var drawn_transitions = function (p__46) {
const map___1_3 = p__46;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const idx_5 = squint_core.get(map___1_4, "idx");
return squint_core.vec(squint_core.lazy((function* () {
for (let G__6 of squint_core.iterable(Object.values(idx_5))) {
const n_7 = G__6;
if (((!squint_core._EQ_("", squint_core.get(n_7, "uid"))) && (squint_core.not(sm.under_collapsed(idx_5, squint_core.get(n_7, "uid")))))) {
for (let G__8 of squint_core.iterable(squint_core.get(n_7, "transitions"))) {
const t_9 = G__8;
if (squint_core.not(squint_core.get(t_9, "bad"))) {
for (let G__10 of squint_core.iterable(((squint_core.truth_(squint_core.seq(squint_core.get(t_9, "targets")))) ? (squint_core.distinct(squint_core.map((function (_PERCENT_1) {
return sm.drawn_target(idx_5, _PERCENT_1);

}), squint_core.get(t_9, "targets")))) : ([squint_core.get(n_7, "uid")])))) {
const dst_11 = G__10;
yield ({"src": squint_core.get(n_7, "uid"), "dst": dst_11, "label": label_of(t_9), "block": block_of(idx_5, squint_core.get(n_7, "uid"), dst_11)});
}}
}}
}
return null;

})));

};
var drawn_children = function (n) {
if (squint_core.truth_(squint_core.get(n, "collapsed"))) {
return []} else {
return squint_core.get(n, "children")};

};
var did = function (idx, uid) {
return squint_core.get(idx[uid], "diagram-id");

};
var mermaid_entities = ({"#": "#35;", "<": "#lt;", ">": "#gt;", ";": "#59;", ":": "#58;", "\"": "#quot;"});
var mermaid_label = function (s) {
return s.replace((new RegExp("[#<>;:\"]", "g")), (function (c) {
return squint_core.get(mermaid_entities, c);

}));

};
var mermaid = function (p__47, active, theme, accent) {
const map___1_3 = p__47;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const m_5 = map___1_4;
const root_6 = squint_core.get(map___1_4, "root");
const idx_7 = squint_core.get(map___1_4, "idx");
const out_8 = [];
const ln_9 = (function (depth, s) {
return out_8.push(`${"  ".repeat(depth)??''}${s??''}`);

});
const by_block_10 = squint_core.group_by("block", drawn_transitions(m_5));
const node_name_11 = (function (n) {
const G__48_12 = squint_core.get(n, "kind");
switch (G__48_12) {case "history":
if (("deep" === squint_core.get(n, "history"))) {
return "H*"} else {
return "H"};

break;
default:
return squint_core.get(n, "key")};

});
const emit_transitions_14 = (function (depth, block) {
for (let G__15 of squint_core.iterable(by_block_10[block])) {
const map___16_18 = G__15;
const map___16_19 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___16_18))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___16_18))) ? (map___16_18) : (squint_core.seq_to_map_for_destructuring(map___16_18)))) : (map___16_18));
const src_20 = squint_core.get(map___16_19, "src");
const dst_21 = squint_core.get(map___16_19, "dst");
const label_22 = squint_core.get(map___16_19, "label");
ln_9(depth, `${did(idx_7, src_20)??''}${" --> "}${did(idx_7, dst_21)??''}${((squint_core.truth_(squint_core.seq(label_22))) ? (`${" : "}${mermaid_label(label_22)??''}`) : (null))??''}`)
}
return null;

});
const emit_level_23 = (function emit_level (depth, parent) {
const kids_24 = drawn_children(parent);
if (squint_core.truth_(squint_core.get(parent, "initial"))) {
ln_9(depth, `${"[*] --> "}${did(idx_7, squint_core.get(parent, "initial"))??''}`)};
for (let G__25 of squint_core.iterable(kids_24)) {
const c_26 = G__25;
ln_9(depth, `${"state \""}${mermaid_label(node_name_11(c_26))??''}${"\" as "}${squint_core.get(c_26, "diagram-id")??''}`);
const G__49_27 = ((squint_core.truth_(squint_core.seq(drawn_children(c_26)))) ? (squint_core.get(c_26, "kind")) : ("atomic"));
switch (G__49_27) {case "compound":
ln_9(depth, `${"state "}${squint_core.get(c_26, "diagram-id")??''}${" {"}`);
emit_level((depth + 1), c_26);
ln_9(depth, "}");

break;
case "parallel":
ln_9(depth, `${"state "}${squint_core.get(c_26, "diagram-id")??''}${" {"}`);
for (let G__29 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, drawn_children(c_26)))) {
const vec___30_33 = G__29;
const i_34 = squint_core.nth(vec___30_33, 0, null);
const region_35 = squint_core.nth(vec___30_33, 1, null);
if ((i_34 > 0)) {
ln_9((depth + 1), "--")};
if (squint_core.truth_(squint_core.seq(drawn_children(region_35)))) {
emit_level((depth + 1), region_35)} else {
ln_9((depth + 1), `${"state \""}${mermaid_label(node_name_11(region_35))??''}${"\" as "}${squint_core.get(region_35, "diagram-id")??''}`)}
};
ln_9(depth, "}");

break;
};
if (("final" === squint_core.get(c_26, "kind"))) {
ln_9(depth, `${squint_core.get(c_26, "diagram-id")??''}${" --> [*]"}`)}
};
return emit_transitions_14(depth, squint_core.get(parent, "uid"));

});
const hl_36 = squint_core.vec(squint_core.lazy((function* () {
for (let G__37 of squint_core.iterable(sm.highlighted(m_5, active))) {
const u_38 = G__37;
if (!squint_core._EQ_("", u_38)) {
yield did(idx_7, u_38);}
}
return null;

})));
out_8.push(`${"%%{init: "}${JSON.stringify(({"theme": "base", "themeVariables": theme}))??''}${"}%%"}`);
out_8.push("stateDiagram-v2");
ln_9(1, `${"classDef active stroke:"}${accent??''}${",stroke-width:4px"}`);
emit_level_23(1, root_6);
if (squint_core.truth_(squint_core.seq(hl_36))) {
ln_9(1, `${"class "}${hl_36.join(",")??''}${" active"}`)};
return out_8.join("\n");

};
var smcat_string = function (s) {
return JSON.stringify(s);

};
var initial_name = function (diagram_id) {
return `hc_initial_${diagram_id??''}`;

};
var smcat = function (p__50, active) {
const map___1_3 = p__50;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const m_5 = map___1_4;
const root_6 = squint_core.get(map___1_4, "root");
const idx_7 = squint_core.get(map___1_4, "idx");
const hl_8 = squint_core.into((new Set ([])), sm.highlighted(m_5, active));
const by_block_9 = squint_core.group_by("block", drawn_transitions(m_5));
const attrs_10 = (function (n) {
const kind_11 = ((squint_core.truth_(squint_core.seq(drawn_children(n)))) ? (squint_core.get(n, "kind")) : (((((("compound" === squint_core.get(n, "kind"))) || (("parallel" === squint_core.get(n, "kind"))))) ? ("atomic") : (squint_core.get(n, "kind")))));
const type_12 = (() => {
const G__51_13 = kind_11;
switch (G__51_13) {case "parallel":
return "parallel";

break;
case "final":
return "final";

break;
case "history":
if (("deep" === squint_core.get(n, "history"))) {
return "deephistory"} else {
return "history"};

break;
};

})();
return `${" ["}${squint_core.vec(squint_core.remove(squint_core.nil_QMARK_, [((squint_core.truth_(type_12)) ? (`${"type="}${type_12??''}`) : (null)), ((("history" === kind_11)) ? (null) : (`${"label="}${smcat_string(squint_core.get(n, "key"))??''}`)), ((squint_core.truth_(hl_8.has(squint_core.get(n, "uid")))) ? (`${"class="}${smcat_string(active_class)??''}`) : (null))])).join(" ")??''}${"]"}`;

});
const level_15 = (function level (parent) {
const kids_16 = drawn_children(parent);
const states_17 = squint_core.vec(squint_core.concat(((squint_core.truth_(squint_core.get(parent, "initial"))) ? ([`${initial_name(squint_core.get(parent, "diagram-id"))??''}${" [type=initial]"}`]) : (null)), squint_core.lazy((function* () {
for (let G__18 of squint_core.iterable(kids_16)) {
const c_19 = G__18;
yield `${squint_core.get(c_19, "diagram-id")??''}${attrs_10(c_19)??''}${((squint_core.truth_(squint_core.seq(drawn_children(c_19)))) ? (`${" {\n"}${level(c_19)??''}${"\n}"}`) : (null))??''}`;
}
return null;

}))));
const ts_20 = squint_core.vec(squint_core.concat(((squint_core.truth_(squint_core.get(parent, "initial"))) ? ([`${initial_name(squint_core.get(parent, "diagram-id"))??''}${" => "}${did(idx_7, squint_core.get(parent, "initial"))??''}${";"}`]) : (null)), squint_core.lazy((function* () {
for (let G__21 of squint_core.iterable(by_block_9[squint_core.get(parent, "uid")])) {
const map___22_24 = G__21;
const map___22_25 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___22_24))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___22_24))) ? (map___22_24) : (squint_core.seq_to_map_for_destructuring(map___22_24)))) : (map___22_24));
const src_26 = squint_core.get(map___22_25, "src");
const dst_27 = squint_core.get(map___22_25, "dst");
const label_28 = squint_core.get(map___22_25, "label");
yield `${did(idx_7, src_26)??''}${" => "}${did(idx_7, dst_27)??''}${((squint_core.truth_(squint_core.seq(label_28))) ? (`${": "}${label_28.replaceAll(";", ",")??''}`) : (null))??''}${";"}`;
}
return null;

}))));
return `${states_17.join(",\n")??''}${";"}${((squint_core.truth_(squint_core.seq(ts_20))) ? (`${"\n"}${ts_20.join("\n")??''}`) : (null))??''}`;

});
return level_15(root_6);

};

export { mermaid_entities, smcat_string, drawn_children, smcat, ancestors_of, block_of, active_class, did, mermaid_label, mermaid, drawn_transitions, initial_name, label_of, container }
