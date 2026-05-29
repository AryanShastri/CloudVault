import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

/**
 * Back navigation control with arrow icon.
 * Use `to` for router links, or `onClick` for custom actions (e.g. parent folder).
 */
export default function BackButton({
  to,
  onClick,
  label = 'Back',
  iconOnly = false,
  className = '',
}) {
  const baseClass =
    'inline-flex items-center gap-2 text-sm font-medium text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white transition-colors group';

  const content = iconOnly ? (
    <ArrowLeft
      className="w-5 h-5 group-hover:-translate-x-0.5 transition-transform"
      aria-hidden="true"
    />
  ) : (
    <>
      <ArrowLeft
        className="w-4 h-4 group-hover:-translate-x-0.5 transition-transform"
        aria-hidden="true"
      />
      <span>{label}</span>
    </>
  );

  const combinedClass = `${baseClass} ${iconOnly ? 'p-1.5 rounded-md hover:bg-gray-100 dark:hover:bg-gray-800' : ''} ${className}`.trim();

  if (to) {
    return (
      <Link to={to} className={combinedClass} aria-label={label}>
        {content}
      </Link>
    );
  }

  return (
    <button
      type="button"
      onClick={onClick}
      className={combinedClass}
      aria-label={label}
    >
      {content}
    </button>
  );
}
