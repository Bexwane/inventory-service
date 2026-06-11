import React, { useState } from 'react';
import api from '../api';
import { PackagePlus, CheckCircle, ScanLine, Plus, Box, FolderOpen, ArrowRight, Trash2 } from 'lucide-react';

export default function Receive({ embedded = false, onSuccess }) {
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  // Batch State
  const [taskId, setTaskId] = useState(null);
  const [sourceLocationId, setSourceLocationId] = useState('');
  const [sourceConfirmed, setSourceConfirmed] = useState(false);
  
  // Array of { containerId: string, items: [{ sku, destinationLocationId, qty }] }
  const [containers, setContainers] = useState([]);
  const [activeContainerId, setActiveContainerId] = useState(null);

  // Current Item Form State
  const [itemForm, setItemForm] = useState({ sku: '', destinationLocationId: '', qty: 1 });
  
  // UUID Regex for validation
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  const handleStartTask = async () => {
    setLoading(true);
    try {
      const res = await api.post('/inventory/tasks/generate');
      setTaskId(res.data);
    } catch (err) {
      console.error(err);
      alert("Failed to generate task ID: " + (err.response?.data?.message || err.message));
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmSource = () => {
    if (!uuidRegex.test(sourceLocationId)) {
      alert("Error: Source Location must be a valid UUID.");
      return;
    }
    setSourceConfirmed(true);
  };

  const handleAddContainer = () => {
    const newId = prompt("Scan or Enter Container UUID:");
    if (!newId) return;
    if (!uuidRegex.test(newId)) {
      alert("Error: Container ID must be a valid UUID.");
      return;
    }
    if (containers.find(c => c.containerId === newId)) {
      alert("Container already added.");
      setActiveContainerId(newId);
      return;
    }
    
    setContainers([...containers, { containerId: newId, items: [] }]);
    setActiveContainerId(newId);
  };

  const handleAddItem = (e) => {
    e.preventDefault();
    if (!activeContainerId) {
      alert("Please select or add a container first.");
      return;
    }
    if (!uuidRegex.test(itemForm.destinationLocationId)) {
      alert("Error: Destination Location must be a valid UUID.");
      return;
    }

    setContainers(prev => prev.map(c => {
      if (c.containerId === activeContainerId) {
        return {
          ...c,
          items: [...c.items, { ...itemForm, qty: parseInt(itemForm.qty, 10) }]
        };
      }
      return c;
    }));
    
    // Reset form except location (often workers put multiple SKUs into the same bin)
    setItemForm(prev => ({ ...prev, sku: '', qty: 1 }));
  };

  const handleRemoveItem = (containerId, index) => {
    setContainers(prev => prev.map(c => {
      if (c.containerId === containerId) {
        const newItems = [...c.items];
        newItems.splice(index, 1);
        return { ...c, items: newItems };
      }
      return c;
    }));
  };

  const handleSubmitBatch = async () => {
    if (containers.length === 0 || containers.every(c => c.items.length === 0)) {
      alert("Cannot submit an empty batch.");
      return;
    }

    // Filter out empty containers
    const payloadContainers = containers.filter(c => c.items.length > 0);

    setLoading(true);
    setSuccess(false);

    try {
      await api.post('/inventory/receive/batch', {
        taskId,
        sourceLocationId,
        containers: payloadContainers
      }, {
        headers: { 'X-Idempotency-Key': crypto.randomUUID() }
      });
      
      setSuccess(true);
      // Reset State
      setTaskId(null);
      setSourceLocationId('');
      setSourceConfirmed(false);
      setContainers([]);
      setActiveContainerId(null);
      setItemForm({ sku: '', destinationLocationId: '', qty: 1 });
      
      if (onSuccess) onSuccess();
      setTimeout(() => setSuccess(false), 3000);
    } catch (error) {
      console.error(error);
      const msg = error.response?.data?.message || error.response?.data?.error || error.message || "Unknown error";
      alert(`Error receiving batch: ${msg}`);
    } finally {
      setLoading(false);
    }
  };

  // ── Render Helpers ──────────────────────────────────────────

  if (success) {
    return (
      <div className="animate-fade-in" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
        <div style={{ background: '#dcfce7', color: '#166534', padding: '3rem', borderRadius: '24px', textAlign: 'center' }}>
          <CheckCircle size={64} style={{ margin: '0 auto 1rem' }} />
          <h2 style={{ fontSize: '2rem', margin: 0 }}>Batch Successful!</h2>
          <p style={{ marginTop: '1rem', fontSize: '1.2rem', opacity: 0.8 }}>All items put away safely.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="animate-fade-in" style={!embedded ? { display: 'flex', justifyContent: 'center', minHeight: '80vh', padding: '2rem' } : { height: '100%', padding: '1rem' }}>
      
      <div style={{ background: 'var(--bg-card)', padding: '2.5rem', borderRadius: '24px', boxShadow: '0 20px 40px rgba(0,0,0,0.08)', width: '100%', maxWidth: '600px', border: '1px solid var(--border-color)', margin: embedded ? '0' : 'auto' }}>
        
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
          <div style={{ background: 'rgba(59, 130, 246, 0.1)', padding: '1rem', borderRadius: '16px', color: 'var(--accent-primary)' }}>
            <PackagePlus size={32} />
          </div>
          <div>
            <h2 style={{ fontSize: '1.8rem', fontWeight: 'bold', color: 'var(--text-primary)', margin: 0 }}>Batch Putaway</h2>
            {taskId && <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', fontWeight: 'bold', marginTop: '4px' }}>Task: {taskId}</div>}
          </div>
        </div>

        {/* STEP 1: Generate Task */}
        {!taskId && (
          <div style={{ textAlign: 'center', padding: '3rem 0' }}>
            <PackagePlus size={64} color="var(--text-muted)" style={{ margin: '0 auto 1.5rem', opacity: 0.5 }} />
            <h3 style={{ marginBottom: '1.5rem', color: 'var(--text-secondary)' }}>Ready to start a new Putaway batch?</h3>
            <button className="btn btn-primary btn-large" onClick={handleStartTask} disabled={loading} style={{ fontSize: '1.2rem', padding: '1rem 3rem', borderRadius: '16px' }}>
              {loading ? 'Starting...' : 'Start Putaway Task'}
            </button>
          </div>
        )}

        {/* STEP 2: Source Location */}
        {taskId && !sourceConfirmed && (
          <div className="animate-fade-in">
            <div className="form-group" style={{ marginBottom: '1.5rem' }}>
              <label style={{ fontSize: '1.2rem', marginBottom: '0.5rem' }}>Scan Source Location (Drop Zone)</label>
              <input 
                type="text" 
                value={sourceLocationId} 
                onChange={e => setSourceLocationId(e.target.value)} 
                placeholder="Scan Location UUID..." 
                style={{ fontSize: '1.25rem', padding: '1rem', borderRadius: '12px' }} 
              />
            </div>
            <button className="btn btn-primary btn-large" onClick={handleConfirmSource} style={{ width: '100%', borderRadius: '16px', fontSize: '1.2rem' }}>
              Confirm Source <ArrowRight size={20} />
            </button>
          </div>
        )}

        {/* STEP 3: Containers and Scanning */}
        {taskId && sourceConfirmed && (
          <div className="animate-fade-in">
            
            {/* Chrome-style Tabs */}
            <div style={{ display: 'flex', overflowX: 'auto', gap: '4px', marginBottom: '-1px', zIndex: 1, position: 'relative', paddingLeft: '8px' }}>
              {containers.length === 0 && (
                <div style={{ padding: '0.75rem 1.5rem', color: 'var(--danger)', fontWeight: 'bold', background: 'var(--bg-primary)', borderTopLeftRadius: '12px', borderTopRightRadius: '12px', border: '1px solid var(--border-color)', borderBottom: 'none' }}>
                  No containers added
                </div>
              )}
              {containers.map((c, index) => {
                const isActive = activeContainerId === c.containerId;
                return (
                  <button
                    type="button"
                    key={c.containerId}
                    onClick={() => setActiveContainerId(c.containerId)}
                    style={{
                      padding: '0.75rem 1.5rem',
                      background: isActive ? 'var(--bg-primary)' : 'var(--bg-secondary)',
                      color: isActive ? 'var(--accent-primary)' : 'var(--text-secondary)',
                      border: '1px solid var(--border-color)',
                      borderBottom: isActive ? 'none' : '1px solid var(--border-color)',
                      borderTopLeftRadius: '12px',
                      borderTopRightRadius: '12px',
                      fontWeight: 'bold',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.5rem',
                      whiteSpace: 'nowrap',
                      position: 'relative',
                      zIndex: isActive ? 2 : 1,
                      marginTop: isActive ? '0' : '4px',
                      boxShadow: isActive ? '0 -4px 10px rgba(0,0,0,0.02)' : 'none'
                    }}
                  >
                    Container {index + 1}
                    <span style={{ background: isActive ? 'rgba(59, 130, 246, 0.1)' : 'var(--bg-hover)', padding: '2px 8px', borderRadius: '50px', fontSize: '0.75rem' }}>
                      {c.items.length}
                    </span>
                  </button>
                );
              })}
              <button 
                type="button"
                onClick={handleAddContainer} 
                style={{ 
                  padding: '0.5rem', 
                  background: 'transparent', 
                  color: 'var(--text-secondary)', 
                  border: 'none', 
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  marginTop: '4px',
                  borderRadius: '50%',
                  margin: '4px 8px 4px 4px'
                }}
              >
                <Plus size={24} />
              </button>
            </div>

            {/* Active Container Content */}
            <div style={{ background: 'var(--bg-primary)', padding: '2rem', borderRadius: '16px', borderTopLeftRadius: containers.length > 0 ? '0' : '16px', border: '1px solid var(--border-color)', marginBottom: '2rem', position: 'relative', zIndex: 0 }}>
              <div style={{ marginBottom: '1.5rem', fontSize: '0.9rem', color: 'var(--text-secondary)', fontFamily: 'monospace', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Box size={16} /> UUID: {activeContainerId || 'N/A'}
              </div>
              <form onSubmit={handleAddItem}>
                <div className="form-group">
                  <label>SKU Barcode</label>
                  <input type="text" value={itemForm.sku} onChange={e => setItemForm({...itemForm, sku: e.target.value})} required placeholder="Enter SKU" style={{ background: 'var(--bg-card)' }} />
                </div>

                <div className="form-group">
                  <label>Destination Location UUID</label>
                  <input type="text" value={itemForm.destinationLocationId} onChange={e => setItemForm({...itemForm, destinationLocationId: e.target.value})} required placeholder="Enter Bin UUID" style={{ background: 'var(--bg-card)' }} />
                </div>

                <div className="form-group" style={{ marginBottom: '2rem' }}>
                  <label>Quantity</label>
                  <input type="number" min="1" value={itemForm.qty} onChange={e => setItemForm({...itemForm, qty: e.target.value})} required style={{ background: 'var(--bg-card)' }} />
                </div>

                <button type="submit" className="btn btn-primary" disabled={!activeContainerId} style={{ width: '100%', borderRadius: '12px', fontWeight: 'bold', height: '56px', fontSize: '1.1rem' }}>
                  <ArrowRight size={20} /> 
                  Add to {activeContainerId ? `Container ${containers.findIndex(c => c.containerId === activeContainerId) + 1}` : 'Container'}
                </button>
              </form>
            </div>

            {/* Scanned Items Summary */}
            <div style={{ marginBottom: '2rem' }}>
              <h4 style={{ margin: '0 0 1rem 0', color: 'var(--text-secondary)' }}>Items in Active Container</h4>
              {activeContainerId && containers.find(c => c.containerId === activeContainerId)?.items.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '2rem', border: '2px dashed var(--border-color)', borderRadius: '16px', color: 'var(--text-muted)' }}>
                  <FolderOpen size={32} style={{ margin: '0 auto 0.5rem', opacity: 0.5 }} />
                  Container is empty.
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  {activeContainerId && containers.find(c => c.containerId === activeContainerId)?.items.map((item, idx) => (
                    <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--bg-secondary)', padding: '0.75rem 1rem', borderRadius: '12px' }}>
                      <div>
                        <div style={{ fontWeight: 'bold', color: 'var(--text-primary)' }}>{item.sku} <span style={{ color: 'var(--accent-primary)' }}>x{item.qty}</span></div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>To: {item.destinationLocationId.substring(0,8)}...</div>
                      </div>
                      <button className="btn" style={{ padding: '0.5rem', color: 'var(--danger)', background: 'transparent' }} onClick={() => handleRemoveItem(activeContainerId, idx)}>
                        <Trash2 size={16} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Submit Batch */}
            <button 
              className="btn btn-primary btn-large" 
              onClick={handleSubmitBatch} 
              disabled={loading || containers.every(c => c.items.length === 0)}
              style={{ width: '100%', borderRadius: '16px', fontSize: '1.2rem', minHeight: '64px' }}
            >
              <Box size={24} /> {loading ? 'Processing Batch...' : 'Confirm Entire Batch'}
            </button>

          </div>
        )}

      </div>
    </div>
  );
}
