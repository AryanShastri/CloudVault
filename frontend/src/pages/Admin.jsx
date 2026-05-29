import React, { useEffect, useState } from 'react';
import { getAdminOverview, getAdminUsers, runBilling } from '../api/admin';
import { Users, HardDrive, DollarSign, Activity, Play } from 'lucide-react';
import { formatBytes } from '../utils/formatBytes';
import Badge from '../components/Badge';
import Spinner from '../components/Spinner';
import { useToast } from '../components/Toast';

export default function Admin() {
  const [overview, setOverview] = useState(null);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [billingYear, setBillingYear] = useState(new Date().getFullYear());
  const [billingMonth, setBillingMonth] = useState(new Date().getMonth() + 1);
  const [runningBilling, setRunningBilling] = useState(false);
  const { showToast } = useToast();

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [overviewData, usersData] = await Promise.all([
        getAdminOverview(),
        getAdminUsers()
      ]);
      setOverview(overviewData);
      setUsers(usersData);
    } catch (error) {
      showToast('Failed to load admin data', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleRunBilling = async (e) => {
    e.preventDefault();
    if (!window.confirm(`Run global billing for ${billingYear}-${billingMonth}? This action cannot be undone.`)) return;
    
    setRunningBilling(true);
    try {
      await runBilling(billingYear, billingMonth);
      showToast('Billing run completed successfully');
      fetchData(); // refresh overview stats
    } catch (error) {
      showToast(error.response?.data?.message || 'Billing run failed', 'error');
    } finally {
      setRunningBilling(false);
    }
  };

  if (loading) return <div className="flex h-full items-center justify-center"><Spinner size="lg" /></div>;

  const stats = [
    { name: 'Total Users', value: overview?.totalUsers || 0, icon: Users, color: 'text-blue-600', bg: 'bg-blue-100' },
    { name: 'Total Platform Storage', value: formatBytes(overview?.totalStorageBytes || 0), icon: HardDrive, color: 'text-green-600', bg: 'bg-green-100' },
    { name: 'Revenue (MTD)', value: `$${(overview?.revenueThisMonth || 0).toFixed(2)}`, icon: Activity, color: 'text-amber-600', bg: 'bg-amber-100' },
    { name: 'All Time Revenue', value: `$${(overview?.revenueAllTime || 0).toFixed(2)}`, icon: DollarSign, color: 'text-purple-600', bg: 'bg-purple-100' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Platform Admin</h1>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {stats.map((stat) => (
          <div key={stat.name} className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 p-6 flex items-center">
            <div className={`p-3 rounded-lg ${stat.bg} dark:bg-opacity-20`}>
              <stat.icon className={`w-6 h-6 ${stat.color} dark:opacity-80`} />
            </div>
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-500 dark:text-gray-400">{stat.name}</p>
              <p className="text-2xl font-semibold text-gray-900 dark:text-white">{stat.value}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 flex flex-col">
          <div className="px-6 py-5 border-b border-gray-200 dark:border-gray-800">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Registered Users</h2>
          </div>
          <div className="overflow-x-auto flex-1">
            <table className="w-full text-left text-sm whitespace-nowrap">
              <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-600 dark:text-gray-400 border-b border-gray-200 dark:border-gray-800">
                <tr>
                  <th className="px-6 py-3 font-semibold">User</th>
                  <th className="px-6 py-3 font-semibold">Tenant ID</th>
                  <th className="px-6 py-3 font-semibold">Storage Used</th>
                  <th className="px-6 py-3 font-semibold">Total Billed</th>
                  <th className="px-6 py-3 font-semibold">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
                {users.map((user) => (
                  <tr key={user.tenantId} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                    <td className="px-6 py-4">
                      <div className="font-medium text-gray-900 dark:text-white">{user.username}</div>
                      <div className="text-gray-500 dark:text-gray-400 text-xs">{user.email}</div>
                    </td>
                    <td className="px-6 py-4 font-mono text-gray-500 text-xs">{user.tenantId}</td>
                    <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{formatBytes(user.storageUsed || 0)}</td>
                    <td className="px-6 py-4 text-gray-900 dark:text-white font-medium">${(user.totalBilled || 0).toFixed(2)}</td>
                    <td className="px-6 py-4"><Badge text={user.isActive ? 'Active' : 'Inactive'} color={user.isActive ? 'green' : 'gray'} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="lg:col-span-1 bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 p-6">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Manual Billing Run</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">
            Trigger a manual billing cycle for all users. This will calculate usage, generate invoices, and charge accounts for the specified period.
          </p>
          
          <form onSubmit={handleRunBilling} className="space-y-4 bg-gray-50 dark:bg-gray-800/50 p-5 rounded-lg border border-gray-200 dark:border-gray-700">
            <div className="flex gap-4">
              <div className="flex-1">
                <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">Year</label>
                <input 
                  type="number" 
                  required 
                  value={billingYear}
                  onChange={e => setBillingYear(parseInt(e.target.value))}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-md dark:bg-gray-900 text-sm"
                />
              </div>
              <div className="flex-1">
                <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">Month</label>
                <input 
                  type="number" 
                  required 
                  min="1" 
                  max="12"
                  value={billingMonth}
                  onChange={e => setBillingMonth(parseInt(e.target.value))}
                  className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-md dark:bg-gray-900 text-sm"
                />
              </div>
            </div>
            
            <button 
              type="submit" 
              disabled={runningBilling}
              className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-md transition-colors focus:ring-2 focus:ring-red-500 focus:ring-offset-2 disabled:opacity-50 mt-4"
            >
              {runningBilling ? <Spinner size="sm" className="text-white" /> : <Play className="w-4 h-4 fill-current" />}
              Run Global Billing
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
