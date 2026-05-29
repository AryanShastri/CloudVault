import React, { useState } from 'react';
import { ChevronDown, ChevronRight, Info } from 'lucide-react';
import { formatBytes } from '../utils/formatBytes';
import { formatCurrency } from '../utils/formatCurrency';

const CHARGE_LINES = [
  {
    key: 'storageCharge',
    label: 'Storage',
    detail: (item) =>
      `${formatBytes(item.storageBytesUsed || 0)} at ${item.storageClass || 'STANDARD'} tier. Billed per GB-month using lifecycle tier rates.`,
  },
  {
    key: 'classACharge',
    label: 'Class A Requests',
    detail: (item) =>
      `${item.classARequests || 0} write/list operations. Class A is billed per 1,000 requests (e.g. PUT, POST, LIST).`,
  },
  {
    key: 'classBCharge',
    label: 'Class B Requests',
    detail: (item) =>
      `${item.classBRequests || 0} read/metadata operations. Class B is billed per 10,000 requests (e.g. GET, HEAD).`,
  },
  {
    key: 'bandwidthCharge',
    label: 'Bandwidth Out',
    detail: (item) =>
      `${formatBytes(item.bandwidthBytesOut || 0)} egress. Tiered pricing applies: lower rates for higher monthly volume.`,
  },
  {
    key: 'retrievalCharge',
    label: 'Data Retrieval',
    detail: (item) =>
      `Applies when data is read from archive/cold tiers (${item.storageClass || 'N/A'}). Based on bytes retrieved this period.`,
  },
  {
    key: 'versioningStorageCharge',
    label: 'Versioning Storage',
    detail: (item) =>
      `Noncurrent version storage (${formatBytes(item.noncurrentVersionBytes || 0)}) with a noncurrent discount, plus noncurrent version downloads (with a surcharge on normal bandwidth rates).`,
  },
];

function InfoTip({ text }) {
  return (
    <span className="relative inline-flex group ml-1">
      <Info className="w-3.5 h-3.5 text-gray-400 hover:text-blue-500 cursor-help" />
      <span
        role="tooltip"
        className="pointer-events-none absolute left-1/2 -translate-x-1/2 bottom-full mb-2 z-20 hidden group-hover:block w-64 rounded-lg bg-gray-900 dark:bg-gray-700 text-white text-xs p-3 shadow-lg leading-relaxed"
      >
        {text}
      </span>
    </span>
  );
}

export default function CostBreakdown({ bucketItems = [], emptyMessage, suffix = '' }) {
  const [expanded, setExpanded] = useState({});

  const toggle = (name) => {
    setExpanded((prev) => ({ ...prev, [name]: !prev[name] }));
  };

  if (!bucketItems.length) {
    return (
      <p className="px-6 py-12 text-center text-gray-500 dark:text-gray-400 text-sm">
        {emptyMessage || 'No bucket usage to display for this period.'}
      </p>
    );
  }

  return (
    <div className="divide-y divide-gray-200 dark:divide-gray-800">
      {bucketItems.map((item) => {
        const isOpen = expanded[item.bucketName];
        const chargeRows = CHARGE_LINES.filter((line) => {
          const amount = item[line.key];
          return amount != null && (typeof amount === 'number' ? amount > 0 : parseFloat(amount) > 0);
        });

        return (
          <div key={item.bucketName}>
            <button
              type="button"
              onClick={() => toggle(item.bucketName)}
              className="w-full flex items-center gap-3 px-6 py-4 hover:bg-gray-50 dark:hover:bg-gray-800/50 text-left transition-colors"
            >
              {isOpen ? (
                <ChevronDown className="w-4 h-4 text-gray-400 shrink-0" />
              ) : (
                <ChevronRight className="w-4 h-4 text-gray-400 shrink-0" />
              )}
              <div className="flex-1 min-w-0 flex items-center justify-between gap-4">
                <div>
                  <p className="font-medium text-gray-900 dark:text-white truncate">{item.bucketName}</p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">{item.storageClass}</p>
                </div>
                <p className="text-sm font-semibold text-blue-600 dark:text-blue-400 shrink-0">
                  {formatCurrency(item.subtotal)}{suffix}
                </p>
              </div>
            </button>

            {isOpen && (
              <div className="px-6 pb-4 pl-14">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-gray-500 dark:text-gray-400 text-left">
                      <th className="pb-2 font-medium">Charge type</th>
                      <th className="pb-2 font-medium text-right">Amount</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                    {chargeRows.length > 0 ? (
                      chargeRows.map((line) => (
                        <tr key={line.key} className="text-gray-700 dark:text-gray-300">
                          <td className="py-2 pr-4">
                            <span className="inline-flex items-center">
                              {line.label}
                              <InfoTip text={line.detail(item)} />
                            </span>
                          </td>
                          <td className="py-2 text-right font-medium">
                            {formatCurrency(item[line.key])}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={2} className="py-3 text-gray-500 text-xs">
                          No billable charges recorded for this bucket yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
