import React, { useState } from 'react';
import api from '../api';
import { PackageMinus, CheckCircle, ShieldAlert } from 'lucide-react';

export default function Pick() {
  const [step, setStep] = useState(1); // 1 = Reserve, 2 = Confirm
  const [formData, setFormData] = useState({
    sku: '',
    locationId: '',
    qty: '',
    actualQty: '',
    taskId: ''
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleReserve = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    setError('');

    try {
      await api.post('/inventory/pick/reserve', {
        sku: formData.sku,
        locationId: formData.locationId,
        qty: parseInt(formData.qty, 10),
        taskId: formData.taskId
      });
      setStep(2);
      setIdempotencyKey(crypto.randomUUID());
      setFormData({ ...formData, actualQty: formData.qty }); // Default actual to reserved
      setMessage('Stock reserved! Please proceed to bin and confirm pick.');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to reserve stock.');
    }
    setLoading(false);
  };

  const handleConfirm = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    setError('');

    try {
      await api.post('/inventory/pick/confirm', {
        sku: formData.sku,
        locationId: formData.locationId,
        reservedQty: parseInt(formData.qty, 10),
        actualQty: parseInt(formData.actualQty, 10),
        taskId: formData.taskId
      }, {
        headers: { 'X-Idempotency-Key': idempotencyKey }
      });

      setMessage('Pick confirmed and sent to SAP successfully!');
      setStep(1);
      setFormData({ sku: '', locationId: '', qty: '', actualQty: '', taskId: '' });
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to confirm pick.');
    }
    setLoading(false);
  };

  return (
    <div className="animate-fade-in" style={{ maxWidth: '600px', margin: '0 auto' }}>
      <div style={{ marginBottom: '2rem' }}>
        <h2 style={{ marginBottom: '0.5rem' }}>Picking</h2>
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

        {step === 1 ? (
          <form onSubmit={handleReserve}>
            <div className="form-group">
              <label>SKU to Pick</label>
              <input type="text" name="sku" value={formData.sku} onChange={handleChange} required placeholder="Enter SKU" />
            </div>
            <div className="form-group">
              <label>Source Location UUID</label>
              <input type="text" name="locationId" value={formData.locationId} onChange={handleChange} required placeholder="Enter Location UUID" />
            </div>
            <div className="form-group">
              <label>Quantity Requested</label>
              <input type="number" name="qty" min="1" value={formData.qty} onChange={handleChange} required placeholder="5" />
            </div>
            <div className="form-group" style={{ marginBottom: '2rem' }}>
              <label>SAP Pick Task ID</label>
              <input type="text" name="taskId" value={formData.taskId} onChange={handleChange} required placeholder="PICK-555123" />
            </div>
            <button type="submit" className="btn btn-secondary" style={{ width: '100%', borderColor: 'var(--warning)', color: 'var(--warning)' }} disabled={loading}>
              <ShieldAlert size={18} />
              {loading ? 'Reserving...' : 'Step 1: Reserve Stock'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleConfirm} className="animate-fade-in">
            <div style={{ padding: '1rem', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', marginBottom: '1.5rem' }}>
              <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>You are picking <strong>{formData.qty}</strong> units of <strong>{formData.sku}</strong> from <strong>{formData.locationId}</strong>.</p>
            </div>
            <div className="form-group" style={{ marginBottom: '2rem' }}>
              <label>Actual Quantity Found (Short Pick Allowed)</label>
              <input type="number" name="actualQty" min="0" max={formData.qty} value={formData.actualQty} onChange={handleChange} required />
            </div>
            <div style={{ display: 'flex', gap: '1rem' }}>
              <button type="button" className="btn btn-secondary" onClick={() => setStep(1)} style={{ flex: 1 }}>Cancel</button>
              <button type="submit" className="btn btn-primary" style={{ flex: 2 }} disabled={loading}>
                <PackageMinus size={18} />
                {loading ? 'Confirming...' : 'Step 2: Confirm Pick'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
