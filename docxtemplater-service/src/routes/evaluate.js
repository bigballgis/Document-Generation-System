const express = require('express');
const router = express.Router();
const { evaluate, evaluateFormula, SandboxSecurityError, SandboxTimeoutError, SandboxMemoryError } = require('../sandbox');

/**
 * POST /evaluate
 * Body: {
 *   expression: string,         // JavaScript expression or Excel formula
 *   type: 'javascript' | 'excel',  // Expression type (default: javascript when omitted)
 *   context: object,            // Data context for expression evaluation
 *   timeout?: number,           // Execution timeout in ms (default: 5000)
 *   memoryLimit?: number        // Memory limit in MB (default: 64)
 * }
 *
 * Unknown `type` values are rejected with HTTP 400 and never reach the JavaScript sandbox.
 */
router.post('/', async (req, res) => {
  try {
    const { expression, context = {}, timeout, memoryLimit } = req.body;

    if (!expression || typeof expression !== 'string') {
      return res.status(400).json({
        error: { code: 'MISSING_EXPRESSION', message: 'expression string is required' },
      });
    }

    let type = req.body.type;
    if (type === undefined || type === null) {
      type = 'javascript';
    } else if (typeof type !== 'string') {
      return res.status(400).json({
        error: { code: 'INVALID_EXPRESSION_TYPE', message: 'type must be a string' },
      });
    } else {
      type = type.trim();
      if (type === '') {
        type = 'javascript';
      }
    }

    if (type !== 'javascript' && type !== 'excel') {
      return res.status(400).json({
        error: {
          code: 'UNKNOWN_EXPRESSION_TYPE',
          message: 'type must be "javascript" or "excel"',
        },
      });
    }

    let result;

    if (type === 'excel') {
      result = evaluateFormula(expression, context);
    } else {
      result = await evaluate(expression, context, { timeout, memoryLimit });
    }

    res.json({ success: true, result });
  } catch (err) {
    if (err instanceof SandboxSecurityError) {
      return res.status(403).json({
        error: { code: err.code, message: err.message },
      });
    }
    if (err instanceof SandboxTimeoutError) {
      return res.status(408).json({
        error: { code: err.code, message: err.message },
      });
    }
    if (err instanceof SandboxMemoryError) {
      return res.status(413).json({
        error: { code: err.code, message: err.message },
      });
    }

    console.error('Evaluate error:', err);
    res.status(500).json({
      error: { code: 'EXPRESSION_EVAL_ERROR', message: err.message },
    });
  }
});

module.exports = router;
