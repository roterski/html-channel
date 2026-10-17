import * as squint_core from '../squint-core.js';
var loaded = squint_core.atom(({}));
var script_BANG_ = function (url) {
const or_1_2 = squint_core.get(squint_core.deref(loaded), url);
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
const p_3 = (new Promise((function (resolve, reject) {
const s_4 = document.createElement("script");
s_4.src = url;
s_4.onload = (function (_) {
return resolve(true);

});
s_4.onerror = (function (_) {
return reject((new Error(`${"could not load "}${url??''}`)));

});
return document.head.append(s_4);

})));
squint_core.swap_BANG_(loaded, squint_core.assoc, url, p_3);
return p_3;
};

};
var scripts_BANG_ = async function (urls) {
for (let G__1 of squint_core.iterable(urls)) {
const u_2 = G__1;
(await script_BANG_(u_2))
}
return null;

};
var dark_QMARK_ = function () {
return matchMedia("(prefers-color-scheme: dark)").matches;

};
var container_BANG_ = function (pre) {
const d_1 = document.createElement("div");
d_1.className = "hc-viz";
pre.after(d_1);
return d_1;

};
var rendered_BANG_ = function (pre) {
return pre.hidden = true;

};

export { loaded, script_BANG_, scripts_BANG_, dark_QMARK_, container_BANG_, rendered_BANG_ }
