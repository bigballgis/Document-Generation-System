/**
 * Unit tests for injectAggregationProperties function in render.js.
 *
 * Tests flat key conversion, nested array recursion, empty data, and no aggregation keys.
 *
 * Validates: Requirements 1.8
 */

const { injectAggregationProperties } = require('../routes/render');

describe('injectAggregationProperties', () => {
  it('should convert flat aggregation keys to array properties', () => {
    const items = [
      { name: 'A', price: 100 },
      { name: 'B', price: 50 },
    ];
    const data = {
      items,
      'items.$count': 2,
      'items.$sum_price': 150,
      'items.$avg_price': 75,
      'items.$join_name': 'A, B',
      'items.$first': items[0],
      'items.$last': items[1],
    };

    injectAggregationProperties(data);

    // Flat keys should be removed
    expect(data['items.$count']).toBeUndefined();
    expect(data['items.$sum_price']).toBeUndefined();
    expect(data['items.$avg_price']).toBeUndefined();
    expect(data['items.$join_name']).toBeUndefined();
    expect(data['items.$first']).toBeUndefined();
    expect(data['items.$last']).toBeUndefined();

    // Array properties should be set
    expect(data.items['$count']).toBe(2);
    expect(data.items['$sum_price']).toBe(150);
    expect(data.items['$avg_price']).toBe(75);
    expect(data.items['$join_name']).toBe('A, B');
    expect(data.items['$first']).toBe(items[0]);
    expect(data.items['$last']).toBe(items[1]);

    // Array elements should be unchanged
    expect(data.items[0]).toEqual({ name: 'A', price: 100 });
    expect(data.items[1]).toEqual({ name: 'B', price: 50 });
  });

  it('should recursively process nested array elements', () => {
    const innerItems = [{ price: 100 }, { price: 200 }];
    const data = {
      orders: [
        {
          orderId: '001',
          items: innerItems,
          'items.$count': 2,
          'items.$sum_price': 300,
        },
      ],
      'orders.$count': 1,
    };

    injectAggregationProperties(data);

    // Top-level flat keys removed
    expect(data['orders.$count']).toBeUndefined();
    expect(data.orders['$count']).toBe(1);

    // Nested flat keys removed and converted
    const order = data.orders[0];
    expect(order['items.$count']).toBeUndefined();
    expect(order['items.$sum_price']).toBeUndefined();
    expect(order.items['$count']).toBe(2);
    expect(order.items['$sum_price']).toBe(300);
  });

  it('should handle empty data object', () => {
    const data = {};
    injectAggregationProperties(data);
    expect(data).toEqual({});
  });

  it('should handle data with no aggregation keys', () => {
    const data = {
      name: 'Test',
      items: [{ price: 100 }],
      count: 5,
    };
    const original = JSON.parse(JSON.stringify(data));

    injectAggregationProperties(data);

    expect(data.name).toBe(original.name);
    expect(data.count).toBe(original.count);
    expect(data.items.length).toBe(1);
    expect(data.items[0].price).toBe(100);
  });

  it('should not inject into non-array values', () => {
    const data = {
      name: 'Test',
      'name.$count': 5,
    };

    injectAggregationProperties(data);

    // Key should be deleted since name is not an array
    expect(data['name.$count']).toBeUndefined();
    // name should remain unchanged
    expect(data.name).toBe('Test');
  });

  it('should handle deeply nested arrays', () => {
    const data = {
      departments: [
        {
          teams: [
            {
              members: [{ name: 'Alice' }],
              'members.$count': 1,
            },
          ],
          'teams.$count': 1,
        },
      ],
      'departments.$count': 1,
    };

    injectAggregationProperties(data);

    expect(data.departments['$count']).toBe(1);
    expect(data.departments[0].teams['$count']).toBe(1);
    expect(data.departments[0].teams[0].members['$count']).toBe(1);
  });

  it('should skip array elements that are not objects', () => {
    const data = {
      tags: ['a', 'b', 'c'],
      'tags.$count': 3,
    };

    injectAggregationProperties(data);

    expect(data.tags['$count']).toBe(3);
    expect(data.tags[0]).toBe('a');
  });
});
