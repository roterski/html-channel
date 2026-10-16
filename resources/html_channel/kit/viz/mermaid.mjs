import * as squint_core from '../squint-core.js';
import * as load from './load.mjs';
import * as viz_DOT_load from './load.mjs';
import * as versions from './versions.mjs';
import * as viz_DOT_versions from './versions.mjs';
var render_BANG_ = async function (blocks, report_BANG_) {
(await load.scripts_BANG_(versions.mermaid));
const m_1 = window.mermaid;
m_1.initialize(({"startOnLoad": false, "securityLevel": "strict", "suppressErrorRendering": true, "theme": ((squint_core.truth_(load.dark_QMARK_())) ? ("dark") : ("default"))}));
for (let G__2 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, blocks))) {
const vec___3_6 = G__2;
const i_7 = squint_core.nth(vec___3_6, 0, null);
const pre_8 = squint_core.nth(vec___3_6, 1, null);
try{
const map___9_11 = (await m_1.render(`hc-mermaid-${i_7??''}`, pre_8.textContent));
const map___9_12 = ((squint_core.truth_(squint_core.sequential_QMARK_(map___9_11))) ? (((squint_core.truth_(squint_core.vector_QMARK_(map___9_11))) ? (map___9_11) : (squint_core.seq_to_map_for_destructuring(map___9_11)))) : (map___9_11));
const svg_13 = squint_core.get(map___9_12, "svg");
const d_14 = load.container_BANG_(pre_8);
d_14.innerHTML = svg_13;
load.rendered_BANG_(pre_8)}
catch(e_15){
load.failed_BANG_(pre_8, report_BANG_, "mermaid", i_7, `${(await (async () => {
const or_16_17 = e_15.message;
if (squint_core.truth_(or_16_17)) {
return or_16_17} else {
return e_15};

})())??''}`.trim())}

}
return null;

};

export { render_BANG_ }
