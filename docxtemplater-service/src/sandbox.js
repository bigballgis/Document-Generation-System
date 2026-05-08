const vm = require('vm');

// Blocked modules/globals that must never be accessible in the sandbox
const BLOCKED_MODULES = ['fs', 'path', 'http', 'https', 'net', 'child_process', 'process', 'os', 'cluster', 'dgram', 'dns', 'tls', 'worker_threads'];
const BLOCKED_GLOBALS = ['eval', 'Function'];

// Try to load isolated-vm (native addon, available in Docker/Linux)
let ivm = null;
try {
  ivm = require('isolated-vm');
} catch {
}

const DEFAULT_TIMEOUT = parseInt(process.env.SANDBOX_TIMEOUT || '5000', 10);
const DEFAULT_MEMORY_LIMIT = parseInt(process.env.SANDBOX_MEMORY_LIMIT || '64', 10);

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

function loadFormulaFunctions() {
  try {
    const formulajs = require('@formulajs/formulajs');
    return formulajs;
  } catch {
    return {};
  }
}

function checkExpressionSafety(expression) {
  const expr = String(expression);

  if (/\brequire\s*\(/.test(expr)) {
    const requireMatch = expr.match(/require\s*\(\s*['"]([^'"]+)['"]\s*\)/);
    if (requireMatch) {
      const moduleName = requireMatch[1];
      if (BLOCKED_MODULES.includes(moduleName)) {
        throw new SandboxSecurityError(`Access to module '${moduleName}' is forbidden in sandbox`);
      }
    }
    throw new SandboxSecurityError('require() is forbidden in sandbox');
  }

  if (/\bprocess\b/.test(expr)) {
    throw new SandboxSecurityError("Access to 'process' is forbidden in sandbox");
  }

  if (/\beval\s*\(/.test(expr)) {
    throw new SandboxSecurityError("Use of 'eval' is forbidden in sandbox");
  }

  if (/\bnew\s+Function\b/.test(expr) || /\bFunction\s*\(/.test(expr)) {
    throw new SandboxSecurityError("Use of 'Function' constructor is forbidden in sandbox");
  }

  for (const mod of BLOCKED_MODULES) {
    const pattern = new RegExp(`\\b${mod}\\b`);
    if (pattern.test(expr)) {
      throw new SandboxSecurityError(`Access to '${mod}' is forbidden in sandbox`);
    }
  }
}

async function evaluate(expression, context = {}, options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const memoryLimit = options.memoryLimit || DEFAULT_MEMORY_LIMIT;

  checkExpressionSafety(expression);

  if (ivm) {
    return evaluateWithIsolatedVm(expression, context, timeout, memoryLimit);
  }
  return evaluateWithVm(expression, context, timeout);
}

async function evaluateWithIsolatedVm(expression, context, timeout, memoryLimit) {
  const isolate = new ivm.Isolate({ memoryLimit });
  try {
    const vmContext = await isolate.createContext();
    const jail = vmContext.global;

    for (const [name, value] of Object.entries(SAFE_FUNCTIONS)) {
      if (typeof value === 'function') {
        await jail.set(name, new ivm.Reference(value));
      } else {
        await jail.set(name, new ivm.ExternalCopy(value).copyInto());
      }
    }

    await jail.set('data', new ivm.ExternalCopy(context).copyInto());

    const formulaFns = loadFormulaFunctions();
    await jail.set('Formula', new ivm.Reference(formulaFns));

    const script = await isolate.compileScript(expression);
    const result = await script.run(vmContext, { timeout });
    return result;
  } finally {
    isolate.dispose();
  }
}

function evaluateWithVm(expression, context, timeout) {
  const sandbox = {
    data: deepClone(context),
    ...SAFE_FUNCTIONS,
    Formula: loadFormulaFunctions(),
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

function evaluateFormula(formula, context = {}) {
  const formulajs = loadFormulaFunctions();
  if (!formulajs) {
    throw new Error('Formula.js is not available');
  }

  const funcMatch = formula.match(/^(\w+)\((.*)\)$/s);
  if (!funcMatch) {
    throw new Error(`Invalid formula syntax: ${formula}`);
  }

  const funcName = funcMatch[1].toUpperCase();
  const argsStr = funcMatch[2];

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
  if ((arg.startsWith('"') && arg.endsWith('"')) || (arg.startsWith("'") && arg.endsWith("'"))) {
    return arg.slice(1, -1);
  }
  if (!isNaN(arg) && arg !== '') return Number(arg);
  if (arg === 'TRUE' || arg === 'true') return true;
  if (arg === 'FALSE' || arg === 'false') return false;
  if (arg.startsWith('[') && arg.endsWith(']')) {
    try { return JSON.parse(arg); } catch { /* fall through */ }
  }
  if (arg.startsWith('data.')) {
    const path = arg.slice(5);
    return getNestedValue(context, path);
  }
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
