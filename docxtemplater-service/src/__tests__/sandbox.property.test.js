/**
 * Property 6: 表达式安全沙箱隔离性
 *
 * For any JavaScript expression that attempts to access fs, http, net,
 * child_process, process, eval, or Function constructor, the sandbox
 * should reject execution and return a security violation error.
 *
 * **Validates: Requirements 5.9, 51.3-51.6**
 *
 * Feature: low-code-document-generation-system, Property 6: 表达式安全沙箱隔离性
 */

const fc = require('fast-check');
const { evaluate, checkExpressionSafety, SandboxSecurityError, BLOCKED_MODULES } = require('../sandbox');

const BLOCKED_ACCESS_PATTERNS = [
  'fs',
  'http',
  'https',
  'net',
  'child_process',
  'process',
  'path',
  'os',
  'cluster',
  'dgram',
  'dns',
  'tls',
];

const DANGEROUS_EXPRESSION_TEMPLATES = [
  (mod) => `require('${mod}')`,
  (mod) => `require("${mod}")`,
  (mod) => `${mod}.readFileSync("/etc/passwd")`,
  (mod) => `${mod}.get("http://evil.com")`,
  (mod) => `${mod}.exec("rm -rf /")`,
  (mod) => `${mod}.connect(80, "evil.com")`,
];

// Expressions using eval and Function constructor
const EVAL_FUNCTION_EXPRESSIONS = [
  'eval("1+1")',
  'eval("process.exit()")',
  "eval('require(\"fs\")')",
  'new Function("return 1")()',
  'new Function("return process.env")()',
  'Function("return 1")()',
  '(function(){return eval("1");})()',
];

describe('Property 6: 表达式安全沙箱隔离性', () => {
  /**
   * **Validates: Requirements 5.9, 51.3-51.6**
   *
   * Property: For any blocked module name, any expression template that
   * references that module should be rejected by the sandbox.
   */
  it('should reject all expressions accessing blocked modules', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...BLOCKED_ACCESS_PATTERNS),
        fc.constantFrom(...DANGEROUS_EXPRESSION_TEMPLATES),
        (moduleName, templateFn) => {
          const expression = templateFn(moduleName);
          expect(() => checkExpressionSafety(expression)).toThrow(SandboxSecurityError);
        }
      ),
      { numRuns: 200 }
    );
  });

  /**
   * **Validates: Requirements 51.5, 51.6**
   *
   * Property: eval() and Function constructor expressions should always be rejected.
   */
  it('should reject all eval and Function constructor expressions', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...EVAL_FUNCTION_EXPRESSIONS),
        (expression) => {
          expect(() => checkExpressionSafety(expression)).toThrow(SandboxSecurityError);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 5.9, 51.3**
   *
   * Property: For any randomly generated require() expression with a blocked module,
   * the sandbox should reject it.
   */
  it('should reject require() with any blocked module name', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...BLOCKED_MODULES),
        (moduleName) => {
          const expr1 = `require('${moduleName}')`;
          const expr2 = `require("${moduleName}")`;
          const expr3 = `var x = require('${moduleName}'); x.something()`;

          expect(() => checkExpressionSafety(expr1)).toThrow(SandboxSecurityError);
          expect(() => checkExpressionSafety(expr2)).toThrow(SandboxSecurityError);
          expect(() => checkExpressionSafety(expr3)).toThrow(SandboxSecurityError);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 51.4**
   *
   * Property: process access in any form should be blocked.
   */
  it('should reject all process access patterns', () => {
    const processPatterns = [
      'process.exit()',
      'process.env.SECRET',
      'process.pid',
      'process.cwd()',
      'process.kill(1)',
      'process.argv',
      'process.execPath',
    ];

    fc.assert(
      fc.property(
        fc.constantFrom(...processPatterns),
        (expression) => {
          expect(() => checkExpressionSafety(expression)).toThrow(SandboxSecurityError);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 5.9**
   *
   * Property: Safe arithmetic and data access expressions should execute successfully.
   */
  it('should allow safe expressions to execute', async () => {
    fc.assert(
      fc.asyncProperty(
        fc.integer({ min: -1000, max: 1000 }),
        fc.integer({ min: -1000, max: 1000 }),
        async (a, b) => {
          const result = await evaluate('data.a + data.b', { a, b });
          expect(result).toBe(a + b);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * **Validates: Requirements 51.3-51.6**
   *
   * Property: Even when expressions are executed (not just statically checked),
   * blocked resources remain inaccessible.
   */
  it('should block dangerous expressions at execution time', async () => {
    const dangerousExpressions = [
      "process.exit(1)",
      "require('fs').readFileSync('/etc/passwd')",
      "eval('1+1')",
      "new Function('return 1')()",
      "child_process.exec('ls')",
      "http.get('http://evil.com')",
      "net.connect(80, 'evil.com')",
      "fs.readFileSync('/etc/passwd')",
    ];

    for (const expr of dangerousExpressions) {
      await expect(evaluate(expr, {})).rejects.toThrow();
    }
  });
});
