import { api } from './axios';

export const getPricing = async () => {
  const response = await api.get('/api/billing/pricing');
  return response.data;
};

export const getCurrentUsage = async () => {
  const response = await api.get('/api/billing/usage/current');
  return response.data;
};

export const getInvoices = async () => {
  const response = await api.get('/api/billing/invoices');
  return response.data;
};

export const getInvoiceById = async (invoiceId) => {
  const response = await api.get(`/api/billing/invoices/${invoiceId}`);
  return response.data;
};

export const generateInvoice = async () => {
  const response = await api.post('/api/billing/invoices/generate');
  return response.data;
};
