const express = require('express');
const router = express.Router();
const { evaluate, evaluateFormula, SandboxSecurityError, SandboxTimeoutError, SandboxMemoryError } = require('../sandbox');

/**
 * POST /evaluate
 * Body: {
 *   expression: string,         // JavaScript expression or Excel formula
 *   type: 'javascript' | 'excel',  // Expression type (default: javascript)
 *   context: object,            // Data context for expression evaluation
 *   timeout?: number,           // Execution timeout in ms (default: 5000)
 *   memoryLimit?: number        // Memory limit in MB (default: 64)
 * }
 */
router.post('/', async (req, res) => {
  try {
    const { expression, type = 'javascript', context = {}, timeout, memoryLimit } = req.body;

    if (!expression || typeof expression !== 'string') {
      return res.status(400).json({
        error: { code: 'MISSING_EXPRESSION', message: 'expression string is required' },
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
