const fetch = require('node-fetch'); // Using built-in fetch if Node 18+, but let's just use axios since it's in the frontend project

const axios = require('axios');

async function test() {
  try {
    const loginRes = await axios.post('http://localhost:8080/api/auth/login', {
      username: 'mohit',
      password: 'Password123!'
    });
    const token = loginRes.data.token;
    
    try {
      const statusRes = await axios.get('http://localhost:8080/api/lifecycle/buckets/second-bucket/status', {
        headers: { Authorization: `Bearer ${token}` }
      });
      console.log('Status Response:', statusRes.data);
    } catch (err) {
      console.error('Status Error Response:', err.response?.data || err.message);
    }
  } catch (e) {
    console.error('Login error:', e.message);
  }
}

test();
