import React, { createContext, useContext, useState, useCallback } from 'react';

/**
 * Global context for tracking async large-file upload jobs.
 *
 * Flow:
 *  1. BucketDetail calls addUploadJob({ filename, fileSize, bucketName })
 *     immediately on form submit — returns a tempId — so the tray appears
 *     and the modal can close before the HTTP round-trip finishes.
 *  2. When the 202 response arrives, BucketDetail calls
 *     activateUploadJob(tempId, realJobId) so polling can begin.
 *  3. On 200 (small file) or error, BucketDetail calls removeUploadJob(tempId).
 */
const UploadJobContext = createContext(null);

export const useUploadJobs = () => {
  const ctx = useContext(UploadJobContext);
  if (!ctx) throw new Error('useUploadJobs must be used inside UploadJobProvider');
  return ctx;
};

export const UploadJobProvider = ({ children }) => {
  /**
   * Each job shape:
   *   { id: string,        ← stable local key
   *     jobId: string|null, ← null until 202 received
   *     filename: string,
   *     fileSize: string,
   *     bucketName: string,
   *     clientProgress: number, ← 0–100 from axios upload events
   *     syncComplete: boolean }  ← true for small-file 200 responses
   */
  const [jobs, setJobs] = useState([]);

  /** Step 1 — add placeholder before API call; returns the temp id */
  const addUploadJob = useCallback(({ filename, fileSize, bucketName }) => {
    const id = `upload-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setJobs((prev) => [
      ...prev,
      { id, jobId: null, filename, fileSize, bucketName, clientProgress: 0, syncComplete: false },
    ]);
    return id; // caller stores this to activate later
  }, []);

  const setJobClientProgress = useCallback((id, percent) => {
    setJobs((prev) =>
      prev.map((j) =>
        j.id === id ? { ...j, clientProgress: Math.min(100, Math.max(0, percent)) } : j
      )
    );
  }, []);

  const markJobSyncComplete = useCallback((id) => {
    setJobs((prev) =>
      prev.map((j) =>
        j.id === id ? { ...j, clientProgress: 100, syncComplete: true } : j
      )
    );
  }, []);

  /** Step 2 — attach real jobId once 202 comes back */
  const activateUploadJob = useCallback((id, jobId) => {
    setJobs((prev) =>
      prev.map((j) => (j.id === id ? { ...j, jobId } : j))
    );
  }, []);

  /** Remove a job from the tray (complete / failed / cancel) */
  const removeUploadJob = useCallback((id) => {
    setJobs((prev) => prev.filter((j) => j.id !== id));
  }, []);

  return (
    <UploadJobContext.Provider
      value={{
        jobs,
        addUploadJob,
        activateUploadJob,
        removeUploadJob,
        setJobClientProgress,
        markJobSyncComplete,
      }}
    >
      {children}
    </UploadJobContext.Provider>
  );
};
