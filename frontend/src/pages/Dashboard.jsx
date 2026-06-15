/**
 * Main dashboard component displaying inventory search, pagination, dynamic layouts, and layout/column toggles.
 */
import React, { useState, useEffect } from 'react';
import api from '../api';
import { PackageSearch, RefreshCw, ChevronLeft, ChevronRight, Search, ArrowUpDown, SlidersHorizontal, Type, Lock } from 'lucide-react';
import { useUI, useMediaQuery } from '../App';
import { useAuth } from '../AuthContext';
import Receive from './Receive';
import Pick from './Pick';
import ActivityLog from './ActivityLog';

export default function Dashboard() {
  const { isTableVisible } = useUI();
  const { canPutaway, canPick, canManageInventory } = useAuth();
  const [inventory, setInventory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const [activeTab, setActiveTab] = useState('inventory');
  
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [size, setSize] = useState(20);
  const [sliderSize, setSliderSize] = useState(20);

  const [searchTerm, setSearchTerm] = useState('');
  const [sortConfig, setSortConfig] = useState({ key: null, direction: 'asc' });

  const [textSize, setTextSize] = useState('medium');

  const [visibleColumns, setVisibleColumns] = useState(() => {
    const saved = localStorage.getItem('dashboard_visibleColumns');
    if (saved) return JSON.parse(saved);
    return {
      sku: true,
      locationId: true,
      containerId: true,
      qtyOnHand: true,
      qtyReserved: true,
      available: true,
      status: true
    };
  });

  useEffect(() => {
    localStorage.setItem('dashboard_visibleColumns', JSON.stringify(visibleColumns));
  }, [visibleColumns]);

  const toggleColumn = (col) => {
    setVisibleColumns(prev => ({ ...prev, [col]: !prev[col] }));
  };

  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('');
  const [leftPanelWidth, setLeftPanelWidth] = useState(450);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearchTerm(searchTerm);
      setPage(0); // Reset to page 0 on new search
    }, 300);
    return () => clearTimeout(handler);
  }, [searchTerm]);

  const fetchInventory = async (currentPage, currentSize, currentSearch, currentSort) => {
    setLoading(true);
    try {
      let url = `/inventory?page=${currentPage}&size=${currentSize}`;
      if (currentSearch) url += `&search=${encodeURIComponent(currentSearch)}`;
      if (currentSort.key) {
        url += `&sortBy=${encodeURIComponent(currentSort.key)}&sortDir=${encodeURIComponent(currentSort.direction)}`;
      }
      const res = await api.get(url);
      setInventory(res.data.content || []);
      setTotalPages(res.data.totalPages || 0);
      setTotalElements(res.data.totalElements || 0);
    } catch (err) {
      console.error('Error fetching inventory:', err);
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchInventory(page, size, debouncedSearchTerm, sortConfig);
  }, [page, size, debouncedSearchTerm, sortConfig]);

  const handleSort = (key) => {
    let direction = 'asc';
    if (sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const getSortIndicator = (key) => {
    if (sortConfig.key !== key) return <ArrowUpDown size={14} style={{ opacity: 0.3, marginLeft: '4px' }} />;
    return <span style={{ marginLeft: '4px', fontSize: '0.8rem' }}>{sortConfig.direction === 'asc' ? '▲' : '▼'}</span>;
  };

  const getFontSize = () => {
    if (textSize === 'small') return '0.85rem';
    if (textSize === 'large') return '1.2rem';
    return '1rem';
  };

  const isDesktop = useMediaQuery('(min-width: 1400px)');

  const tableContent = canManageInventory ? (
    <>
      {isTableVisible ? (
        <div className="table-area animate-fade-in" style={{ '--table-font-size': getFontSize() }}>
          
          <div className="sticky-search-header" style={{ flexDirection: 'column', alignItems: 'stretch', gap: '0.5rem', padding: '1rem' }}>
            
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem' }}>
              <div className="search-input-wrapper" style={{ flex: 1 }}>
                <Search size={20} className="search-icon" />
                <input 
                  type="text" 
                  placeholder="Search by SKU or Location..." 
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{ padding: '0.6rem 1rem 0.6rem 2.5rem', fontSize: '1rem' }}
                />
              </div>
              
              {activeTab === 'inventory' && (
                <button className="btn btn-blue" onClick={() => fetchInventory(page, size, debouncedSearchTerm, sortConfig)} disabled={loading} style={{ height: '42px', padding: '0 1rem', borderRadius: '10px', fontSize: '1rem', fontWeight: 'bold' }}>
                  <RefreshCw size={18} className={loading ? 'animate-spin' : ''} />
                  Refresh
                </button>
              )}
            </div>

            <div style={{ display: 'flex', gap: '1rem', marginBottom: '1.5rem', background: 'var(--bg-primary)', padding: '0.5rem', borderRadius: '16px', width: 'fit-content', border: '1px solid var(--border-color)' }}>
              <button 
                className={`btn ${activeTab === 'inventory' ? 'btn-primary' : 'btn-secondary'}`} 
                onClick={() => setActiveTab('inventory')}
                style={{ borderRadius: '12px', padding: '0.5rem 1.5rem', fontWeight: 'bold' }}
              >
                Live Inventory
              </button>
              <button 
                className={`btn ${activeTab === 'activity' ? 'btn-primary' : 'btn-secondary'}`} 
                onClick={() => setActiveTab('activity')}
                style={{ borderRadius: '12px', padding: '0.5rem 1.5rem', fontWeight: 'bold' }}
              >
                My Activity Log
              </button>
            </div>

            {activeTab === 'inventory' ? (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem' }}>
                  
              <div className="control-pill" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', background: 'var(--bg-primary)', padding: '0.4rem 0.75rem', borderRadius: '100px', border: '1px solid var(--border-color)' }}>
                <button className="btn btn-secondary btn-large" style={{ borderRadius: '50px', padding: '0.4rem 1rem', minHeight: '36px', fontSize: '0.9rem' }} disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>
                  <ChevronLeft size={18} /> Prev
                </button>
                <span style={{ fontWeight: '600', color: 'var(--text-secondary)', fontSize: '0.95rem', minWidth: '90px', textAlign: 'center' }}>
                  Page {page + 1} of {Math.max(1, totalPages)}
                </span>
                <button className="btn btn-secondary btn-large" style={{ borderRadius: '50px', padding: '0.4rem 1rem', minHeight: '36px', fontSize: '0.9rem' }} disabled={page >= totalPages - 1 || loading} onClick={() => setPage(page + 1)}>
                  Next <ChevronRight size={18} />
                </button>
              </div>

              <div className="control-pill" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', background: 'var(--bg-primary)', padding: '0.4rem 1rem', borderRadius: '100px', border: '1px solid var(--border-color)' }}>
                <SlidersHorizontal size={18} color="var(--text-secondary)" />
                <span style={{ fontWeight: '600', color: 'var(--text-secondary)', fontSize: '0.95rem' }}>Size: {sliderSize}</span>
                <input 
                  type="range" 
                  min="10" 
                  max="100" 
                  step="10"
                  value={sliderSize}
                  onChange={(e) => {
                    setSliderSize(Number(e.target.value));
                  }}
                  onPointerUp={(e) => {
                    setSize(Number(e.target.value));
                    setPage(0);
                  }}
                  onTouchEnd={(e) => {
                    setSize(Number(e.target.value));
                    setPage(0);
                  }}
                  style={{ width: '100px', margin: 0 }}
                />
              </div>

              <div className="control-pill desktop-only" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', background: 'var(--bg-primary)', padding: '0.4rem 0.75rem', borderRadius: '100px', border: '1px solid var(--border-color)' }}>
                <Type size={18} color="var(--text-secondary)" />
                <div className="text-size-toggles">
                  <button className={`text-size-btn ${textSize === 'small' ? 'active' : ''}`} onClick={() => setTextSize('small')} style={{ padding: '6px 12px', fontSize: '0.95rem', borderRadius: '50px' }}>S</button>
                  <button className={`text-size-btn ${textSize === 'medium' ? 'active' : ''}`} onClick={() => setTextSize('medium')} style={{ padding: '6px 12px', fontSize: '0.95rem', borderRadius: '50px' }}>M</button>
                  <button className={`text-size-btn ${textSize === 'large' ? 'active' : ''}`} onClick={() => setTextSize('large')} style={{ padding: '6px 12px', fontSize: '0.95rem', borderRadius: '50px' }}>L</button>
                </div>
              </div>

              <div className="control-pill desktop-only" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', background: 'var(--bg-primary)', padding: '0.4rem 1rem', borderRadius: '100px', border: '1px solid var(--border-color)' }}>
                <span style={{ fontWeight: '600', color: 'var(--text-secondary)', fontSize: '0.95rem' }}>Forms Width</span>
                <input 
                  type="range" 
                  min="350" 
                  max="800" 
                  step="10"
                  value={leftPanelWidth}
                  onChange={(e) => setLeftPanelWidth(Number(e.target.value))}
                  style={{ width: '80px', margin: 0 }}
                />
              </div>

              <div className="control-pill" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'var(--bg-primary)', padding: '0.4rem 0.75rem', borderRadius: '100px', border: '1px solid var(--border-color)' }}>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Cols:</span>
                {['sku', 'locationId', 'containerId', 'qtyOnHand', 'qtyReserved', 'available', 'status'].map(col => (
                  <button 
                    key={col}
                    className={`pill-btn ${visibleColumns[col] ? 'active' : ''}`}
                    onClick={() => toggleColumn(col)}
                    style={{ border: 'none', background: visibleColumns[col] ? 'var(--accent-primary)' : 'var(--bg-hover)', color: visibleColumns[col] ? 'white' : 'var(--text-secondary)', padding: '2px 8px', borderRadius: '12px', cursor: 'pointer', fontSize: '0.8rem', fontWeight: 'bold' }}
                  >
                    {col === 'locationId' ? 'Loc' : 
                     col === 'containerId' ? 'Cont' :
                     col === 'qtyOnHand' ? 'OnHand' :
                     col === 'qtyReserved' ? 'Rsvd' : 
                     col === 'available' ? 'Avail' : 
                     col.charAt(0).toUpperCase() + col.slice(1)}
                  </button>
                ))}
              </div>
            </div>
            </>
            ) : (
              <div style={{ height: 'calc(100vh - 250px)' }}>
                <ActivityLog />
              </div>
            )}
          </div>

          {activeTab === 'inventory' && (
          <div className="table-scroll-wrapper" style={{ opacity: loading ? 0.6 : 1, transition: 'opacity 0.2s' }}>
            <table>
              <thead>
                <tr>
                  {visibleColumns.sku && (
                    <th onClick={() => handleSort('sku')} style={{ cursor: 'pointer', userSelect: 'none' }}>
                      SKU {getSortIndicator('sku')}
                    </th>
                  )}
                  {visibleColumns.locationId && (
                    <th onClick={() => handleSort('locationId')} style={{ cursor: 'pointer', userSelect: 'none' }}>
                      Location {getSortIndicator('locationId')}
                    </th>
                  )}
                  {visibleColumns.containerId && (
                    <th onClick={() => handleSort('containerId')} style={{ cursor: 'pointer', userSelect: 'none' }}>
                      Container {getSortIndicator('containerId')}
                    </th>
                  )}
                  {visibleColumns.qtyOnHand && (
                    <th onClick={() => handleSort('qtyOnHand')} style={{ cursor: 'pointer', userSelect: 'none' }}>
                      Qty On Hand {getSortIndicator('qtyOnHand')}
                    </th>
                  )}
                  {visibleColumns.qtyReserved && (
                    <th onClick={() => handleSort('qtyReserved')} style={{ cursor: 'pointer', userSelect: 'none' }}>
                      Qty Reserved {getSortIndicator('qtyReserved')}
                    </th>
                  )}
                  {visibleColumns.available && (
                    <th onClick={() => handleSort('available')} style={{ cursor: 'pointer', userSelect: 'none' }}>
                      Available {getSortIndicator('available')}
                    </th>
                  )}
                  {visibleColumns.status && <th>Status</th>}
                </tr>
              </thead>
              <tbody>
                {loading && inventory.length === 0 ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', padding: '4rem', color: 'var(--text-muted)' }}>
                      <PackageSearch size={40} style={{ margin: '0 auto 1rem', display: 'block' }} />
                      Loading inventory...
                    </td>
                  </tr>
                ) : inventory.length === 0 ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', padding: '4rem', color: 'var(--text-muted)' }}>
                      {searchTerm ? `No results found for "${searchTerm}"` : "No inventory found."}
                    </td>
                  </tr>
                ) : (
                  inventory.map((item) => {
                    const available = item.qtyOnHand - item.qtyReserved;
                    return (
                      <tr key={`${item.sku}-${item.locationId}-${item.containerId || 'none'}`}>
                        {visibleColumns.sku && <td style={{ fontWeight: '500' }}>{item.sku}</td>}
                        {visibleColumns.locationId && <td style={{ color: 'var(--text-secondary)', fontFamily: 'monospace' }}>{item.locationId}</td>}
                        {visibleColumns.containerId && <td style={{ color: 'var(--text-secondary)', fontFamily: 'monospace' }}>{item.containerId || '-'}</td>}
                        {visibleColumns.qtyOnHand && <td>{item.qtyOnHand}</td>}
                        {visibleColumns.qtyReserved && <td>{item.qtyReserved}</td>}
                        {visibleColumns.available && <td style={{ fontWeight: '600', color: 'var(--accent-primary)' }}>{available}</td>}
                        {visibleColumns.status && (
                          <td>
                            {available > 0 ? (
                              <span className="badge badge-success">Available</span>
                            ) : (
                              <span className="badge badge-warning">Out of Stock</span>
                            )}
                          </td>
                        )}
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          )}
        </div>
      ) : (
        <div style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-muted)' }}>
          <PackageSearch size={64} style={{ opacity: 0.3, margin: '0 auto 1rem' }} />
          <h2>Dashboard Hidden</h2>
          <p>Click "Dash" at the bottom to view inventory table.</p>
        </div>
      )}
    </>
  ) : (
    <div style={{ padding: '6rem 2rem', textAlign: 'center', color: 'var(--danger)', background: 'var(--bg-secondary)', borderRadius: '24px', border: '1px solid var(--danger)', margin: 'auto' }}>
      <Lock size={64} style={{ margin: '0 auto 1.5rem' }} />
      <h2>Access Restricted</h2>
      <p style={{ color: 'var(--text-secondary)' }}>You do not have permission to view the global inventory table.</p>
    </div>
  );

  return (
    <div 
      className={`dashboard-container animate-fade-in ${isDesktop ? 'dashboard-mega-layout' : ''}`} 
      style={{
        ...(!isDesktop ? { justifyContent: 'center' } : {}),
        ...(isDesktop ? { gridTemplateColumns: `${leftPanelWidth}px 1fr` } : {})
      }}
    >
      
      {isDesktop && (
        <div className="mega-col-left">
          {canPutaway && <Receive embedded={true} onSuccess={() => fetchInventory(page, size)} />}
          {canPick && <Pick embedded={true} onSuccess={() => fetchInventory(page, size)} />}
        </div>
      )}

      <div className="mega-col-center">
        {tableContent}
      </div>

    </div>
  );
}
