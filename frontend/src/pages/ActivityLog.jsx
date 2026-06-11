import React, { useState, useEffect } from 'react';
import api from '../api';
import { RefreshCw, ChevronLeft, ChevronRight, Activity, CheckCircle, XCircle } from 'lucide-react';

export default function ActivityLog() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [totalPages, setTotalPages] = useState(0);

  const fetchLogs = async (pageIndex) => {
    setLoading(true);
    setError('');
    try {
      const res = await api.get(`/inventory/movements/me?page=${pageIndex}&size=${size}`);
      setLogs(res.data.content || []);
      setTotalPages(res.data.totalPages || 0);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to fetch activity logs');
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchLogs(page);
  }, [page]);

  const formatDate = (isoString) => {
    if (!isoString) return '';
    const d = new Date(isoString);
    return d.toLocaleString();
  };

  const getActionBadge = (type) => {
    switch(type) {
      case 'RECEIVE': return <span style={{ background: 'var(--success)', color: 'white', padding: '6px 12px', borderRadius: '12px', fontSize: '0.85rem', fontWeight: '800' }}>PUTAWAY</span>;
      case 'RESERVE': return <span style={{ background: 'var(--warning)', color: 'white', padding: '6px 12px', borderRadius: '12px', fontSize: '0.85rem', fontWeight: '800' }}>RESERVE</span>;
      case 'PICK': return <span style={{ background: 'var(--accent-primary)', color: 'white', padding: '6px 12px', borderRadius: '12px', fontSize: '0.85rem', fontWeight: '800' }}>PICK</span>;
      case 'RELEASE': return <span style={{ background: 'var(--danger)', color: 'white', padding: '6px 12px', borderRadius: '12px', fontSize: '0.85rem', fontWeight: '800' }}>RELEASE</span>;
      default: return <span style={{ background: 'var(--text-muted)', color: 'white', padding: '6px 12px', borderRadius: '12px', fontSize: '0.85rem', fontWeight: '800' }}>{type}</span>;
    }
  };

  return (
    <div className="card animate-fade-in" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', margin: 0 }}>
          <Activity size={24} color="var(--accent-primary)" />
          My Activity Log
        </h2>
        
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          {error && <span style={{ color: 'var(--danger-color)', fontWeight: 'bold', fontSize: '0.9rem' }}>{error}</span>}
          
          <div className="control-pill" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'var(--bg-primary)', padding: '0.4rem 0.75rem', borderRadius: '100px', border: '1px solid var(--border-color)' }}>
            <button className="btn btn-secondary" style={{ borderRadius: '50px', padding: '0.2rem 0.6rem', fontSize: '0.8rem' }} disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>
              <ChevronLeft size={16} />
            </button>
            <span style={{ fontWeight: '600', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
              Page {page + 1} / {Math.max(1, totalPages)}
            </span>
            <button className="btn btn-secondary" style={{ borderRadius: '50px', padding: '0.2rem 0.6rem', fontSize: '0.8rem' }} disabled={page >= totalPages - 1 || loading} onClick={() => setPage(page + 1)}>
              <ChevronRight size={16} />
            </button>
          </div>

          <button className="btn btn-blue" onClick={() => fetchLogs(page)} disabled={loading} style={{ height: '36px', padding: '0 1rem', borderRadius: '10px', fontSize: '0.9rem', fontWeight: 'bold' }}>
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
            Refresh
          </button>
        </div>
      </div>

      <div className="card-body" style={{ flex: 1, overflow: 'auto', padding: 0 }}>
        <div className="table-container" style={{ margin: 0, borderRadius: 0, border: 'none' }}>
          <table className="inventory-table">
            <thead>
              <tr>
                <th>Date & Time</th>
                <th>Action</th>
                <th>SKU</th>
                <th>Qty</th>
                <th>Location</th>
                <th>Container</th>
                <th>Task ID</th>
                <th>SAP Sync</th>
              </tr>
            </thead>
            <tbody>
              {loading && logs.length === 0 ? (
                <tr>
                  <td colSpan="8" style={{ textAlign: 'center', padding: '4rem', color: 'var(--text-muted)' }}>
                    Loading activity logs...
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan="8" style={{ textAlign: 'center', padding: '4rem', color: 'var(--text-muted)' }}>
                    You have no recent activity.
                  </td>
                </tr>
              ) : (
                logs.map(log => (
                  <tr key={log.id}>
                    <td style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>{formatDate(log.occurredAt)}</td>
                    <td>{getActionBadge(log.movementType)}</td>
                    <td style={{ fontWeight: '500' }}>{log.sku}</td>
                    <td style={{ fontWeight: 'bold' }}>{log.qty}</td>
                    <td style={{ fontFamily: 'monospace', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                      {log.toLocationId || log.fromLocationId || '-'}
                    </td>
                    <td style={{ fontFamily: 'monospace', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>{log.containerId || '-'}</td>
                    <td style={{ fontFamily: 'monospace', color: 'var(--text-secondary)' }}>{log.referenceId || '-'}</td>
                    <td>
                      {log.syncedToSap ? 
                        <CheckCircle size={18} color="var(--success-color)" /> : 
                        <span title="Pending sync"><RefreshCw size={16} color="var(--warning-color)" /></span>
                      }
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
