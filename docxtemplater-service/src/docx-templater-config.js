const expressionParser = require('docxtemplater/expressions.js');
const ImageModule = require('docxtemplater-image-module-free');

const angularParser = expressionParser.configure({
  filters: {
    upper(input) { return input ? String(input).toUpperCase() : input; },
    lower(input) { return input ? String(input).toLowerCase() : input; },
    trim(input) { return input ? String(input).trim() : input; },
    padStart(input, len, char) { return input ? String(input).padStart(len, char || ' ') : input; },
    padEnd(input, len, char) { return input ? String(input).padEnd(len, char || ' ') : input; },
    replace(input, search, replacement) { return input ? String(input).replaceAll(search, replacement || '') : input; },
    substr(input, start, length) { return input ? String(input).substring(start, length != null ? start + length : undefined) : input; },
    default(input, fallback) { return (input == null || input === '') ? fallback : input; },

    toFixed(input, precision) { return input != null ? Number(input).toFixed(precision || 0) : input; },
    round(input, decimals) {
      if (input == null) return input;
      const f = Math.pow(10, decimals || 0);
      return Math.round(Number(input) * f) / f;
    },
    currency(input, symbol, decimals) {
      if (input == null) return input;
      const s = symbol || '¥';
      const d = decimals != null ? decimals : 2;
      return s + Number(input).toFixed(d);
    },
    percent(input, decimals) {
      if (input == null) return input;
      return (Number(input) * 100).toFixed(decimals != null ? decimals : 0) + '%';
    },
    abs(input) { return input != null ? Math.abs(Number(input)) : input; },

    dateFormat(input, format) {
      if (!input) return input;
      const d = new Date(input);
      if (isNaN(d.getTime())) return input;
      const fmt = format || 'YYYY-MM-DD';
      const pad = (n) => String(n).padStart(2, '0');
      return fmt
        .replace('YYYY', d.getFullYear())
        .replace('MM', pad(d.getMonth() + 1))
        .replace('DD', pad(d.getDate()))
        .replace('HH', pad(d.getHours()))
        .replace('mm', pad(d.getMinutes()))
        .replace('ss', pad(d.getSeconds()));
    },

    join(input, separator) { return Array.isArray(input) ? input.filter(v => v != null).join(separator || ', ') : input; },
    joinBy(input, field, separator) {
      if (!Array.isArray(input)) return input;
      return input.map(item => item && item[field]).filter(v => v != null).join(separator || ', ');
    },
    sumBy(input, field) {
      if (!Array.isArray(input)) return input;
      return input.reduce((sum, item) => sum + (Number(item && item[field]) || 0), 0);
    },
    avgBy(input, field) {
      if (!Array.isArray(input) || input.length === 0) return 0;
      const sum = input.reduce((s, item) => s + (Number(item && item[field]) || 0), 0);
      return sum / input.length;
    },
    minBy(input, field) {
      if (!Array.isArray(input) || input.length === 0) return null;
      return Math.min(...input.map(item => Number(item && item[field]) || 0));
    },
    maxBy(input, field) {
      if (!Array.isArray(input) || input.length === 0) return null;
      return Math.max(...input.map(item => Number(item && item[field]) || 0));
    },
    sortBy(input, ...fields) {
      if (!Array.isArray(input)) return input;
      return [...input].sort((a, b) => {
        for (const f of fields) {
          const va = a && a[f], vb = b && b[f];
          if (va < vb) return -1;
          if (va > vb) return 1;
        }
        return 0;
      });
    },
    where(input, query) {
      if (!Array.isArray(input)) return input;
      return input.filter(item => expressionParser.compile(query)(item));
    },
    first(input) { return Array.isArray(input) && input.length > 0 ? input[0] : null; },
    last(input) { return Array.isArray(input) && input.length > 0 ? input[input.length - 1] : null; },
    count(input) { return Array.isArray(input) ? input.length : 0; },
    reverse(input) { return Array.isArray(input) ? [...input].reverse() : input; },
    unique(input, field) {
      if (!Array.isArray(input)) return input;
      if (!field) return [...new Set(input)];
      const seen = new Set();
      return input.filter(item => {
        const v = item && item[field];
        if (seen.has(v)) return false;
        seen.add(v);
        return true;
      });
    },
    slice(input, start, end) { return Array.isArray(input) ? input.slice(start, end) : input; },
    groupBy(input, field) {
      if (!Array.isArray(input)) return input;
      const groups = {};
      for (const item of input) {
        const key = item && item[field];
        if (!groups[key]) groups[key] = [];
        groups[key].push(item);
      }
      return Object.entries(groups).map(([key, items]) => ({ key, items }));
    },
  },
});

function parser(tag) {
  if (typeof tag === 'string' && tag.startsWith('%')) {
    return angularParser(tag.slice(1));
  }
  if (typeof tag === 'string') {
    // Legacy: "x | count + 1" -> "(x | count) + 1" for angular-expressions
    const countPlusRe = /^(.+?\|\s*count)\s*\+\s*(\d+)\s*$/;
    const m = tag.match(countPlusRe);
    if (m) {
      return angularParser(`(${m[1].trim()}) + ${m[2]}`);
    }
  }
  return angularParser(tag);
}

function createImageModule() {
  return new ImageModule({
    centered: false,
    getImage(tagValue) {
      if (typeof tagValue === 'string' && tagValue.startsWith('data:')) {
        const base64Data = tagValue.split(',')[1] || tagValue;
        return Buffer.from(base64Data, 'base64');
      }
      if (Buffer.isBuffer(tagValue)) {
        return tagValue;
      }
      return tagValue;
    },
    getSize(img) {
      return [150, 150];
    },
  });
}

module.exports = { parser, createImageModule };
