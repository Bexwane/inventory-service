import React, { createContext, useState, useContext, useEffect } from 'react';
import api from './api';

const AuthContext = createContext(null);

// Decode a JWT payload without a library — the payload is just base64
function decodeToken(token) {
  try {
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload));
  } catch {
    return null;
  }
}

export const AuthProvider = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [userRole, setUserRole] = useState(null); // 'ROLE_MANAGER', 'ROLE_WORKER', etc.
  const [permissions, setPermissions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      const decoded = decodeToken(token);
      if (decoded) {
        setIsAuthenticated(true);
        // roles is an array like ['ROLE_MANAGER', 'CAN_PICK', 'CAN_PUTAWAY']
        const rolesArray = decoded.roles || [];
        setUserRole(rolesArray.find(r => r.startsWith('ROLE_')) || null);
        setPermissions(rolesArray);
      }
    }
    setLoading(false);
  }, []);

  const login = async (username, password) => {
    try {
      const response = await api.post('/auth/login', { username, password });
      const { accessToken, refreshToken } = response.data;
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);

      const decoded = decodeToken(accessToken);
      const rolesArray = decoded?.roles || [];
      setUserRole(rolesArray.find(r => r.startsWith('ROLE_')) || null);
      setPermissions(rolesArray);
      setIsAuthenticated(true);
      return true;
    } catch (error) {
      console.error('Login failed', error);
      return false;
    }
  };

  const logout = async () => {
    try {
      await api.post('/auth/logout');
    } catch (e) {
      // Ignore errors on logout
    } finally {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      setIsAuthenticated(false);
      setUserRole(null);
      setPermissions([]);
    }
  };

  const isManager = userRole === 'ROLE_MANAGER';
  const canPutaway = permissions.includes('CAN_PUTAWAY');
  const canPick = permissions.includes('CAN_PICK');
  const canManageInventory = permissions.includes('CAN_MANAGE_INVENTORY');
  const canManageUsers = permissions.includes('CAN_MANAGE_USERS');

  return (
    <AuthContext.Provider value={{ isAuthenticated, loading, login, logout, userRole, isManager, canPutaway, canPick, canManageInventory, canManageUsers }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
