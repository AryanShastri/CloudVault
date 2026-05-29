import React, { useEffect, useState } from 'react';
import { Info } from 'lucide-react';
import { getPricing } from '../api/billing';
import Spinner from './Spinner';

const CATEGORY_ORDER = ['Storage', 'Versioning', 'Requests', 'Bandwidth', 'Retrieval', 'Restore (DEEP_GLACIER)'];

export default function PricingReference() {
  const [lines, setLines] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getPricing()
      .then((data) => setLines(data?.lines || []))
      .catch(() => setLines([]))
      .finally(() => setLoading(false));
  }, []);

  const grouped = CATEGORY_ORDER.map((cat) => ({
    category: cat,
    items: lines.filter((l) => l.category === cat),
  })).filter((g) => g.items.length > 0);

  if (loading) {
    return (
      <div className="py-8 flex justify-center">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {grouped.map(({ category, items }) => (
        <div key={category}>
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2 uppercase tracking-wide">
            {category}
          </h3>
          <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-800">
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-600 dark:text-gray-400">
                <tr>
                  <th className="px-4 py-2 font-medium">Preset</th>
                  <th className="px-4 py-2 font-medium">Rate</th>
                  <th className="px-4 py-2 font-medium">Unit</th>
                  <th className="px-4 py-2 font-medium hidden md:table-cell">Notes</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-800 bg-white dark:bg-gray-900">
                {items.map((row, idx) => (
                  <tr key={`${category}-${idx}`} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                    <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">{row.name}</td>
                    <td className="px-4 py-3 text-blue-600 dark:text-blue-400 font-mono">{row.rate}</td>
                    <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{row.unit}</td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400 text-xs hidden md:table-cell">
                      {row.notes || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
      {grouped.length === 0 && (
        <p className="text-sm text-gray-500 text-center py-4">Pricing presets unavailable.</p>
      )}
    </div>
  );
}
