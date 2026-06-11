import React, { useState, useEffect, createContext, useContext } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Link, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './AuthContext';
import { Users, LogOut } from 'lucide-react';

// Responsive hook
export const useMediaQuery = (query) => {
  const [matches, setMatches] = useState(false);

  useEffect(() => {
    const media = window.matchMedia(query);
    if (media.matches !== matches) setMatches(media.matches);
    const listener = () => setMatches(media.matches);
    media.addEventListener('change', listener);
    return () => media.removeEventListener('change', listener);
  }, [matches, query]);

  return matches;
};

// Pages
import Login     from './pages/Login';
import Dashboard from './pages/Dashboard';
import Receive   from './pages/Receive';
import Pick      from './pages/Pick';
import UsersPage from './pages/Users';

// Components
import BottomNav from './components/BottomNav';

// Global UI Context for table visibility
export const UIContext = createContext();

export const useUI = () => useContext(UIContext);

export const UIProvider = ({ children }) => {
  const [isTableVisible, setIsTableVisible] = useState(true);
  return (
    <UIContext.Provider value={{ isTableVisible, setIsTableVisible }}>
      {children}
    </UIContext.Provider>
  );
};

const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) return <div>Loading...</div>;
  if (!isAuthenticated) return <Navigate to="/login" state={{ from: location }} replace />;

  return children;
};

const AppLayout = ({ children }) => {
  const { isAuthenticated, logout, canManageUsers } = useAuth();
  
  // Reload protection popup
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      e.preventDefault();
      e.returnValue = ''; // Standard way to trigger browser's reload warning
    };
    
    if (isAuthenticated) {
      window.addEventListener('beforeunload', handleBeforeUnload);
    }
    
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [isAuthenticated]);

  return (
    <div className="app-container" style={{ display: 'flex', flexDirection: 'column', height: '100vh', width: '100vw', padding: 0, margin: 0, overflow: 'hidden' }}>
      
      {/* Top action buttons (Absolute positioned) */}
      {isAuthenticated && (
        <>
          {canManageUsers && (
            <Link 
              to="/users" 
              style={{ position: 'absolute', top: '15px', left: '25px', zIndex: 100, background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 'bold', textDecoration: 'none' }}
            >
              <Users size={20} /> Users
            </Link>
          )}

          <button 
            onClick={() => {
              logout().then(() => {
                window.location.href = '/';
              });
            }} 
            style={{ position: 'absolute', top: '15px', right: '25px', zIndex: 100, background: 'transparent', border: 'none', color: 'var(--danger)', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 'bold' }}
          >
            <LogOut size={20} /> Logout
          </button>
        </>
      )}

      {/* Main Content Area */}
      <main className="main-content" style={{ flex: 1, overflowY: 'hidden', minHeight: 0, padding: '40px 20px 100px 20px', width: '100%', display: 'flex', flexDirection: 'column' }}>
        {children}
      </main>

      {/* Bottom Floating Pill Nav (Hidden on Desktop Mega-View) */}
      {isAuthenticated && (
        <div className="mobile-only">
          <BottomNav />
        </div>
      )}

    </div>
  );
};

export default function App() {
  return (
    <AuthProvider>
      <UIProvider>
        <Router>
          <AppLayout>
            <Routes>
              <Route path="/login"   element={<Login />} />
              <Route path="/"        element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
              <Route path="/receive" element={<ProtectedRoute><Receive /></ProtectedRoute>} />
              <Route path="/pick"    element={<ProtectedRoute><Pick /></ProtectedRoute>} />
              <Route path="/users"   element={<ProtectedRoute><UsersPage /></ProtectedRoute>} />
            </Routes>
          </AppLayout>
        </Router>
      </UIProvider>
    </AuthProvider>
  );
}
