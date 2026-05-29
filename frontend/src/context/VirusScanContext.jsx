import React, { createContext, useCallback, useContext, useState } from 'react';
import VirusScanPopups from '../components/VirusScanPopups';

const VirusScanContext = createContext(null);

export const useVirusScan = () => {
  const ctx = useContext(VirusScanContext);
  if (!ctx) throw new Error('useVirusScan must be used inside VirusScanProvider');
  return ctx;
};

export const VirusScanProvider = ({ children }) => {
  const [scanning, setScanning] = useState(null);
  const [virusFound, setVirusFound] = useState(null);
  const [downloadWarning, setDownloadWarning] = useState(null);

  const showScanningPopup = useCallback((filename, fileSize = null) => {
    setScanning({ filename, fileSize });
  }, []);

  const closeScanningPopup = useCallback(() => {
    setScanning(null);
  }, []);

  const showVirusFoundPopup = useCallback((filename, virusName) => {
    setScanning(null);
    setVirusFound({
      filename,
      virusName: virusName || 'Unknown threat',
    });
  }, []);

  const closeVirusFoundPopup = useCallback(() => {
    setVirusFound(null);
  }, []);

  const showDownloadWarningPopup = useCallback((filename, virusName) => {
    return new Promise((resolve) => {
      setDownloadWarning({
        filename,
        virusName: virusName || 'Unknown threat',
        resolve,
      });
    });
  }, []);

  const dismissDownloadWarning = useCallback((confirmed) => {
    setDownloadWarning((prev) => {
      prev?.resolve(confirmed);
      return null;
    });
  }, []);

  const value = {
    scanning,
    showScanningPopup,
    closeScanningPopup,
    showVirusFoundPopup,
    closeVirusFoundPopup,
    showDownloadWarningPopup,
  };

  return (
    <VirusScanContext.Provider value={value}>
      {children}
      <VirusScanPopups
        virusFound={virusFound}
        downloadWarning={downloadWarning}
        onCloseVirusFound={closeVirusFoundPopup}
        onDismissDownloadWarning={dismissDownloadWarning}
      />
    </VirusScanContext.Provider>
  );
};

/** Parse threat name from backend upload rejection message. */
export function parseUploadThreatMessage(message) {
  if (!message) return 'Unknown threat';
  return message.split('Threat:')[1]?.trim() || 'Unknown threat';
}

/** Parse threat name from async job error message. */
export function parseAsyncMalwareMessage(errorMessage) {
  if (!errorMessage) return 'Unknown threat';
  return errorMessage.split('Malware detected:')[1]?.trim() || 'Unknown threat';
}

export function isMalwareUploadError(error) {
  return (
    error?.response?.status === 409 &&
    error.response?.data?.message?.toLowerCase().includes('malware')
  );
}
