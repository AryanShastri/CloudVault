import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { getInvoices, getInvoiceById } from '../api/billing';
import { FileText, Cloud, Receipt, Download } from 'lucide-react';
import Badge from '../components/Badge';
import Modal from '../components/Modal';
import Spinner from '../components/Spinner';
import CostBreakdown from '../components/CostBreakdown';
import { formatBytes } from '../utils/formatBytes';
import { formatCurrency } from '../utils/formatCurrency';
import { useToast } from '../components/Toast';

export default function Invoices() {
  const location = useLocation();
  const [invoices, setInvoices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [loadingInvoice, setLoadingInvoice] = useState(false);
  const { showToast } = useToast();

  const fetchInvoices = async () => {
    try {
      const data = await getInvoices();
      setInvoices(Array.isArray(data) ? data : []);
    } catch (error) {
      showToast('Failed to fetch invoices', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setLoading(true);
    fetchInvoices();
  }, [location.key]);

  const handleOpenInvoice = async (invoiceId) => {
    setIsModalOpen(true);
    setLoadingInvoice(true);
    setSelectedInvoice(null);
    try {
      const data = await getInvoiceById(invoiceId);
      setSelectedInvoice(data);
    } catch (error) {
      showToast('Failed to load invoice details', 'error');
      setIsModalOpen(false);
    } finally {
      setLoadingInvoice(false);
    }
  };

  if (loading) return <div className="flex h-full items-center justify-center"><Spinner size="lg" /></div>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Invoices</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Finalized bills from Billing & Usage. Re-run Generate / Refresh Invoice to update the current month.
          </p>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 overflow-hidden transition-colors">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm whitespace-nowrap">
            <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-600 dark:text-gray-400">
              <tr>
                <th className="px-6 py-4 font-semibold">Billing Period</th>
                <th className="px-6 py-4 font-semibold">Generated On</th>
                <th className="px-6 py-4 font-semibold">Total Storage</th>
                <th className="px-6 py-4 font-semibold">Amount Due</th>
                <th className="px-6 py-4 font-semibold">Status</th>
                <th className="px-6 py-4 font-semibold text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
              {invoices.map((inv) => (
                <tr
                  key={inv.id}
                  className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors cursor-pointer"
                  onClick={() => handleOpenInvoice(inv.id)}
                >
                  <td className="px-6 py-4 font-medium text-gray-900 dark:text-white flex items-center gap-2">
                    <FileText className="w-4 h-4 text-blue-500" />
                    {inv.billingPeriod}
                  </td>
                  <td className="px-6 py-4 text-gray-500 dark:text-gray-400">
                    {inv.generatedAt ? new Date(inv.generatedAt).toLocaleDateString() : '—'}
                  </td>
                  <td className="px-6 py-4 text-gray-500 dark:text-gray-400">
                    {formatBytes(inv.storageBytesUsed || 0)}
                  </td>
                  <td className="px-6 py-4 font-semibold text-gray-900 dark:text-white">
                    {formatCurrency(inv.amountDue)}
                  </td>
                  <td className="px-6 py-4"><Badge text={inv.status} color={inv.status} /></td>
                  <td className="px-6 py-4 text-right">
                    <button
                      type="button"
                      className="text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 text-sm font-medium"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleOpenInvoice(inv.id);
                      }}
                    >
                      View Detail
                    </button>
                  </td>
                </tr>
              ))}
              {invoices.length === 0 && (
                <tr>
                  <td colSpan="6" className="px-6 py-12 text-center">
                    <Receipt className="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600 mb-4" />
                    <p className="text-gray-500 dark:text-gray-400">No invoices yet.</p>
                    <p className="text-sm text-gray-400 dark:text-gray-500 mt-1">
                      Go to Billing & Usage and click &quot;Generate Invoice Now&quot; to create one.
                    </p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Invoice Detail">
        {loadingInvoice ? (
          <div className="py-12 flex justify-center"><Spinner /></div>
        ) : selectedInvoice ? (
          <div className="space-y-6">
            <div className="flex justify-between items-start pb-6 border-b border-gray-200 dark:border-gray-800">
              <div>
                <h3 className="text-xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
                  <Cloud className="w-6 h-6 text-blue-600" />
                  CloudVault Invoice
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Period: {selectedInvoice.billingPeriod}</p>
                <p className="text-sm text-gray-500 dark:text-gray-400">ID: {selectedInvoice.id}</p>
              </div>
              <div className="text-right">
                <Badge text={selectedInvoice.status} color={selectedInvoice.status} className="mb-2 block" />
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatCurrency(selectedInvoice.amountDue)}</p>
                <p className="text-xs text-gray-500 dark:text-gray-400">Total Amount Due</p>
              </div>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 text-sm">
              <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                <p className="text-gray-500 text-xs">Storage</p>
                <p className="font-medium text-gray-900 dark:text-white">{formatBytes(selectedInvoice.storageBytesUsed || 0)}</p>
              </div>
              <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                <p className="text-gray-500 text-xs">Class A / B</p>
                <p className="font-medium text-gray-900 dark:text-white">
                  {selectedInvoice.classARequests || 0} / {selectedInvoice.classBRequests || 0}
                </p>
              </div>
              <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                <p className="text-gray-500 text-xs">Bandwidth</p>
                <p className="font-medium text-gray-900 dark:text-white">{formatBytes(selectedInvoice.bandwidthBytesOut || 0)}</p>
              </div>
              <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                <p className="text-gray-500 text-xs">Versioning</p>
                <p className="font-medium text-gray-900 dark:text-white font-semibold">
                  {formatCurrency(selectedInvoice.bucketItems?.reduce((sum, item) => sum + (item.versioningStorageCharge || 0), 0) || 0)}
                </p>
              </div>
              <div className="p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
                <p className="text-gray-500 text-xs">Total charge</p>
                <p className="font-medium text-gray-900 dark:text-white">{formatCurrency(selectedInvoice.totalCharge)}</p>
              </div>
            </div>

            <div>
              <h4 className="font-semibold text-gray-900 dark:text-white mb-2 text-sm">Bucket Cost Breakdown</h4>
              <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">Click a bucket to see line-item charges.</p>
              <div className="border border-gray-200 dark:border-gray-800 rounded-lg overflow-hidden">
                <CostBreakdown
                  bucketItems={selectedInvoice.bucketItems}
                  emptyMessage="No bucket line items on this invoice."
                />
              </div>
            </div>

            <div className="flex justify-end pt-4">
              <button
                type="button"
                onClick={() => window.print()}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm text-gray-700 bg-gray-100 hover:bg-gray-200 dark:text-gray-200 dark:bg-gray-800 dark:hover:bg-gray-700 rounded-lg transition-colors"
              >
                <Download className="w-4 h-4" />
                Download PDF
              </button>
            </div>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
