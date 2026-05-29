import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getBuckets, createBucket, deleteBucket } from '../api/storage';
import { setLifecyclePolicy } from '../api/lifecycle';
import { Folder, Plus, Trash2, HardDrive, Database, Calendar } from 'lucide-react';
import Badge from '../components/Badge';
import Modal from '../components/Modal';
import Spinner from '../components/Spinner';
import { useToast } from '../components/Toast';
import { formatBytes } from '../utils/formatBytes';

export default function Buckets() {
  const [buckets, setBuckets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newBucketName, setNewBucketName] = useState('');
  const [newBucketDesc, setNewBucketDesc] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { showToast } = useToast();

  const [policyType, setPolicyType] = useState('PREDEFINED');
  const [customRules, setCustomRules] = useState([
    { fromTier: 'STANDARD', toTier: 'WARM', daysOfInactivity: 30 },
    { fromTier: 'WARM', toTier: 'INSTANT_GLACIER', daysOfInactivity: 60 },
    { fromTier: 'INSTANT_GLACIER', toTier: 'DEEP_GLACIER', daysOfInactivity: 90 }
  ]);

  const fetchBuckets = async () => {
    try {
      setLoading(true);
      const data = await getBuckets();
      setBuckets(data);
    } catch (error) {
      showToast('Failed to load buckets', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBuckets();
  }, []);

  const handleCreateBucket = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await createBucket({ name: newBucketName, description: newBucketDesc });
      
      const payload = {
        policyType,
        transitionRules: policyType === 'CUSTOM' ? customRules : undefined
      };
      await setLifecyclePolicy(newBucketName, payload);

      showToast('Bucket and policy created successfully');
      setIsModalOpen(false);
      setNewBucketName('');
      setNewBucketDesc('');
      setPolicyType('PREDEFINED');
      fetchBuckets();
    } catch (error) {
      showToast(error.response?.data?.message || 'Failed to create bucket', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteBucket = async (bucketName) => {
    if (!window.confirm(`Are you sure you want to delete bucket ${bucketName}?`)) return;
    
    try {
      await deleteBucket(bucketName);
      showToast('Bucket deleted successfully');
      fetchBuckets();
    } catch (error) {
      showToast(error.response?.data?.message || 'Failed to delete bucket', 'error');
    }
  };

  if (loading) {
    return <div className="flex h-full items-center justify-center"><Spinner size="lg" /></div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Buckets</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Manage your storage buckets and policies</p>
        </div>
        <button
          onClick={() => setIsModalOpen(true)}
          className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors shadow-sm"
        >
          <Plus className="w-4 h-4" />
          Create Bucket
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
        {buckets.map((bucket) => (
          <div key={bucket.name} className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 overflow-hidden transition-all hover:shadow-md hover:border-blue-300 dark:hover:border-blue-700 group flex flex-col">
            <Link to={`/buckets/${bucket.name}`} className="block flex-1 p-6">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-lg">
                    <Folder className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                      {bucket.name}
                    </h3>
                  </div>
                </div>
              </div>

              <div className="mt-5 grid grid-cols-2 gap-4">
                <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
                  <Database className="w-4 h-4 text-gray-400 dark:text-gray-500" />
                  <span>{bucket.objectCount || 0} objects</span>
                </div>
                <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
                  <HardDrive className="w-4 h-4 text-gray-400 dark:text-gray-500" />
                  <span>{formatBytes(bucket.totalSizeBytes || 0)}</span>
                </div>
                <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 col-span-2">
                  <Calendar className="w-4 h-4 text-gray-400 dark:text-gray-500" />
                  <span>Created {new Date(bucket.createdAt || Date.now()).toLocaleDateString()}</span>
                </div>
              </div>

              <div className="mt-5 flex items-center gap-2">
                <Badge text={bucket.lifecycleTier || 'STANDARD'} color={bucket.lifecycleTier} />
                <Badge text={bucket.policyType || 'PREDEFINED'} color="gray" />
              </div>
            </Link>
            
            <div className="px-6 py-3 border-t border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-800/30 flex justify-end">
              {(bucket.objectCount === 0 || !bucket.objectCount) && (
                <button
                  onClick={(e) => {
                    e.preventDefault();
                    handleDeleteBucket(bucket.name);
                  }}
                  className="text-sm flex items-center gap-1.5 text-gray-500 hover:text-red-600 dark:text-gray-400 dark:hover:text-red-400 transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                  Delete
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {buckets.length === 0 && (
        <div className="text-center py-16 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl shadow-sm border-dashed">
          <Folder className="mx-auto h-12 w-12 text-gray-400" />
          <h3 className="mt-4 text-sm font-semibold text-gray-900 dark:text-white">No buckets</h3>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Get started by creating a new storage bucket.</p>
          <div className="mt-6">
            <button
              onClick={() => setIsModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
            >
              <Plus className="w-4 h-4" />
              Create Bucket
            </button>
          </div>
        </div>
      )}

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Create Bucket">
        <form onSubmit={handleCreateBucket} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Bucket Name
            </label>
            <input
              type="text"
              required
              pattern="[a-z0-9-]{3,63}"
              value={newBucketName}
              onChange={(e) => setNewBucketName(e.target.value.toLowerCase())}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              placeholder="my-storage-bucket"
            />
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Lowercase letters, numbers, and hyphens only. 3-63 characters.</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Description (Optional)
            </label>
            <textarea
              value={newBucketDesc}
              onChange={(e) => setNewBucketDesc(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              placeholder="What is this bucket for?"
              rows="2"
            />
          </div>

          <div className="pt-2 border-t border-gray-200 dark:border-gray-800 mt-4">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-3">Lifecycle Policy</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Policy Type</label>
                <select value={policyType} onChange={e => setPolicyType(e.target.value)} className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg dark:bg-gray-800 text-sm">
                  <option value="PREDEFINED">Predefined (Automatic tracking)</option>
                  <option value="CUSTOM">Custom (Time-based only)</option>
                </select>
                {policyType === 'PREDEFINED' && (
                  <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                    Predefined policies automatically downgrade objects based on inactivity and upgrade them based on access frequency according to intelligent tracking. New buckets start in STANDARD tier.
                  </p>
                )}
              </div>
              
              {policyType === 'CUSTOM' && (
                <div className="space-y-3 pt-2 bg-gray-50 dark:bg-gray-800/50 p-3 rounded-lg border border-gray-200 dark:border-gray-700">
                  <p className="text-sm font-medium text-gray-700 dark:text-gray-300">Days of Inactivity Thresholds</p>
                  {customRules.map((rule, idx) => (
                    <div key={idx} className="flex items-center gap-3 text-sm">
                      <span className="w-20 font-mono text-gray-500 text-xs">{rule.fromTier}</span>
                      <span className="text-gray-400">→</span>
                      <span className="w-28 font-mono text-gray-500 text-xs">{rule.toTier}</span>
                      <input type="number" required min="1" value={rule.daysOfInactivity} onChange={e => {
                        const newRules = [...customRules];
                        newRules[idx].daysOfInactivity = parseInt(e.target.value);
                        setCustomRules(newRules);
                      }} className="w-16 px-2 py-1 border rounded dark:bg-gray-900 dark:border-gray-600 focus:ring-blue-500" />
                      <span className="text-gray-500 text-xs">days</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="pt-4 flex justify-end gap-3 border-t border-gray-200 dark:border-gray-800">
            <button
              type="button"
              onClick={() => setIsModalOpen(false)}
              className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center justify-center min-w-[100px] px-4 py-2 text-sm font-medium text-white bg-blue-600 border border-transparent rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 transition-colors"
            >
              {submitting ? <Spinner size="sm" className="text-white" /> : 'Create'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
