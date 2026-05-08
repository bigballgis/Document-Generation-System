# Template Expression Filters

The docxtemplater rendering engine supports expression-based template syntax with built-in filters. Filters can be chained using the pipe (`|`) operator inside template tags.

## Syntax

```
{expression | filterName}
{expression | filterName:'arg1':'arg2'}
{items | sumBy:'price'}
```

Expressions also support dot-notation access (`{user.name}`) and arithmetic (`{price * 1.2}`).

## Available Filters

### String Filters

| Filter | Usage | Description |
|--------|-------|-------------|
| `upper` | `{name \| upper}` | Converts to uppercase |
| `lower` | `{name \| lower}` | Converts to lowercase |
| `trim` | `{name \| trim}` | Trims whitespace |
| `padStart` | `{id \| padStart:5:'0'}` | Left-pads to length with char |
| `padEnd` | `{id \| padEnd:10:'.'}` | Right-pads to length with char |
| `replace` | `{text \| replace:'old':'new'}` | Replaces all occurrences |
| `substr` | `{text \| substr:0:10}` | Extracts substring (start, length) |
| `default` | `{name \| default:'N/A'}` | Fallback for null/empty values |

### Number Filters

| Filter | Usage | Description |
|--------|-------|-------------|
| `toFixed` | `{price \| toFixed:2}` | Fixed decimal places |
| `round` | `{value \| round:1}` | Rounds to N decimals |
| `currency` | `{price \| currency:'$':2}` | Formats as currency (default ¥) |
| `percent` | `{rate \| percent:1}` | Formats as percentage |
| `abs` | `{diff \| abs}` | Absolute value |

### Date Filters

| Filter | Usage | Description |
|--------|-------|-------------|
| `dateFormat` | `{date \| dateFormat:'YYYY-MM-DD'}` | Formats date string |

Supported tokens: `YYYY`, `MM`, `DD`, `HH`, `mm`, `ss`

### Array Filters

| Filter | Usage | Description |
|--------|-------|-------------|
| `join` | `{tags \| join:', '}` | Joins array elements |
| `joinBy` | `{users \| joinBy:'name':','}` | Joins by field |
| `sumBy` | `{items \| sumBy:'price'}` | Sum of field values |
| `avgBy` | `{items \| avgBy:'score'}` | Average of field values |
| `minBy` | `{items \| minBy:'age'}` | Minimum field value |
| `maxBy` | `{items \| maxBy:'age'}` | Maximum field value |
| `sortBy` | `{items \| sortBy:'name'}` | Sorts by field(s) |
| `where` | `{items \| where:'active'}` | Filters by expression |
| `first` | `{items \| first}` | First element |
| `last` | `{items \| last}` | Last element |
| `count` | `{items \| count}` | Array length |
| `reverse` | `{items \| reverse}` | Reverses order |
| `unique` | `{items \| unique:'category'}` | Deduplicates (optionally by field) |
| `slice` | `{items \| slice:0:5}` | Extracts sub-array |
| `groupBy` | `{items \| groupBy:'dept'}` | Groups into `[{key, items}]` |

## Examples

```
Contract Date: {signDate | dateFormat:'YYYY年MM月DD日'}
Total Amount: {lineItems | sumBy:'amount' | currency:'¥':2}
Participants: {attendees | joinBy:'name':'、'}
Top 3 Items: {items | sortBy:'score' | reverse | slice:0:3}
Status: {status | default:'Pending'}
```

## Notes

- Filters are evaluated left-to-right when chained
- `null`/`undefined` inputs are passed through safely (no errors thrown)
- The `where` filter accepts any valid angular expression as its argument
- `groupBy` returns an array of `{ key: string, items: array }` objects suitable for nested loops
