import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { getAuditLogs, getBucketAuditLogs } from '../api/audit';
import { getBuckets } from '../api/storage';
import { Filter, Search } from 'lucide-react';
import Badge from '../components/Badge';
import Spinner from '../components/Spinner';
import Pagination from '../components/Pagination';
import { formatBytes } from '../utils/formatBytes';
import { useToast } from '../components/Toast';

export default function AuditLogs() {
  const location = useLocation();
  const [logsData, setLogsData] = useState(null);
  const [buckets, setBuckets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedBucket, setSelectedBucket] = useState('');
  const [page, setPage] = useState(0);
  const { showToast } = useToast();

  useEffect(() => {
    fetchBuckets();
  }, []);

  useEffect(() => {
    fetchLogs(page, selectedBucket);
  }, [page, selectedBucket, location.key]);

  const fetchBuckets = async () => {
    try {
      const data = await getBuckets();
      setBuckets(data);
    } catch (error) {
      console.error(error);
    }
  };

  const fetchLogs = async (pageNum, bucket) => {
    setLoading(true);
    try {
      let data;
      if (bucket) {
        data = await getBucketAuditLogs(bucket, pageNum, 20);
      } else {
        data = await getAuditLogs(pageNum, 20);
      }
      setLogsData(data);
    } catch (error) {
      showToast('Failed to fetch audit logs', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleFilterChange = (e) => {
    setSelectedBucket(e.target.value);
    setPage(0);
  };

  return (
    <div className="space-y-6 h-full flex flex-col">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Audit Logs</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Track API operations and lifecycle events</p>
        </div>
        
        <div className="flex items-center gap-3">
          <div className="relative">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <Filter className="h-4 w-4 text-gray-400" />
            </div>
            <select
              value={selectedBucket}
              onChange={handleFilterChange}
              className="pl-10 pr-8 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-700 rounded-lg text-sm text-gray-900 dark:text-white focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="">All Buckets</option>
              {buckets.map(b => (
                <option key={b.name} value={b.name}>{b.name}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 flex-1 flex flex-col min-h-[500px]">
        {loading && !logsData ? (
          <div className="flex-1 flex justify-center items-center"><Spinner size="lg" /></div>
        ) : (
          <>
            <div className="flex-1 overflow-x-auto">
              <table className="w-full text-left text-sm whitespace-nowrap">
                <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-600 dark:text-gray-400 sticky top-0 z-10">
                  <tr>
                    <th className="px-6 py-3 font-semibold">Timestamp</th>
                    <th className="px-6 py-3 font-semibold">Operation</th>
                    <th className="px-6 py-3 font-semibold">Bucket</th>
                    <th className="px-6 py-3 font-semibold">Object Key</th>
                    <th className="px-6 py-3 font-semibold">Size</th>
                    <th className="px-6 py-3 font-semibold">Class</th>
                    <th className="px-6 py-3 font-semibold">Tier</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
                  {logsData?.content?.map((log) => (
                    <tr key={log.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                      <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{new Date(log.timestamp).toLocaleString()}</td>
                      <td className="px-6 py-4"><Badge text={log.operationType} /></td>
                      <td className="px-6 py-4 font-medium text-gray-900 dark:text-white">{log.bucketName}</td>
                      <td className="px-6 py-4 font-mono text-gray-600 dark:text-gray-400 max-w-[200px] truncate">{log.objectKey || '-'}</td>
                      <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{log.sizeBytes > 0 ? formatBytes(log.sizeBytes) : '-'}</td>
                      <td className="px-6 py-4"><span className="text-xs font-mono bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded">{log.requestClass}</span></td>
                      <td className="px-6 py-4"><Badge text={log.tierAtTime || 'N/A'} color="gray" /></td>
                    </tr>
                  ))}
                  {(!logsData?.content || logsData.content.length === 0) && (
                    <tr>
                      <td colSpan="7" className="px-6 py-12 text-center text-gray-500 dark:text-gray-400">
                        No audit logs found.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {logsData && <Pagination pageData={logsData} onPageChange={setPage} />}
          </>
        )}
      </div>
    </div>
  );
}
