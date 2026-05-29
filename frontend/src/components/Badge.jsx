import React from 'react';

export default function Badge({ text, color = 'gray', className = '' }) {
  const colorStyles = {
    green: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400 border-green-200 dark:border-green-800',
    yellow: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400 border-yellow-200 dark:border-yellow-800',
    blue: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400 border-blue-200 dark:border-blue-800',
    navy: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-400 border-indigo-200 dark:border-indigo-800',
    red: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400 border-red-200 dark:border-red-800',
    gray: 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300 border-gray-200 dark:border-gray-700',
  };

  // Maps some standard keywords to colors
  const getColor = () => {
    if (color !== 'gray') return colorStyles[color] || colorStyles.gray;
    
    const lowerText = text?.toLowerCase() || '';
    if (['standard', 'put', 'paid', 'active'].includes(lowerText)) return colorStyles.green;
    if (['warm', 'list', 'pending'].includes(lowerText)) return colorStyles.yellow;
    if (['instant_glacier', 'get', 'generated', 'completed'].includes(lowerText)) return colorStyles.blue;
    if (['deep_glacier'].includes(lowerText)) return colorStyles.navy;
    if (['delete', 'overdue', 'expired'].includes(lowerText)) return colorStyles.red;
    
    return colorStyles.gray;
  };

  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${getColor()} ${className}`}>
      {text}
    </span>
  );
}
