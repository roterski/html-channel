import * as squint_core from '../squint-core.js';
import * as load from './load.mjs';
import * as viz_DOT_load from './load.mjs';
import * as versions from './versions.mjs';
import * as viz_DOT_versions from './versions.mjs';
var render_BANG_ = async function (blocks, fail_BANG_) {
(await Promise.all([load.stylesheet_BANG_(versions.katex_css), load.scripts_BANG_(versions.katex)]));
for (let G__1 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, blocks))) {
const vec___2_5 = G__1;
const i_6 = squint_core.nth(vec___2_5, 0, null);
const pre_7 = squint_core.nth(vec___2_5, 1, null);
const d_8 = load.container_BANG_(pre_7);
try{
window.katex.render(pre_7.textContent, d_8, ({"displayMode": !("SPAN" === pre_7.tagName), "throwOnError": true}));
load.rendered_BANG_(pre_7)}
catch(e_9){
d_8.remove();
fail_BANG_(pre_7, i_6, `${(await (async () => {
const or_10_11 = e_9.message;
if (squint_core.truth_(or_10_11)) {
return or_10_11} else {
return e_9};

})())??''}`)}

}
return null;

};

export { render_BANG_ }
