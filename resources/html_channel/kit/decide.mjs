import * as squint_core from './squint-core.js';
import * as core from './decide/core.mjs';
import * as decide_DOT_core from './decide/core.mjs';
import * as http from './decide/http.mjs';
import * as decide_DOT_http from './decide/http.mjs';
core.start_BANG_(http.transport());
