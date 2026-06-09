import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Link, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './AuthContext';
import { LayoutDashboard, PackagePlus, PackageMinus, LogOut } from 'lucide-react';

// Pages
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Receive from './pages/Receive';
import Pick from './pages/Pick';

const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) return <div>Loading...</div>;
  if (!isAuthenticated) return <Navigate to="/login" state={{ from: location }} replace />;

  return children;
};

const Navigation = () => {
  const { logout } = useAuth();
  const location = useLocation();

  return (
    <nav className="navbar">
      <div style={{ fontWeight: '700', fontSize: '1.25rem', color: 'var(--accent-primary)' }}>
      </div>
      <div className="nav-links">
        <Link to="/" className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}>
          <LayoutDashboard size={18} style={{ display: 'inline', marginRight: '6px', verticalAlign: 'text-bottom' }} />
          Dashboard
        </Link>
        <Link to="/receive" className={`nav-link ${location.pathname === '/receive' ? 'active' : ''}`}>
          <PackagePlus size={18} style={{ display: 'inline', marginRight: '6px', verticalAlign: 'text-bottom' }} />
          Receive
        </Link>
        <Link to="/pick" className={`nav-link ${location.pathname === '/pick' ? 'active' : ''}`}>
          <PackageMinus size={18} style={{ display: 'inline', marginRight: '6px', verticalAlign: 'text-bottom' }} />
          Pick
        </Link>
        <button onClick={logout} className="btn btn-secondary" style={{ padding: '0.5rem 1rem', fontSize: '0.875rem' }}>
          <LogOut size={16} /> Logout
        </button>
      </div>
    </nav>
  );
};

const AppLayout = ({ children }) => {
  const { isAuthenticated } = useAuth();
  return (
    <div className="app-container">
      {isAuthenticated && <Navigation />}
      <main className="main-content">
        {children}
      </main>
    </div>
  );
};

export default function App() {
  return (
    <AuthProvider>
      <Router>
        <AppLayout>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
            <Route path="/receive" element={<ProtectedRoute><Receive /></ProtectedRoute>} />
            <Route path="/pick" element={<ProtectedRoute><Pick /></ProtectedRoute>} />
          </Routes>
        </AppLayout>
      </Router>
    </AuthProvider>
  );
}
