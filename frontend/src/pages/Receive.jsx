import React, { useState } from 'react';
import api from '../api';
import { PackagePlus, CheckCircle } from 'lucide-react';

export default function Receive() {
  const [formData, setFormData] = useState({
    sku: '',
    locationId: '',
    qty: '',
    taskId: ''
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    setError('');

    try {
      // Generate a simple UUID for idempotency
      const idempotencyKey = crypto.randomUUID();
      
      await api.post('/inventory/receive', {
        sku: formData.sku,
        locationId: formData.locationId,
        qty: parseInt(formData.qty, 10),
        taskId: formData.taskId
      }, {
        headers: { 'X-Idempotency-Key': idempotencyKey }
      });

      setMessage('Stock received successfully!');
      setFormData({ sku: '', locationId: '', qty: '', taskId: '' });
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to receive stock. Please check inputs.');
    }
    setLoading(false);
  };

  return (
    <div className="animate-fade-in" style={{ maxWidth: '600px', margin: '0 auto' }}>
      <div style={{ marginBottom: '2rem' }}>
        <h2 style={{ marginBottom: '0.5rem' }}>Putaway</h2>
      </div>

      <div className="glass-panel">
        {message && (
          <div style={{ padding: '1rem', background: 'rgba(16, 185, 129, 0.1)', color: 'var(--success)', borderRadius: '8px', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <CheckCircle size={18} />
            {message}
          </div>
        )}

        {error && (
          <div style={{ padding: '1rem', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)', borderRadius: '8px', marginBottom: '1.5rem' }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>SKU (Barcode)</label>
            <input type="text" name="sku" value={formData.sku} onChange={handleChange} required placeholder="Enter SKU" />
          </div>

          <div className="form-group">
            <label>Destination Location UUID</label>
            <input type="text" name="locationId" value={formData.locationId} onChange={handleChange} required placeholder="Enter Location UUID" />
          </div>

          <div className="form-group">
            <label>Quantity Received</label>
            <input type="number" name="qty" min="1" value={formData.qty} onChange={handleChange} required placeholder="10" />
          </div>

          <div className="form-group" style={{ marginBottom: '2rem' }}>
            <label>Reference ID / PO Number</label>
            <input type="text" name="taskId" value={formData.taskId} onChange={handleChange} required placeholder="Enter Reference ID" />
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={loading}>
            <PackagePlus size={18} />
            {loading ? 'Processing...' : 'Confirm Putaway'}
          </button>
        </form>
      </div>
    </div>
  );
}
