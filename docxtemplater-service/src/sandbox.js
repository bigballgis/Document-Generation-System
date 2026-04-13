const vm = require('vm');

// Blocked modules/globals that must never be accessible in the sandbox
const BLOCKED_MODULES = ['fs', 'path', 'http', 'https', 'net', 'child_process', 'process', 'os', 'cluster', 'dgram', 'dns', 'tls', 'worker_threads'];
const BLOCKED_GLOBALS = ['eval', 'Function'];

// Try to load isolated-vm (native addon, available in Docker/Linux)
let ivm = null;
try {
  ivm = require('isolated-vm');
} catch {
  // isolated-vm not available, will use vm module fallback
}

const DEFAULT_TIMEOUT = parseInt(process.env.SANDBOX_TIMEOUT || '5000', 10);
const DEFAULT_MEMORY_LIMIT = parseInt(process.env.SANDBOX_MEMORY_LIMIT || '64', 10);

/**
 * Safe math/utility functions available in the sandbox
 */
const SAFE_FUNCTIONS = {
  Math: Math,
  parseInt: parseInt,
  parseFloat: parseFloat,
  isNaN: isNaN,
  isFinite: isFinite,
  Number: Number,
  String: String,
  Boolean: Boolean,
  Array: Array,
  Object: Object,
  JSON: JSON,
  Date: Date,
  RegExp: RegExp,
  encodeURIComponent: encodeURIComponent,
  decodeURIComponent: decodeURIComponent,
};

/**
 * Load Formula.js functions for Excel formula support
 */
function loadFormulaFunctions() {
  try {
    const formulajs = require('@formulajs/formulajs');
    return formulajs;
  } catch {
    return {};
  }
}

/**
 * Check if an expression attempts to access blocked resources.
 * This is a static pre-check before execution.
 */
function checkExpressionSafety(expression) {
  const expr = String(expression);

  // Check for require() calls
  if (/\brequire\s*\(/.test(expr)) {
    const requireMatch = expr.match(/require\s*\(\s*['"]([^'"]+)['"]\s*\)/);
    if (requireMatch) {
      const moduleName = requireMatch[1];
      if (BLOCKED_MODULES.includes(moduleName)) {
        throw new SandboxSecurityError(`Access to module '${moduleName}' is forbidden in sandbox`);
      }
    }
    // Block all require calls regardless
    throw new SandboxSecurityError('require() is forbidden in sandbox');
  }

  // Check for process access
  if (/\bprocess\b/.test(expr)) {
    throw new SandboxSecurityError("Access to 'process' is forbidden in sandbox");
  }

  // Check for eval() calls
  if (/\beval\s*\(/.test(expr)) {
    throw new SandboxSecurityError("Use of 'eval' is forbidden in sandbox");
  }

  // Check for Function constructor
  if (/\bnew\s+Function\b/.test(expr) || /\bFunction\s*\(/.test(expr)) {
    throw new SandboxSecurityError("Use of 'Function' constructor is forbidden in sandbox");
  }

  // Check for global/globalThis access to blocked modules
  for (const mod of BLOCKED_MODULES) {
    const pattern = new RegExp(`\\b${mod}\\b`);
    if (pattern.test(expr)) {
      throw new SandboxSecurityError(`Access to '${mod}' is forbidden in sandbox`);
    }
  }
}

/**
 * Evaluate an expression using isolated-vm (preferred) or vm module (fallback).
 *
 * @param {string} expression - JavaScript expression to evaluate
 * @param {object} context - Data context object
 * @param {object} options - Execution options
 * @param {number} [options.timeout] - Execution timeout in ms
 * @param {number} [options.memoryLimit] - Memory limit in MB
 * @returns {Promise<any>} Expression result
 */
async function evaluate(expression, context = {}, options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const memoryLimit = options.memoryLimit || DEFAULT_MEMORY_LIMIT;

  // Static safety check
  checkExpressionSafety(expression);

  if (ivm) {
    return evaluateWithIsolatedVm(expression, context, timeout, memoryLimit);
  }
  return evaluateWithVm(expression, context, timeout);
}

/**
 * Evaluate using isolated-vm (production path)
 */
async function evaluateWithIsolatedVm(expression, context, timeout, memoryLimit) {
  const isolate = new ivm.Isolate({ memoryLimit });
  try {
    const vmContext = await isolate.createContext();
    const jail = vmContext.global;

    // Inject safe functions
    for (const [name, fn] of Object.entries(SAFE_FUNCTIONS)) {
      await jail.set(name, new ivm.ExternalCopy(fn).copyInto());
    }

    // Inject data context
    await jail.set('data', new ivm.ExternalCopy(context).copyInto());

    // Inject Formula.js functions
    const formulaFns = loadFormulaFunctions();
    await jail.set('Formula', new ivm.ExternalCopy(formulaFns).copyInto());

    const script = await isolate.compileScript(expression);
    const result = await script.run(vmContext, { timeout });
    return result;
  } finally {
    isolate.dispose();
  }
}

/**
 * Evaluate using Node.js vm module (fallback for dev/test)
 */
function evaluateWithVm(expression, context, timeout) {
  // Build a restricted sandbox context
  const sandbox = {
    data: deepClone(context),
    ...SAFE_FUNCTIONS,
    Formula: loadFormulaFunctions(),
    // Explicitly block dangerous globals
    require: undefined,
    process: undefined,
    eval: undefined,
    Function: undefined,
    global: undefined,
    globalThis: undefined,
    fs: undefined,
    path: undefined,
    http: undefined,
    https: undefined,
    net: undefined,
    child_process: undefined,
    os: undefined,
    cluster: undefined,
    __dirname: undefined,
    __filename: undefined,
    module: undefined,
    exports: undefined,
    Buffer: undefined,
    setTimeout: undefined,
    setInterval: undefined,
    setImmediate: undefined,
    queueMicrotask: undefined,
  };

  const vmContext = vm.createContext(sandbox);

  try {
    const script = new vm.Script(expression, { timeout });
    const result = script.runInContext(vmContext, { timeout });
    return result;
  } catch (err) {
    if (err.code === 'ERR_SCRIPT_EXECUTION_TIMEOUT' || err.message.includes('timed out')) {
      throw new SandboxTimeoutError(`Expression execution timed out after ${timeout}ms`);
    }
    throw err;
  }
}

/**
 * Evaluate an Excel formula using Formula.js
 */
function evaluateFormula(formula, context = {}) {
  const formulajs = loadFormulaFunctions();
  if (!formulajs) {
    throw new Error('Formula.js is not available');
  }

  // Parse the formula and execute
  // Support common Excel functions: SUM, AVERAGE, IF, VLOOKUP, CONCATENATE, etc.
  const funcMatch = formula.match(/^(\w+)\((.*)\)$/s);
  if (!funcMatch) {
    throw new Error(`Invalid formula syntax: ${formula}`);
  }

  const funcName = funcMatch[1].toUpperCase();
  const argsStr = funcMatch[2];

  // Map common Excel function names to formulajs
  const funcMap = {
    SUM: 'SUM',
    AVERAGE: 'AVERAGE',
    IF: 'IF',
    VLOOKUP: 'VLOOKUP',
    CONCATENATE: 'CONCATENATE',
    COUNT: 'COUNT',
    MAX: 'MAX',
    MIN: 'MIN',
    ROUND: 'ROUND',
    ABS: 'ABS',
    LEN: 'LEN',
    LEFT: 'LEFT',
    RIGHT: 'RIGHT',
    MID: 'MID',
    TRIM: 'TRIM',
    UPPER: 'UPPER',
    LOWER: 'LOWER',
    TODAY: 'TODAY',
    NOW: 'NOW',
    YEAR: 'YEAR',
    MONTH: 'MONTH',
    DAY: 'DAY',
    AND: 'AND',
    OR: 'OR',
    NOT: 'NOT',
  };

  const jsFuncName = funcMap[funcName] || funcName;
  const fn = formulajs[jsFuncName];
  if (typeof fn !== 'function') {
    throw new Error(`Unsupported formula function: ${funcName}`);
  }

  // Parse arguments - simple comma-split with context variable resolution
  const args = parseFormulaArgs(argsStr, context);
  return fn(...args);
}

function parseFormulaArgs(argsStr, context) {
  const args = [];
  let current = '';
  let depth = 0;
  let inString = false;
  let stringChar = '';

  for (let i = 0; i < argsStr.length; i++) {
    const ch = argsStr[i];
    if (inString) {
      current += ch;
      if (ch === stringChar) inString = false;
      continue;
    }
    if (ch === '"' || ch === "'") {
      inString = true;
      stringChar = ch;
      current += ch;
      continue;
    }
    if (ch === '(') { depth++; current += ch; continue; }
    if (ch === ')') { depth--; current += ch; continue; }
    if (ch === ',' && depth === 0) {
      args.push(resolveArg(current.trim(), context));
      current = '';
      continue;
    }
    current += ch;
  }
  if (current.trim()) {
    args.push(resolveArg(current.trim(), context));
  }
  return args;
}

function resolveArg(arg, context) {
  // String literal
  if ((arg.startsWith('"') && arg.endsWith('"')) || (arg.startsWith("'") && arg.endsWith("'"))) {
    return arg.slice(1, -1);
  }
  // Number
  if (!isNaN(arg) && arg !== '') return Number(arg);
  // Boolean
  if (arg === 'TRUE' || arg === 'true') return true;
  if (arg === 'FALSE' || arg === 'false') return false;
  // Array literal [1,2,3]
  if (arg.startsWith('[') && arg.endsWith(']')) {
    try { return JSON.parse(arg); } catch { /* fall through */ }
  }
  // Context variable reference (e.g., data.field)
  if (arg.startsWith('data.')) {
    const path = arg.slice(5);
    return getNestedValue(context, path);
  }
  // Direct context key
  if (context[arg] !== undefined) return context[arg];
  return arg;
}

function getNestedValue(obj, path) {
  return path.split('.').reduce((o, k) => (o && o[k] !== undefined ? o[k] : undefined), obj);
}

function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj));
}

class SandboxSecurityError extends Error {
  constructor(message) {
    super(message);
    this.name = 'SandboxSecurityError';
    this.code = 'SANDBOX_SECURITY_VIOLATION';
  }
}

class SandboxTimeoutError extends Error {
  constructor(message) {
    super(message);
    this.name = 'SandboxTimeoutError';
    this.code = 'SANDBOX_TIMEOUT';
  }
}

class SandboxMemoryError extends Error {
  constructor(message) {
    super(message);
    this.name = 'SandboxMemoryError';
    this.code = 'SANDBOX_MEMORY_EXCEEDED';
  }
}

module.exports = {
  evaluate,
  evaluateFormula,
  checkExpressionSafety,
  SandboxSecurityError,
  SandboxTimeoutError,
  SandboxMemoryError,
  BLOCKED_MODULES,
};
