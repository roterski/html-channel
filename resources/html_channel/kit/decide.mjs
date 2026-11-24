import * as squint_core from './squint-core.js';
import * as code_links from './decide/code_links.mjs';
import * as decide_DOT_code_links from './decide/code_links.mjs';
import * as core from './decide/core.mjs';
import * as decide_DOT_core from './decide/core.mjs';
import * as diff from './decide/diff.mjs';
import * as decide_DOT_diff from './decide/diff.mjs';
import * as download from './decide/download.mjs';
import * as decide_DOT_download from './decide/download.mjs';
import * as explore from './decide/explore.mjs';
import * as decide_DOT_explore from './decide/explore.mjs';
import * as highlight from './decide/highlight.mjs';
import * as decide_DOT_highlight from './decide/highlight.mjs';
import * as http from './decide/http.mjs';
import * as decide_DOT_http from './decide/http.mjs';
import * as lint from './decide/lint.mjs';
import * as decide_DOT_lint from './decide/lint.mjs';
import * as moot from './decide/moot.mjs';
import * as decide_DOT_moot from './decide/moot.mjs';
import * as offline from './decide/offline.mjs';
import * as decide_DOT_offline from './decide/offline.mjs';
import * as probe from './decide/probe.mjs';
import * as decide_DOT_probe from './decide/probe.mjs';
import * as statechart from './decide/statechart.mjs';
import * as decide_DOT_statechart from './decide/statechart.mjs';
import * as tiers from './decide/tiers.mjs';
import * as decide_DOT_tiers from './decide/tiers.mjs';
import * as viz from './decide/viz.mjs';
import * as decide_DOT_viz from './decide/viz.mjs';
core.start_BANG_((() => {
const temp__23255__auto___1 = offline.share_meta();
if (squint_core.truth_(temp__23255__auto___1)) {
const copy_2 = temp__23255__auto___1;
return offline.transport(copy_2);
} else {
return http.transport()};

})());
download.start_BANG_();
moot.start_BANG_(core.report_error_BANG_);
tiers.start_BANG_();
viz.start_BANG_(core.report_error_BANG_);
statechart.start_BANG_(core.report_error_BANG_);
explore.start_BANG_(core.report_error_BANG_);
code_links.start_BANG_(core.report_error_BANG_);
diff.start_BANG_(core.report_error_BANG_);
highlight.start_BANG_(core.report_error_BANG_);
if (squint_core.truth_(explore.explorable_QMARK_())) {
lint.start_BANG_(core.report_error_BANG_)};
if (squint_core.truth_((() => {
let and_31 = (() => {
const or_1_2 = explore.explorable_QMARK_();
if (squint_core.truth_(or_1_2)) {
return or_1_2} else {
return statechart.present_QMARK_()};

})();
if (squint_core.truth_(and_31)) {
return (new URLSearchParams(location.search)).get("_check")} else {
return and_31};

})())) {
probe.start_BANG_(core.report_error_BANG_)};
