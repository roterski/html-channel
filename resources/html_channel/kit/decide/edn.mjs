import * as squint_core from '../squint-core.js';
var kw_name = Symbol("edn-keyword");
var kw = function (n) {
const o_1 = ({});
(o_1[kw_name] = n);
return o_1;

};
var write = function (v) {
if ((v == null)) {
return "nil"} else {
if (squint_core.string_QMARK_(v)) {
return JSON.stringify(v)} else {
if (squint_core.boolean_QMARK_(v)) {
return `${v??''}`} else {
if (squint_core.number_QMARK_(v)) {
if (squint_core.truth_(Number.isFinite(v))) {
return `${v??''}`} else {
return "nil"}} else {
if (!(v[kw_name] == null)) {
return `${":"}${v[kw_name]??''}`} else {
if (squint_core.array_QMARK_(v)) {
return `${"["}${v.map(write).join(" ")??''}${"]"}`} else {
if ("else") {
return `${"{"}${Object.entries(v).map((function (p__43) {
const vec___1_4 = p__43;
const k_5 = squint_core.nth(vec___1_4, 0, null);
const x_6 = squint_core.nth(vec___1_4, 1, null);
return `${JSON.stringify(k_5)??''}${" "}${write(x_6)??''}`;

})).join(", ")??''}${"}"}`} else {
return null}}}}}}};

};
var write_fields = function (fields) {
return `${"{"}${fields.map((function (p__44) {
const vec___1_4 = p__44;
const k_5 = squint_core.nth(vec___1_4, 0, null);
const v_6 = squint_core.nth(vec___1_4, 1, null);
return `${":"}${k_5??''}${" "}${write(v_6)??''}`;

})).join("\n ")??''}${"}"}`;

};

export { kw_name, kw, write, write_fields }
