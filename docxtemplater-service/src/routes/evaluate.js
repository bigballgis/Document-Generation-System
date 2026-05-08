const express = require('express');
const router = express.Router();
const { evaluate, evaluateFormula, SandboxSecurityError, SandboxTimeoutError, SandboxMemoryError } = require('../sandbox');
const { badRequest } = require('../utils/http-errors');

router.post('/', async (req, res) => {
  try {
    const { expression, context = {}, timeout, memoryLimit } = req.body;

    if (!expression || typeof expression !== 'string') {
      return badRequest(res, 'MISSING_EXPRESSION', 'expression string is required');
    }

    let type = req.body.type;
    if (type === undefined || type === null) {
      type = 'javascript';
    } else if (typeof type !== 'string') {
      return badRequest(res, 'INVALID_EXPRESSION_TYPE', 'type must be a string');
    } else {
      type = type.trim();
      if (type === '') {
        type = 'javascript';
      }
    }

    if (type !== 'javascript' && type !== 'excel') {
      return badRequest(res, 'UNKNOWN_EXPRESSION_TYPE', 'type must be "javascript" or "excel"');
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
