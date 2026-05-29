import { api } from './axios';

export const enableVersioning = async (bucketName) => {
  const response = await api.post(`/api/versioning/buckets/${bucketName}/enable`);
  return response.data;
};

export const getVersions = async (bucketName, objectKey) => {
  const response = await api.get(`/api/versioning/buckets/${bucketName}/versions`, {
    params: { objectKey }
  });
  return response.data;
};

export const deleteVersion = async (bucketName, versionNumber, objectKey) => {
  const response = await api.delete(`/api/versioning/buckets/${bucketName}/versions/${versionNumber}`, {
    params: { objectKey }
  });
  return response.data;
};

export const downloadVersion = async (bucketName, objectKey, versionNumber) => {
  return api.get(`/api/versioning/buckets/${bucketName}/versions/download`, {
    params: { objectKey, versionNumber },
    responseType: 'blob'
  });
};

