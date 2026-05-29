import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Cloud, Folder, HardDrive, DollarSign, Activity, Database, ArrowRight } from 'lucide-react';
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip } from 'recharts';
import { getCurrentUsage } from '../api/billing';
import { getBuckets } from '../api/storage';
import { formatBytes } from '../utils/formatBytes';
import Spinner from '../components/Spinner';

export default function Dashboard() {
  const location = useLocation();
  const [loading, setLoading] = useState(true);
  const [usage, setUsage] = useState(null);
  const [buckets, setBuckets] = useState([]);

  useEffect(() => {
    let cancelled = false;
    const fetchData = async () => {
      setLoading(true);
      try {
        const [usageData, bucketsData] = await Promise.all([
          getCurrentUsage(),
          getBuckets()
        ]);
        if (!cancelled) {
          setUsage(usageData);
          setBuckets(bucketsData);
        }
      } catch (error) {
        console.error('Failed to fetch dashboard data', error);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchData();
    return () => { cancelled = true; };
  }, [location.key]);

  if (loading) {
    return <div className="flex h-full items-center justify-center"><Spinner size="lg" /></div>;
  }

  // Calculate some derived stats
  const totalObjects = buckets.reduce((acc, b) => acc + (b.objectCount || 0), 0);
  const totalStorage = buckets.reduce((acc, b) => acc + (b.totalSizeBytes || 0), 0);

  const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'];
  const pieData = buckets
    .filter(b => b.totalSizeBytes > 0)
    .map(b => ({
      name: b.name,
      value: b.totalSizeBytes
    }));

  const stats = [
    { name: 'Total Storage', value: formatBytes(totalStorage), icon: HardDrive, color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-100 dark:bg-blue-900/50' },
    { name: 'Total Objects', value: totalObjects, icon: Database, color: 'text-green-600 dark:text-green-400', bg: 'bg-green-100 dark:bg-green-900/50' },
    { name: 'Total Buckets', value: buckets.length, icon: Folder, color: 'text-amber-600 dark:text-amber-400', bg: 'bg-amber-100 dark:bg-amber-900/50' },
    { name: 'Est. Cost (MTD)', value: `$${(usage?.estimatedTotal || 0).toFixed(2)}`, icon: DollarSign, color: 'text-red-600 dark:text-red-400', bg: 'bg-red-100 dark:bg-red-900/50' },
    { name: 'Class A Requests', value: usage?.classARequests || 0, icon: Activity, color: 'text-purple-600 dark:text-purple-400', bg: 'bg-purple-100 dark:bg-purple-900/50' },
    { name: 'Bandwidth (MTD)', value: formatBytes(usage?.bandwidthBytesOut || 0), icon: Cloud, color: 'text-indigo-600 dark:text-indigo-400', bg: 'bg-indigo-100 dark:bg-indigo-900/50' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Dashboard</h1>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {stats.map((stat) => (
          <div key={stat.name} className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 p-6 flex items-center transition-colors">
            <div className={`p-3 rounded-lg ${stat.bg}`}>
              <stat.icon className={`w-6 h-6 ${stat.color}`} />
            </div>
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-500 dark:text-gray-400">{stat.name}</p>
              <p className="text-2xl font-semibold text-gray-900 dark:text-white">{stat.value}</p>
            </div>
          </div>
        ))}
      </div>

      <div>
        <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 p-6 transition-colors">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-6">Storage Distribution</h2>
          <div className="h-72">
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={80}
                    paddingAngle={5}
                    dataKey="value"
                  >
                    {pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip 
                    contentStyle={{ backgroundColor: '#1f2937', borderColor: '#374151', borderRadius: '8px', border: 'none', color: '#f3f4f6' }}
                    itemStyle={{ color: '#10b981' }}
                    formatter={(value, name) => [formatBytes(value), name]}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-full flex items-center justify-center text-gray-500 dark:text-gray-400">
                No storage data to display
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 transition-colors">
        <div className="px-6 py-5 border-b border-gray-200 dark:border-gray-800 flex justify-between items-center">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Recent Buckets</h2>
          <Link to="/buckets" className="text-sm font-medium text-blue-600 hover:text-blue-500 dark:text-blue-400 dark:hover:text-blue-300 flex items-center gap-1">
            View all <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
        <div className="divide-y divide-gray-200 dark:divide-gray-800">
          {buckets.slice(0, 5).map((bucket) => (
            <div key={bucket.name} className="px-6 py-4 flex items-center justify-between hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
              <div className="flex items-center gap-4">
                <div className="p-2 bg-gray-100 dark:bg-gray-800 rounded-lg">
                  <Folder className="w-5 h-5 text-gray-500 dark:text-gray-400" />
                </div>
                <div>
                  <Link to={`/buckets/${bucket.name}`} className="text-sm font-medium text-gray-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400">
                    {bucket.name}
                  </Link>
                  <p className="text-xs text-gray-500 dark:text-gray-400">{bucket.objectCount || 0} objects</p>
                </div>
              </div>
              <div className="text-sm text-gray-500 dark:text-gray-400">
                {formatBytes(bucket.totalSizeBytes || 0)}
              </div>
            </div>
          ))}
          {buckets.length === 0 && (
            <div className="px-6 py-8 text-center text-gray-500 dark:text-gray-400">
              No buckets created yet.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
