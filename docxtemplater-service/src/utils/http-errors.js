'use strict';

/**
 * Standard 400 JSON envelope for route validation failures (Java client contract).
 * @param {import('express').Response} res
 * @param {string} code
 * @param {string} message
 */
function badRequest(res, code, message) {
  return res.status(400).json({ error: { code, message } });
}

module.exports = { badRequest };
