import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { UploadJobProvider } from './context/UploadJobContext';
import { useToast } from './components/Toast';

import Sidebar from './components/Sidebar';
import UploadProgressPopup from './components/UploadProgressPopup';
import { useVirusScan } from './context/VirusScanContext';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Buckets from './pages/Buckets';
import BucketDetail from './pages/BucketDetail';
import Billing from './pages/Billing';
import Invoices from './pages/Invoices';
import AuditLogs from './pages/AuditLogs';
import Admin from './pages/Admin';

const ProtectedRoute = ({ children, requireAdmin = false }) => {
  const { isAuthenticated, user } = useAuth();
  
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  
  if (requireAdmin && user?.role !== 'ADMIN') {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
};

const Layout = () => {
  return (
    <div className="flex h-screen bg-gray-50 dark:bg-gray-950 transition-colors">
      <Sidebar />
      <div className="flex-1 overflow-auto">
        <main className="p-8 max-w-7xl mx-auto h-full">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

/** Inner component so it can use useToast (which needs ToastProvider above it in main.jsx) */
function AppInner() {
  const { isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const { showVirusFoundPopup } = useVirusScan();

  return (
    <UploadJobProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login"    element={!isAuthenticated ? <Login />    : <Navigate to="/dashboard" />} />
          <Route path="/register" element={!isAuthenticated ? <Register /> : <Navigate to="/dashboard" />} />
          
          <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
            <Route path="/dashboard"            element={<Dashboard />} />
            <Route path="/buckets"              element={<Buckets />} />
            <Route path="/buckets/:bucketName"  element={<BucketDetail />} />
            <Route path="/billing"              element={<Billing />} />
            <Route path="/invoices"             element={<Invoices />} />
            <Route path="/audit"               element={<AuditLogs />} />
            
            <Route
              path="/admin"
              element={
                <ProtectedRoute requireAdmin={true}>
                  <Admin />
                </ProtectedRoute>
              }
            />
          </Route>

          <Route path="/"  element={<Navigate to={isAuthenticated ? '/dashboard' : '/login'} replace />} />
          <Route path="*"  element={<Navigate to="/" replace />} />
        </Routes>

        {/* Global floating upload tray — survives page navigation */}
        <UploadProgressPopup
          onJobComplete={() => showToast('File uploaded successfully')}
          onJobFailed={(msg) => {
            if (msg?.toLowerCase().includes('malware')) return;
            showToast(msg || 'Upload failed. Please try again.', 'error');
          }}
          onMalwareRejected={(filename, virusName) => showVirusFoundPopup(filename, virusName)}
        />
      </BrowserRouter>
    </UploadJobProvider>
  );
}

export default function App() {
  return <AppInner />;
}
