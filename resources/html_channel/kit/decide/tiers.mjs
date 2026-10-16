import * as squint_core from '../squint-core.js';
var level = squint_core.atom(null);
var tier_of = function (el) {
const t_1 = el.dataset.tier;
if (squint_core.truth_(((squint_core.truth_(t_1)) ? (squint_core.re_matches(/\d+/, t_1)) : (t_1)))) {
return parseInt(t_1, 10);
};

};
var tagged = function () {
return squint_core.filter(tier_of, Array.from(document.querySelectorAll("[data-tier]")));

};
var storage_key = function () {
return `${"html-channel:tier:"}${location.pathname??''}`;

};
var saved_level = function () {
try{
const G__3_1 = localStorage.getItem(storage_key());
if ((G__3_1 == null)) {
return null} else {
return parseInt(G__3_1, 10)};
}
catch(__2){
return null;
}
;

};
var set_level_BANG_ = function (bar, l) {
squint_core.reset_BANG_(level, l);
for (let G__1 of squint_core.iterable(tagged())) {
const el_2 = G__1;
if ((tier_of(el_2) > l)) {
el_2.setAttribute("hidden", "until-found")} else {
el_2.removeAttribute("hidden")}
};
for (let G__3 of squint_core.iterable(bar.querySelectorAll("button"))) {
const b_4 = G__3;
b_4.setAttribute("aria-pressed", `${squint_core._EQ_(l, parseInt(b_4.dataset.level, 10))}`)
};
try{
return localStorage.setItem(storage_key(), `${l??''}`);
}
catch(__5){
return null;
}
;

};
var level_names = function (max_tier) {
const named_1 = (() => {
const G__4_2 = document.querySelector("meta[name=hc-tiers]");
const G__4_3 = (((G__4_2 == null)) ? (null) : (G__4_2.getAttribute("content")));
const G__4_4 = (((G__4_3 == null)) ? (null) : (G__4_3.split(",")));
const G__4_5 = (((G__4_4 == null)) ? (null) : (G__4_4.map((function (_PERCENT_1) {
return _PERCENT_1.trim();

}))));
if ((G__4_5 == null)) {
return null} else {
return G__4_5.filter((function (_PERCENT_1) {
return (_PERCENT_1.length > 0);

}))};

})();
return squint_core.vec(squint_core.lazy((function* () {
for (let G__6 of squint_core.iterable(squint_core.range(squint_core.max((max_tier + 1), squint_core.count(named_1))))) {
const i_7 = G__6;
yield (yield* (function* () {
const or_8_9 = squint_core.get(named_1, i_7);
if (squint_core.truth_(or_8_9)) {
return or_8_9} else {
return `${i_7??''}`};

})());
}
return null;

})));

};
var start_BANG_ = function () {
const tiers_1 = squint_core.map(tier_of, tagged());
if (squint_core.truth_(squint_core.seq(tiers_1))) {
const names_2 = level_names(squint_core.apply(squint_core.max, tiers_1));
const bar_3 = document.createElement("nav");
const label_4 = document.createElement("span");
const saved_5 = saved_level();
bar_3.id = "decide-tiers";
bar_3.setAttribute("aria-label", "Depth");
label_4.textContent = "Depth";
bar_3.append(label_4);
for (let G__6 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, names_2))) {
const vec___7_10 = G__6;
const i_11 = squint_core.nth(vec___7_10, 0, null);
const n_12 = squint_core.nth(vec___7_10, 1, null);
const b_13 = document.createElement("button");
b_13.type = "button";
b_13.dataset.level = `${i_11??''}`;
b_13.textContent = n_12;
b_13.addEventListener("click", (function (_) {
return set_level_BANG_(bar_3, i_11);

}));
bar_3.append(b_13)
};
document.body.append(bar_3);
document.addEventListener("beforematch", (function (e) {
const temp__23314__auto___14 = (() => {
const G__5_15 = e.target;
const G__5_16 = (((G__5_15 == null)) ? (null) : (G__5_15.closest("[data-tier]")));
if ((G__5_16 == null)) {
return null} else {
return tier_of(G__5_16)};

})();
if (squint_core.truth_(temp__23314__auto___14)) {
const t_17 = temp__23314__auto___14;
return set_level_BANG_(bar_3, squint_core.max(t_17, squint_core.deref(level)));
};

}));
return set_level_BANG_(bar_3, ((((!(saved_5 == null)) && ((((saved_5 < squint_core.count(names_2))) && ((saved_5 >= 0)))))) ? (saved_5) : (0)));
};

};

export { level, tier_of, tagged, storage_key, saved_level, set_level_BANG_, level_names, start_BANG_ }
