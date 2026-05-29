import { api } from './axios';

export const getAdminOverview = async () => {
  const response = await api.get('/api/admin/overview');
  return response.data;
};

export const getAdminUsers = async () => {
  const response = await api.get('/api/admin/users');
  return response.data;
};

export const runBilling = async (year, month) => {
  const response = await api.post(`/api/admin/billing/run/${year}/${month}`);
  return response.data;
};
