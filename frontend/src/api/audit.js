import { api } from './axios';

export const getAuditLogs = async (page = 0, size = 20) => {
  const response = await api.get('/api/audit/logs', {
    params: { page, size }
  });
  return response.data;
};

export const getBucketAuditLogs = async (bucketName, page = 0, size = 20) => {
  const response = await api.get(`/api/audit/logs/bucket/${bucketName}`, {
    params: { page, size }
  });
  return response.data;
};

export const getObjectAuditLogs = async (objectKey) => {
  const response = await api.get('/api/audit/logs/object', {
    params: { objectKey }
  });
  return response.data;
};
