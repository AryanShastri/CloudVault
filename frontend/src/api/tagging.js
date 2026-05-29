import { api } from './axios';

export const getObjectTags = async (bucketName, objectKey) => {
  const response = await api.get(`/api/storage/buckets/${bucketName}/objects/${encodeURIComponent(objectKey)}/tags`);
  return response.data;
};

export const addObjectTag = async (bucketName, objectKey, key, value) => {
  const response = await api.put(`/api/storage/buckets/${bucketName}/objects/${encodeURIComponent(objectKey)}/tags`, {
    key,
    value
  });
  return response.data;
};

export const deleteObjectTag = async (bucketName, objectKey, tagKey) => {
  const response = await api.delete(`/api/storage/buckets/${bucketName}/objects/${encodeURIComponent(objectKey)}/tags/${encodeURIComponent(tagKey)}`);
  return response.data;
};
