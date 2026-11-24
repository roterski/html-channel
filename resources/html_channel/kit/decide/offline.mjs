import * as squint_core from '../squint-core.js';
var share_meta = function () {
const temp__23314__auto___1 = document.querySelector("meta[name=hc-offline]");
if (squint_core.truth_(temp__23314__auto___1)) {
const m_2 = temp__23314__auto___1;
const fields_3 = squint_core.reduce((function (acc, part) {
const i_4 = part.indexOf("=");
if ((i_4 > 0)) {
return squint_core.assoc(acc, part.slice(0, i_4).trim(), part.slice((i_4 + 1)).trim())} else {
return acc};

}), ({}), (() => {
const or_5_6 = m_2.getAttribute("content");
if (squint_core.truth_(or_5_6)) {
return or_5_6} else {
return ""};

})().split(";"));
if (("share" === squint_core.get(fields_3, "mode"))) {
return ({"session": squint_core.get(fields_3, "session"), "page": squint_core.get(fields_3, "page"), "rev": parseInt(squint_core.get(fields_3, "rev"), 10)});
};
};

};
var storage_key = function (p__69) {
const map___1_3 = p__69;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const session_5 = squint_core.get(map___1_4, "session");
const page_6 = squint_core.get(map___1_4, "page");
const rev_7 = squint_core.get(map___1_4, "rev");
return `${"html-channel:"}${session_5??''}${"/"}${page_6??''}${"@"}${rev_7??''}`;

};
var stored = function (k) {
const or_3_4 = (() => {
try{
const G__70_1 = localStorage.getItem(k);
if ((G__70_1 == null)) {
return null} else {
return JSON.parse(G__70_1)};
}
catch(__2){
return null;
}

})();
if (squint_core.truth_(or_3_4)) {
return or_3_4} else {
return ({})};

};
var transport = function (p__71) {
const map___1_3 = p__71;
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const copy_5 = map___1_4;
const session_6 = squint_core.get(map___1_4, "session");
const page_7 = squint_core.get(map___1_4, "page");
const rev_8 = squint_core.get(map___1_4, "rev");
const k_9 = storage_key(copy_5);
return ({"page": page_7, "session": session_6, "mode": "share", "send!": (function (entry) {
return Promise.resolve((() => {
try{
const m_10 = stored(k_9);
(m_10[squint_core.get(entry, "id")] = ({"value": squint_core.get(entry, "value"), "rev": rev_8, "at": squint_core.get(entry, "at")}));
localStorage.setItem(k_9, JSON.stringify(m_10));
return ({"outcome": "saved"});
}
catch(e_11){
return ({"outcome": "rejected", "reason": `${"localStorage refused it ("}${e_11.message??''}${"); Download answers still has it"}`});
}

})());

}), "load": (function () {
return Promise.resolve(({"rev": rev_8, "done": false, "deeper": [], "decisions": stored(k_9)}));

}), "rev": (function () {
return Promise.resolve(rev_8);

})});

};

export { share_meta, storage_key, stored, transport }
