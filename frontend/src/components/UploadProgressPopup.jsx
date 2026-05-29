import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { X, ChevronDown, ChevronUp, Loader2, CheckCircle2, AlertCircle, Upload } from 'lucide-react';
import { getUploadJob } from '../api/storage';
import { useUploadJobs } from '../context/UploadJobContext';
import { useVirusScan, parseAsyncMalwareMessage } from '../context/VirusScanContext';
import { VirusScanSideCard } from './VirusScanPopups';

const CLIENT_PHASE_MAX = 38;
const POLL_MS = 600;

function stageLabel(pct, hasJobId, isScanning) {
  if (isScanning) return 'Scanning for viruses…';
  if (!hasJobId) return 'Sending file to server…';
  if (pct < 15) return 'Preparing upload…';
  if (pct < 30) return 'Uploading to storage…';
  if (pct < 55) return 'Scanning for viruses…';
  if (pct < 80) return 'Moving to bucket…';
  if (pct < 95) return 'Saving metadata…';
  if (pct < 100) return 'Finalizing…';
  return 'Complete!';
}

function computeTargetProgress(job, serverProgress) {
  if (job.syncComplete) return 100;
  if (!job.jobId) {
    return ((job.clientProgress ?? 0) / 100) * CLIENT_PHASE_MAX;
  }
  return CLIENT_PHASE_MAX + (serverProgress / 100) * (100 - CLIENT_PHASE_MAX);
}

function JobCard({ job, onDone, onFail, onCancel, onMalwareRejected }) {
  const { showScanningPopup, closeScanningPopup } = useVirusScan();

  const [serverProgress, setServerProgress] = useState(0);
  const [displayProgress, setDisplayProgress] = useState(0);
  const [status, setStatus] = useState('PENDING');
  const [errorMsg, setErrorMsg] = useState(null);
  const pollRef = useRef(null);
  const animRef = useRef(null);
  const scanningShownRef = useRef(false);

  const targetProgress = useMemo(
    () => computeTargetProgress(job, serverProgress),
    [job, serverProgress]
  );

  useEffect(() => {
    animRef.current = setInterval(() => {
      setDisplayProgress((prev) => {
        const target = targetProgress;
        if (prev >= target) return prev;
        const gap = target - prev;
        const step = Math.min(Math.max(gap * 0.08, 0.35), 6);
        return Math.min(+(prev + step).toFixed(1), target);
      });
    }, 35);
    return () => clearInterval(animRef.current);
  }, [targetProgress]);

  const stopPolling = useCallback(() => clearInterval(pollRef.current), []);

  useEffect(() => {
    if (job.syncComplete) {
      setStatus('COMPLETED');
      setServerProgress(100);
      closeScanningPopup();
      const t = setTimeout(() => onDone(job.id), 1000);
      return () => clearTimeout(t);
    }
  }, [job.syncComplete, job.id, onDone, closeScanningPopup]);

  useEffect(() => {
    if (!job.jobId) return;

    const poll = async () => {
      try {
        const data = await getUploadJob(job.jobId);
        setServerProgress(data.progressPercent ?? 0);
        setStatus(data.status);

        if (data.status === 'SCANNING' && !scanningShownRef.current) {
          scanningShownRef.current = true;
          showScanningPopup(job.filename, job.fileSize);
        }

        if (data.status === 'REJECTED') {
          stopPolling();
          closeScanningPopup();
          scanningShownRef.current = false;
          const virusName = parseAsyncMalwareMessage(data.errorMessage);
          onMalwareRejected(job.filename, virusName);
          setErrorMsg(data.errorMessage || 'Malware detected.');
          setTimeout(() => onFail(job.id, data.errorMessage), 1500);
        }

        if (data.status === 'COMPLETED') {
          closeScanningPopup();
          scanningShownRef.current = false;
          setServerProgress(100);
          stopPolling();
          setTimeout(() => onDone(job.id), 1000);
        }

        if (data.status === 'FAILED') {
          closeScanningPopup();
          scanningShownRef.current = false;
          stopPolling();
          setErrorMsg(data.errorMessage || 'Upload failed.');
          setTimeout(() => onFail(job.id, data.errorMessage), 1500);
        }
      } catch (err) {
        console.error('Upload poll error', err);
      }
    };

    poll();
    pollRef.current = setInterval(poll, POLL_MS);
    return stopPolling;
  }, [job.jobId, job.filename, job.fileSize, onDone, onFail, onMalwareRejected, showScanningPopup, closeScanningPopup, stopPolling]);

  const isFinal =
    status === 'COMPLETED' || status === 'FAILED' || status === 'REJECTED' || job.syncComplete;
  const isWaiting = !job.jobId && !job.syncComplete;
  const isScanning = status === 'SCANNING';

  const pctInt =
    isFinal && (status === 'COMPLETED' || job.syncComplete)
      ? 100
      : Math.min(99, Math.floor(displayProgress));

  const barClass =
    status === 'COMPLETED' || job.syncComplete
      ? 'from-emerald-400 to-emerald-500'
      : status === 'FAILED' || status === 'REJECTED'
        ? 'from-red-400 to-red-500'
        : 'from-blue-500 to-indigo-500';

  const pctClass =
    status === 'COMPLETED' || job.syncComplete
      ? 'text-emerald-400'
      : status === 'FAILED' || status === 'REJECTED'
        ? 'text-red-400'
        : 'text-blue-400';

  const iconEl =
    status === 'COMPLETED' || job.syncComplete ? (
      <CheckCircle2 className="w-4 h-4 text-emerald-400" />
    ) : status === 'FAILED' || status === 'REJECTED' ? (
      <AlertCircle className="w-4 h-4 text-red-400" />
    ) : (
      <Loader2 className="w-4 h-4 text-blue-400 animate-spin" />
    );

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-xl overflow-hidden">
      <div className="flex items-center gap-2 px-3 pt-3 pb-2">
        <div className="shrink-0">{iconEl}</div>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium text-white truncate">{job.filename}</p>
          <p className="text-[10px] text-gray-400">{job.fileSize}</p>
        </div>
        <button
          onClick={() => {
            stopPolling();
            closeScanningPopup();
            onCancel(job.id);
          }}
          className="shrink-0 text-gray-500 hover:text-gray-300 transition-colors"
          title={isFinal ? 'Dismiss' : 'Cancel'}
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>

      <div className="px-3 pb-3 space-y-1.5">
        <div className="flex justify-between items-center">
          <span className="text-[10px] text-gray-400 truncate max-w-[72%]">
            {status === 'FAILED' || status === 'REJECTED'
              ? (errorMsg ?? 'Upload failed')
              : stageLabel(displayProgress, !isWaiting, isScanning)}
          </span>
          <span className={`text-[10px] font-bold tabular-nums ${pctClass}`}>
            {`${pctInt}%`}
          </span>
        </div>

        <div className="w-full h-1.5 bg-gray-700 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full bg-gradient-to-r relative overflow-hidden ${barClass}`}
            style={{ width: `${Math.max(pctInt, isWaiting && pctInt === 0 ? 2 : 0)}%`, transition: 'width 60ms linear' }}
          >
            {!isFinal && (
              <span
                className="absolute inset-0"
                style={{
                  backgroundImage:
                    'linear-gradient(90deg,transparent 0%,rgba(255,255,255,0.28) 50%,transparent 100%)',
                  backgroundSize: '200% 100%',
                  animation: 'cv-shimmer 1.6s infinite',
                }}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function UploadProgressPopup({ onJobComplete, onJobFailed, onMalwareRejected }) {
  const { jobs, removeUploadJob } = useUploadJobs();
  const { scanning } = useVirusScan();
  const [collapsed, setCollapsed] = useState(false);

  if (jobs.length === 0 && !scanning) return null;

  const handleDone = (id) => {
    removeUploadJob(id);
    onJobComplete?.();
  };
  const handleFail = (id, msg) => {
    removeUploadJob(id);
    onJobFailed?.(msg);
  };
  const handleCancel = (id) => removeUploadJob(id);

  return (
    <>
      <style>{`
        @keyframes cv-shimmer {
          0%   { background-position: -200% 0; }
          100% { background-position:  200% 0; }
        }
        @keyframes cv-slide-up {
          from { opacity: 0; transform: translateY(14px); }
          to   { opacity: 1; transform: translateY(0);    }
        }
      `}</style>

      <div
        className="fixed bottom-5 right-5 z-[9999] flex flex-col gap-2 items-end"
        style={{ animation: 'cv-slide-up 0.22s ease-out' }}
      >
        {scanning && <VirusScanSideCard scanning={scanning} />}

        {jobs.length > 0 && (
          <div className="flex flex-col gap-2 w-80">
            <div
              className="flex items-center justify-between bg-gray-900 border border-gray-700 rounded-xl px-4 py-2.5 shadow-2xl cursor-pointer select-none"
              onClick={() => setCollapsed((c) => !c)}
            >
              <div className="flex items-center gap-2">
                <div className="relative">
                  <Upload className="w-4 h-4 text-blue-400" />
                  <span className="absolute -top-1.5 -right-1.5 w-3.5 h-3.5 bg-blue-500 text-white text-[8px] font-bold rounded-full flex items-center justify-center">
                    {jobs.length}
                  </span>
                </div>
                <span className="text-sm font-semibold text-white">
                  {jobs.length === 1 ? 'Uploading 1 file' : `Uploading ${jobs.length} files`}
                </span>
              </div>
              <span className="text-gray-400">
                {collapsed ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
              </span>
            </div>

            {!collapsed && (
              <div className="flex flex-col gap-2 max-h-[60vh] overflow-y-auto">
                {jobs.map((job) => (
                  <div key={job.id} className="shadow-xl">
                    <JobCard
                      job={job}
                      onDone={handleDone}
                      onFail={handleFail}
                      onCancel={handleCancel}
                      onMalwareRejected={onMalwareRejected}
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
}
