import * as squint_core from '../squint-core.js';
var page_context = function () {
const parts_1 = location.pathname.split("/");
const file_2 = parts_1.pop();
return ({"base": `${(new URL(import.meta.url)).origin??''}${parts_1.join("/")??''}`, "page": file_2.replace(/\.html?$/, "")});

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
return (await fetch(url, ({"method": "POST", "body": JSON.stringify(entry)}))).ok;
}
catch(__1){
return false;
}
;

};
var transport = function () {
const map___1_3 = page_context();
const map___1_4 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___1_3))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___1_3))) ? (map___1_3) : (squint_core.seq_to_map_for_destructuring(map___1_3)))) : (map___1_3));
const base_5 = squint_core.get(map___1_4, "base");
const page_6 = squint_core.get(map___1_4, "page");
const q_7 = `${"?page="}${encodeURIComponent(page_6)??''}`;
return ({"page": page_6, "send!": (function (entry) {
return post_entry(`${base_5??''}${"/_decisions"}`, entry);

}), "load": (function () {
return get_json(`${base_5??''}${"/_decisions"}${q_7}`);

}), "rev": (function () {
return get_json(`${base_5??''}${"/_rev"}${q_7}`).then((function (_PERCENT_1) {
const G__1_8 = _PERCENT_1;
if ((G__1_8 == null)) {
return null} else {
return squint_core.get(G__1_8, "rev")};

}));

})});

};

export { page_context, get_json, post_entry, transport }
