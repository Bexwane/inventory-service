import React, { useState, useEffect } from 'react';
import api from '../api';
import { PackageMinus, CheckCircle, ShieldAlert, ScanLine } from 'lucide-react';

export default function Pick({ embedded = false, onSuccess }) {
  const [step, setStep] = useState(() => {
    const saved = localStorage.getItem('pickState_step');
    return saved ? parseInt(saved, 10) : 1;
  });
  const [formData, setFormData] = useState(() => {
    const saved = localStorage.getItem('pickState_formData');
    return saved ? JSON.parse(saved) : {
      sku: '',
      locationId: '',
      containerId: '',
      qty: '',
      actualQty: '',
      taskId: ''
    };
  });
  const [idempotencyKey, setIdempotencyKey] = useState(() => {
    return localStorage.getItem('pickState_idempotencyKey') || '';
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  // Persist state
  useEffect(() => {
    localStorage.setItem('pickState_step', step.toString());
    localStorage.setItem('pickState_formData', JSON.stringify(formData));
    localStorage.setItem('pickState_idempotencyKey', idempotencyKey);
  }, [step, formData, idempotencyKey]);

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleReserve = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    setError('');

    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!uuidRegex.test(formData.locationId)) {
      setError("Source Location must be a valid 36-character UUID.");
      setLoading(false);
      return;
    }
    if (formData.containerId && !uuidRegex.test(formData.containerId)) {
      setError("Container ID must be a valid 36-character UUID if provided.");
      setLoading(false);
      return;
    }

    try {
      await api.post('/inventory/pick/reserve', {
        sku: formData.sku,
        locationId: formData.locationId,
        containerId: formData.containerId || null,
        qty: parseInt(formData.qty, 10),
        taskId: formData.taskId
      });
      setStep(2);
      setIdempotencyKey(crypto.randomUUID());
      setFormData({ ...formData, actualQty: formData.qty }); // Default actual to reserved
      setMessage('Stock reserved! Please proceed to bin and confirm pick.');
      if (onSuccess) onSuccess();
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
        containerId: formData.containerId || null,
        reservedQty: parseInt(formData.qty, 10),
        actualQty: parseInt(formData.actualQty, 10),
        taskId: formData.taskId
      }, {
        headers: { 'X-Idempotency-Key': idempotencyKey }
      });

      setMessage('Pick confirmed and sent to SAP successfully!');
      
      // Reset state
      setStep(1);
      setFormData({ sku: '', locationId: '', containerId: '', qty: '', actualQty: '', taskId: '' });
      setIdempotencyKey('');
      localStorage.removeItem('pickState_step');
      localStorage.removeItem('pickState_formData');
      localStorage.removeItem('pickState_idempotencyKey');

      if (onSuccess) onSuccess();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to confirm pick.');
    }
    setLoading(false);
  };

  const handleCancel = async () => {
    setLoading(true);
    try {
      await api.post('/inventory/pick/release', {
        sku: formData.sku,
        locationId: formData.locationId,
        containerId: formData.containerId || null,
        qty: parseInt(formData.qty, 10),
        taskId: formData.taskId
      });
      setMessage('Reservation released successfully.');
      
      // Reset state
      setStep(1);
      setFormData({ sku: '', locationId: '', containerId: '', qty: '', actualQty: '', taskId: '' });
      setIdempotencyKey('');
      localStorage.removeItem('pickState_step');
      localStorage.removeItem('pickState_formData');
      localStorage.removeItem('pickState_idempotencyKey');
      
      if (onSuccess) onSuccess();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to release reservation.');
    }
    setLoading(false);
  };

  return (
    <div className="animate-fade-in" style={{ 
      display: 'flex', 
      flexDirection: 'column', 
      alignItems: 'center', 
      justifyContent: 'center', 
      height: '100%', 
      width: '100%' 
    }}>
      <div className="glass-panel" style={{ 
        width: '100%', 
        maxWidth: '700px', 
        padding: '3rem', 
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1)',
        borderRadius: '16px'
      }}>
        
        <h2 style={{ marginBottom: '2rem', fontSize: '2rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <PackageMinus size={36} color="var(--accent-primary)" />
          Picking
        </h2>

        {message && (
          <div style={{ padding: '1rem', background: 'rgba(16, 185, 129, 0.1)', color: 'var(--success)', borderRadius: '8px', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '1.1rem', fontWeight: 'bold' }}>
            <CheckCircle size={24} />
            {message}
          </div>
        )}

        {error && (
          <div style={{ padding: '1rem', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)', borderRadius: '8px', marginBottom: '1.5rem', fontSize: '1.1rem', fontWeight: 'bold' }}>
            {error}
          </div>
        )}

        {step === 1 ? (
          <form onSubmit={handleReserve}>
            <div style={{ background: '#ffffff', padding: '1.5rem', borderRadius: '12px', border: '1px solid var(--border-color)', boxShadow: '0 2px 4px rgba(0,0,0,0.05)', marginBottom: '1.5rem' }}>
              <h3 style={{ fontSize: '1rem', color: 'var(--text-secondary)', marginBottom: '1rem', textTransform: 'uppercase', letterSpacing: '1px' }}>Task & Item Details</h3>
              
              <div className="form-group" style={{ marginBottom: '1rem' }}>
                <label style={{ fontSize: '0.95rem', marginBottom: '0.25rem' }}>SAP Pick Task ID</label>
                <input type="text" name="taskId" value={formData.taskId} onChange={handleChange} required placeholder="e.g. PICK-555123" style={{ fontSize: '1.1rem', padding: '0.75rem 1rem', borderRadius: '8px' }} />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1rem' }}>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label style={{ fontSize: '0.95rem', marginBottom: '0.25rem' }}>SKU to Pick (Barcode)</label>
                  <input type="text" name="sku" value={formData.sku} onChange={handleChange} required placeholder="Enter SKU" style={{ fontSize: '1.1rem', padding: '0.75rem 1rem', borderRadius: '8px' }} />
                </div>
                
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label style={{ fontSize: '0.95rem', marginBottom: '0.25rem' }}>Quantity</label>
                  <input type="number" name="qty" min="1" value={formData.qty} onChange={handleChange} required placeholder="1" style={{ fontSize: '1.1rem', padding: '0.75rem 1rem', borderRadius: '8px' }} />
                </div>
              </div>
            </div>

            <div style={{ background: '#ffffff', padding: '1.5rem', borderRadius: '12px', border: '1px solid var(--border-color)', boxShadow: '0 2px 4px rgba(0,0,0,0.05)', marginBottom: '2rem' }}>
              <h3 style={{ fontSize: '1rem', color: 'var(--text-secondary)', marginBottom: '1rem', textTransform: 'uppercase', letterSpacing: '1px' }}>Location Details</h3>
              
              <div className="form-group" style={{ marginBottom: '1rem' }}>
                <label style={{ fontSize: '0.95rem', marginBottom: '0.25rem' }}>Source Location UUID</label>
                <input type="text" name="locationId" value={formData.locationId} onChange={handleChange} required placeholder="Scan Bin / Location Barcode" style={{ fontSize: '1.1rem', padding: '0.75rem 1rem', borderRadius: '8px', fontFamily: 'monospace' }} />
              </div>
              
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label style={{ fontSize: '0.95rem', marginBottom: '0.25rem' }}>Container UUID (Optional)</label>
                <input type="text" name="containerId" value={formData.containerId} onChange={handleChange} placeholder="e.g. Tote or Pallet LPN" style={{ fontSize: '1.1rem', padding: '0.75rem 1rem', borderRadius: '8px', fontFamily: 'monospace' }} />
              </div>
            </div>
            
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <button type="submit" className="btn btn-warning btn-large" style={{ flex: 1, height: '70px', fontSize: '1.25rem', borderRadius: '12px', fontWeight: 'bold' }} disabled={loading}>
                <ShieldAlert size={24} />
                {loading ? 'Reserving...' : 'Step 1: Reserve Stock'}
              </button>
              <button type="button" className="btn-scan-circle desktop-only" onClick={() => alert('Scan triggered')}>
                <ScanLine size={28} />
              </button>
            </div>
          </form>
        ) : (
          <form onSubmit={handleConfirm} className="animate-fade-in">
            <div style={{ padding: '1.5rem', background: '#ffffff', borderRadius: '12px', border: '1px solid var(--border-color)', boxShadow: '0 2px 4px rgba(0,0,0,0.05)', marginBottom: '2rem' }}>
              <p style={{ fontSize: '1.2rem', color: 'var(--text-secondary)', margin: 0 }}>
                You are picking <strong style={{ color: 'var(--text-primary)', fontSize: '1.4rem' }}>{formData.qty}</strong> units of <strong style={{ color: 'var(--accent-primary)', fontSize: '1.4rem' }}>{formData.sku}</strong> from <strong style={{ fontFamily: 'monospace' }}>{formData.locationId}</strong>.
              </p>
            </div>
            
            <div className="form-group" style={{ marginBottom: '2.5rem' }}>
              <label style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>Actual Quantity Found (Short Pick Allowed)</label>
              <input type="number" name="actualQty" min="0" max={formData.qty} value={formData.actualQty} onChange={handleChange} required style={{ fontSize: '1.25rem', padding: '1rem', borderRadius: '12px', border: '2px solid var(--accent-primary)' }} />
            </div>
            
            <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
              <button type="button" className="btn btn-secondary btn-large" onClick={handleCancel} disabled={loading} style={{ width: '120px', height: '70px', fontSize: '1.1rem', borderRadius: '12px' }}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary btn-large" style={{ flex: 1, height: '70px', fontSize: '1.25rem', borderRadius: '12px', fontWeight: 'bold' }} disabled={loading}>
                <CheckCircle size={24} />
                {loading ? 'Confirming...' : 'Step 2: Confirm Pick'}
              </button>
              <button type="button" className="btn-scan-circle desktop-only" onClick={() => alert('Scan triggered')}>
                <ScanLine size={28} />
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
