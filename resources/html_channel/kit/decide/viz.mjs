import * as squint_core from '../squint-core.js';
var declared_libs = function () {
const G__8_1 = document.querySelector("meta[name=hc-libs]");
const G__8_2 = (((G__8_1 == null)) ? (null) : (G__8_1.getAttribute("content")));
const G__8_3 = (((G__8_2 == null)) ? (null) : (G__8_2.split(/[\s,]+/)));
if ((G__8_3 == null)) {
return null} else {
return G__8_3.filter((function (_PERCENT_1) {
return (_PERCENT_1.length > 0);

}))};

};
var adapter_url = function (lib) {
return (new URL(`${"../viz/"}${lib.replaceAll("-", "_")??''}${".mjs"}`, import.meta.url)).href;

};
var notice_BANG_ = function (pre, text) {
const n_1 = document.createElement("div");
n_1.className = "hc-viz-error";
n_1.textContent = text;
return pre.after(n_1);

};
var block_failed_BANG_ = function (report_BANG_, lib, pre, i, message) {
const text_1 = `${lib??''}${" block "}${i??''}${": "}${message??''}`;
notice_BANG_(pre, text_1);
return report_BANG_("viz", text_1, `${lib??''}${"#"}${i??''}`);

};
var start_BANG_ = async function (report_BANG_) {
const declared_1 = (await (async () => {
const or_2_3 = declared_libs();
if (squint_core.truth_(or_2_3)) {
return or_2_3} else {
return []};

})());
const blocks_4 = Array.from(document.querySelectorAll("pre[data-viz]"));
const by_lib_5 = squint_core.group_by((function (_PERCENT_1) {
return _PERCENT_1.dataset.viz;

}), blocks_4);
for (let G__6 of squint_core.iterable(by_lib_5)) {
const vec___7_10 = G__6;
const lib_11 = squint_core.nth(vec___7_10, 0, null);
const bs_12 = squint_core.nth(vec___7_10, 1, null);
if (squint_core.not(declared_1.includes(lib_11))) {
report_BANG_("undeclared", `${squint_core.count(bs_12)??''}${" <pre data-viz=\""}${lib_11??''}${"\"> block(s), but \""}${lib_11??''}${"\" isn't listed in <meta name=\"hc-libs\">"}`, lib_11)}
};
for (let G__13 of squint_core.iterable(declared_1)) {
const lib_14 = G__13;
const bs_15 = by_lib_5[lib_14];
if (squint_core.truth_(squint_core.seq(bs_15))) {
try{
const adapter_16 = (await import(adapter_url(lib_14)));
(await adapter_16.render_BANG_(bs_15, (function (pre, i, message) {
return block_failed_BANG_(report_BANG_, lib_14, pre, i, message);

})))}
catch(e_17){
const why_18 = `${lib_14??''}${" didn't load, so its blocks show as source: "}${e_17.message??''}`;
for (let G__19 of squint_core.iterable(bs_15)) {
const pre_20 = G__19;
notice_BANG_(pre_20, why_18)
};
report_BANG_("viz", `${squint_core.count(bs_15)??''}${" "}${why_18}`, lib_14)}
}
}
return null;

};

export { declared_libs, adapter_url, notice_BANG_, block_failed_BANG_, start_BANG_ }
