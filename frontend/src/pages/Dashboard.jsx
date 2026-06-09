import React, { useState, useEffect } from 'react';
import api from '../api';
import { PackageSearch, RefreshCw } from 'lucide-react';

export default function Dashboard() {
  const [inventory, setInventory] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchInventory = async () => {
    setLoading(true);
    try {
      const res = await api.get('/inventory?page=0&size=50');
      // res.data is PagedResponse
      setInventory(res.data.content || []);
    } catch (err) {
      console.error('Error fetching inventory:', err);
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchInventory();
  }, []);

  return (
    <div className="animate-fade-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <div>
          <h2 className="text-gradient">Warehouse Dashboard</h2>
        </div>
        <button className="btn btn-secondary" onClick={fetchInventory} disabled={loading}>
          <RefreshCw size={18} className={loading ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      <div className="glass-panel" style={{ padding: '0' }}>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>SKU</th>
                <th>Location</th>
                <th>Qty On Hand</th>
                <th>Qty Reserved</th>
                <th>Available</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {loading && inventory.length === 0 ? (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                    <PackageSearch size={32} style={{ margin: '0 auto 1rem', display: 'block' }} />
                    Loading inventory...
                  </td>
                </tr>
              ) : inventory.length === 0 ? (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                    No inventory found. Use 'Receive Stock' to add items.
                  </td>
                </tr>
              ) : (
                inventory.map((item) => (
                  <tr key={item.id}>
                    <td style={{ fontWeight: '500' }}>{item.sku}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{item.locationId}</td>
                    <td>{item.qtyOnHand}</td>
                    <td>{item.qtyReserved}</td>
                    <td style={{ fontWeight: '600', color: 'var(--accent-primary)' }}>{item.qtyOnHand - item.qtyReserved}</td>
                    <td>
                      {item.qtyOnHand - item.qtyReserved > 0 ? (
                        <span className="badge badge-success">Available</span>
                      ) : (
                        <span className="badge badge-warning">Out of Stock</span>
                      )}
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
