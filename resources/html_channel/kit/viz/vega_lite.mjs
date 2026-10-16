import * as squint_core from '../squint-core.js';
import * as load from './load.mjs';
import * as viz_DOT_load from './load.mjs';
import * as versions from './versions.mjs';
import * as viz_DOT_versions from './versions.mjs';
var render_BANG_ = async function (blocks, report_BANG_) {
(await load.scripts_BANG_(versions.vega_lite));
for (let G__1 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, blocks))) {
const vec___2_5 = G__1;
const i_6 = squint_core.nth(vec___2_5, 0, null);
const pre_7 = squint_core.nth(vec___2_5, 1, null);
const d_8 = load.container_BANG_(pre_7);
try{
const spec_9 = JSON.parse(pre_7.textContent);
(await window.vegaEmbed(d_8, spec_9, (await (async () => {
const G__8_10 = ({"actions": false, "renderer": "svg"});
if (squint_core.truth_(load.dark_QMARK_())) {
return ({...G__8_10,["theme"]:"dark"})} else {
return G__8_10};

})())));
load.rendered_BANG_(pre_7)}
catch(e_11){
d_8.remove();
load.failed_BANG_(pre_7, report_BANG_, "vega-lite", i_6, `${(await (async () => {
const or_12_13 = e_11.message;
if (squint_core.truth_(or_12_13)) {
return or_12_13} else {
return e_11};

})())??''}`)}

}
return null;

};

export { render_BANG_ }
