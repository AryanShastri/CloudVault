import { api } from './axios';

export const getLifecycleStatus = async (bucketName) => {
  const response = await api.get(`/api/lifecycle/buckets/${bucketName}/status`);
  return response.data;
};

export const getLifecycleHistory = async (bucketName) => {
  const response = await api.get(`/api/lifecycle/buckets/${bucketName}/history`);
  return response.data;
};

export const setLifecyclePolicy = async (bucketName, policyData) => {
  const response = await api.post(`/api/lifecycle/buckets/${bucketName}/policy`, policyData);
  return response.data;
};

export const requestRestore = async (bucketName, objectKey, restoreSpeed) => {
  const response = await api.post(`/api/lifecycle/buckets/${bucketName}/restore`, {
    objectKey,
    restoreSpeed
  });
  return response.data;
};

export const getRestoreStatus = async (bucketName) => {
  const response = await api.get(`/api/lifecycle/buckets/${bucketName}/restore/status`);
  return response.data;
};

export const getLifecyclePolicy = async (bucketName) => {
  const response = await api.get(`/api/lifecycle/buckets/${bucketName}/policy`);
  return response.data;
};

