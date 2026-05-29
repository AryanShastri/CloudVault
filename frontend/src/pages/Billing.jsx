import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { getCurrentUsage, generateInvoice } from '../api/billing';
import { formatBytes } from '../utils/formatBytes';
import { formatCurrency } from '../utils/formatCurrency';
import { CreditCard, Download, HardDrive, Activity, Cloud, Info, History } from 'lucide-react';
import Spinner from '../components/Spinner';
import CostBreakdown from '../components/CostBreakdown';
import PricingReference from '../components/PricingReference';
import { useToast } from '../components/Toast';

export default function Billing() {
  const location = useLocation();
  const navigate = useNavigate();
  const [usage, setUsage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const { showToast } = useToast();

  const fetchUsage = async () => {
    setLoading(true);
    try {
      const data = await getCurrentUsage();
      setUsage(data);
    } catch (error) {
      showToast('Failed to fetch billing usage', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsage();
  }, [location.key]);

  const handleGenerateInvoice = async () => {
    setGenerating(true);
    try {
      const data = await generateInvoice();
      showToast(`Invoice #${data.id} saved (${formatCurrency(data.amountDue)})`);
      fetchUsage();
      navigate('/invoices');
    } catch (error) {
      showToast(error.response?.data?.message || 'Failed to generate invoice', 'error');
    } finally {
      setGenerating(false);
    }
  };

  if (loading) return <div className="flex h-full items-center justify-center"><Spinner size="lg" /></div>;

  return (
    <div className="space-y-8">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Billing & Usage</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">View your current month's live usage and estimated costs</p>
        </div>
        <button
          onClick={handleGenerateInvoice}
          disabled={generating}
          className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg disabled:opacity-50 transition-colors"
        >
          {generating ? <Spinner size="sm" className="text-white" /> : <Download className="w-4 h-4" />}
          Generate / Refresh Invoice
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-6">
        <div className="bg-white dark:bg-gray-900 p-6 rounded-xl border border-gray-200 dark:border-gray-800 shadow-sm flex flex-col justify-between">
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 bg-blue-50 dark:bg-blue-900/30 rounded-lg">
              <HardDrive className="w-5 h-5 text-blue-600 dark:text-blue-400" />
            </div>
            <h3 className="font-medium text-gray-700 dark:text-gray-300">Storage Used</h3>
          </div>
          <div>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatBytes(usage?.storageBytesUsed || 0)}</p>
            <p className="text-sm text-gray-500 mt-1">{formatCurrency(usage?.estimatedStorageCharge)} est. charge</p>
          </div>
        </div>

        <div className="bg-white dark:bg-gray-900 p-6 rounded-xl border border-gray-200 dark:border-gray-800 shadow-sm flex flex-col justify-between">
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 bg-purple-50 dark:bg-purple-900/30 rounded-lg">
              <Activity className="w-5 h-5 text-purple-600 dark:text-purple-400" />
            </div>
            <h3 className="font-medium text-gray-700 dark:text-gray-300">Requests</h3>
          </div>
          <div>
            <p className="text-lg font-semibold text-gray-900 dark:text-white">{usage?.classARequests || 0} Class A</p>
            <p className="text-lg font-semibold text-gray-900 dark:text-white">{usage?.classBRequests || 0} Class B</p>
            <p className="text-sm text-gray-500 mt-1">
              {formatCurrency(usage?.estimatedRequestCharge)} est. charge
            </p>
            <p className="text-xs text-gray-400 mt-0.5">
              Class A: {formatCurrency(usage?.estimatedClassACharge)} · Class B: {formatCurrency(usage?.estimatedClassBCharge)}
            </p>
          </div>
        </div>

        <div className="bg-white dark:bg-gray-900 p-6 rounded-xl border border-gray-200 dark:border-gray-800 shadow-sm flex flex-col justify-between">
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 bg-indigo-50 dark:bg-indigo-900/30 rounded-lg">
              <Cloud className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
            </div>
            <h3 className="font-medium text-gray-700 dark:text-gray-300">Bandwidth Out</h3>
          </div>
          <div>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatBytes(usage?.bandwidthBytesOut || 0)}</p>
            <p className="text-sm text-gray-500 mt-1">{formatCurrency(usage?.estimatedBandwidthCharge)} est. charge</p>
          </div>
        </div>

        <div className="bg-white dark:bg-gray-900 p-6 rounded-xl border border-gray-200 dark:border-gray-800 shadow-sm flex flex-col justify-between">
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 bg-teal-50 dark:bg-teal-900/30 rounded-lg">
              <History className="w-5 h-5 text-teal-600 dark:text-teal-400" />
            </div>
            <h3 className="font-medium text-gray-700 dark:text-gray-300">Versioning Charges</h3>
          </div>
          <div>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatCurrency(usage?.estimatedVersioningCharge || 0)}</p>
            <p className="text-sm text-gray-500 mt-1">Noncurrent storage & downloads</p>
          </div>
        </div>

        <div className="bg-white dark:bg-gray-900 p-6 rounded-xl border border-blue-200 dark:border-blue-800 shadow-sm flex flex-col justify-between relative overflow-hidden">
          <div className="absolute top-0 right-0 w-24 h-24 bg-blue-50 dark:bg-blue-900/20 rounded-bl-full -z-10"></div>
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 bg-blue-100 dark:bg-blue-900/50 rounded-lg">
              <CreditCard className="w-5 h-5 text-blue-700 dark:text-blue-400" />
            </div>
            <h3 className="font-medium text-gray-900 dark:text-white">Estimated Total</h3>
          </div>
          <div>
            <p className="text-3xl font-extrabold text-blue-600 dark:text-blue-400">{formatCurrency(usage?.estimatedTotal)}</p>
            <p className="text-sm text-gray-500 mt-1">For current billing period</p>
          </div>
        </div>
      </div>

      {/* Estimated bucket costs */}
      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 overflow-hidden">
        <div className="px-6 py-5 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Estimated Bucket Cost</h2>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
              Click a bucket to see charge breakdown. Hover the info icon on each line for details.
            </p>
          </div>
          <p className="text-lg font-bold text-blue-600 dark:text-blue-400">{formatCurrency(usage?.estimatedTotal)}</p>
        </div>
        <CostBreakdown
          bucketItems={usage?.bucketItems}
          suffix=" est."
          emptyMessage="No active buckets with usage this period."
        />
      </div>

      {/* Pricing reference — all presets from server config */}
      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 p-6">
        <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-1 flex items-center gap-2">
          <Info className="w-5 h-5 text-gray-400" />
          Pricing Reference
        </h2>
        <p className="text-xs text-gray-500 dark:text-gray-400 mb-6">
          All rate presets loaded from application configuration (storage tiers, requests, bandwidth, retrieval, and restore).
        </p>
        <PricingReference />
      </div>
    </div>
  );
}
