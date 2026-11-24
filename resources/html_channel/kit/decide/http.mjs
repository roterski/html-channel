import * as squint_core from '../squint-core.js';
var page_context = function () {
const parts_1 = location.pathname.split("/");
const file_2 = parts_1.pop();
return ({"base": `${(new URL(import.meta.url)).origin??''}${parts_1.join("/")??''}`, "session": parts_1[(parts_1.length - 1)], "page": file_2.replace(/\.html?$/, "")});

};
var get_json = async function (url) {
try{
const r_1 = (await fetch(url, ({"cache": "no-store"})));
if (squint_core.truth_(r_1.ok)) {
return (await r_1.json());
};
}
catch(__2){
return null;
}
;

};
var post_entry = async function (url, entry) {
try{
const r_1 = (await fetch(url, ({"method": "POST", "body": JSON.stringify(entry)})));
const status_2 = r_1.status;
if (squint_core.truth_(r_1.ok)) {
return ({"outcome": "saved"})} else {
if ((((status_2 >= 400)) && ((status_2 < 500)))) {
return ({"outcome": "rejected", "reason": (await r_1.text())})} else {
if ("else") {
return ({"outcome": "unreachable"})} else {
return null}}};
}
catch(__3){
return ({"outcome": "unreachable"});
}
;

};
var transport = function () {
const map___1_3 = page_context();
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const base_5 = squint_core.get(map___1_4, "base");
const session_6 = squint_core.get(map___1_4, "session");
const page_7 = squint_core.get(map___1_4, "page");
const q_8 = `${"?page="}${encodeURIComponent(page_7)??''}`;
return ({"page": page_7, "session": session_6, "mode": "served", "send!": (function (entry) {
return post_entry(`${base_5??''}${"/_decisions"}`, entry);

}), "load": (function () {
return get_json(`${base_5??''}${"/_decisions"}${q_8}`);

}), "rev": (function () {
return get_json(`${base_5??''}${"/_rev"}${q_8}`).then((function (_PERCENT_1) {
const G__55_9 = _PERCENT_1;
if ((G__55_9 == null)) {
return null} else {
return squint_core.get(G__55_9, "rev")};

}));

})});

};

export { page_context, get_json, post_entry, transport }
