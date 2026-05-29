import React from 'react';
import { FileText, Search, ShieldAlert, AlertTriangle } from 'lucide-react';
import { formatBytes } from '../utils/formatBytes';

/** Non-blocking side card — sits above the upload tray; user can keep working */
export function VirusScanSideCard({ scanning }) {
  if (!scanning) return null;

  const sizeLabel =
    scanning.fileSize != null
      ? typeof scanning.fileSize === 'string'
        ? scanning.fileSize
        : formatBytes(scanning.fileSize)
      : null;

  return (
    <div
      className="w-80 bg-gray-900 border border-blue-500/40 rounded-xl shadow-2xl overflow-hidden"
      style={{ animation: 'cv-slide-up 0.22s ease-out' }}
      role="status"
      aria-live="polite"
    >
      <div className="flex items-center gap-2 px-4 py-3 border-b border-gray-800">
        <Search className="w-4 h-4 text-blue-400 shrink-0 animate-pulse" />
        <span className="text-sm font-semibold text-white">Scanning for viruses…</span>
      </div>

      <div className="px-4 py-3 space-y-2.5">
        <div className="flex items-center gap-2 text-xs text-gray-300">
          <FileText className="w-3.5 h-3.5 shrink-0 text-gray-500" />
          <span className="truncate font-medium">{scanning.filename}</span>
          {sizeLabel && (
            <span className="text-gray-500 shrink-0">({sizeLabel})</span>
          )}
        </div>

        <div className="w-full h-1.5 bg-gray-700 rounded-full overflow-hidden">
          <div
            className="h-full w-1/3 rounded-full bg-gradient-to-r from-blue-500 to-indigo-500"
            style={{ animation: 'cv-scan-indeterminate 1.4s ease-in-out infinite' }}
          />
        </div>

        <p className="text-[10px] text-gray-500">You can continue using the app while we scan.</p>
      </div>

      <style>{`
        @keyframes cv-scan-indeterminate {
          0%   { transform: translateX(-100%); }
          100% { transform: translateX(400%); }
        }
        @keyframes cv-slide-up {
          from { opacity: 0; transform: translateY(14px); }
          to   { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}

function BlockingOverlay({ children }) {
  return (
    <div className="fixed inset-0 z-[10050] flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/60 dark:bg-black/75 backdrop-blur-sm" aria-hidden />
      <div
        className="relative w-full max-w-md rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-2xl p-6"
        role="alertdialog"
        aria-modal="true"
      >
        {children}
      </div>
    </div>
  );
}

function VirusFoundPopup({ virusFound, onClose }) {
  return (
    <BlockingOverlay>
      <div className="flex items-center gap-3 mb-4">
        <ShieldAlert className="w-6 h-6 text-red-500 shrink-0" />
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Malware Detected</h3>
      </div>

      <dl className="space-y-2 text-sm mb-5">
        <div>
          <dt className="text-gray-500 dark:text-gray-400">File</dt>
          <dd className="font-medium text-gray-900 dark:text-white truncate">{virusFound.filename}</dd>
        </div>
        <div>
          <dt className="text-gray-500 dark:text-gray-400">Threat</dt>
          <dd className="font-medium text-red-600 dark:text-red-400">{virusFound.virusName}</dd>
        </div>
      </dl>

      <p className="text-sm text-gray-600 dark:text-gray-300 mb-6">
        This file has been rejected and was not uploaded to your storage.
      </p>

      <button
        type="button"
        onClick={onClose}
        className="w-full py-2.5 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded-lg transition-colors"
      >
        Okay, I got it
      </button>
    </BlockingOverlay>
  );
}

function DownloadWarningPopup({ downloadWarning, onDismiss }) {
  return (
    <BlockingOverlay>
      <div className="flex items-center gap-3 mb-4">
        <AlertTriangle className="w-6 h-6 text-amber-500 shrink-0" />
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Security Warning</h3>
      </div>

      <dl className="space-y-2 text-sm mb-4">
        <div>
          <dt className="text-gray-500 dark:text-gray-400">File</dt>
          <dd className="font-medium text-gray-900 dark:text-white truncate">{downloadWarning.filename}</dd>
        </div>
        <div>
          <dt className="text-gray-500 dark:text-gray-400">Threat</dt>
          <dd className="font-medium text-amber-600 dark:text-amber-400">{downloadWarning.virusName}</dd>
        </div>
      </dl>

      <p className="text-sm text-gray-600 dark:text-gray-300 mb-6">
        This file may contain malware. Downloading it could harm your computer.
      </p>

      <div className="flex gap-3">
        <button
          type="button"
          onClick={() => onDismiss(false)}
          className="flex-1 py-2.5 text-sm font-medium text-gray-700 dark:text-gray-200 bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-lg transition-colors"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => onDismiss(true)}
          className="flex-1 py-2.5 text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 rounded-lg transition-colors"
        >
          Download Anyway
        </button>
      </div>
    </BlockingOverlay>
  );
}

/** Blocking modals only (malware / download warning) */
export default function VirusScanPopups({
  virusFound,
  downloadWarning,
  onCloseVirusFound,
  onDismissDownloadWarning,
}) {
  if (virusFound) {
    return <VirusFoundPopup virusFound={virusFound} onClose={onCloseVirusFound} />;
  }
  if (downloadWarning) {
    return (
      <DownloadWarningPopup
        downloadWarning={downloadWarning}
        onDismiss={onDismissDownloadWarning}
      />
    );
  }
  return null;
}
