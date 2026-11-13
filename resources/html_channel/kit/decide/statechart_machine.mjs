import * as squint_core from '../squint-core.js';
var sep = "__";
var compare_ops = ({"eq": "==", "neq": "!=", "lt": "<", "lte": "<=", "gt": ">", "gte": ">="});
var after_event = function (ms) {
return `${"after "}${ms??''}ms`;

};
var internal_event_QMARK_ = function (e) {
const or_1_2 = e.startsWith("after ");
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return e.startsWith("hc.")};

};
var as_vec = function (x) {
if ((x == null)) {
return []} else {
if (squint_core.truth_(Array.isArray(x))) {
return x} else {
if ("else") {
return [x]} else {
return null}}};

};
var obj_QMARK_ = function (x) {
if (!(x == null)) {
if (("object" === (typeof x))) {
return squint_core.not(Array.isArray(x))} else {
return false}} else {
return false};

};
var entries = function (o) {
if (squint_core.truth_(obj_QMARK_(o))) {
return Object.entries(o)} else {
return []};

};
var transition_specs = function (raw) {
return squint_core.lazy((function* () {
for (let G__1 of squint_core.iterable(as_vec(raw))) {
const t_2 = G__1;
yield ((squint_core.string_QMARK_(t_2)) ? (({"target": t_2})) : (t_2));
}
return null;

}));

};
var assign_of = function (actions) {
return squint_core.some((function (a) {
if (squint_core.truth_(obj_QMARK_(a))) {
return squint_core.get(a, "assign");
};

}), as_vec(actions));

};
var code_refs = function (m) {
return squint_core.vec(as_vec(((squint_core.truth_(obj_QMARK_(m))) ? (squint_core.get(m, "code")) : (null))));

};
var read_transitions = function (d) {
const mk_1 = (function (kind, event, delay, spec) {
return ({"kind": kind, "event": event, "delay": delay, "target-strs": squint_core.vec(as_vec(squint_core.get(spec, "target"))), "guard": squint_core.get(spec, "guard"), "assign": assign_of(squint_core.get(spec, "actions")), "code": code_refs(squint_core.get(spec, "meta"))});

});
return squint_core.vec(squint_core.concat(squint_core.lazy((function* () {
for (let G__2 of squint_core.iterable(entries(squint_core.get(d, "on")))) {
const vec___3_6 = G__2;
const ev_7 = squint_core.nth(vec___3_6, 0, null);
const raw_8 = squint_core.nth(vec___3_6, 1, null);
for (let G__9 of squint_core.iterable(transition_specs(raw_8))) {
const spec_10 = G__9;
yield mk_1("on", ev_7, null, spec_10);
}
}
return null;

})), squint_core.lazy((function* () {
for (let G__11 of squint_core.iterable(transition_specs(squint_core.get(d, "always")))) {
const spec_12 = G__11;
yield mk_1("always", null, null, spec_12);
}
return null;

})), squint_core.lazy((function* () {
for (let G__13 of squint_core.iterable(entries(squint_core.get(d, "after")))) {
const vec___14_17 = G__13;
const ms_18 = squint_core.nth(vec___14_17, 0, null);
const raw_19 = squint_core.nth(vec___14_17, 1, null);
for (let G__20 of squint_core.iterable(transition_specs(raw_19))) {
const spec_21 = G__20;
yield mk_1("after", after_event(ms_18), Number(ms_18), spec_21);
}
}
return null;

}))));

};
var kind_of = function (d) {
const t_1 = squint_core.get(d, "type");
const kids_2 = squint_core.seq(entries(squint_core.get(d, "states")));
if ((t_1 === "parallel")) {
return "parallel"} else {
if ((t_1 === "final")) {
return "final"} else {
if ((t_1 === "history")) {
return "history"} else {
if (squint_core.truth_(kids_2)) {
return "compound"} else {
if ("else") {
return "atomic"} else {
return null}}}}};

};
var build = function (d, k, path, parent, region_QMARK_) {
const uid_1 = path.join(sep);
const kind_2 = kind_of(d);
const children_3 = squint_core.vec(squint_core.lazy((function* () {
for (let G__4 of squint_core.iterable(entries(squint_core.get(d, "states")))) {
const vec___5_8 = G__4;
const ck_9 = squint_core.nth(vec___5_8, 0, null);
const cd_10 = squint_core.nth(vec___5_8, 1, null);
yield build(cd_10, ck_9, squint_core.conj(path, ck_9), uid_1, (kind_2 === "parallel"));
}
return null;

})));
return ({"children": children_3, "raw-transitions": read_transitions(d), "initial-key": squint_core.get(d, "initial"), "key": k, "parent": parent, "uid": uid_1, "history": (((kind_2 === "history")) ? ((() => {
const or_11_12 = squint_core.get(d, "history");
if (squint_core.truth_(or_11_12)) {
return or_11_12} else {
return "shallow"};

})()) : (null)), "id": squint_core.get(d, "id"), "kind": kind_2, "code": code_refs(squint_core.get(d, "meta")), "region?": region_QMARK_});

};
var index = function (root) {
const acc_1 = ({});
const n_seen_2 = squint_core.atom(0);
(function walk (n) {
(n["diagram-id"] = `s${squint_core.deref(n_seen_2)??''}`);
squint_core.swap_BANG_(n_seen_2, squint_core.inc);
(acc_1[squint_core.get(n, "uid")] = n);
for (let G__3 of squint_core.iterable(squint_core.get(n, "children"))) {
const c_4 = G__3;
walk(c_4)
}
return null;

})(root);
return acc_1;

};
var uid_of_diagram_id = function (p__52, diagram_id) {
const map___1_3 = p__52;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const idx_5 = squint_core.get(map___1_4, "idx");
return squint_core.some((function (_PERCENT_1) {
if (squint_core._EQ_(diagram_id, squint_core.get(_PERCENT_1, "diagram-id"))) {
return squint_core.get(_PERCENT_1, "uid");
};

}), Object.values(idx_5));

};
var path_of = function (uid) {
if (squint_core._EQ_(uid, "")) {
return []} else {
return uid.split(sep)};

};
var resolve_target = function (idx, ids, machine_id, source, t) {
const found_1 = (function (path) {
const uid_2 = path.join(sep);
if (squint_core.truth_(idx[uid_2])) {
return uid_2;
};

});
const base_3 = (function (n) {
if (squint_core._EQ_(squint_core.get(n, "uid"), "")) {
return []} else {
return path_of(squint_core.get(n, "parent"))};

});
if (squint_core.truth_(t.startsWith("#"))) {
const r_4 = t.slice(1);
const or_5_6 = ids[r_4];
if (squint_core.truth_(or_5_6)) {
return or_5_6} else {
const or_7_8 = ((squint_core._EQ_(r_4, machine_id)) ? ("") : (null));
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
if (squint_core.truth_(r_4.startsWith(`${machine_id??''}${"."}`))) {
return found_1(r_4.slice((squint_core.count(machine_id) + 1)).split("."));
}};
};
} else {
if (squint_core.truth_(t.startsWith("."))) {
return found_1(squint_core.vec(squint_core.concat(path_of(squint_core.get(source, "uid")), t.slice(1).split("."))))} else {
if ("else") {
return found_1(squint_core.vec(squint_core.concat(base_3(source), t.split("."))))} else {
return null}}};

};
var guard_problem = function (g, context) {
if (squint_core.not(obj_QMARK_(g))) {
return `${"a guard must be an object like {\"lt\": [\"retries\", 3]}, got "}${JSON.stringify(g)??''}`} else {
if (!(1 === squint_core.count(Object.keys(g)))) {
return `${"a guard has exactly one operator, got "}${JSON.stringify(g)??''}`} else {
if ("else") {
const vec___1_4 = squint_core.first(Object.entries(g));
const op_5 = squint_core.nth(vec___1_4, 0, null);
const arg_6 = squint_core.nth(vec___1_4, 1, null);
if (squint_core.truth_(squint_core.get(compare_ops, op_5))) {
if (squint_core.not((() => {
const and_7_8 = Array.isArray(arg_6);
if (squint_core.truth_(and_7_8)) {
if ((2 === squint_core.count(arg_6))) {
return squint_core.string_QMARK_(squint_core.first(arg_6))} else {
return false}} else {
return and_7_8};

})())) {
return `${"\""}${op_5??''}${"\" takes [contextKey, value], got "}${JSON.stringify(arg_6)??''}`} else {
if (squint_core.not(Object.hasOwn(context, squint_core.first(arg_6)))) {
return `${"guard names context key \""}${squint_core.first(arg_6)??''}${"\", which the definition's context doesn't have"}`} else {
return null}}} else {
if ((((op_5 === "and")) || ((op_5 === "or")))) {
if (squint_core.truth_(Array.isArray(arg_6))) {
return squint_core.some((function (_PERCENT_1) {
return guard_problem(_PERCENT_1, context);

}), arg_6)} else {
return `${"\""}${op_5??''}${"\" takes a list of guards"}`}} else {
if ((op_5 === "not")) {
return guard_problem(arg_6, context)} else {
if ("else") {
return `${"unknown guard operator \""}${op_5??''}${"\""}`} else {
return null}}}};
} else {
return null}}};

};
var guard_fn = function (g) {
const vec___1_4 = squint_core.first(Object.entries(g));
const op_5 = squint_core.nth(vec___1_4, 0, null);
const arg_6 = squint_core.nth(vec___1_4, 1, null);
const G__53_7 = op_5;
switch (G__53_7) {case "and":
const fs_9 = squint_core.mapv(guard_fn, arg_6);
return function (c) {
return squint_core.every_QMARK_((function (_PERCENT_1) {
return _PERCENT_1(c);

}), fs_9);

};

break;
case "or":
const fs_10 = squint_core.mapv(guard_fn, arg_6);
return function (c) {
return squint_core.boolean$(squint_core.some((function (_PERCENT_1) {
return _PERCENT_1(c);

}), fs_10));

};

break;
case "not":
const f_11 = guard_fn(arg_6);
return function (c) {
return squint_core.not(f_11(c));

};

break;
default:
const vec___12_15 = arg_6;
const k_16 = squint_core.nth(vec___12_15, 0, null);
const v_17 = squint_core.nth(vec___12_15, 1, null);
const G__54_18 = op_5;
switch (G__54_18) {case "eq":
return function (c) {
return squint_core._EQ_(c[k_16], v_17);

};

break;
case "neq":
return function (c) {
return !squint_core._EQ_(c[k_16], v_17);

};

break;
case "lt":
return function (c) {
return (c[k_16] < v_17);

};

break;
case "lte":
return function (c) {
return (c[k_16] <= v_17);

};

break;
case "gt":
return function (c) {
return (c[k_16] > v_17);

};

break;
case "gte":
return function (c) {
return (c[k_16] >= v_17);

};

break;
default:
throw (new Error(`${"No matching clause: "}${G__54_18??''}`))};
};

};
var guard_text = function (g) {
const vec___1_4 = squint_core.first(Object.entries(g));
const op_5 = squint_core.nth(vec___1_4, 0, null);
const arg_6 = squint_core.nth(vec___1_4, 1, null);
const G__55_7 = op_5;
switch (G__55_7) {case "and":
return squint_core.mapv(guard_text, arg_6).join(" && ");

break;
case "or":
return `${"("}${squint_core.mapv(guard_text, arg_6).join(" || ")??''}${")"}`;

break;
case "not":
return `${"!("}${guard_text(arg_6)??''}${")"}`;

break;
default:
return `${squint_core.first(arg_6)??''}${" "}${squint_core.get(compare_ops, op_5)??''}${" "}${JSON.stringify(squint_core.second(arg_6))??''}`};

};
var assign_problem = function (a, context) {
if (squint_core.not(obj_QMARK_(a))) {
return `${"assign must be an object like {\"retries\": {\"add\": 1}}, got "}${JSON.stringify(a)??''}`} else {
return squint_core.some((function (p__56) {
const vec___1_4 = p__56;
const k_5 = squint_core.nth(vec___1_4, 0, null);
const op_6 = squint_core.nth(vec___1_4, 1, null);
if (squint_core.not(Object.hasOwn(context, k_5))) {
return `${"assign names context key \""}${k_5??''}${"\", which the definition's context doesn't have"}`} else {
if (squint_core.not((() => {
const and_7_8 = obj_QMARK_(op_6);
if (squint_core.truth_(and_7_8)) {
if ((1 === squint_core.count(Object.keys(op_6)))) {
const or_9_10 = Object.hasOwn(op_6, "set");
if (squint_core.truth_(or_9_10)) {
return or_9_10} else {
return Object.hasOwn(op_6, "add")};
} else {
return false}} else {
return and_7_8};

})())) {
return `${"assign of \""}${k_5??''}${"\" takes {\"set\": value} or {\"add\": number}, got "}${JSON.stringify(op_6)??''}`} else {
return null}};

}), Object.entries(a))};

};
var assign_patch = function (a, context) {
const patch_1 = ({});
for (let G__2 of squint_core.iterable(Object.entries(a))) {
const vec___3_6 = G__2;
const k_7 = squint_core.nth(vec___3_6, 0, null);
const op_8 = squint_core.nth(vec___3_6, 1, null);
(patch_1[k_7] = ((squint_core.truth_(Object.hasOwn(op_8, "set"))) ? (squint_core.get(op_8, "set")) : ((context[k_7] + squint_core.get(op_8, "add")))))
};
return patch_1;

};
var collapse_BANG_ = function (root, idx, names) {
const by_key_1 = squint_core.reduce((function (m, n) {
return squint_core.update(m, squint_core.get(n, "key"), squint_core.fnil(squint_core.conj, []), n);

}), ({}), Object.values(idx));
const findings_2 = [];
for (let G__3 of squint_core.iterable(names)) {
const nm_4 = G__3;
const n_5 = (() => {
const or_6_7 = idx[nm_4.replaceAll(".", sep)];
if (squint_core.truth_(or_6_7)) {
return or_6_7} else {
const ns_8 = squint_core.get(by_key_1, nm_4);
if ((1 === squint_core.count(ns_8))) {
return squint_core.first(ns_8);
};
};

})();
if ((n_5 == null)) {
findings_2.push(["statechart-machine", `${"data-collapse names \""}${nm_4??''}${"\", which isn't a state"}`])} else {
if (squint_core.empty_QMARK_(squint_core.get(n_5, "children"))) {
findings_2.push(["statechart-machine", `${"data-collapse names \""}${nm_4??''}${"\", which has no inner states"}`])} else {
if ("else") {
(n_5["collapsed"] = true)} else {
}}}
};
return findings_2;

};
var under_collapsed = function (idx, uid) {
let u_1 = uid;
let found_2 = null;
while(true){
if ((u_1 == null)) {
return found_2} else {
const n_3 = idx[u_1];
let G__4 = squint_core.get(n_3, "parent");
let G__5 = ((squint_core.truth_(((squint_core.truth_(n_3)) ? ((() => {
const and_6_7 = squint_core.get(n_3, "collapsed");
if (squint_core.truth_(and_6_7)) {
return !squint_core._EQ_(u_1, uid)} else {
return and_6_7};

})()) : (n_3)))) ? (u_1) : (found_2));
u_1 = G__4;
found_2 = G__5;
continue;
};
;break;
}
;

};
var model = function (d, collapse) {
if (squint_core.not((() => {
const and_1_2 = obj_QMARK_(d);
if (squint_core.truth_(and_1_2)) {
const and_3_4 = obj_QMARK_(squint_core.get(d, "states"));
if (squint_core.truth_(and_3_4)) {
return squint_core.string_QMARK_(squint_core.get(d, "initial"))} else {
return and_3_4};
} else {
return and_1_2};

})())) {
return ({"findings": [["statechart-json", "the definition needs \"initial\" (a string) and \"states\" (an object)"]], "fatal": true})} else {
const context_5 = ((squint_core.truth_(obj_QMARK_(squint_core.get(d, "context")))) ? (squint_core.get(d, "context")) : (({})));
const machine_id_6 = (() => {
const or_7_8 = squint_core.get(d, "id");
if (squint_core.truth_(or_7_8)) {
return or_7_8} else {
return "machine"};

})();
const root_9 = build(d, machine_id_6, [], null, false);
const root_10 = squint_core.assoc(root_9, "uid", "");
const idx_11 = index(root_10);
const ids_12 = squint_core.reduce((function (m, n) {
const temp__23255__auto___13 = squint_core.get(n, "id");
if (squint_core.truth_(temp__23255__auto___13)) {
const i_14 = temp__23255__auto___13;
return squint_core.assoc(m, i_14, squint_core.get(n, "uid"));
} else {
return m};

}), ({}), Object.values(idx_11));
const findings_15 = [];
const report_BANG__16 = (function (rule, msg) {
return findings_15.push([rule, msg]);

});
const label_17 = (function (n) {
if (squint_core._EQ_("", squint_core.get(n, "uid"))) {
return "the machine"} else {
return `${"state \""}${path_of(squint_core.get(n, "uid")).join(".")??''}${"\""}`};

});
for (let G__18 of squint_core.iterable(Object.values(idx_11))) {
const n_19 = G__18;
if (("compound" === squint_core.get(n_19, "kind"))) {
const ik_20 = squint_core.get(n_19, "initial-key");
const child_21 = squint_core.some((function (_PERCENT_1) {
if (squint_core._EQ_(ik_20, squint_core.get(_PERCENT_1, "key"))) {
return _PERCENT_1;
};

}), squint_core.get(n_19, "children"));
if ((ik_20 == null)) {
report_BANG__16("statechart-json", `${label_17(n_19)??''}${" has inner states but no \"initial\""}`)} else {
if ((child_21 == null)) {
report_BANG__16("statechart-target", `${label_17(n_19)??''}${" has initial \""}${ik_20??''}${"\", which isn't one of its states"}`)} else {
if ("else") {
(n_19["initial"] = squint_core.get(child_21, "uid"))} else {
}}}}
};
for (let G__22 of squint_core.iterable(Object.values(idx_11))) {
const n_23 = G__22;
(n_23["transitions"] = squint_core.vec(squint_core.lazy((function* () {
for (let G__24 of squint_core.iterable(squint_core.get(n_23, "raw-transitions"))) {
const t_25 = G__24;
yield (yield* (function* () {
const targets_26 = squint_core.vec(squint_core.lazy((function* () {
for (let G__27 of squint_core.iterable(squint_core.get(t_25, "target-strs"))) {
const s_28 = G__27;
const u_29 = resolve_target(idx_11, ids_12, machine_id_6, n_23, s_28);
yield (yield* (function* () {
if ((u_29 == null)) {
report_BANG__16("statechart-target", `${label_17(n_23)??''}${", "}${(yield* (function* () {
const or_30_31 = squint_core.get(t_25, "event");
if (squint_core.truth_(or_30_31)) {
return or_30_31} else {
return "always"};

})())??''}${": target \""}${s_28??''}${"\" isn't a state"}`)};
return u_29
})());
}
return null;

})));
const gp_32 = ((!(squint_core.get(t_25, "guard") == null)) ? (guard_problem(squint_core.get(t_25, "guard"), context_5)) : (null));
const ap_33 = ((!(squint_core.get(t_25, "assign") == null)) ? (assign_problem(squint_core.get(t_25, "assign"), context_5)) : (null));
const dp_34 = ((((("after" === squint_core.get(t_25, "kind"))) && (squint_core.not((yield* (function* () {
const and_35_36 = Number.isFinite(squint_core.get(t_25, "delay"));
if (squint_core.truth_(and_35_36)) {
return (squint_core.get(t_25, "delay") >= 0)} else {
return and_35_36};

})()))))) ? (`${"after: delay \""}${squint_core.get(t_25, "event").replace(/^after (.*)ms$/, "$1")??''}${"\" isn't a number of milliseconds"}`) : (null));
if (squint_core.truth_(dp_34)) {
report_BANG__16("statechart-json", `${label_17(n_23)??''}${", "}${dp_34??''}`)};
if (squint_core.truth_(gp_32)) {
report_BANG__16("statechart-guard", `${label_17(n_23)??''}${", "}${(yield* (function* () {
const or_37_38 = squint_core.get(t_25, "event");
if (squint_core.truth_(or_37_38)) {
return or_37_38} else {
return "always"};

})())??''}${": "}${gp_32??''}`)};
if (squint_core.truth_(ap_33)) {
report_BANG__16("statechart-guard", `${label_17(n_23)??''}${", "}${(yield* (function* () {
const or_39_40 = squint_core.get(t_25, "event");
if (squint_core.truth_(or_39_40)) {
return or_39_40} else {
return "always"};

})())??''}${": "}${ap_33??''}`)};
return squint_core.assoc(t_25, "targets", squint_core.vec(squint_core.remove(squint_core.nil_QMARK_, targets_26)), "bad", squint_core.boolean$(((squint_core.truth_(gp_32)) ? (gp_32) : (((squint_core.truth_(ap_33)) ? (ap_33) : (((squint_core.truth_(dp_34)) ? (dp_34) : (squint_core.some(squint_core.nil_QMARK_, targets_26)))))))));

})());
}
return null;

}))))
};
const cf_41 = collapse_BANG_(root_10, idx_11, ((squint_core.truth_(collapse)) ? (collapse) : ([])));
for (let G__42 of squint_core.iterable(cf_41)) {
const f_43 = G__42;
findings_15.push(f_43)
};
return ({"root": root_10, "idx": idx_11, "context": context_5, "machine-id": machine_id_6, "findings": squint_core.vec(findings_15), "fatal": squint_core.boolean$(squint_core.some((function (_PERCENT_1) {
if (("compound" === squint_core.get(_PERCENT_1, "kind"))) {
return (squint_core.get(_PERCENT_1, "initial") == null)} else {
return false};

}), Object.values(idx_11)))});
};

};
var entered = function (idx, uid) {
const acc_1 = [];
const down_2 = (function down (u) {
const n_3 = idx[u];
acc_1.push(u);
const G__57_4 = squint_core.get(n_3, "kind");
switch (G__57_4) {case "compound":
if (squint_core.truth_(squint_core.get(n_3, "initial"))) {
return down(squint_core.get(n_3, "initial"));
};

break;
case "parallel":
for (let G__6 of squint_core.iterable(squint_core.get(n_3, "children"))) {
const c_7 = G__6;
down(squint_core.get(c_7, "uid"))
}
return null;

break;
case "history":
for (let G__8 of squint_core.iterable(squint_core.get(idx[squint_core.get(n_3, "parent")], "children"))) {
const c_9 = G__8;
if (!squint_core._EQ_(squint_core.get(c_9, "uid"), u)) {
down(squint_core.get(c_9, "uid"))}
}
return null;

break;
};

});
let p_10 = squint_core.get(idx[uid], "parent");
while(true){
if (squint_core.truth_(((squint_core.truth_(p_10)) ? (!squint_core._EQ_(p_10, "")) : (p_10)))) {
acc_1.push(p_10);
let G__11 = squint_core.get(idx[p_10], "parent");
p_10 = G__11;
continue;
};break;
}
;
down_2(uid);
return acc_1;

};
var unreachable = function (p__58) {
const map___1_3 = p__58;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const root_5 = squint_core.get(map___1_4, "root");
const idx_6 = squint_core.get(map___1_4, "idx");
const seen_7 = (new Set());
const queue_8 = [];
seen_7.add("");
for (let G__9 of squint_core.iterable(entered(idx_6, squint_core.get(root_5, "initial")))) {
const u_10 = G__9;
queue_8.push(u_10)
};
while(true){
const temp__23314__auto___11 = queue_8.shift();
if (squint_core.truth_(temp__23314__auto___11)) {
const u_12 = temp__23314__auto___11;
if (squint_core.truth_(seen_7.has(u_12))) {
} else {
seen_7.add(u_12);
for (let G__13 of squint_core.iterable(squint_core.get(idx_6[u_12], "transitions"))) {
const t_14 = G__13;
for (let G__15 of squint_core.iterable(squint_core.get(t_14, "targets"))) {
const target_16 = G__15;
for (let G__17 of squint_core.iterable(entered(idx_6, target_16))) {
const e_18 = G__17;
queue_8.push(e_18)
}
}
}};
continue;
};break;
}
;
for (let G__19 of squint_core.iterable(squint_core.get(root_5, "transitions"))) {
const t_20 = G__19;
for (let G__21 of squint_core.iterable(squint_core.get(t_20, "targets"))) {
const target_22 = G__21;
for (let G__23 of squint_core.iterable(entered(idx_6, target_22))) {
const e_24 = G__23;
if (squint_core.truth_(seen_7.has(e_24))) {
} else {
seen_7.add(e_24)}
}
}
};
return squint_core.vec(squint_core.lazy((function* () {
for (let G__25 of squint_core.iterable(Object.values(idx_6))) {
const n_26 = G__25;
if (((!squint_core._EQ_("", squint_core.get(n_26, "uid"))) && (((squint_core.not(seen_7.has(squint_core.get(n_26, "uid")))) && (squint_core.not(under_collapsed(idx_6, squint_core.get(n_26, "uid")))))))) {
yield squint_core.get(n_26, "uid");}
}
return null;

})));

};
var drawn_target = function (idx, uid) {
const or_1_2 = under_collapsed(idx, uid);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return uid};

};
var engine_config = function (p__59, assign) {
const map___1_3 = p__59;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const root_5 = squint_core.get(map___1_4, "root");
const idx_6 = squint_core.get(map___1_4, "idx");
const context_7 = squint_core.get(map___1_4, "context");
const transition_8 = (function (t) {
const G__60_9 = ({});
const G__60_10 = ((squint_core.truth_(squint_core.seq(squint_core.get(t, "targets")))) ? (({...G__60_9,["target"]:squint_core.vec(squint_core.distinct(squint_core.lazy((function* () {
for (let G__11 of squint_core.iterable(squint_core.get(t, "targets"))) {
const u_12 = G__11;
yield `${"#"}${drawn_target(idx_6, u_12)??''}`;
}
return null;

}))))})) : (G__60_9));
const G__60_13 = ((squint_core.truth_(squint_core.get(t, "guard"))) ? (squint_core.assoc(G__60_10, "guard", (() => {
const f_14 = guard_fn(squint_core.get(t, "guard"));
return function (args) {
return f_14(args.context);

};

})())) : (G__60_10));
if (squint_core.truth_(squint_core.get(t, "assign"))) {
return squint_core.assoc(G__60_13, "actions", [assign((() => {
const a_15 = squint_core.get(t, "assign");
return function (args) {
return assign_patch(a_15, args.context);

};

})())])} else {
return G__60_13};

});
const node_16 = (function node (n) {
const ts_17 = squint_core.remove("bad", squint_core.get(n, "transitions"));
const on_18 = squint_core.reduce((function (m, t) {
if (("always" === squint_core.get(t, "kind"))) {
return m} else {
return squint_core.update(m, squint_core.get(t, "event"), squint_core.fnil(squint_core.conj, []), transition_8(t))};

}), ({}), ts_17);
const always_19 = squint_core.vec(squint_core.lazy((function* () {
for (let G__20 of squint_core.iterable(ts_17)) {
const t_21 = G__20;
if (("always" === squint_core.get(t_21, "kind"))) {
yield transition_8(t_21);}
}
return null;

})));
const kids_22 = ((squint_core.truth_(squint_core.get(n, "collapsed"))) ? ([]) : (squint_core.get(n, "children")));
const G__61_23 = ({"id": ((squint_core._EQ_("", squint_core.get(n, "uid"))) ? ("hc-root") : (squint_core.get(n, "uid")))});
const G__61_24 = ((squint_core.truth_(squint_core.seq(Object.keys(on_18)))) ? (({...G__61_23,["on"]:on_18})) : (G__61_23));
const G__61_25 = ((squint_core.truth_(squint_core.seq(always_19))) ? (squint_core.assoc(G__61_24, "always", always_19)) : (G__61_24));
const G__61_26 = ((((("compound" === squint_core.get(n, "kind"))) && (squint_core.not(squint_core.get(n, "collapsed"))))) ? (squint_core.assoc(G__61_25, "initial", squint_core.get(idx_6[squint_core.get(n, "initial")], "key"))) : (G__61_25));
const G__61_27 = ((((("parallel" === squint_core.get(n, "kind"))) && (squint_core.not(squint_core.get(n, "collapsed"))))) ? (squint_core.assoc(G__61_26, "type", "parallel")) : (G__61_26));
const G__61_28 = ((("final" === squint_core.get(n, "kind"))) ? (squint_core.assoc(G__61_27, "type", "final")) : (G__61_27));
const G__61_29 = ((("history" === squint_core.get(n, "kind"))) ? (squint_core.assoc(G__61_28, "type", "history", "history", squint_core.get(n, "history"))) : (G__61_28));
if (squint_core.truth_(squint_core.seq(kids_22))) {
return squint_core.assoc(G__61_29, "states", squint_core.reduce((function (m, c) {
return squint_core.assoc(m, squint_core.get(c, "key"), node(c));

}), ({}), kids_22))} else {
return G__61_29};

});
const cfg_30 = node_16(root_5);
return squint_core.assoc(cfg_30, "context", context_7, "on", squint_core.assoc((() => {
const or_31_32 = squint_core.get(cfg_30, "on");
if (squint_core.truth_(or_31_32)) {
return or_31_32} else {
return ({})};

})(), "hc.context", ({"actions": [assign((function (args) {
return args.event.patch;

}))]})));

};
var active_uids = function (p__62, v) {
const map___1_3 = p__62;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const root_5 = squint_core.get(map___1_4, "root");
const idx_6 = squint_core.get(map___1_4, "idx");
const acc_7 = [];
const walk_8 = (function walk (parent_uid, v) {
const child_9 = (function (k) {
if (squint_core._EQ_("", parent_uid)) {
return k} else {
return `${parent_uid??''}${sep??''}${k??''}`};

});
if (squint_core.string_QMARK_(v)) {
if (squint_core.truth_(idx_6[child_9(v)])) {
return acc_7.push(child_9(v));
}} else {
for (let G__10 of squint_core.iterable(Object.entries(v))) {
const vec___11_14 = G__10;
const k_15 = squint_core.nth(vec___11_14, 0, null);
const sub_16 = squint_core.nth(vec___11_14, 1, null);
acc_7.push(child_9(k_15));
walk(child_9(k_15), sub_16)
}
return null};

});
walk_8("", v);
return acc_7;

};
var highlighted = function (p__63, active) {
const map___1_3 = p__63;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const idx_5 = squint_core.get(map___1_4, "idx");
return squint_core.vec(squint_core.remove((function (_PERCENT_1) {
return squint_core.get(idx_5[_PERCENT_1], "region?");

}), active));

};
var events_of = function (p__64, active) {
const map___1_3 = p__64;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const root_5 = squint_core.get(map___1_4, "root");
const idx_6 = squint_core.get(map___1_4, "idx");
const seen_7 = (new Set());
const out_8 = [];
for (let G__9 of squint_core.iterable(squint_core.concat([root_5], squint_core.map((function (_PERCENT_1) {
return idx_6[_PERCENT_1];

}), active)))) {
const n_10 = G__9;
for (let G__11 of squint_core.iterable(squint_core.get(n_10, "transitions"))) {
const t_12 = G__11;
const e_13 = squint_core.get(t_12, "event");
if (squint_core.truth_(((squint_core.truth_(e_13)) ? (((("on" === squint_core.get(t_12, "kind"))) && (((squint_core.not(squint_core.get(t_12, "bad"))) && (((squint_core.not(seen_7.has(e_13))) && (squint_core.not(internal_event_QMARK_(e_13))))))))) : (e_13)))) {
seen_7.add(e_13);
out_8.push(e_13)}
}
};
return out_8;

};
var delays_of = function (p__65, active) {
const map___1_3 = p__65;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const idx_5 = squint_core.get(map___1_4, "idx");
return squint_core.vec(squint_core.lazy((function* () {
for (let G__6 of squint_core.iterable(active)) {
const u_7 = G__6;
for (let G__8 of squint_core.iterable(squint_core.get(idx_5[u_7], "transitions"))) {
const t_9 = G__8;
if (((("after" === squint_core.get(t_9, "kind"))) && (squint_core.not(squint_core.get(t_9, "bad"))))) {
yield ({"event": squint_core.get(t_9, "event"), "delay": squint_core.get(t_9, "delay")});}
}
}
return null;

})));

};
var has_history_QMARK_ = function (p__66) {
const map___1_3 = p__66;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const idx_5 = squint_core.get(map___1_4, "idx");
return squint_core.boolean$(squint_core.some((function (_PERCENT_1) {
if (("history" === squint_core.get(_PERCENT_1, "kind"))) {
return squint_core.not(under_collapsed(idx_5, squint_core.get(_PERCENT_1, "uid")))} else {
return false};

}), Object.values(idx_5)));

};

export { as_vec, compare_ops, assign_patch, internal_event_QMARK_, model, index, sep, events_of, under_collapsed, assign_problem, code_refs, uid_of_diagram_id, highlighted, obj_QMARK_, path_of, drawn_target, guard_fn, delays_of, after_event, read_transitions, unreachable, guard_text, transition_specs, kind_of, build, collapse_BANG_, entries, active_uids, engine_config, resolve_target, assign_of, entered, guard_problem, has_history_QMARK_ }
