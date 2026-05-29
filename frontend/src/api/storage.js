import { api } from './axios';

export const getBuckets = async () => {
  const response = await api.get('/api/storage/buckets');
  return response.data;
};

export const createBucket = async (data) => {
  const response = await api.post('/api/storage/buckets', data);
  return response.data;
};

export const deleteBucket = async (bucketName) => {
  const response = await api.delete(`/api/storage/buckets/${bucketName}`);
  return response.data;
};

export const getObjects = async (bucketName, page = 0, size = 20) => {
  const response = await api.get(`/api/storage/buckets/${bucketName}/objects`, {
    params: { page, size }
  });
  return response.data;
};

export const uploadObject = async (bucketName, file, customKey, { onUploadProgress } = {}) => {
  const formData = new FormData();
  formData.append('file', file);
  if (customKey) {
    formData.append('key', customKey);
  }

  // Return full response so callers can inspect status (200 vs 202)
  const response = await api.post(`/api/storage/buckets/${bucketName}/objects`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    onUploadProgress,
    // Prevent axios from throwing on 202
    validateStatus: (status) => status >= 200 && status < 300,
  });
  return response; // { status, data }
};

export const getUploadJob = async (jobId) => {
  const response = await api.get(`/api/storage/upload-jobs/${jobId}`);
  return response.data;
};

export const getUploadJobs = async () => {
  const response = await api.get('/api/storage/upload-jobs');
  return response.data;
};

export const fetchObjectDownload = async (bucketName, objectKey) => {
  return api.get(
    `/api/storage/buckets/${bucketName}/objects/${encodeURIComponent(objectKey)}/download`,
    { responseType: 'blob' }
  );
};

export const saveObjectBlob = (blob, objectKey, contentDisposition) => {
  const url = window.URL.createObjectURL(new Blob([blob]));
  const link = document.createElement('a');
  link.href = url;

  let fileName = objectKey;
  if (contentDisposition?.includes('filename=')) {
    const filenameMatch = contentDisposition.match(/filename="?([^"]+)"?/);
    if (filenameMatch?.[1]) {
      fileName = filenameMatch[1];
    }
  }

  link.setAttribute('download', fileName);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

/** @deprecated Prefer fetchObjectDownload + scan header handling in the UI layer */
export const downloadObject = async (bucketName, objectKey) => {
  const response = await fetchObjectDownload(bucketName, objectKey);
  saveObjectBlob(response.data, objectKey, response.headers['content-disposition']);
};

export const presignUrl = async (bucketName, objectKey, expiryMinutes = 60) => {
  const response = await api.get(`/api/storage/buckets/${bucketName}/objects/${encodeURIComponent(objectKey)}/presign`, {
    params: { expiryMinutes }
  });
  return response.data;
};

export const deleteObject = async (bucketName, objectKey) => {
  const response = await api.delete(`/api/storage/buckets/${bucketName}/objects/${encodeURIComponent(objectKey)}`);
  return response.data;
};

export const filterObjects = async (tagKey, tagValue) => {
  const params = { tagKey };
  if (tagValue) params.tagValue = tagValue;
  const response = await api.get('/api/storage/objects/filter', { params });
  return response.data;
};
