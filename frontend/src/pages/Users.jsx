/**
 * Component for user management, allowing creation, editing, status toggle, and batch permission updates.
 */
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api';
import { UserPlus, UserCheck, UserX, Shield, Edit2, Check, X, ArrowLeft, Users as UsersIcon } from 'lucide-react';

const ROLES = ['WORKER', 'SUPERVISOR', 'MANAGER', 'PICKER_ONLY'];
const PERMISSIONS = [
  'CAN_PICK',
  'CAN_PUTAWAY',
  'CAN_MANAGE_INVENTORY',
  'CAN_MANAGE_USERS'
];

const DEFAULT_ROLE_PERMS = {
  MANAGER: ['CAN_PICK', 'CAN_PUTAWAY', 'CAN_MANAGE_INVENTORY', 'CAN_MANAGE_USERS'],
  SUPERVISOR: ['CAN_PICK', 'CAN_PUTAWAY', 'CAN_MANAGE_INVENTORY'],
  WORKER: ['CAN_PICK', 'CAN_PUTAWAY'],
  PICKER_ONLY: ['CAN_PICK']
};

const roleBadgeStyle = (role) => {
  const colors = {
    MANAGER:    { background: '#dbeafe', color: '#1e40af' },
    SUPERVISOR: { background: '#fef9c3', color: '#854d0e' },
    WORKER:     { background: '#dcfce7', color: '#166534' },
    PICKER_ONLY: { background: '#f3e8ff', color: '#6b21a8' },
  };
  return {
    padding: '2px 10px',
    borderRadius: '999px',
    fontSize: '0.75rem',
    fontWeight: '600',
    ...(colors[role] || { background: '#f3f4f6', color: '#374151' }),
  };
};

export default function Users() {
  const navigate = useNavigate();
  const [users, setUsers]       = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  
  const [selectedUserIds, setSelectedUserIds] = useState(new Set());

  const [showForm, setShowForm] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editUserId, setEditUserId] = useState(null);

  const [showBatchForm, setShowBatchForm] = useState(false);
  const [batchPermissions, setBatchPermissions] = useState([]);
  const [touchedPermissions, setTouchedPermissions] = useState(new Set());

  const [formData, setFormData] = useState({ 
    employeeId: '', 
    username: '', 
    password: '', 
    role: 'WORKER',
    permissions: DEFAULT_ROLE_PERMS['WORKER']
  });
  
  const [formError, setFormError]     = useState('');
  const [formSuccess, setFormSuccess] = useState('');
  const [submitting, setSubmitting]   = useState(false);

  const fetchUsers = async () => {
    try {
      const res = await api.get('/users');
      setUsers(res.data);
    } catch {
      setError('Failed to load users.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchUsers(); }, []);

  const handleRoleChange = (newRole) => {
    setFormData({
      ...formData,
      role: newRole,
      permissions: DEFAULT_ROLE_PERMS[newRole] || []
    });
  };

  const handlePermToggle = (perm) => {
    const hasPerm = formData.permissions.includes(perm);
    if (hasPerm) {
      setFormData({ ...formData, permissions: formData.permissions.filter(p => p !== perm) });
    } else {
      setFormData({ ...formData, permissions: [...formData.permissions, perm] });
    }
  };

  const handleBatchPermToggle = (perm) => {
    const newTouched = new Set(touchedPermissions);
    newTouched.add(perm);
    setTouchedPermissions(newTouched);

    if (batchPermissions.includes(perm)) {
      setBatchPermissions(batchPermissions.filter(p => p !== perm));
    } else {
      setBatchPermissions([...batchPermissions, perm]);
    }
  };

  const openCreate = () => {
    setIsEditing(false);
    setEditUserId(null);
    setFormData({ employeeId: '', username: '', password: '', role: 'WORKER', permissions: DEFAULT_ROLE_PERMS['WORKER'] });
    setFormError('');
    setFormSuccess('');
    setShowBatchForm(false);
    setShowForm(true);
  };

  const openEdit = (user) => {
    setIsEditing(true);
    setEditUserId(user.id);
    setFormData({ 
      employeeId: user.employeeId, 
      username: user.username, 
      password: '',
      role: user.role,
      permissions: user.permissions || []
    });
    setFormError('');
    setFormSuccess('');
    setShowBatchForm(false);
    setShowForm(true);
  };

  const openBatchEdit = () => {
    setFormError('');
    setFormSuccess('');
    setBatchPermissions([]);
    setTouchedPermissions(new Set());
    setShowForm(false);
    setShowBatchForm(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setFormError('');
    setFormSuccess('');

    try {
      if (isEditing) {
        await api.put(`/users/${editUserId}`, {
          employeeId: formData.employeeId,
          username: formData.username,
          role: formData.role,
          permissions: formData.permissions
        });
        setFormSuccess(`User "${formData.username}" updated successfully.`);
      } else {
        if (!formData.password) {
          setFormError('Password is required for new users.');
          setSubmitting(false);
          return;
        }
        await api.post('/users', formData);
        setFormSuccess(`User "${formData.username}" created successfully.`);
      }
      setShowForm(false);
      fetchUsers();
    } catch (err) {
      if (err.response?.status === 409) {
        setFormError('Username already exists.');
      } else {
        setFormError(err.response?.data?.message || `Failed to ${isEditing ? 'update' : 'create'} user.`);
      }
    }
    setSubmitting(false);
  };

  const handleBatchSubmit = async (mode) => {
    setSubmitting(true);
    setFormError('');
    setFormSuccess('');

    try {
      const userIds = [...selectedUserIds];

      // Single atomic request — the backend handles all users in one transaction
      await api.patch('/users/batch/permissions', {
        userIds,
        permissions: batchPermissions,
        mode,
      });

      setFormSuccess(`Successfully updated permissions for ${userIds.length} users.`);
      setShowBatchForm(false);
      setSelectedUserIds(new Set());
      fetchUsers();
    } catch (err) {
      setFormError(err.response?.data?.message || 'Failed to update permissions. No changes were applied.');
    }
    setSubmitting(false);
  };

  const toggleActive = async (user) => {
    const endpoint = user.isActive
      ? `/users/${user.id}/deactivate`
      : `/users/${user.id}/activate`;
    try {
      await api.patch(endpoint);
      fetchUsers();
    } catch {
      setError('Failed to update user status.');
    }
  };

  const toggleUserSelection = (userId) => {
    const newSelected = new Set(selectedUserIds);
    if (newSelected.has(userId)) {
      newSelected.delete(userId);
    } else {
      newSelected.add(userId);
    }
    setSelectedUserIds(newSelected);
  };

  const toggleAllSelection = () => {
    if (selectedUserIds.size === users.length) {
      setSelectedUserIds(new Set());
    } else {
      setSelectedUserIds(new Set(users.map(u => u.id)));
    }
  };

  return (
    <div className="animate-fade-in" style={{ maxWidth: '1000px', margin: '0 auto', paddingTop: '1rem', paddingBottom: '3rem' }}>
      
      <div style={{ marginBottom: '2rem' }}>
        <button 
          onClick={() => navigate('/')} 
          className="btn" 
          style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', background: 'transparent', border: 'none', color: 'var(--text-secondary)', padding: '0 0 1rem 0', cursor: 'pointer', fontWeight: '600' }}
        >
          <ArrowLeft size={18} /> Back to Dashboard
        </button>
        
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Shield size={26} color="var(--accent-primary)" /> User & Permissions Management
          </h2>
          
          <div style={{ display: 'flex', gap: '1rem' }}>
            {selectedUserIds.size > 0 && !showBatchForm && !showForm && (
              <button
                className="btn btn-warning"
                style={{ padding: '0.5rem 1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}
                onClick={openBatchEdit}
              >
                <UsersIcon size={16} /> Batch Edit ({selectedUserIds.size})
              </button>
            )}
            {!showForm && !showBatchForm && (
              <button
                className="btn btn-primary"
                style={{ padding: '0.5rem 1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}
                onClick={openCreate}
              >
                <UserPlus size={16} /> Add User
              </button>
            )}
          </div>
        </div>
      </div>

      {showBatchForm && (
        <div className="glass-panel" style={{ marginBottom: '2rem', border: '2px solid var(--warning)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <UsersIcon size={20} color="var(--warning)"/> Batch Edit Permissions ({selectedUserIds.size} users)
            </h3>
            <button className="btn btn-secondary" onClick={() => setShowBatchForm(false)} style={{ padding: '0.3rem', borderRadius: '50%' }}>
              <X size={20} />
            </button>
          </div>

          {formError && (
            <div style={{ padding: '0.75rem 1rem', background: 'rgba(239,68,68,0.08)', color: '#dc2626', borderRadius: '6px', marginBottom: '1rem' }}>
              {formError}
            </div>
          )}

          <div style={{ marginBottom: '1.5rem', padding: '1.5rem', background: 'rgba(255,255,255,0.8)', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
            <p style={{ margin: '0 0 1rem 0', color: 'var(--text-secondary)' }}>
              Select the permissions you want to modify. You can then choose to securely <strong>Update</strong> only the changed fields across the selected users, or entirely <strong>Overwrite</strong> their permissions.
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              {PERMISSIONS.map(perm => (
                <label key={perm} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', padding: '0.5rem', background: touchedPermissions.has(perm) ? 'rgba(59,130,246,0.1)' : 'transparent', borderRadius: '6px' }}>
                  <input 
                    type="checkbox" 
                    checked={batchPermissions.includes(perm)}
                    onChange={() => handleBatchPermToggle(perm)}
                    style={{ width: '18px', height: '18px', accentColor: 'var(--accent-primary)' }}
                  />
                  <span style={{ fontSize: '0.95rem', fontWeight: touchedPermissions.has(perm) ? 'bold' : 'normal', color: 'var(--text-primary)' }}>
                    {perm} {touchedPermissions.has(perm) && <span style={{ color: 'var(--accent-primary)', fontSize: '0.75rem', marginLeft: '0.5rem' }}>(Modified)</span>}
                  </span>
                </label>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowBatchForm(false)}>
              Cancel
            </button>
            <button type="button" className="btn btn-warning" onClick={() => handleBatchSubmit('OVERWRITE')} disabled={submitting}>
              Overwrite All
            </button>
            <button type="button" className="btn btn-primary" onClick={() => handleBatchSubmit('UPDATE')} disabled={submitting || touchedPermissions.size === 0}>
              Update Modified Only
            </button>
          </div>
        </div>
      )}

      {showForm && (
        <div className="glass-panel" style={{ marginBottom: '2rem', border: '2px solid var(--accent-primary)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <h3 style={{ margin: 0 }}>{isEditing ? 'Edit User Permissions' : 'New User'}</h3>
            <button className="btn btn-secondary" onClick={() => setShowForm(false)} style={{ padding: '0.3rem', borderRadius: '50%' }}>
              <X size={20} />
            </button>
          </div>

          {formError && (
            <div style={{ padding: '0.75rem 1rem', background: 'rgba(239,68,68,0.08)', color: '#dc2626', borderRadius: '6px', marginBottom: '1rem' }}>
              {formError}
            </div>
          )}
          
          <form onSubmit={handleSubmit}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label>Employee ID</label>
                <input
                  type="text" required
                  placeholder="EMP-005"
                  value={formData.employeeId}
                  onChange={e => setFormData({ ...formData, employeeId: e.target.value })}
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label>Username</label>
                <input
                  type="text" required
                  placeholder="john.doe"
                  value={formData.username}
                  onChange={e => setFormData({ ...formData, username: e.target.value })}
                />
              </div>
              {!isEditing && (
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label>Password</label>
                  <input
                    type="password" required={!isEditing}
                    placeholder="Minimum 8 characters"
                    value={formData.password}
                    onChange={e => setFormData({ ...formData, password: e.target.value })}
                  />
                </div>
              )}
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label>Organizational Role (Template)</label>
                <select value={formData.role} onChange={e => handleRoleChange(e.target.value)} style={{ width: '100%', padding: '0.75rem', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                  {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                </select>
              </div>
            </div>

            <div style={{ marginBottom: '1.5rem', padding: '1.5rem', background: 'rgba(255,255,255,0.8)', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
              <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '1rem', color: 'var(--text-secondary)' }}>Custom Permissions</label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                {PERMISSIONS.map(perm => (
                  <label key={perm} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                    <input 
                      type="checkbox" 
                      checked={formData.permissions.includes(perm)}
                      onChange={() => handlePermToggle(perm)}
                      style={{ width: '18px', height: '18px', accentColor: 'var(--accent-primary)' }}
                    />
                    <span style={{ fontSize: '0.95rem', color: 'var(--text-primary)' }}>{perm}</span>
                  </label>
                ))}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
              <button type="button" className="btn btn-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {isEditing ? <Edit2 size={16} /> : <UserPlus size={16} />}
                {submitting ? 'Saving...' : (isEditing ? 'Save Changes' : 'Create User')}
              </button>
            </div>
          </form>
        </div>
      )}

      {formSuccess && (
        <div style={{ padding: '0.75rem 1rem', background: 'rgba(16,185,129,0.08)', color: '#059669', borderRadius: '6px', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: '500' }}>
          <Check size={18} /> {formSuccess}
        </div>
      )}

      {error && (
        <div style={{ padding: '0.75rem 1rem', background: 'rgba(239,68,68,0.08)', color: '#dc2626', borderRadius: '6px', marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      <div className="glass-panel" style={{ padding: 0, overflow: 'hidden' }}>
        {loading ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>Loading users...</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-color)', textAlign: 'left', background: 'rgba(0,0,0,0.02)' }}>
                <th style={{ padding: '1rem 1.25rem', width: '40px' }}>
                  <input 
                    type="checkbox" 
                    checked={users.length > 0 && selectedUserIds.size === users.length}
                    onChange={toggleAllSelection}
                    style={{ width: '16px', height: '16px', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
                  />
                </th>
                {['User', 'Role', 'Custom Permissions', 'Status', 'Actions'].map(h => (
                  <th key={h} style={{ padding: '1rem 1.25rem', fontSize: '0.8rem', fontWeight: '600', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {users.map((u, i) => (
                <tr key={u.id} style={{ borderBottom: i < users.length - 1 ? '1px solid var(--border-color)' : 'none', opacity: u.isActive ? 1 : 0.5, transition: 'background 0.2s', background: selectedUserIds.has(u.id) ? 'rgba(59,130,246,0.03)' : 'transparent' }}>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <input 
                      type="checkbox" 
                      checked={selectedUserIds.has(u.id)}
                      onChange={() => toggleUserSelection(u.id)}
                      style={{ width: '16px', height: '16px', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
                    />
                  </td>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <div style={{ fontWeight: '600', color: 'var(--text-primary)' }}>{u.username}</div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontFamily: 'monospace' }}>{u.employeeId}</div>
                  </td>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <span style={roleBadgeStyle(u.role)}>{u.role}</span>
                  </td>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem' }}>
                      {(u.permissions || []).map(p => (
                        <span key={p} style={{ background: 'var(--bg-primary)', border: '1px solid var(--border-color)', padding: '2px 6px', borderRadius: '4px', fontSize: '0.7rem', color: 'var(--text-secondary)', fontWeight: '500' }}>
                          {p.replace('CAN_', '')}
                        </span>
                      ))}
                      {(!u.permissions || u.permissions.length === 0) && (
                        <span style={{ fontSize: '0.8rem', color: 'var(--danger)' }}>No Permissions</span>
                      )}
                    </div>
                  </td>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <span style={{ fontSize: '0.8rem', fontWeight: '600', color: u.isActive ? '#059669' : '#9ca3af', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                      {u.isActive ? <Check size={14}/> : <X size={14}/>} {u.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td style={{ padding: '1rem 1.25rem' }}>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        onClick={() => openEdit(u)}
                        className="btn btn-secondary"
                        style={{ padding: '0.4rem 0.75rem', fontSize: '0.8rem', display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}
                      >
                        <Edit2 size={14} /> Edit
                      </button>
                      <button
                        onClick={() => toggleActive(u)}
                        className="btn btn-secondary"
                        style={{ padding: '0.4rem 0.75rem', fontSize: '0.8rem', display: 'inline-flex', alignItems: 'center', gap: '0.3rem', color: u.isActive ? 'var(--danger)' : 'var(--success)' }}
                      >
                        {u.isActive ? <><UserX size={14} /> Deactivate</> : <><UserCheck size={14} /> Activate</>}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
