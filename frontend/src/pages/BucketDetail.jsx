import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Cloud, Upload, Download, Trash2, Link as LinkIcon, RefreshCw, Archive, Tag, History, Search, File as FileIcon } from 'lucide-react';
import { getObjects, uploadObject, fetchObjectDownload, saveObjectBlob, presignUrl, deleteObject, filterObjects } from '../api/storage';
import { getLifecycleStatus, getLifecycleHistory, setLifecyclePolicy, requestRestore, getRestoreStatus, getLifecyclePolicy } from '../api/lifecycle';
import { getObjectTags, addObjectTag, deleteObjectTag } from '../api/tagging';
import { enableVersioning, getVersions, deleteVersion, downloadVersion } from '../api/versioning';
import { useToast } from '../components/Toast';
import { useUploadJobs } from '../context/UploadJobContext';
import {
  useVirusScan,
  isMalwareUploadError,
  parseUploadThreatMessage,
} from '../context/VirusScanContext';
import { formatBytes } from '../utils/formatBytes';
import Badge from '../components/Badge';
import Spinner from '../components/Spinner';
import Modal from '../components/Modal';
import Pagination from '../components/Pagination';
import BackButton from '../components/BackButton';

export default function BucketDetail() {
  const { bucketName } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { addUploadJob, activateUploadJob, removeUploadJob, setJobClientProgress, markJobSyncComplete } = useUploadJobs();
  const { showScanningPopup, closeScanningPopup, showVirusFoundPopup } = useVirusScan();
  
  const [activeTab, setActiveTab] = useState('objects');
  const [loading, setLoading] = useState(true);
  
  // Tab 1: Objects State
  const [objectsData, setObjectsData] = useState(null);
  const [objectPage, setObjectPage] = useState(0);
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadKey, setUploadKey] = useState('');
  const [uploading, setUploading] = useState(false);
  const [searchFilter, setSearchFilter] = useState('');
  
  // Tab 2: Lifecycle State
  const [lifecycleStatus, setLifecycleStatus] = useState(null);
  const [lifecycleHistory, setLifecycleHistory] = useState([]);
  const [lifecyclePolicy, setLifecyclePolicyData] = useState(null);
  const [restoreSpeed, setRestoreSpeed] = useState('EXPEDITED');
  const [restoreKey, setRestoreKey] = useState('');
  const [restoreStatusData, setRestoreStatusData] = useState(null);

  // Tab 3: Tags State
  const [selectedTagObject, setSelectedTagObject] = useState('');
  const [objectTags, setObjectTags] = useState([]);
  const [tagsLoading, setTagsLoading] = useState(false);
  const [addingTag, setAddingTag] = useState(false);
  const [deletingTagKey, setDeletingTagKey] = useState(null);
  const [newTagKey, setNewTagKey] = useState('');
  const [newTagValue, setNewTagValue] = useState('');
  const [filterTagKey, setFilterTagKey] = useState('');
  const [filterTagValue, setFilterTagValue] = useState('');
  const [filteredObjects, setFilteredObjects] = useState([]);
  const [filterLoading, setFilterLoading] = useState(false);

  // Tab 4: Versions State
  const [selectedVersionObject, setSelectedVersionObject] = useState('');
  const [versionsList, setVersionsList] = useState([]);
  const [isVersioningEnabledLocally, setIsVersioningEnabledLocally] = useState(false);

  // Folder navigation state
  const [currentPrefix, setCurrentPrefix] = useState('');

  useEffect(() => {
    fetchObjects(0);
    fetchLifecycleData();
  }, [bucketName]);

  // Preload objects list when Tags tab is opened
  useEffect(() => {
    if (activeTab === 'tags' && !objectsData) {
      fetchObjects(0);
    }
  }, [activeTab]);

  const fetchObjects = async (page = 0) => {
    try {
      const data = await getObjects(bucketName, page, 20);
      setObjectsData(data);
      setObjectPage(page);
    } catch (error) {
      if (error.response?.status === 404) {
        showToast('Bucket not found', 'error');
        navigate('/buckets');
      } else {
        showToast('Failed to fetch objects', 'error');
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchLifecycleData = async () => {
    try {
      const [status, history, policy] = await Promise.all([
        getLifecycleStatus(bucketName),
        getLifecycleHistory(bucketName),
        getLifecyclePolicy(bucketName)
      ]);
      setLifecycleStatus(status);
      setLifecycleHistory(history);
      setLifecyclePolicyData(policy);
      
      if (status?.currentTier === 'DEEP_GLACIER') {
        const restoreData = await getRestoreStatus(bucketName);
        setRestoreStatusData(restoreData);
      }
    } catch (error) {
      console.error(error);
    }
  };

  // ---- Objects Tab Actions ----
  const handleUpload = async (e) => {
    e.preventDefault();
    if (!uploadFile) return;
    setUploading(true);

    // ── Step 1: capture values & show tray BEFORE the HTTP round-trip ──
    const file     = uploadFile;   // hold ref; setUploadFile(null) is async
    const key      = uploadKey;    // same for uploadKey
    const filename = file.name;
    const fileSize = formatBytes(file.size);
    const tempId   = addUploadJob({ filename, fileSize, bucketName });

    // Close modal immediately — user can navigate while upload runs
    setIsUploadOpen(false);
    setUploadFile(null);
    setUploadKey('');

    showScanningPopup(filename, file.size);

    try {
      const response = await uploadObject(bucketName, file, key || undefined, {
        onUploadProgress: (event) => {
          if (event.total) {
            const pct = Math.round((event.loaded * 100) / event.total);
            setJobClientProgress(tempId, pct);
          }
        },
      });

      if (response.status === 202) {
        const { jobId } = response.data;
        setJobClientProgress(tempId, 100);
        activateUploadJob(tempId, jobId);
      } else {
        closeScanningPopup();
        markJobSyncComplete(tempId);
        fetchObjects(0);
      }
    } catch (error) {
      closeScanningPopup();
      removeUploadJob(tempId);

      if (isMalwareUploadError(error)) {
        const virusName = parseUploadThreatMessage(error.response?.data?.message);
        showVirusFoundPopup(filename, virusName);
      } else {
        showToast(error.response?.data?.message || 'Upload failed', 'error');
      }
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (objectKey) => {
    try {
      const response = await fetchObjectDownload(bucketName, objectKey);
      saveObjectBlob(
        response.data,
        objectKey,
        response.headers['content-disposition']
      );
    } catch {
      showToast('Download failed', 'error');
    }
  };

  const handleDeleteObject = async (objectKey) => {
    if (!window.confirm(`Delete ${objectKey}?`)) return;
    try {
      await deleteObject(bucketName, objectKey);
      showToast('Object deleted');
      fetchObjects(objectPage);
    } catch (error) {
      showToast('Failed to delete', 'error');
    }
  };

  const handlePresign = async (objectKey) => {
    try {
      const data = await presignUrl(bucketName, objectKey);
      await navigator.clipboard.writeText(data.url);
      showToast('Presigned URL copied to clipboard');
    } catch (error) {
      showToast('Failed to generate presigned URL', 'error');
    }
  };

  // ---- Lifecycle Tab Actions ----

  const handleRestore = async (e) => {
    e.preventDefault();
    try {
      await requestRestore(bucketName, restoreKey, restoreSpeed);
      showToast('Restore requested successfully');
      fetchLifecycleData();
    } catch (error) {
      showToast(error.response?.data?.message || 'Restore failed', 'error');
    }
  };

  // ---- Tags Tab Actions ----
  const handleObjectSelectForTags = async (objectKey) => {
    setSelectedTagObject(objectKey);
    if (!objectKey) { setObjectTags([]); return; }
    setTagsLoading(true);
    try {
      const data = await getObjectTags(bucketName, objectKey);
      setObjectTags(Array.isArray(data) ? data : []);
    } catch (error) {
      showToast('Failed to fetch tags', 'error');
    } finally {
      setTagsLoading(false);
    }
  };

  const handleAddTag = async (e) => {
    e.preventDefault();
    if (!newTagKey.trim()) return;
    setAddingTag(true);
    try {
      const response = await addObjectTag(bucketName, selectedTagObject, newTagKey.trim(), newTagValue.trim());
      const existingIndex = objectTags.findIndex(t => t.key === newTagKey.trim());
      if (existingIndex >= 0) {
        const updated = [...objectTags];
        updated[existingIndex] = response;
        setObjectTags(updated);
      } else {
        setObjectTags([...objectTags, response]);
      }
      setNewTagKey('');
      setNewTagValue('');
      showToast('Tag added successfully');
    } catch (error) {
      if (error.response?.status === 404) showToast('Object not found', 'error');
      else if (error.response?.status === 400) showToast('Invalid tag key or value', 'error');
      else showToast('Failed to add tag. Please try again.', 'error');
    } finally {
      setAddingTag(false);
    }
  };

  const handleDeleteTag = async (tagKey) => {
    setDeletingTagKey(tagKey);
    try {
      await deleteObjectTag(bucketName, selectedTagObject, tagKey);
      setObjectTags(objectTags.filter(t => t.key !== tagKey));
      showToast('Tag deleted');
    } catch (error) {
      showToast('Failed to delete tag', 'error');
    } finally {
      setDeletingTagKey(null);
    }
  };

  const handleFilterTags = async (e) => {
    e.preventDefault();
    setFilterLoading(true);
    try {
      const data = await filterObjects(filterTagKey, filterTagValue);
      setFilteredObjects(data);
    } catch (error) {
      showToast('Filter failed', 'error');
    } finally {
      setFilterLoading(false);
    }
  };

  // ---- Versions Tab Actions ----
  const handleEnableVersioning = async () => {
    try {
      await enableVersioning(bucketName);
      showToast('Versioning enabled');
      setIsVersioningEnabledLocally(true);
      fetchLifecycleData(); // refresh policy to see versioning status
    } catch (error) {
      showToast('Failed to enable versioning', 'error');
    }
  };

  const fetchVersions = async (objectKey) => {
    if (!objectKey) return;
    try {
      const data = await getVersions(bucketName, objectKey);
      setVersionsList(data);
    } catch (error) {
      showToast('Failed to fetch versions', 'error');
    }
  };

  const handleDeleteVersion = async (versionNumber) => {
    try {
      await deleteVersion(bucketName, versionNumber, selectedVersionObject);
      showToast('Version deleted');
      fetchVersions(selectedVersionObject);
    } catch (error) {
      showToast('Failed to delete version', 'error');
    }
  };

  const handleDownloadVersion = async (versionNumber) => {
    try {
      const response = await downloadVersion(bucketName, selectedVersionObject, versionNumber);
      saveObjectBlob(
        response.data,
        selectedVersionObject,
        response.headers['content-disposition']
      );
    } catch {
      showToast('Version download failed', 'error');
    }
  };

  const getCustomRuleDays = (fromTier, toTier) => {
    if (!lifecyclePolicy?.customRules) return 'Custom Days';
    const rule = lifecyclePolicy.customRules.find(
      r => r.fromTier === fromTier && r.toTier === toTier
    );
    return rule ? `${rule.daysOfInactivity} Days` : 'N/A';
  };

  if (loading) return <div className="flex h-full items-center justify-center"><Spinner size="lg" /></div>;

  const tabs = [
    { id: 'objects', name: 'Objects', icon: Cloud },
    { id: 'lifecycle', name: 'Lifecycle', icon: RefreshCw },
    { id: 'tags', name: 'Tags', icon: Tag },
    { id: 'versions', name: 'Versions', icon: History }
  ];

  const filteredObjectsList = objectsData?.content?.filter(obj => obj.objectKey.toLowerCase().includes(searchFilter.toLowerCase())) || [];
  const uniqueObjectKeys = objectsData?.content?.map(o => o.objectKey) || [];

  // ── Folder-aware view computation ──
  // Objects whose key starts with currentPrefix are "in scope".
  // From those, extract virtual folders (next path segment) and direct files.
  const objectsInScope = filteredObjectsList.filter(obj => obj.objectKey.startsWith(currentPrefix));
  const folderSet = new Set();
  const directFiles = [];
  objectsInScope.forEach(obj => {
    const rest = obj.objectKey.slice(currentPrefix.length);
    const slashIdx = rest.indexOf('/');
    if (slashIdx !== -1) {
      // This object lives inside a sub-folder
      folderSet.add(rest.slice(0, slashIdx + 1)); // e.g. "reports/"
    } else {
      directFiles.push(obj);
    }
  });
  const virtualFolders = Array.from(folderSet).sort();

  // Breadcrumb segments from currentPrefix
  const breadcrumbSegments = currentPrefix
    ? currentPrefix.split('/').filter(Boolean)
    : [];
  const buildPrefixUpTo = (idx) => breadcrumbSegments.slice(0, idx + 1).join('/') + '/';

  const getParentPrefix = (prefix) => {
    if (!prefix) return '';
    const segments = prefix.split('/').filter(Boolean);
    segments.pop();
    return segments.length ? `${segments.join('/')}/` : '';
  };

  return (
    <div className="space-y-6">
      <BackButton to="/buckets" label="Buckets" className="mb-2" />

      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 p-6 transition-colors">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
          {bucketName}
          {lifecycleStatus?.currentTier === 'DEEP_GLACIER' && (
            <Badge text="DEEP_GLACIER" color="navy" className="ml-2" />
          )}
        </h1>
        
        {lifecycleStatus?.currentTier === 'DEEP_GLACIER' && (
          <div className="mt-4 p-4 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-800 dark:text-indigo-300 rounded-lg border border-indigo-200 dark:border-indigo-800 flex items-start gap-3">
            <Archive className="w-5 h-5 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-sm">Bucket is in DEEP_GLACIER</p>
              <p className="text-sm mt-1">Objects in this bucket cannot be downloaded immediately. You must request a restore from the Lifecycle tab before accessing them.</p>
            </div>
          </div>
        )}

        <div className="mt-6 border-b border-gray-200 dark:border-gray-800">
          <nav className="-mb-px flex space-x-8">
            {tabs.map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`
                  whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm flex items-center gap-2 transition-colors
                  ${activeTab === tab.id
                    ? 'border-blue-500 text-blue-600 dark:text-blue-400 dark:border-blue-400'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300'
                  }
                `}
              >
                <tab.icon className="w-4 h-4" />
                {tab.name}
              </button>
            ))}
          </nav>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-900 rounded-xl shadow-sm border border-gray-200 dark:border-gray-800 transition-colors">
        
        {/* ===================== OBJECTS TAB ===================== */}
        {activeTab === 'objects' && (
          <div>
            <div className="p-6 border-b border-gray-200 dark:border-gray-800 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="relative w-full sm:w-64">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Search className="h-4 w-4 text-gray-400" />
                </div>
                <input
                  type="text"
                  placeholder="Filter objects by name..."
                  value={searchFilter}
                  onChange={e => setSearchFilter(e.target.value)}
                  className="block w-full pl-10 pr-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800 text-sm focus:ring-blue-500 focus:border-blue-500 dark:text-white"
                />
              </div>
              <button
                onClick={() => setIsUploadOpen(true)}
                className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
              >
                <Upload className="w-4 h-4" />
                Upload Object
              </button>
            </div>
            
            {/* Breadcrumb navigation */}
            {(currentPrefix || breadcrumbSegments.length > 0) && (
              <div className="px-6 py-2 flex items-center gap-2 text-sm border-b border-gray-100 dark:border-gray-800 bg-gray-50/60 dark:bg-gray-800/30">
                {currentPrefix && (
                  <BackButton
                    iconOnly
                    label="Parent folder"
                    onClick={() => setCurrentPrefix(getParentPrefix(currentPrefix))}
                  />
                )}
                <button
                  onClick={() => setCurrentPrefix('')}
                  className="text-blue-600 dark:text-blue-400 hover:underline font-medium"
                >
                  {bucketName}
                </button>
                {breadcrumbSegments.map((seg, idx) => (
                  <span key={idx} className="flex items-center gap-1">
                    <span className="text-gray-400">/</span>
                    <button
                      onClick={() => setCurrentPrefix(buildPrefixUpTo(idx))}
                      className={`hover:underline font-medium ${
                        idx === breadcrumbSegments.length - 1
                          ? 'text-gray-700 dark:text-gray-200 cursor-default'
                          : 'text-blue-600 dark:text-blue-400'
                      }`}
                    >
                      {seg}
                    </button>
                  </span>
                ))}
              </div>
            )}

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm whitespace-nowrap">
                <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-600 dark:text-gray-400">
                  <tr>
                    <th className="px-6 py-3 font-semibold">Name</th>
                    <th className="px-6 py-3 font-semibold">Last Modified</th>
                    <th className="px-6 py-3 font-semibold">Size</th>
                    <th className="px-6 py-3 font-semibold text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 dark:divide-gray-800">

                  {/* Virtual folder rows */}
                  {virtualFolders.map(folder => (
                    <tr
                      key={folder}
                      onClick={() => setCurrentPrefix(currentPrefix + folder)}
                      className="hover:bg-amber-50 dark:hover:bg-amber-900/10 cursor-pointer transition-colors"
                    >
                      <td className="px-6 py-3 font-mono text-gray-900 dark:text-gray-100">
                        <div className="flex items-center gap-2">
                          {/* Folder icon */}
                          <svg className="w-5 h-5 text-amber-400 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor">
                            <path d="M2 6a2 2 0 012-2h4l2 2h4a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z" />
                          </svg>
                          <span className="font-medium">{folder.replace(/\/$/, '')}</span>
                        </div>
                      </td>
                      <td className="px-6 py-3 text-gray-400">—</td>
                      <td className="px-6 py-3 text-gray-400">—</td>
                      <td className="px-6 py-3 text-right text-gray-300 dark:text-gray-600 text-xs select-none">folder</td>
                    </tr>
                  ))}

                  {/* Direct file rows */}
                  {directFiles.map(obj => (
                    <tr key={obj.objectKey} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                      <td className="px-6 py-4 font-mono text-gray-900 dark:text-gray-100">
                        <div className="flex items-center gap-2">
                          <FileIcon className="w-4 h-4 text-gray-400 flex-shrink-0" />
                          <span>{obj.objectKey.slice(currentPrefix.length)}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{new Date(obj.createdAt).toLocaleString()}</td>
                      <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{formatBytes(obj.sizeBytes)}</td>
                      <td className="px-6 py-4 text-right space-x-3">
                        <button onClick={() => handlePresign(obj.objectKey)} className="text-gray-500 hover:text-blue-600 dark:text-gray-400 dark:hover:text-blue-400" title="Presign URL">
                          <LinkIcon className="w-4 h-4 inline" />
                        </button>
                        <button
                          onClick={() => handleDownload(obj.objectKey)}
                          className="text-gray-500 hover:text-green-600 dark:text-gray-400 dark:hover:text-green-400"
                          title="Download"
                        >
                          <Download className="w-4 h-4 inline" />
                        </button>
                        <button onClick={() => handleDeleteObject(obj.objectKey)} className="text-gray-500 hover:text-red-600 dark:text-gray-400 dark:hover:text-red-400" title="Delete">
                          <Trash2 className="w-4 h-4 inline" />
                        </button>
                      </td>
                    </tr>
                  ))}

                  {virtualFolders.length === 0 && directFiles.length === 0 && (
                    <tr>
                      <td colSpan="4" className="px-6 py-8 text-center text-gray-500 dark:text-gray-400">
                        {currentPrefix ? 'This folder is empty.' : 'No objects found.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {!currentPrefix && objectsData && <Pagination pageData={objectsData} onPageChange={fetchObjects} />}
          </div>
        )}

        {/* ===================== LIFECYCLE TAB ===================== */}
        {activeTab === 'lifecycle' && (
          <div className="p-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              <div>
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Current Status</h3>
                <div className="bg-gray-50 dark:bg-gray-800/50 rounded-lg p-5 border border-gray-200 dark:border-gray-700 space-y-4">
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-500 dark:text-gray-400">Current Tier</span>
                    <Badge text={lifecycleStatus?.currentTier || 'STANDARD'} color={lifecycleStatus?.currentTier} />
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-500 dark:text-gray-400">Policy Type</span>
                    <Badge text={lifecycleStatus?.policyType || 'PREDEFINED'} />
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm text-gray-500 dark:text-gray-400">Days in current tier</span>
                    <span className="text-sm font-medium text-gray-900 dark:text-white">{lifecycleStatus?.daysInCurrentTier || 0}</span>
                  </div>
                  {lifecycleStatus?.daysUntilDowngrade !== undefined && lifecycleStatus?.daysUntilDowngrade !== null && (
                    <div className="flex justify-between items-center">
                      <span className="text-sm text-gray-500 dark:text-gray-400">Next downgrade in</span>
                      <span className="text-sm font-medium text-blue-600 dark:text-blue-400">{lifecycleStatus?.daysUntilDowngrade} days</span>
                    </div>
                  )}
                  {lifecycleStatus?.requestsUntilUpgrade !== undefined && lifecycleStatus?.requestsUntilUpgrade !== null && (
                    <div className="flex justify-between items-center border-t border-gray-200 dark:border-gray-700 pt-3">
                      <span className="text-sm text-gray-500 dark:text-gray-400">Next upgrade in</span>
                      <span className="text-sm font-medium text-green-600 dark:text-green-400">
                        {lifecycleStatus?.currentTier === 'STANDARD' ? 'Unlimited requests' : `${lifecycleStatus?.requestsUntilUpgrade} requests`}
                      </span>
                    </div>
                  )}
                </div>

                <div className="mt-6 bg-gray-50 dark:bg-gray-800/50 rounded-lg p-5 border border-gray-200 dark:border-gray-700 space-y-3">
                  <h4 className="text-sm font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                    <RefreshCw className="w-4 h-4 text-gray-500" />
                    Lifecycle Policy Applied
                  </h4>
                  {lifecycleStatus?.policyType === 'PREDEFINED' ? (
                    <div className="space-y-3">
                      <p className="text-sm text-gray-600 dark:text-gray-400">
                        <strong>Predefined (Intelligent Tracking):</strong> The system automatically downgrades objects to colder storage tiers based on inactivity periods.
                      </p>
                      <div className="bg-white dark:bg-gray-900/50 rounded border border-gray-200 dark:border-gray-800 p-3 space-y-2">
                        <div className="flex justify-between text-xs font-medium">
                          <span className="text-gray-500">STANDARD → WARM</span>
                          <span className="text-blue-600 dark:text-blue-400">
                            {lifecyclePolicy?.predefinedRules?.standardToWarmDays !== undefined 
                              ? `${lifecyclePolicy.predefinedRules.standardToWarmDays} Days` 
                              : '30 Days'}
                          </span>
                        </div>
                        <div className="flex justify-between text-xs font-medium">
                          <span className="text-gray-500">WARM → INSTANT GLACIER</span>
                          <span className="text-blue-600 dark:text-blue-400">
                            {lifecyclePolicy?.predefinedRules?.warmToInstantGlacierDays !== undefined 
                              ? `${lifecyclePolicy.predefinedRules.warmToInstantGlacierDays} Days` 
                              : '60 Days'}
                          </span>
                        </div>
                        <div className="flex justify-between text-xs font-medium">
                          <span className="text-gray-500">INSTANT GLACIER → DEEP GLACIER</span>
                          <span className="text-blue-600 dark:text-blue-400">
                            {lifecyclePolicy?.predefinedRules?.instantGlacierToDeepGlacierDays !== undefined 
                              ? `${lifecyclePolicy.predefinedRules.instantGlacierToDeepGlacierDays} Days` 
                              : '90 Days'}
                          </span>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      <p className="text-sm text-gray-600 dark:text-gray-400">
                        <strong>Custom (Time-based):</strong> Custom inactivity thresholds are applied for downgrading objects.
                      </p>
                      <div className="bg-white dark:bg-gray-900/50 rounded border border-gray-200 dark:border-gray-800 p-3 space-y-2">
                        <div className="flex justify-between text-xs font-medium">
                          <span className="text-gray-500">STANDARD → WARM</span>
                          <span className="text-blue-600 dark:text-blue-400">
                            {getCustomRuleDays('STANDARD', 'WARM')}
                          </span>
                        </div>
                        <div className="flex justify-between text-xs font-medium">
                          <span className="text-gray-500">WARM → INSTANT GLACIER</span>
                          <span className="text-blue-600 dark:text-blue-400">
                            {getCustomRuleDays('WARM', 'INSTANT_GLACIER')}
                          </span>
                        </div>
                        <div className="flex justify-between text-xs font-medium">
                          <span className="text-gray-500">INSTANT GLACIER → DEEP GLACIER</span>
                          <span className="text-blue-600 dark:text-blue-400">
                            {getCustomRuleDays('INSTANT_GLACIER', 'DEEP_GLACIER')}
                          </span>
                        </div>
                        <div className="mt-2 pt-2 border-t border-gray-100 dark:border-gray-800 flex justify-between text-xs font-semibold">
                          <span className="text-gray-700 dark:text-gray-300">Next transition:</span>
                          <span className="text-indigo-600 dark:text-indigo-400">{lifecycleStatus?.nextDowngradeAt || 'N/A'}</span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              <div>
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Lifecycle Timeline</h3>
                <div className="flex items-center justify-between mt-8 relative">
                  <div className="absolute inset-0 top-1/2 h-0.5 bg-gray-200 dark:bg-gray-700 -z-10 transform -translate-y-1/2 mx-8"></div>
                  {['STANDARD', 'WARM', 'INSTANT_GLACIER', 'DEEP_GLACIER'].map((tier, idx) => {
                    const isActive = lifecycleStatus?.currentTier === tier;
                    return (
                      <div key={tier} className="flex flex-col items-center gap-2">
                        <div className={`w-10 h-10 rounded-full flex items-center justify-center border-4 ${isActive ? 'bg-blue-600 border-blue-200 dark:border-blue-900 shadow-lg' : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700'}`}>
                          {isActive && <div className="w-3 h-3 bg-white rounded-full"></div>}
                        </div>
                        <span className={`text-xs font-medium ${isActive ? 'text-blue-600 dark:text-blue-400' : 'text-gray-500 dark:text-gray-400'}`}>
                          {tier.replace('_', ' ')}
                        </span>
                      </div>
                    );
                  })}
                </div>

                {lifecycleStatus?.currentTier === 'DEEP_GLACIER' && (
                  <div className="mt-12 bg-indigo-50 dark:bg-indigo-900/20 p-5 rounded-lg border border-indigo-200 dark:border-indigo-800">
                    <h4 className="text-sm font-semibold text-indigo-900 dark:text-indigo-300 mb-3">Request Object Restore</h4>
                    <form onSubmit={handleRestore} className="space-y-3">
                      <input 
                        type="text" 
                        placeholder="Object Key" 
                        required
                        value={restoreKey}
                        onChange={e => setRestoreKey(e.target.value)}
                        className="w-full text-sm px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-md dark:bg-gray-800"
                      />
                      <select 
                        value={restoreSpeed}
                        onChange={e => setRestoreSpeed(e.target.value)}
                        className="w-full text-sm px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-md dark:bg-gray-800"
                      >
                        <option value="EXPEDITED">Expedited ($0.03/GB)</option>
                        <option value="STANDARD">Standard ($0.02/GB)</option>
                        <option value="BULK">Bulk ($0.0025/GB)</option>
                      </select>
                      <button type="submit" className="w-full px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm rounded-md transition-colors">
                        Submit Request
                      </button>
                    </form>
                    
                    {restoreStatusData && restoreStatusData.length > 0 && (
                      <div className="mt-4 pt-4 border-t border-indigo-200 dark:border-indigo-800">
                        <p className="text-xs font-semibold text-indigo-800 dark:text-indigo-400 mb-2">Active Restores:</p>
                        {restoreStatusData.map((restoreObj) => (
                          <div key={restoreObj.id} className="flex justify-between items-center text-xs py-1 gap-2">
                            <span className="font-mono text-gray-600 dark:text-gray-400 truncate max-w-[150px]" title={restoreObj.objectKey}>
                              {restoreObj.objectKey}
                            </span>
                            <Badge 
                              text={restoreObj.status} 
                              color={restoreObj.status === 'COMPLETED' ? 'green' : restoreObj.status === 'PENDING' ? 'yellow' : 'red'} 
                            />
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>

            <div className="mt-10">
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Event History</h3>
              <div className="overflow-x-auto border border-gray-200 dark:border-gray-800 rounded-lg">
                <table className="w-full text-left text-sm whitespace-nowrap">
                  <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-600 dark:text-gray-400 border-b border-gray-200 dark:border-gray-800">
                    <tr>
                      <th className="px-6 py-3 font-semibold">Date</th>
                      <th className="px-6 py-3 font-semibold">From</th>
                      <th className="px-6 py-3 font-semibold">To</th>
                      <th className="px-6 py-3 font-semibold">Reason</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
                    {[...lifecycleHistory].sort((a, b) => new Date(b.transitionedAt) - new Date(a.transitionedAt)).map((hist, idx) => (
                      <tr key={idx} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                        <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{new Date(hist.transitionedAt).toLocaleString()}</td>
                        <td className="px-6 py-4"><Badge text={hist.fromTier} color={hist.fromTier} /></td>
                        <td className="px-6 py-4"><Badge text={hist.toTier} color={hist.toTier} /></td>
                        <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{hist.reason}</td>
                      </tr>
                    ))}
                    {lifecycleHistory.length === 0 && (
                      <tr>
                        <td colSpan="4" className="px-6 py-6 text-center text-gray-500 dark:text-gray-400">
                          No lifecycle events recorded yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* ===================== TAGS TAB ===================== */}
        {activeTab === 'tags' && (
          <div className="p-6 space-y-10">

            {/* ── Tag Manager ── */}
            <div>
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-1">Tag Manager</h3>
              <p className="text-sm text-gray-500 dark:text-gray-400 mb-5">Select an object to view and manage its tags.</p>

              {/* Object selector */}
              <div className="mb-5">
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Select Object</label>
                <select
                  id="tag-object-select"
                  className="w-full max-w-md px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg dark:bg-gray-800 text-sm focus:ring-2 focus:ring-blue-500"
                  value={selectedTagObject}
                  onChange={(e) => handleObjectSelectForTags(e.target.value)}
                >
                  <option value="">-- Select an object --</option>
                  {uniqueObjectKeys.map(key => (
                    <option key={key} value={key}>{key}</option>
                  ))}
                </select>
              </div>

              {selectedTagObject && (
                <div className="border border-gray-200 dark:border-gray-800 rounded-xl overflow-hidden">
                  {/* Tags table */}
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead className="bg-gray-50 dark:bg-gray-800/60 text-gray-600 dark:text-gray-400 border-b border-gray-200 dark:border-gray-700">
                        <tr>
                          <th className="px-5 py-3 font-semibold">Key</th>
                          <th className="px-5 py-3 font-semibold">Value</th>
                          <th className="px-5 py-3 font-semibold">Created</th>
                          <th className="px-5 py-3 font-semibold text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                        {tagsLoading ? (
                          [1,2,3].map(i => (
                            <tr key={i}>
                              {[1,2,3,4].map(j => (
                                <td key={j} className="px-5 py-4">
                                  <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse w-24" />
                                </td>
                              ))}
                            </tr>
                          ))
                        ) : objectTags.length === 0 ? (
                          <tr>
                            <td colSpan="4" className="px-5 py-8 text-center text-gray-400 dark:text-gray-500 text-sm">
                              No tags added yet. Add your first tag below.
                            </td>
                          </tr>
                        ) : (
                          objectTags.map(tag => (
                            <tr key={tag.key} className="hover:bg-gray-50 dark:hover:bg-gray-800/40 transition-colors">
                              <td className="px-5 py-3">
                                <span className="bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 px-2 py-0.5 rounded text-xs font-mono font-semibold">{tag.key}</span>
                              </td>
                              <td className="px-5 py-3 text-gray-700 dark:text-gray-300 text-sm">{tag.value}</td>
                              <td className="px-5 py-3 text-gray-500 dark:text-gray-400 text-sm">
                                {tag.createdAt ? new Date(tag.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '—'}
                              </td>
                              <td className="px-5 py-3 text-right">
                                <button
                                  id={`delete-tag-${tag.key}`}
                                  onClick={() => handleDeleteTag(tag.key)}
                                  disabled={deletingTagKey === tag.key}
                                  className="text-red-500 hover:text-red-700 disabled:opacity-50 transition-colors"
                                  title="Delete tag"
                                >
                                  {deletingTagKey === tag.key
                                    ? <Spinner size="sm" />
                                    : <Trash2 className="w-4 h-4" />}
                                </button>
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>

                  {/* Add tag form */}
                  <div className="p-5 border-t border-gray-200 dark:border-gray-800 bg-gray-50 dark:bg-gray-800/30">
                    <p className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">Add New Tag</p>
                    <form onSubmit={handleAddTag} className="flex flex-wrap gap-3 items-end">
                      <div className="flex-1 min-w-32">
                        <label className="block text-xs text-gray-500 mb-1">Key</label>
                        <input
                          id="new-tag-key"
                          type="text"
                          placeholder="e.g. environment"
                          required
                          value={newTagKey}
                          onChange={e => setNewTagKey(e.target.value)}
                          className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-700 rounded-lg dark:bg-gray-800 focus:ring-2 focus:ring-blue-500"
                        />
                      </div>
                      <div className="flex-1 min-w-32">
                        <label className="block text-xs text-gray-500 mb-1">Value</label>
                        <input
                          id="new-tag-value"
                          type="text"
                          placeholder="e.g. production"
                          required
                          value={newTagValue}
                          onChange={e => setNewTagValue(e.target.value)}
                          className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-700 rounded-lg dark:bg-gray-800 focus:ring-2 focus:ring-blue-500"
                        />
                      </div>
                      <button
                        id="add-tag-btn"
                        type="submit"
                        disabled={addingTag}
                        className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white text-sm font-medium rounded-lg transition-colors"
                      >
                        {addingTag ? <Spinner size="sm" /> : <span>+ Add Tag</span>}
                      </button>
                    </form>
                  </div>
                </div>
              )}
            </div>

            {/* ── Tag Filter ── */}
            <div>
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-1">Filter Objects by Tag</h3>
              <p className="text-sm text-gray-500 dark:text-gray-400 mb-5">Search for objects across all buckets by tag key and value.</p>

              <div className="bg-gray-50 dark:bg-gray-800/50 p-5 rounded-xl border border-gray-200 dark:border-gray-700">
                <form onSubmit={handleFilterTags} className="flex flex-wrap gap-3 items-end">
                  <div className="flex-1 min-w-40">
                    <label className="block text-xs text-gray-500 mb-1">Tag Key <span className="text-red-400">*</span></label>
                    <input
                      id="filter-tag-key"
                      type="text"
                      placeholder="e.g. environment"
                      required
                      value={filterTagKey}
                      onChange={e => setFilterTagKey(e.target.value)}
                      className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-700 rounded-lg dark:bg-gray-900 focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div className="flex-1 min-w-40">
                    <label className="block text-xs text-gray-500 mb-1">Tag Value <span className="text-gray-400">(optional)</span></label>
                    <input
                      id="filter-tag-value"
                      type="text"
                      placeholder="e.g. production"
                      value={filterTagValue}
                      onChange={e => setFilterTagValue(e.target.value)}
                      className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-700 rounded-lg dark:bg-gray-900 focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <button
                    id="filter-search-btn"
                    type="submit"
                    disabled={filterLoading}
                    className="flex items-center gap-2 px-5 py-2 bg-gray-800 dark:bg-gray-600 hover:bg-gray-900 dark:hover:bg-gray-500 disabled:opacity-60 text-white text-sm font-medium rounded-lg transition-colors"
                  >
                    {filterLoading ? <Spinner size="sm" /> : <Search className="w-4 h-4" />}
                    Search
                  </button>
                </form>
              </div>

              {filteredObjects.length > 0 && (
                <div className="mt-5 border border-gray-200 dark:border-gray-800 rounded-xl overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm whitespace-nowrap">
                      <thead className="bg-gray-50 dark:bg-gray-800/60 text-gray-600 dark:text-gray-400 border-b border-gray-200 dark:border-gray-700">
                        <tr>
                          <th className="px-5 py-3 font-semibold">Object Key</th>
                          <th className="px-5 py-3 font-semibold">Size</th>
                          <th className="px-5 py-3 font-semibold">Type</th>
                          <th className="px-5 py-3 font-semibold">Tags</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                        {filteredObjects.map(obj => (
                          <tr key={obj.objectKey} className="hover:bg-gray-50 dark:hover:bg-gray-800/40 transition-colors">
                            <td className="px-5 py-3 font-mono text-gray-900 dark:text-gray-100 text-xs">{obj.objectKey}</td>
                            <td className="px-5 py-3 text-gray-500 dark:text-gray-400">{obj.sizeFormatted || '—'}</td>
                            <td className="px-5 py-3 text-gray-500 dark:text-gray-400 uppercase text-xs">
                              {obj.contentType?.split('/')[1] || obj.contentType || '—'}
                            </td>
                            <td className="px-5 py-3">
                              <div className="flex flex-wrap gap-1">
                                {(obj.tags || []).map(tag => (
                                  <span
                                    key={tag.key}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800"
                                  >
                                    <span className="font-semibold">{tag.key}</span>
                                    <span className="text-indigo-400">:</span>
                                    <span>{tag.value}</span>
                                  </span>
                                ))}
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {!filterLoading && filteredObjects.length === 0 && filterTagKey && (
                <p className="mt-4 text-sm text-center text-gray-400 dark:text-gray-500">No objects matched your tag filter.</p>
              )}
            </div>

          </div>
        )}

        {/* ===================== VERSIONS TAB ===================== */}
        {activeTab === 'versions' && (
          <div className="p-6">
            <div className="flex justify-between items-center mb-6">
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Object Versions</h3>
              <button 
                onClick={handleEnableVersioning} 
                disabled={lifecycleStatus?.versioningEnabled || isVersioningEnabledLocally}
                className={`px-4 py-2 text-white text-sm font-medium rounded-lg transition-colors ${(lifecycleStatus?.versioningEnabled || isVersioningEnabledLocally) ? 'bg-gray-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'}`}
              >
                {(lifecycleStatus?.versioningEnabled || isVersioningEnabledLocally) ? 'Versioning Enabled' : 'Enable Versioning'}
              </button>
            </div>

            {/* Info banner */}
            <div className="flex items-start gap-3 p-4 mb-4 rounded-xl border border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-900/20">
              <svg className="w-5 h-5 text-blue-500 dark:text-blue-400 flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M12 2a10 10 0 100 20A10 10 0 0012 2z" />
              </svg>
              <div>
                <p className="text-sm font-semibold text-blue-800 dark:text-blue-300 mb-0.5">How versioning works</p>
                <p className="text-sm text-blue-700 dark:text-blue-400">
                  To track multiple versions of an object, upload it using a{' '}
                  <span className="font-semibold">custom key</span> (e.g.{' '}
                  <code className="px-1 py-0.5 rounded bg-blue-100 dark:bg-blue-800 font-mono text-xs">reports/q1.pdf</code>).
                  Each time you re-upload a file with the <span className="font-semibold">same key</span>, CloudVault
                  saves it as a new version instead of overwriting the previous one.
                </p>
              </div>
            </div>

            {(lifecycleStatus?.versioningEnabled || isVersioningEnabledLocally) && (
              <div className="space-y-6">
                <div className="w-full max-w-sm">
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Select Object</label>
                  <select
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg dark:bg-gray-800 text-sm"
                    value={selectedVersionObject}
                    onChange={(e) => {
                      setSelectedVersionObject(e.target.value);
                      fetchVersions(e.target.value);
                    }}
                  >
                    <option value="">-- Select an object --</option>
                    {uniqueObjectKeys.map(key => (
                      <option key={key} value={key}>{key}</option>
                    ))}
                  </select>
                </div>

                {selectedVersionObject && (
                  <div className="overflow-x-auto border border-gray-200 dark:border-gray-800 rounded-lg">
                    <table className="w-full text-left text-sm whitespace-nowrap">
                      <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-600 dark:text-gray-400">
                        <tr>
                          <th className="px-6 py-3 font-semibold">Version #</th>
                          <th className="px-6 py-3 font-semibold">Size</th>
                          <th className="px-6 py-3 font-semibold">Tier</th>
                          <th className="px-6 py-3 font-semibold">Status</th>
                          <th className="px-6 py-3 font-semibold">Created</th>
                          <th className="px-6 py-3 font-semibold text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-200 dark:divide-gray-800">
                        {versionsList.map(v => (
                          <tr key={v.versionNumber} className={`hover:bg-gray-50 dark:hover:bg-gray-800/50 ${v.deleted ? 'opacity-60' : ''}`}>
                            <td className="px-6 py-4 font-mono text-gray-900 dark:text-gray-100">v{v.versionNumber}</td>
                            <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{formatBytes(v.sizeBytes)}</td>
                            <td className="px-6 py-4">
                              <Badge text={v.currentTier || 'STANDARD'} color={v.currentTier} />
                            </td>
                            <td className="px-6 py-4">
                              {v.current && <Badge text="Current" color="green" />}
                              {v.deleted && <Badge text="Deleted" color="red" />}
                            </td>
                            <td className="px-6 py-4 text-gray-500 dark:text-gray-400">{new Date(v.createdAt).toLocaleString()}</td>
                            <td className="px-6 py-4 text-right space-x-3">
                              {!v.deleted && (
                                <button
                                  onClick={() => handleDownloadVersion(v.versionNumber)}
                                  className="text-gray-500 hover:text-green-600 dark:text-gray-400 dark:hover:text-green-400"
                                  title="Download Version"
                                >
                                  <Download className="w-4 h-4 inline" />
                                </button>
                              )}
                              {!v.current && (
                                <button onClick={() => handleDeleteVersion(v.versionNumber)} className="text-gray-500 hover:text-red-600 dark:text-gray-400 dark:hover:text-red-400" title="Delete Version">
                                  <Trash2 className="w-4 h-4 inline" />
                                </button>
                              )}
                            </td>
                          </tr>
                        ))}
                        {versionsList.length === 0 && (
                          <tr><td colSpan="6" className="px-6 py-6 text-center text-gray-500">No versions found.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

      </div>

      {/* Upload Modal */}
      <Modal isOpen={isUploadOpen} onClose={() => setIsUploadOpen(false)} title="Upload Object">
        <form onSubmit={handleUpload} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">File</label>
            <input type="file" required onChange={e => setUploadFile(e.target.files[0])} className="w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100 dark:file:bg-blue-900/30 dark:file:text-blue-400" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Custom Key (Optional)</label>
            <input type="text" value={uploadKey} onChange={e => setUploadKey(e.target.value)} placeholder="e.g. folder/image.png" className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg dark:bg-gray-800 text-sm" />
          </div>
          <div className="pt-4 flex justify-end gap-3">
            <button type="button" onClick={() => setIsUploadOpen(false)} className="px-4 py-2 text-sm bg-white border rounded-lg dark:bg-gray-800 dark:border-gray-700">Cancel</button>
            <button type="submit" disabled={uploading} className="px-4 py-2 text-sm text-white bg-blue-600 rounded-lg">
              {uploading ? <Spinner size="sm" className="text-white" /> : 'Upload'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
