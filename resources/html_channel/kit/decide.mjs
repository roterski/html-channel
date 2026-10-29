import * as squint_core from './squint-core.js';
import * as core from './decide/core.mjs';
import * as decide_DOT_core from './decide/core.mjs';
import * as explore from './decide/explore.mjs';
import * as decide_DOT_explore from './decide/explore.mjs';
import * as http from './decide/http.mjs';
import * as decide_DOT_http from './decide/http.mjs';
import * as lint from './decide/lint.mjs';
import * as decide_DOT_lint from './decide/lint.mjs';
import * as probe from './decide/probe.mjs';
import * as decide_DOT_probe from './decide/probe.mjs';
import * as tiers from './decide/tiers.mjs';
import * as decide_DOT_tiers from './decide/tiers.mjs';
import * as viz from './decide/viz.mjs';
import * as decide_DOT_viz from './decide/viz.mjs';
core.start_BANG_(http.transport());
tiers.start_BANG_();
viz.start_BANG_(core.report_error_BANG_);
explore.start_BANG_(core.report_error_BANG_);
if (squint_core.truth_(explore.explorable_QMARK_())) {
lint.start_BANG_(core.report_error_BANG_);
if (squint_core.truth_((new URLSearchParams(location.search)).get("_check"))) {
probe.start_BANG_(core.report_error_BANG_)}};
